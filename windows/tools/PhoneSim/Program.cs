using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

// Phone simulator for the companion. Speaks protocol v1 exactly like the Android app should.
//   PhoneSim discover                         find companions by UDP broadcast
//   PhoneSim demo  --host H --pin P           pair, sync a profile with every control kind, press things
//   PhoneSim token --host H --token T --id D  reconnect with a saved token
//   PhoneSim mod                              pretend to be the BeamNG mod (TCP 127.0.0.1:47803)
var argv = args.ToList();
string Opt(string n, string d) { int i = argv.IndexOf(n); return i >= 0 && i + 1 < argv.Count ? argv[i + 1] : d; }
var cmd = argv.FirstOrDefault() ?? "demo";
var host = Opt("--host", "127.0.0.1");
int wsPort = int.Parse(Opt("--ws", "47800")), udpPort = int.Parse(Opt("--udp", "47801"));

switch (cmd)
{
    case "discover": return await Discover(int.Parse(Opt("--port", "47802")));
    case "mod": return await FakeMod(int.Parse(Opt("--port", "47803")));
    case "demo": return await Demo(Opt("--pin", ""), Opt("--token", ""), Opt("--id", "sim-" + Environment.MachineName), Opt("--profile", ""));
    case "token": return await Demo("", Opt("--token", ""), Opt("--id", "sim-" + Environment.MachineName), "");
    default: Console.WriteLine($"unknown command {cmd}"); return 2;
}

async Task<int> Discover(int port)
{
    using var udp = new UdpClient(AddressFamily.InterNetwork) { EnableBroadcast = true };
    udp.Client.Bind(new IPEndPoint(IPAddress.Any, 0));
    var q = Encoding.UTF8.GetBytes("""{"v":1,"type":"discover"}""");
    await udp.SendAsync(q, new IPEndPoint(IPAddress.Broadcast, port));
    await udp.SendAsync(q, new IPEndPoint(IPAddress.Loopback, port));
    using var cts = new CancellationTokenSource(1500);
    int found = 0;
    try
    {
        while (true)
        {
            var r = await udp.ReceiveAsync(cts.Token);
            Console.WriteLine($"{r.RemoteEndPoint.Address}: {Encoding.UTF8.GetString(r.Buffer)}");
            found++;
        }
    }
    catch (OperationCanceledException) { }
    Console.WriteLine(found == 0 ? "no companion answered" : $"{found} answer(s)");
    return found == 0 ? 1 : 0;
}

async Task<int> Demo(string pin, string token, string deviceId, string profilePath)
{
    using var ws = new ClientWebSocket();
    await ws.ConnectAsync(new Uri($"ws://{host}:{wsPort}/ws"), CancellationToken.None);
    var inbox = new List<JsonObject>();
    var reader = Task.Run(async () =>
    {
        var buf = new byte[65536];
        while (ws.State == WebSocketState.Open)
        {
            using var ms = new MemoryStream();
            WebSocketReceiveResult r;
            try
            {
                do { r = await ws.ReceiveAsync(buf, CancellationToken.None); ms.Write(buf, 0, r.Count); } while (!r.EndOfMessage);
            }
            catch { break; }
            if (r.MessageType == WebSocketMessageType.Close) break;
            var text = Encoding.UTF8.GetString(ms.ToArray());
            Console.WriteLine($"<- {text}");
            if (JsonNode.Parse(text) is JsonObject o) lock (inbox) inbox.Add(o);
        }
    });

    async Task Send(JsonObject o)
    {
        var t = o.ToJsonString();
        Console.WriteLine($"-> {t}");
        await ws.SendAsync(Encoding.UTF8.GetBytes(t), WebSocketMessageType.Text, true, CancellationToken.None);
    }
    async Task<JsonObject?> WaitFor(string type, int ms = 3000)
    {
        var until = DateTime.UtcNow.AddMilliseconds(ms);
        while (DateTime.UtcNow < until)
        {
            lock (inbox) { var m = inbox.FirstOrDefault(x => (string?)x["type"] == type); if (m != null) { inbox.Remove(m); return m; } }
            await Task.Delay(20);
        }
        return null;
    }

    var hello = new JsonObject { ["v"] = 1, ["type"] = "hello", ["deviceId"] = deviceId, ["deviceName"] = "PhoneSim", ["appVersion"] = "sim" };
    if (token != "") hello["token"] = token; else hello["pin"] = pin;
    await Send(hello);
    var welcome = await WaitFor("welcome");
    if (welcome == null) { Console.WriteLine("FAIL: no welcome"); return 1; }
    var session = (string)welcome["session"]!;
    Console.WriteLine($"paired. token={welcome["token"]} session={session}");
    if (cmd == "token") { await ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "bye", CancellationToken.None); return 0; }

    JsonObject sync;
    if (profilePath != "") sync = (JsonObject)JsonNode.Parse(File.ReadAllText(profilePath))!;
    else sync = (JsonObject)JsonNode.Parse("""
    {"v":1,"type":"profile.sync","profileId":"sim-all-kinds","game":"generic","bindings":[
      {"id":"horn","kind":"momentary","action":{"type":"key","keys":["H"],"trigger":"hold"}},
      {"id":"boost","kind":"momentary","action":{"type":"vjoy_button","device":1,"button":12}},
      {"id":"lights","kind":"latching","action":{"type":"key","keys":["LCTRL","L"],"trigger":"tap"}},
      {"id":"gear_mode","kind":"switch","positions":[{"type":"key","keys":["D1"],"trigger":"tap"},{"type":"key","keys":["D2"],"trigger":"tap"},{"type":"vjoy_button","device":1,"button":3}]},
      {"id":"drive","kind":"radio","positions":[{"type":"vjoy_axis","device":1,"axis":"SL1"},{"type":"vjoy_axis","device":1,"axis":"SL1"},{"type":"vjoy_axis","device":1,"axis":"SL1"}]},
      {"id":"throttle","kind":"slider","action":{"type":"vjoy_axis","device":1,"axis":"SL0","range":"unipolar"}},
      {"id":"zoom","kind":"rotary","cw":{"type":"key","keys":["NUMPAD_ADD"],"trigger":"tap"},"ccw":{"type":"key","keys":["NUMPAD_SUBTRACT"],"trigger":"tap"}},
      {"id":"combo","kind":"momentary","action":{"type":"macro","steps":[{"keys":["E"],"holdMs":50},{"delayMs":100},{"keys":["LSHIFT","E"],"holdMs":50}]}},
      {"id":"pad_a","kind":"momentary","action":{"type":"xinput_button","button":"A"}},
      {"id":"hazard","kind":"latching","action":{"type":"beamng","command":"hazard"}},
      {"id":"hid_btn","kind":"momentary","route":"hid","action":{"type":"vjoy_button","device":1,"button":1}},
      {"id":"future","kind":"momentary","action":{"type":"teleport"},"newField":true}
    ]}
    """)!;
    await Send(sync);
    var ack = await WaitFor("profile.ack");
    if (ack == null) { Console.WriteLine("FAIL: no profile.ack"); return 1; }

    using var udp = new UdpClient(AddressFamily.InterNetwork);
    long seq = 1;
    async Task Udp(string id, double val)
    {
        var t = new JsonObject { ["v"] = 1, ["k"] = session, ["s"] = seq++, ["id"] = id, ["val"] = val }.ToJsonString();
        Console.WriteLine($"=> udp {t}");
        await udp.SendAsync(Encoding.UTF8.GetBytes(t), new IPEndPoint(IPAddress.Parse(host == "localhost" ? "127.0.0.1" : host), udpPort));
    }
    Task Input(string id, double val) => Send(new JsonObject { ["v"] = 1, ["type"] = "input", ["s"] = seq++, ["id"] = id, ["val"] = val });

    await Send(new JsonObject { ["v"] = 1, ["type"] = "ping", ["t"] = 42 });
    if (await WaitFor("pong") == null) { Console.WriteLine("FAIL: no pong"); return 1; }

    // Momentary over UDP: press, keep-alive, release x3.
    await Udp("horn", 1); await Task.Delay(250); await Udp("horn", 1); await Task.Delay(100);
    for (int i = 0; i < 3; i++) { await Udp("horn", 0); await Task.Delay(30); }
    await Udp("boost", 1); await Task.Delay(100); await Udp("boost", 0);
    // Stale sequence is dropped.
    await Udp("boost", 1); seq -= 5; await Udp("boost", 1); seq += 10; await Udp("boost", 0);
    // Latching / switch / radio / rotary over WebSocket.
    await Input("lights", 1); await Input("lights", 1); await Input("lights", 0);
    await Input("gear_mode", 1); await Input("gear_mode", 1); await Input("gear_mode", 2);
    await Input("drive", 2);
    await Input("zoom", 1); await Input("zoom", -2);
    await Udp("throttle", 0.75);
    await Udp("combo", 1); await Udp("combo", 0);
    await Udp("pad_a", 1); await Udp("pad_a", 0);
    await Input("hazard", 1);
    await Udp("hid_btn", 1);
    await Input("future", 1);
    await Input("nope", 1);
    if (await WaitFor("error") is { } err) Console.WriteLine($"expected error for unknown control: {err["code"]}");
    // Lost release: auto-release after 1 s.
    await Udp("horn", 1);
    Console.WriteLine("waiting for auto-release...");
    await Task.Delay(1500);
    await ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "bye", CancellationToken.None);
    await Task.WhenAny(reader, Task.Delay(1000));
    Console.WriteLine("demo finished");
    return 0;
}

async Task<int> FakeMod(int port)
{
    using var tcp = new TcpClient();
    await tcp.ConnectAsync(IPAddress.Loopback, port);
    var s = tcp.GetStream();
    var w = new StreamWriter(s) { AutoFlush = true, NewLine = "\n" };
    var r = new StreamReader(s);
    await w.WriteLineAsync("""{"v":1,"type":"hello","role":"beamng","modVersion":"sim","gameVersion":"0.3x"}""");
    await w.WriteLineAsync("""{"v":1,"type":"commands","list":["hazard.toggle","hazard","horn","reset"]}""");
    await w.WriteLineAsync("""{"v":1,"type":"state","full":true,"values":{"has.diff.front":true,"lights.low":false,"gear":"N","speed":0}}""");
    Console.WriteLine("fake mod connected; echoing commands. Ctrl+C to stop.");
    int n = 0;
    while (await r.ReadLineAsync() is { } line)
    {
        Console.WriteLine($"mod <- {line}");
        var m = JsonNode.Parse(line);
        if ((string?)m?["type"] == "command" && (string?)m["command"] == "hazard")
        {
            bool on = (double?)m["value"] >= 0.5;
            var st = new JsonObject { ["v"] = 1, ["type"] = "state", ["values"] = new JsonObject { ["hazard"] = on, ["tick"] = ++n } };
            await w.WriteLineAsync(st.ToJsonString());
        }
        if ((string?)m?["type"] == "bindings.apply")
            await w.WriteLineAsync(new JsonObject { ["v"] = 1, ["type"] = "bindings.result", ["ok"] = true, ["message"] = $"Bound to {m["vidpid"]}" }.ToJsonString());
    }
    return 0;
}
