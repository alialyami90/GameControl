using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Security.Cryptography;
using System.Text;

namespace GameController.Companion.Net;

/// <summary>
/// A minimal WebSocket server on a raw TcpListener. HttpListener would need a URL ACL (admin)
/// to listen on the LAN, so the HTTP upgrade is done by hand and the framing is left to
/// <see cref="WebSocket.CreateFromStream(Stream, bool, string?, TimeSpan)"/>.
/// </summary>
public sealed class WsServer : IDisposable
{
    const string Magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    const int MaxHeaderBytes = 8192;

    readonly TcpListener _listener;
    readonly string _path;
    readonly Func<WebSocket, IPEndPoint, CancellationToken, Task> _onClient;
    readonly CancellationTokenSource _cts = new();

    public int Port => ((IPEndPoint)_listener.LocalEndpoint).Port;

    public WsServer(IPAddress bind, int port, string path, Func<WebSocket, IPEndPoint, CancellationToken, Task> onClient)
    {
        _listener = new TcpListener(bind, port);
        _path = path;
        _onClient = onClient;
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
            TcpClient client;
            try { client = await _listener.AcceptTcpClientAsync(_cts.Token).ConfigureAwait(false); }
            catch (Exception) when (_cts.IsCancellationRequested) { return; }
            catch (SocketException) { continue; }
            _ = Handle(client);
        }
    }

    async Task Handle(TcpClient client)
    {
        using var _ = client;
        client.NoDelay = true;
        var remote = (IPEndPoint)client.Client.RemoteEndPoint!;
        var stream = client.GetStream();
        try
        {
            using var handshakeTimeout = CancellationTokenSource.CreateLinkedTokenSource(_cts.Token);
            handshakeTimeout.CancelAfter(TimeSpan.FromSeconds(5));
            var request = await ReadHeaders(stream, handshakeTimeout.Token).ConfigureAwait(false);
            if (request == null) return;

            var (path, headers) = request.Value;
            if (!headers.TryGetValue("sec-websocket-key", out var key) ||
                !headers.TryGetValue("upgrade", out var upgrade) || !upgrade.Equals("websocket", StringComparison.OrdinalIgnoreCase))
            {
                await WriteText(stream, "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").ConfigureAwait(false);
                return;
            }
            // Browsers always send Origin and the phone app never does: refusing it stops any web page
            // the PC visits from opening a socket to us (cross-site WebSocket hijacking).
            if (headers.ContainsKey("origin"))
            {
                await WriteText(stream, "HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").ConfigureAwait(false);
                return;
            }
            if (path.Split('?')[0] != _path)
            {
                await WriteText(stream, "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").ConfigureAwait(false);
                return;
            }

            var accept = Convert.ToBase64String(SHA1.HashData(Encoding.ASCII.GetBytes(key.Trim() + Magic)));
            await WriteText(stream,
                "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                $"Sec-WebSocket-Accept: {accept}\r\n\r\n").ConfigureAwait(false);

            using var ws = WebSocket.CreateFromStream(stream, isServer: true, subProtocol: null, keepAliveInterval: TimeSpan.FromSeconds(30));
            await _onClient(ws, remote, _cts.Token).ConfigureAwait(false);
        }
        catch (Exception)
        {
            // Connection-level failures end that client only.
        }
    }

    static async Task WriteText(Stream s, string text)
    {
        var bytes = Encoding.ASCII.GetBytes(text);
        await s.WriteAsync(bytes).ConfigureAwait(false);
        await s.FlushAsync().ConfigureAwait(false);
    }

    /// <summary>Reads the HTTP request head byte by byte so no WebSocket frame bytes are consumed.</summary>
    static async Task<(string Path, Dictionary<string, string> Headers)?> ReadHeaders(Stream s, CancellationToken ct)
    {
        var buf = new byte[MaxHeaderBytes];
        int len = 0;
        var one = new byte[1];
        while (len < buf.Length)
        {
            int n = await s.ReadAsync(one, ct).ConfigureAwait(false);
            if (n == 0) return null;
            buf[len++] = one[0];
            if (len >= 4 && buf[len - 4] == '\r' && buf[len - 3] == '\n' && buf[len - 2] == '\r' && buf[len - 1] == '\n') break;
        }
        if (len >= buf.Length) return null;

        var lines = Encoding.ASCII.GetString(buf, 0, len).Split("\r\n", StringSplitOptions.RemoveEmptyEntries);
        if (lines.Length == 0) return null;
        var first = lines[0].Split(' ');
        if (first.Length < 3 || first[0] != "GET") return null;
        var headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        foreach (var line in lines.Skip(1))
        {
            int c = line.IndexOf(':');
            if (c > 0) headers[line[..c].Trim()] = line[(c + 1)..].Trim();
        }
        return (first[1], headers);
    }

    public void Dispose()
    {
        _cts.Cancel();
        try { _listener.Stop(); } catch { }
    }
}
