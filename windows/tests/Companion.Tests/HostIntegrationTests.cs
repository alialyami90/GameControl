using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json.Nodes;
using GameController.Companion.Config;
using GameController.Companion.Input;

namespace GameController.Companion.Tests;

/// <summary>Runs the real host on loopback with free ports and talks to it like a phone and like the mod.</summary>
public sealed class HostIntegrationTests : IDisposable
{
    readonly RecordingOutputs _out = new();
    readonly CompanionHost _host;
    readonly ConfigStore _store;
    readonly FakePads _pads = new();

    sealed class FakePads : GameController.Companion.Engine.IBluetoothPads
    {
        public List<GameController.Companion.Engine.BluetoothPad> Now { get; } = new();
        public IReadOnlyList<GameController.Companion.Engine.BluetoothPad> Present() { lock (Now) return Now.ToList(); }
    }

    static int FreeTcp() { var l = new TcpListener(IPAddress.Loopback, 0); l.Start(); int p = ((IPEndPoint)l.LocalEndpoint).Port; l.Stop(); return p; }
    static int FreeUdp() { using var u = new UdpClient(0); return ((IPEndPoint)u.Client.LocalEndPoint!).Port; }

    public HostIntegrationTests()
    {
        _store = new ConfigStore(Path.Combine(Path.GetTempPath(), $"gc-host-{Guid.NewGuid():N}.json"));
        var c = _store.Config;
        c.ServerName = "TEST";
        c.WsPort = FreeTcp(); c.UdpPort = FreeUdp(); c.DiscoveryPort = FreeUdp(); c.BeamNgPort = FreeTcp(); c.OutGaugePort = FreeUdp();
        c.EnableMdns = false;
        _host = new CompanionHost(_store, new InputRouter(_out, _out, _out), new HostOptions { Bind = IPAddress.Loopback, IdleTimeout = TimeSpan.FromSeconds(1.5), BluetoothPads = _pads });
        _host.Pairing.UsePin("424242");
        _host.Start();
    }

    public void Dispose() => _host.Dispose();

    sealed class Phone : IAsyncDisposable
    {
        public readonly ClientWebSocket Ws = new();
        readonly List<JsonObject> _inbox = new();
        Task? _reader;

        public async Task Connect(int port)
        {
            await Ws.ConnectAsync(new Uri($"ws://127.0.0.1:{port}/ws"), CancellationToken.None);
            _reader = Task.Run(async () =>
            {
                var buf = new byte[65536];
                try
                {
                    while (Ws.State == WebSocketState.Open)
                    {
                        var r = await Ws.ReceiveAsync(buf, CancellationToken.None);
                        if (r.MessageType == WebSocketMessageType.Close) break;
                        if (JsonNode.Parse(Encoding.UTF8.GetString(buf, 0, r.Count)) is JsonObject o) lock (_inbox) _inbox.Add(o);
                    }
                }
                catch { }
            });
        }

        public Task Send(string json) => Ws.SendAsync(Encoding.UTF8.GetBytes(json), WebSocketMessageType.Text, true, CancellationToken.None);

        public async Task<JsonObject> Expect(string type, int ms = 3000)
        {
            var until = DateTime.UtcNow.AddMilliseconds(ms);
            while (DateTime.UtcNow < until)
            {
                lock (_inbox)
                {
                    var m = _inbox.FirstOrDefault(x => (string?)x["type"] == type);
                    if (m != null) { _inbox.Remove(m); return m; }
                }
                await Task.Delay(10);
            }
            throw new TimeoutException($"no {type}");
        }

        public async ValueTask DisposeAsync()
        {
            try { await Ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "", CancellationToken.None); } catch { }
            Ws.Dispose();
        }
    }

    async Task<(Phone Phone, string Session, string Token)> Pair(string id = "dev1")
    {
        var p = new Phone();
        await p.Connect(_store.Config.WsPort);
        await p.Send($$"""{"v":1,"type":"hello","deviceId":"{{id}}","deviceName":"T","appVersion":"1","pin":"{{_host.Pairing.Pin}}"}""");
        var w = await p.Expect("welcome");
        return (p, (string)w["session"]!, (string)w["token"]!);
    }

    async Task Udp(string json)
    {
        using var u = new UdpClient(AddressFamily.InterNetwork);
        await u.SendAsync(Encoding.UTF8.GetBytes(json), new IPEndPoint(IPAddress.Loopback, _store.Config.UdpPort));
    }

    async Task WaitFor(Func<bool> cond, int ms = 2000)
    {
        var until = DateTime.UtcNow.AddMilliseconds(ms);
        while (!cond() && DateTime.UtcNow < until) await Task.Delay(10);
    }

    const string Sync = """{"v":1,"type":"profile.sync","profileId":"t","game":"any","bindings":[{"id":"horn","kind":"momentary","action":{"type":"key","keys":["H"]}},{"id":"hz","kind":"latching","action":{"type":"beamng","command":"hazard"}}]}""";

    [Fact]
    public async Task Bad_pin_is_refused()
    {
        await using var p = new Phone();
        await p.Connect(_store.Config.WsPort);
        await p.Send("""{"v":1,"type":"hello","deviceId":"x","deviceName":"x","pin":"000001"}""");
        Assert.Equal("bad_pin", (string?)(await p.Expect("error"))["code"]);
    }

    [Fact]
    public async Task Newer_protocol_version_is_refused()
    {
        await using var p = new Phone();
        await p.Connect(_store.Config.WsPort);
        await p.Send("""{"v":2,"type":"hello","deviceId":"x","pin":"424242"}""");
        Assert.Equal("version_mismatch", (string?)(await p.Expect("error"))["code"]);
    }

    [Fact]
    public async Task Pair_sync_and_inject_over_udp_then_release_on_disconnect()
    {
        var (p, session, token) = await Pair();
        Assert.False(string.IsNullOrEmpty(token));
        await p.Send(Sync);
        Assert.Equal(2, (int)(await p.Expect("profile.ack"))["count"]!);

        await Udp($$"""{"v":1,"k":"wrongkey","s":1,"id":"horn","val":1}""");
        await Udp($$"""{"v":1,"k":"{{session}}","s":2,"id":"horn","val":1}""");
        await WaitFor(() => _out.Log.Count > 0);
        Assert.Equal(new[] { "key H down" }, _out.Log);

        await p.DisposeAsync();
        await WaitFor(() => _out.Log.Count > 1);
        Assert.Equal(new[] { "key H down", "key H up" }, _out.Log);
    }

    [Fact]
    public async Task Input_over_websocket_and_ping()
    {
        var (p, _, _) = await Pair();
        await using var _p = p;
        await p.Send(Sync);
        await p.Expect("profile.ack");
        await p.Send("""{"v":1,"type":"ping","t":7}""");
        Assert.Equal(7.0, (double)(await p.Expect("pong"))["t"]!);
        await p.Send("""{"v":1,"type":"input","s":1,"id":"horn","val":1}""");
        await p.Send("""{"v":1,"type":"input","s":2,"id":"horn","val":0}""");
        await p.Send("""{"v":1,"type":"something.new","x":1}"""); // ignored
        await p.Send("""{"v":1,"type":"input","s":3,"id":"ghost","val":1}""");
        Assert.Equal("unknown_control", (string?)(await p.Expect("error"))["code"]);
        Assert.Equal(new[] { "key H down", "key H up" }, _out.Log);
    }

    [Fact]
    public async Task Token_reconnect_works()
    {
        var (p, _, token) = await Pair("devT");
        await p.DisposeAsync();
        await using var p2 = new Phone();
        await p2.Connect(_store.Config.WsPort);
        await p2.Send($$"""{"v":1,"type":"hello","deviceId":"devT","deviceName":"T","token":"{{token}}"}""");
        await p2.Expect("welcome");
    }

    [Fact]
    public async Task Discovery_reply()
    {
        using var u = new UdpClient(AddressFamily.InterNetwork);
        await u.SendAsync("""{"v":1,"type":"discover"}"""u8.ToArray(), new IPEndPoint(IPAddress.Loopback, _store.Config.DiscoveryPort));
        using var cts = new CancellationTokenSource(2000);
        var r = await u.ReceiveAsync(cts.Token);
        var m = JsonNode.Parse(r.Buffer)!;
        Assert.Equal("discover.reply", (string?)m["type"]);
        Assert.Equal(_store.Config.WsPort, (int)m["wsPort"]!);
    }

    [Fact]
    public async Task BeamNG_state_commands_and_bindings_are_relayed()
    {
        var (p, _, _) = await Pair();
        await using var _p = p;
        using var mod = new TcpClient();
        await mod.ConnectAsync(IPAddress.Loopback, _store.Config.BeamNgPort);
        var w = new StreamWriter(mod.GetStream()) { AutoFlush = true, NewLine = "\n" };
        var r = new StreamReader(mod.GetStream());
        Assert.Equal("state.request", (string?)JsonNode.Parse((await r.ReadLineAsync())!)!["type"]);
        await w.WriteLineAsync("""{"v":1,"type":"hello","role":"beamng","modVersion":"1"}""");
        Assert.True((bool)(await p.Expect("capabilities"))["capabilities"]!["beamng"]!);

        await w.WriteLineAsync("""{"v":1,"type":"commands","list":["hazard.toggle","hazard"]}""");
        Assert.Equal(2, (await p.Expect("beamng.commands"))["list"]!.AsArray().Count);
        await w.WriteLineAsync("""{"v":1,"type":"state","values":{"lights.low":true,"gear":"D"}}""");
        var st = await p.Expect("state");
        Assert.Equal("beamng", (string?)st["source"]);
        Assert.True((bool)st["values"]!["lights.low"]!);

        await p.Send(Sync);
        await p.Expect("profile.ack");
        var snap = await p.Expect("state");
        Assert.True((bool?)snap["full"]);

        await p.Send("""{"v":1,"type":"input","s":1,"id":"hz","val":1}""");
        JsonNode? cmd = null;
        for (int i = 0; i < 5 && cmd?["type"]?.GetValue<string>() != "command"; i++) cmd = JsonNode.Parse((await r.ReadLineAsync())!);
        Assert.Equal("hazard", (string?)cmd!["command"]);
        Assert.Equal(1.0, (double)cmd["value"]!);

        await p.Send("""{"v":1,"type":"beamng.bindings.apply","device":"vjoy1","bindings":[{"beamngAction":"horn","button":1}]}""");
        JsonNode? fwd = null;
        for (int i = 0; i < 5 && fwd?["type"]?.GetValue<string>() != "bindings.apply"; i++) fwd = JsonNode.Parse((await r.ReadLineAsync())!);
        Assert.Equal("bead1234", (string?)fwd!["vidpid"]);

        await w.WriteLineAsync("""{"v":1,"type":"bindings.result","ok":true,"written":true,"message":"done"}""");
        var result = await p.Expect("bindings.result");
        Assert.Equal("done", (string?)result["message"]);
        Assert.True((bool)result["written"]!);

        mod.Close();
        Assert.False((bool)(await p.Expect("capabilities"))["capabilities"]!["beamng"]!);
        var cleared = await p.Expect("state");
        Assert.Equal("beamng", (string?)cleared["source"]);
        Assert.True((bool?)cleared["full"]);
        Assert.Empty(cleared["values"]!.AsObject());
    }

    [Fact]
    public async Task Pairing_with_an_unwritable_config_replies_an_error()
    {
        Directory.CreateDirectory(_store.Path);
        var p = new Phone();
        await using var _p = p;
        await p.Connect(_store.Config.WsPort);
        await p.Send($$"""{"v":1,"type":"hello","deviceId":"dev9","deviceName":"T","appVersion":"1","pin":"{{_host.Pairing.Pin}}"}""");
        Assert.Equal("config_write_failed", (string?)(await p.Expect("error"))["code"]);
        Assert.Empty(_host.Config.Devices);
        Assert.Empty(_host.Sessions);
    }

    [Fact]
    public async Task Forget_stops_the_phone_even_when_the_save_fails()
    {
        var (p, _, _) = await Pair();
        await using var _p = p;
        await p.Send(Sync);
        await p.Expect("profile.ack");
        await p.Send("""{"v":1,"type":"input","s":1,"id":"horn","val":1}""");
        await WaitFor(() => _out.Log.Contains("key H down"));
        Directory.CreateDirectory(_store.Path + ".tmp"); // the save cannot write its temp file
        Assert.ThrowsAny<Exception>(() => _host.ForgetDevice("dev1"));
        Assert.Empty(_host.Sessions);
        Assert.Contains("key H up", _out.Log);
    }

    [Fact]
    public async Task Bluetooth_pad_is_shown_and_counted_once_with_its_wifi_link()
    {
        int changes = 0;
        _host.BluetoothChanged += () => Interlocked.Increment(ref changes);

        // Bluetooth only: one phone, no Wi-Fi link.
        lock (_pads.Now) _pads.Now.Add(new("pad1", "GameCtl Pad"));
        _host.ScanBluetooth();
        var only = Assert.Single(_host.Phones());
        Assert.Null(only.Wifi);
        Assert.Equal("GameCtl Pad", only.Name);
        Assert.Equal(1, changes);
        _host.ScanBluetooth();
        Assert.Equal(1, changes); // no change, no event

        // The same phone links over Wi-Fi too: still one phone, with both links.
        var (p, _, _) = await Pair();
        await using var _p = p;
        var both = Assert.Single(_host.Phones());
        Assert.NotNull(both.Wifi);
        Assert.NotNull(both.Bluetooth);

        // Pad switched off: it drops out, the Wi-Fi link stays.
        lock (_pads.Now) _pads.Now.Clear();
        _host.ScanBluetooth();
        var wifi = Assert.Single(_host.Phones());
        Assert.Null(wifi.Bluetooth);
        Assert.Equal(2, changes);
    }

    [Fact]
    public async Task Browser_origin_is_refused()
    {
        using var ws = new ClientWebSocket();
        ws.Options.SetRequestHeader("Origin", "http://evil.example");
        var e = await Assert.ThrowsAnyAsync<WebSocketException>(() => ws.ConnectAsync(new Uri($"ws://127.0.0.1:{_store.Config.WsPort}/ws"), CancellationToken.None));
        Assert.Contains("403", e.Message);
    }

    [Fact]
    public async Task Silent_phone_is_dropped_and_released()
    {
        var (p, _, _) = await Pair();
        await using var _p = p;
        await p.Send(Sync);
        await p.Expect("profile.ack");
        await p.Send("""{"v":1,"type":"input","s":1,"id":"horn","val":1}""");
        await WaitFor(() => _out.Log.Contains("key H down"));
        // No pings: the idle timeout (1.5 s in these tests, 8 s for real) ends the session and releases its input.
        await WaitFor(() => _out.Log.Contains("key H up"), 4000);
        Assert.Contains("key H up", _out.Log);
        await WaitFor(() => _host.Sessions.Count == 0, 4000);
        Assert.Empty(_host.Sessions);
    }

    [Fact]
    public async Task Pings_keep_a_phone_connected()
    {
        var (p, _, _) = await Pair();
        await using var _p = p;
        for (int i = 0; i < 6; i++) { await p.Send("""{"v":1,"type":"ping","t":1}"""); await Task.Delay(500); }
        Assert.Single(_host.Sessions);
    }

    [Fact]
    public async Task Bindings_apply_without_mod_errors()
    {
        var (p, _, _) = await Pair();
        await using var _p = p;
        await p.Send("""{"v":1,"type":"beamng.bindings.apply","device":"vjoy1","bindings":[]}""");
        Assert.Equal("no_beamng", (string?)(await p.Expect("error"))["code"]);
    }

    [Fact]
    public void OutGauge_ignored_packets_are_logged_once_per_reason()
    {
        var log = new List<string>();
        _host.Logged += e => { lock (log) log.Add(e.Message); };
        Assert.Null(_host.OutGaugeLastPacket);

        var foreign = new IPEndPoint(IPAddress.Parse("203.0.113.9"), 5000);
        _host.OnOutGauge(new byte[96], foreign);
        _host.OnOutGauge(new byte[96], foreign);
        var local = new IPEndPoint(IPAddress.Loopback, 5000);
        _host.OnOutGauge(new byte[40], local);
        _host.OnOutGauge(new byte[12], local);

        lock (log)
        {
            Assert.Single(log, m => m == "OutGauge packet from 203.0.113.9 ignored (not this PC)");
            Assert.Single(log, m => m == "OutGauge packet too short (40 bytes)");
            Assert.DoesNotContain(log, m => m.Contains("(12 bytes)"));
        }
        Assert.False(_host.OutGaugeActive);
        var last = _host.OutGaugeLastPacket!.Value;
        Assert.Equal(IPAddress.Loopback, last.From);
        Assert.True(last.Ago < TimeSpan.FromSeconds(5));

        _host.OnOutGauge(new byte[96], local);
        Assert.True(_host.OutGaugeActive);
    }

    [Fact]
    public void OutGauge_port_in_use_is_reported_as_failed()
    {
        Assert.False(_host.OutGaugeFailed);
        var store = new ConfigStore(Path.Combine(Path.GetTempPath(), $"gc-host-{Guid.NewGuid():N}.json"));
        var c = store.Config;
        c.WsPort = FreeTcp(); c.UdpPort = FreeUdp(); c.DiscoveryPort = FreeUdp(); c.BeamNgPort = FreeTcp();
        c.OutGaugePort = _store.Config.OutGaugePort; // held by the fixture's host, as SimHub would hold 4444
        c.EnableMdns = false;
        using var other = new CompanionHost(store, new InputRouter(_out, _out, _out), new HostOptions { Bind = IPAddress.Loopback });
        other.Start();
        Assert.True(other.OutGaugeFailed);
    }

    [Fact]
    public async Task Bluetooth_scan_runs_on_its_own_task_without_the_tick()
    {
        var pads = new FakePads();
        var store = new ConfigStore(Path.Combine(Path.GetTempPath(), $"gc-host-{Guid.NewGuid():N}.json"));
        var c = store.Config;
        c.WsPort = FreeTcp(); c.UdpPort = FreeUdp(); c.DiscoveryPort = FreeUdp(); c.BeamNgPort = FreeTcp(); c.OutGaugePort = FreeUdp();
        c.EnableMdns = false;
        var host = new CompanionHost(store, new InputRouter(_out, _out, _out),
            new HostOptions { Bind = IPAddress.Loopback, BluetoothPads = pads, BluetoothScanInterval = TimeSpan.FromMilliseconds(50) });
        int threadOfScan = -1;
        host.BluetoothChanged += () => threadOfScan = Environment.CurrentManagedThreadId;
        host.Start();
        lock (pads.Now) pads.Now.Add(new("pad1", "GameCtl Pad"));
        await WaitFor(() => host.BluetoothPads.Count == 1, 2000);
        Assert.Single(host.BluetoothPads);
        Assert.NotEqual(Environment.CurrentManagedThreadId, threadOfScan);

        host.Dispose(); // stops the scan loop
        lock (pads.Now) pads.Now.Clear();
        await Task.Delay(200);
        Assert.Single(host.BluetoothPads);
    }

    sealed class FailingPads : GameController.Companion.Engine.IBluetoothPads
    {
        public IReadOnlyList<GameController.Companion.Engine.BluetoothPad> Present() => throw new InvalidOperationException("SetupAPI said no");
    }

    [Fact]
    public async Task Bluetooth_scan_failures_are_logged_once_and_the_pin_is_not_logged()
    {
        var store = new ConfigStore(Path.Combine(Path.GetTempPath(), $"gc-host-{Guid.NewGuid():N}.json"));
        var c = store.Config;
        c.WsPort = FreeTcp(); c.UdpPort = FreeUdp(); c.DiscoveryPort = FreeUdp(); c.BeamNgPort = FreeTcp(); c.OutGaugePort = FreeUdp();
        c.EnableMdns = false;
        var host = new CompanionHost(store, new InputRouter(_out, _out, _out),
            new HostOptions { Bind = IPAddress.Loopback, BluetoothPads = new FailingPads(), BluetoothScanInterval = TimeSpan.FromMilliseconds(30) });
        var log = new List<string>();
        host.Logged += e => { lock (log) log.Add(e.Message); };
        host.Pairing.UsePin("135791");
        host.Start();
        await Task.Delay(400);
        host.Dispose();
        host.Dispose(); // twice is fine
        lock (log)
        {
            Assert.Single(log, m => m == "Bluetooth scan failed: SetupAPI said no");
            Assert.DoesNotContain(log, m => m.Contains("135791"));
        }
    }
}
