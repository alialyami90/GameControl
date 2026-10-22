using System.Net;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json.Nodes;

namespace GameController.Companion.Engine;

/// <summary>One connected, paired phone.</summary>
public sealed class PhoneSession
{
    readonly WebSocket _ws;
    readonly SemaphoreSlim _sendLock = new(1, 1);

    public string SessionKey { get; }
    public string DeviceId { get; }
    public string DeviceName { get; }
    public IPAddress RemoteAddress { get; }
    public BindingEngine Engine { get; }
    public DateTimeOffset ConnectedAt { get; } = DateTimeOffset.Now;
    /// <summary>Environment.TickCount64 of the last message (monotonic, so a clock change never drops a phone).</summary>
    public long LastMessageTick;
    public void Touch() => Interlocked.Exchange(ref LastMessageTick, Environment.TickCount64);
    public long EventCount;
    public string? ProfileId { get; set; }
    public string? Game { get; set; }

    public PhoneSession(WebSocket ws, string key, string deviceId, string deviceName, IPAddress remote, BindingEngine engine)
    {
        _ws = ws; SessionKey = key; DeviceId = deviceId; DeviceName = deviceName; RemoteAddress = remote; Engine = engine;
        LastMessageTick = Environment.TickCount64;
    }

    public async Task SendAsync(JsonObject msg)
    {
        var bytes = Encoding.UTF8.GetBytes(msg.ToJsonString());
        await _sendLock.WaitAsync().ConfigureAwait(false);
        try
        {
            if (_ws.State != WebSocketState.Open) return;
            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(5));
            await _ws.SendAsync(bytes, WebSocketMessageType.Text, true, cts.Token).ConfigureAwait(false);
        }
        catch (Exception) { /* the receive loop notices the dead socket */ }
        finally { _sendLock.Release(); }
    }

    /// <summary>Fire-and-forget send that never throws.</summary>
    public void Send(JsonObject msg) => _ = SendAsync(msg);

    public Task CloseAsync(string reason)
    {
        try
        {
            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(2));
            return _ws.CloseOutputAsync(WebSocketCloseStatus.NormalClosure, reason, cts.Token);
        }
        catch (Exception) { return Task.CompletedTask; }
    }
}
