using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using GameController.Companion.Protocol;

namespace GameController.Companion.Net;

/// <summary>A UDP socket with a receive loop that hands every datagram to a callback.</summary>
public sealed class UdpLoop : IDisposable
{
    readonly UdpClient _udp;
    readonly Action<byte[], IPEndPoint> _onPacket;
    readonly CancellationTokenSource _cts = new();

    public int Port => ((IPEndPoint)_udp.Client.LocalEndPoint!).Port;

    public UdpLoop(IPAddress bind, int port, Action<byte[], IPEndPoint> onPacket, bool broadcast = false)
    {
        _udp = new UdpClient(AddressFamily.InterNetwork);
        if (OperatingSystem.IsWindows())
        {
            // Stop ICMP "port unreachable" from a phone that went away from killing ReceiveAsync.
            const int SIO_UDP_CONNRESET = -1744830452;
            try { _udp.Client.IOControl(SIO_UDP_CONNRESET, new byte[] { 0 }, null); } catch { }
        }
        _udp.EnableBroadcast = broadcast;
        _udp.Client.Bind(new IPEndPoint(bind, port));
        _onPacket = onPacket;
    }

    public void Start() => _ = Loop();

    async Task Loop()
    {
        while (!_cts.IsCancellationRequested)
        {
            UdpReceiveResult r;
            try { r = await _udp.ReceiveAsync(_cts.Token).ConfigureAwait(false); }
            catch (Exception) when (_cts.IsCancellationRequested) { return; }
            catch (ObjectDisposedException) { return; }
            catch (SocketException) { continue; }
            try { _onPacket(r.Buffer, r.RemoteEndPoint); } catch (Exception) { /* one bad datagram never stops the loop */ }
        }
    }

    public void Send(byte[] data, IPEndPoint to)
    {
        try { _udp.Send(data, data.Length, to); } catch (SocketException) { } catch (ObjectDisposedException) { }
    }

    public void Dispose()
    {
        _cts.Cancel();
        _udp.Dispose();
    }
}

/// <summary>Answers <c>discover</c> broadcasts on UDP 47802 (PROTOCOL.md "Discovery", step 2).</summary>
public sealed class DiscoveryResponder : IDisposable
{
    readonly UdpLoop _loop;
    readonly Func<JsonObject> _reply;

    public DiscoveryResponder(int port, Func<JsonObject> reply)
    {
        _reply = reply;
        _loop = new UdpLoop(IPAddress.Any, port, OnPacket, broadcast: true);
    }

    public void Start() => _loop.Start();

    void OnPacket(byte[] data, IPEndPoint from)
    {
        if (data.Length > 2048) return;
        using var doc = JsonDocument.Parse(data);
        if (doc.RootElement.Str("type") != "discover") return;
        _loop.Send(Encoding.UTF8.GetBytes(_reply().ToJsonString()), from);
    }

    public void Dispose() => _loop.Dispose();
}
