using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using GameController.Companion.Engine;
using GameController.Companion.Protocol;

namespace GameController.Companion.BeamNG;

/// <summary>
/// TCP server on 127.0.0.1:47803 that the BeamNG mod connects to (newline-delimited JSON).
/// Only one mod connection is live at a time; a new one replaces the old.
/// </summary>
public sealed class BeamNgLink : ICommandSink, IDisposable
{
    const int MaxLine = 1 << 20;

    readonly TcpListener _listener;
    readonly CancellationTokenSource _cts = new();
    readonly object _gate = new();
    TcpClient? _client;
    StreamWriter? _writer;
    IReadOnlyCollection<string> _commands = Array.Empty<string>();

    public string? ModVersion { get; private set; }
    public string? GameVersion { get; private set; }
    public bool Connected { get { lock (_gate) return _writer != null; } }
    public IReadOnlyCollection<string> KnownCommands { get { lock (_gate) return _commands; } }

    /// <summary>Raised on connect (true) and disconnect (false).</summary>
    public event Action<bool>? ConnectionChanged;
    /// <summary>State values from the mod, and whether it is a full snapshot.</summary>
    public event Action<JsonObject, bool>? StateReceived;
    public event Action<IReadOnlyList<string>>? CommandsReceived;
    public event Action<JsonObject>? BindingsResult;
    public event Action<string>? Log;

    public int Port => ((IPEndPoint)_listener.LocalEndpoint).Port;

    public BeamNgLink(int port)
    {
        _listener = new TcpListener(IPAddress.Loopback, port);
    }

    public void Start()
    {
        _listener.Start();
        _ = AcceptLoop();
    }

    async Task AcceptLoop()
    {
        while (!_cts.IsCancellationRequested)
        {
            TcpClient c;
            try { c = await _listener.AcceptTcpClientAsync(_cts.Token).ConfigureAwait(false); }
            catch (Exception) when (_cts.IsCancellationRequested) { return; }
            catch (SocketException) { continue; }
            _ = Serve(c);
        }
    }

    async Task Serve(TcpClient c)
    {
        c.NoDelay = true;
        var stream = c.GetStream();
        var writer = new StreamWriter(stream, new UTF8Encoding(false)) { AutoFlush = true, NewLine = "\n" };
        TcpClient? old;
        lock (_gate)
        {
            old = _client;
            _client = c;
            _writer = writer;
        }
        old?.Dispose();
        Log?.Invoke("BeamNG mod connected");
        ConnectionChanged?.Invoke(true);
        Send(JsonExt.Msg("state.request"));

        try
        {
            using var reader = new StreamReader(stream, Encoding.UTF8);
            while (!_cts.IsCancellationRequested)
            {
                var line = await reader.ReadLineAsync(_cts.Token).ConfigureAwait(false);
                if (line == null) break;
                if (line.Length == 0 || line.Length > MaxLine) continue;
                try { HandleLine(line); }
                catch (JsonException) { Log?.Invoke("BeamNG mod sent malformed JSON"); }
            }
        }
        catch (Exception) { /* socket closed */ }
        finally
        {
            bool wasCurrent;
            lock (_gate)
            {
                wasCurrent = _client == c;
                if (wasCurrent) { _client = null; _writer = null; _commands = Array.Empty<string>(); ModVersion = null; }
            }
            c.Dispose();
            if (wasCurrent)
            {
                Log?.Invoke("BeamNG mod disconnected");
                ConnectionChanged?.Invoke(false);
            }
        }
    }

    void HandleLine(string line)
    {
        using var doc = JsonDocument.Parse(line);
        var m = doc.RootElement;
        if (m.ValueKind != JsonValueKind.Object) return;
        if (m.Int("v", 1) > ProtocolInfo.Version)
        {
            Send(JsonExt.Error("version_mismatch", $"companion speaks v{ProtocolInfo.Version}"));
            return;
        }
        switch (m.Str("type"))
        {
            case "hello":
                ModVersion = m.Str("modVersion");
                GameVersion = m.Str("gameVersion");
                Log?.Invoke($"BeamNG mod {ModVersion} (game {GameVersion})");
                ConnectionChanged?.Invoke(true);
                break;
            case "state":
                if (m.TryGetProperty("values", out var vals) && vals.ValueKind == JsonValueKind.Object)
                {
                    var o = new JsonObject();
                    foreach (var p in vals.EnumerateObject())
                        if (JsonExt.StateValue(p.Value) is { } node) o[p.Name] = node;
                    StateReceived?.Invoke(o, m.Bool("full"));
                }
                break;
            case "commands":
                if (m.TryGetProperty("list", out var list) && list.ValueKind == JsonValueKind.Array)
                {
                    var l = list.EnumerateArray().Where(x => x.ValueKind == JsonValueKind.String).Select(x => x.GetString()!).ToList();
                    lock (_gate) _commands = l.ToHashSet(StringComparer.Ordinal);
                    CommandsReceived?.Invoke(l);
                }
                break;
            case "bindings.result":
                if (JsonNode.Parse(line) is JsonObject r) BindingsResult?.Invoke(r);
                break;
        }
    }

    public bool Send(JsonObject msg)
    {
        StreamWriter? w;
        lock (_gate) w = _writer;
        if (w == null) return false;
        try
        {
            var text = msg.ToJsonString();
            lock (w) w.WriteLine(text);
            return true;
        }
        catch (Exception) { return false; }
    }

    public void SendCommand(string command, double value)
    {
        var m = JsonExt.Msg("command");
        m["command"] = command;
        m["value"] = value;
        Send(m);
    }

    public void RequestState() => Send(JsonExt.Msg("state.request"));

    public void Dispose()
    {
        _cts.Cancel();
        try { _listener.Stop(); } catch { }
        lock (_gate) { _client?.Dispose(); _client = null; _writer = null; }
    }
}
