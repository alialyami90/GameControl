using System.Buffers.Binary;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;

namespace GameController.Companion.Net;

/// <summary>Builds and parses the small subset of DNS used by mDNS service discovery (RFC 6762/6763).</summary>
public static class DnsWire
{
    public const ushort TypeA = 1, TypePtr = 12, TypeTxt = 16, TypeSrv = 33, TypeAny = 255;
    public const ushort ClassIn = 1, CacheFlush = 0x8000;

    public readonly record struct Question(string Name, ushort Type);

    public sealed record Record(string Name, ushort Type, ushort Class, uint Ttl, byte[] Data);

    public static List<Question>? ParseQuestions(ReadOnlySpan<byte> p, out bool isResponse)
    {
        isResponse = false;
        if (p.Length < 12) return null;
        ushort flags = BinaryPrimitives.ReadUInt16BigEndian(p[2..]);
        isResponse = (flags & 0x8000) != 0;
        int qd = BinaryPrimitives.ReadUInt16BigEndian(p[4..]);
        int off = 12;
        var list = new List<Question>();
        for (int i = 0; i < qd; i++)
        {
            var name = ReadName(p, ref off);
            if (name == null || off + 4 > p.Length) return list;
            ushort type = BinaryPrimitives.ReadUInt16BigEndian(p[off..]);
            off += 4; // type + class (top bit is the unicast-response flag)
            list.Add(new Question(name, type));
        }
        return list;
    }

    /// <summary>Reads a possibly-compressed name. Returns null on malformed input.</summary>
    public static string? ReadName(ReadOnlySpan<byte> p, ref int off)
    {
        var sb = new StringBuilder();
        int pos = off, jumps = 0;
        bool jumped = false;
        while (true)
        {
            if (pos >= p.Length) return null;
            byte len = p[pos];
            if (len == 0) { pos++; break; }
            if ((len & 0xC0) == 0xC0)
            {
                if (pos + 1 >= p.Length || ++jumps > 16) return null;
                int ptr = ((len & 0x3F) << 8) | p[pos + 1];
                if (!jumped) off = pos + 2;
                jumped = true;
                pos = ptr;
                continue;
            }
            if (pos + 1 + len > p.Length) return null;
            if (sb.Length > 0) sb.Append('.');
            sb.Append(Encoding.UTF8.GetString(p.Slice(pos + 1, len)));
            pos += 1 + len;
        }
        if (!jumped) off = pos;
        return sb.ToString();
    }

    public static void WriteName(List<byte> b, string name)
    {
        foreach (var label in name.TrimEnd('.').Split('.'))
        {
            var bytes = Encoding.UTF8.GetBytes(label);
            int n = Math.Min(bytes.Length, 63);
            b.Add((byte)n);
            b.AddRange(bytes.AsSpan(0, n).ToArray());
        }
        b.Add(0);
    }

    public static byte[] NameBytes(string name) { var b = new List<byte>(); WriteName(b, name); return b.ToArray(); }

    public static byte[] Srv(ushort port, string target)
    {
        var b = new List<byte> { 0, 0, 0, 0, (byte)(port >> 8), (byte)port };
        WriteName(b, target);
        return b.ToArray();
    }

    public static byte[] Txt(IEnumerable<string> entries)
    {
        var b = new List<byte>();
        foreach (var e in entries)
        {
            var bytes = Encoding.UTF8.GetBytes(e);
            b.Add((byte)Math.Min(bytes.Length, 255));
            b.AddRange(bytes.Take(255));
        }
        if (b.Count == 0) b.Add(0);
        return b.ToArray();
    }

    public static byte[] Response(IReadOnlyList<Record> answers, IReadOnlyList<Record> additional)
    {
        var b = new List<byte>
        {
            0, 0,                // id: 0 for multicast responses
            0x84, 0x00,          // QR + AA
            0, 0,                // questions
            (byte)(answers.Count >> 8), (byte)answers.Count,
            0, 0,
            (byte)(additional.Count >> 8), (byte)additional.Count,
        };
        foreach (var r in answers.Concat(additional))
        {
            WriteName(b, r.Name);
            b.Add((byte)(r.Type >> 8)); b.Add((byte)r.Type);
            b.Add((byte)(r.Class >> 8)); b.Add((byte)r.Class);
            b.Add((byte)(r.Ttl >> 24)); b.Add((byte)(r.Ttl >> 16)); b.Add((byte)(r.Ttl >> 8)); b.Add((byte)r.Ttl);
            b.Add((byte)(r.Data.Length >> 8)); b.Add((byte)r.Data.Length);
            b.AddRange(r.Data);
        }
        return b.ToArray();
    }

    public static byte[] Query(string name, ushort type)
    {
        var b = new List<byte> { 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0 };
        WriteName(b, name);
        b.Add((byte)(type >> 8)); b.Add((byte)type); b.Add(0); b.Add(1);
        return b.ToArray();
    }
}

/// <summary>
/// Advertises <c>_gamectl._tcp.local</c> so Android NSD finds the companion. It is a small
/// responder that shares UDP 5353 with any system responder (SO_REUSEADDR).
/// </summary>
public sealed class MdnsAdvertiser : IDisposable
{
    static readonly IPAddress Group = IPAddress.Parse("224.0.0.251");
    const int Port = 5353;
    const uint Ttl = 120;

    readonly string _service;      // _gamectl._tcp.local
    readonly string _instance;     // <name>._gamectl._tcp.local
    readonly string _host;         // <name>-gamectl.local
    readonly ushort _port;
    readonly string[] _txt;
    readonly CancellationTokenSource _cts = new();
    UdpClient? _udp;

    public string InstanceName => _instance;
    public event Action<string>? Log;

    public MdnsAdvertiser(string serverName, string serviceType, ushort port, IEnumerable<string> txt)
    {
        var label = Sanitize(serverName);
        _service = serviceType.TrimEnd('.') + ".local";
        _instance = $"{label}.{_service}";
        _host = $"{label}-gamectl.local";
        _port = port;
        _txt = txt.ToArray();
    }

    static string Sanitize(string s)
    {
        var sb = new StringBuilder();
        foreach (var ch in s) sb.Append(char.IsLetterOrDigit(ch) || ch == '-' ? ch : '-');
        var r = sb.ToString().Trim('-');
        if (r.Length == 0) r = "GameController";
        return r.Length > 40 ? r[..40] : r;
    }

    public void Start()
    {
        var udp = new UdpClient(AddressFamily.InterNetwork);
        udp.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
        udp.ExclusiveAddressUse = false;
        udp.Client.Bind(new IPEndPoint(IPAddress.Any, Port));
        udp.MulticastLoopback = true;
        foreach (var addr in AdvertisedAddresses())
        {
            try { udp.JoinMulticastGroup(Group, addr); } catch (SocketException) { }
        }
        try { udp.JoinMulticastGroup(Group); } catch (SocketException) { }
        _udp = udp;
        _ = ReceiveLoop();
        _ = Announce();
    }

    async Task Announce()
    {
        for (int i = 0; i < 3 && !_cts.IsCancellationRequested; i++)
        {
            Send(FullAnswer(Ttl));
            try { await Task.Delay(1000 << i, _cts.Token).ConfigureAwait(false); } catch { return; }
        }
    }

    async Task ReceiveLoop()
    {
        while (!_cts.IsCancellationRequested && _udp != null)
        {
            UdpReceiveResult r;
            try { r = await _udp.ReceiveAsync(_cts.Token).ConfigureAwait(false); }
            catch (Exception) when (_cts.IsCancellationRequested) { return; }
            catch (SocketException) { continue; }
            catch (ObjectDisposedException) { return; }
            try { Handle(r.Buffer); } catch (Exception) { /* malformed packet */ }
        }
    }

    void Handle(byte[] packet)
    {
        var qs = DnsWire.ParseQuestions(packet, out bool isResponse);
        if (isResponse || qs == null || qs.Count == 0) return;
        bool wantPtr = false, wantSrvTxt = false, wantA = false, wantEnum = false;
        foreach (var q in qs)
        {
            if (Eq(q.Name, _service) && q.Type is DnsWire.TypePtr or DnsWire.TypeAny) wantPtr = true;
            else if (Eq(q.Name, _instance) && q.Type is DnsWire.TypeSrv or DnsWire.TypeTxt or DnsWire.TypeAny) wantSrvTxt = true;
            else if (Eq(q.Name, _host) && q.Type is DnsWire.TypeA or DnsWire.TypeAny) wantA = true;
            else if (Eq(q.Name, "_services._dns-sd._udp.local") && q.Type is DnsWire.TypePtr or DnsWire.TypeAny) wantEnum = true;
        }
        if (wantPtr) Send(FullAnswer(Ttl));
        else if (wantSrvTxt) Send(DnsWire.Response(new[] { SrvRecord(Ttl), TxtRecord(Ttl) }, ARecords(Ttl)));
        else if (wantA) Send(DnsWire.Response(ARecords(Ttl), Array.Empty<DnsWire.Record>()));
        if (wantEnum)
            Send(DnsWire.Response(new[] { new DnsWire.Record("_services._dns-sd._udp.local", DnsWire.TypePtr, DnsWire.ClassIn, Ttl, DnsWire.NameBytes(_service)) },
                Array.Empty<DnsWire.Record>()));
    }

    static bool Eq(string a, string b) => string.Equals(a.TrimEnd('.'), b, StringComparison.OrdinalIgnoreCase);

    byte[] FullAnswer(uint ttl) => DnsWire.Response(
        new[] { new DnsWire.Record(_service, DnsWire.TypePtr, DnsWire.ClassIn, ttl, DnsWire.NameBytes(_instance)) },
        new[] { SrvRecord(ttl), TxtRecord(ttl) }.Concat(ARecords(ttl)).ToList());

    DnsWire.Record SrvRecord(uint ttl) => new(_instance, DnsWire.TypeSrv, DnsWire.ClassIn | DnsWire.CacheFlush, ttl, DnsWire.Srv(_port, _host));
    DnsWire.Record TxtRecord(uint ttl) => new(_instance, DnsWire.TypeTxt, DnsWire.ClassIn | DnsWire.CacheFlush, ttl, DnsWire.Txt(_txt));

    List<DnsWire.Record> ARecords(uint ttl) => AdvertisedAddresses()
        .Select(a => new DnsWire.Record(_host, DnsWire.TypeA, DnsWire.ClassIn | DnsWire.CacheFlush, ttl, a.GetAddressBytes())).ToList();

    readonly object _sendGate = new();

    /// <summary>
    /// Sends on every LAN interface in turn. Windows otherwise picks one interface for multicast,
    /// often a Hyper-V or VPN adapter, and the phone on the Wi-Fi never hears the answer.
    /// </summary>
    void Send(byte[] data)
    {
        var udp = _udp;
        if (udp == null) return;
        var target = new IPEndPoint(Group, Port);
        lock (_sendGate)
        {
            var addrs = AdvertisedAddresses();
            if (addrs.Count == 0) { TrySend(udp, data, target, null); return; }
            foreach (var a in addrs) TrySend(udp, data, target, a);
        }
    }

    void TrySend(UdpClient udp, byte[] data, IPEndPoint target, IPAddress? via)
    {
        try
        {
            if (via != null) udp.Client.SetSocketOption(SocketOptionLevel.IP, SocketOptionName.MulticastInterface, via.GetAddressBytes());
            udp.Send(data, data.Length, target);
        }
        catch (Exception e) { Log?.Invoke($"mDNS send via {via?.ToString() ?? "default"} failed: {e.Message}"); }
    }

    /// <summary>
    /// Addresses to advertise: those of interfaces with a default gateway (the real LAN), which leaves
    /// out host-only adapters (Hyper-V, WSL, VirtualBox). Falls back to every address when none has one.
    /// </summary>
    public static List<IPAddress> AdvertisedAddresses()
    {
        var lan = LocalAddresses(requireGateway: true);
        return lan.Count > 0 ? lan : LocalAddresses();
    }

    /// <summary>Usable IPv4 addresses of interfaces that are up (no loopback, no link-local).</summary>
    public static List<IPAddress> LocalAddresses(bool requireGateway = false)
    {
        var list = new List<IPAddress>();
        try
        {
            foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (ni.OperationalStatus != OperationalStatus.Up || ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
                var props = ni.GetIPProperties();
                if (requireGateway && !props.GatewayAddresses.Any(g => g.Address.AddressFamily == AddressFamily.InterNetwork && !g.Address.Equals(IPAddress.Any)))
                    continue;
                foreach (var ua in props.UnicastAddresses)
                {
                    var a = ua.Address;
                    if (a.AddressFamily != AddressFamily.InterNetwork || IPAddress.IsLoopback(a)) continue;
                    if (a.GetAddressBytes() is [169, 254, ..]) continue;
                    list.Add(a);
                }
            }
        }
        catch (NetworkInformationException) { }
        return list;
    }

    public void Dispose()
    {
        try { Send(FullAnswer(0)); } catch { } // goodbye packet
        _cts.Cancel();
        _udp?.Dispose();
    }
}
