using System.Runtime.InteropServices;
using System.Runtime.Versioning;

namespace GameController.Companion.Windows;

/// <summary>
/// Registers the service with Windows' own mDNS responder (DnsServiceRegister, Windows 10 1809+).
/// Used only when the built-in responder cannot bind UDP 5353.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class WindowsMdns : IDisposable
{
    const uint DNS_QUERY_REQUEST_VERSION1 = 1;
    const uint DNS_REQUEST_PENDING = 9506;

    [UnmanagedFunctionPointer(CallingConvention.Winapi)]
    delegate void RegisterComplete(uint status, IntPtr context, IntPtr instance);

    [StructLayout(LayoutKind.Sequential)]
    struct DNS_SERVICE_REGISTER_REQUEST
    {
        public uint Version;
        public uint InterfaceIndex;
        public IntPtr pServiceInstance;
        public IntPtr pRegisterCompletionCallback;
        public IntPtr pQueryContext;
        public IntPtr hCredentials;
        [MarshalAs(UnmanagedType.Bool)] public bool unicastEnabled;
    }

    [DllImport("dnsapi.dll", CharSet = CharSet.Unicode)]
    static extern IntPtr DnsServiceConstructInstance(string serviceName, string hostName, IntPtr ip4, IntPtr ip6,
        ushort port, ushort priority, ushort weight, uint propertiesCount, string[] keys, string[] values);
    [DllImport("dnsapi.dll")] static extern void DnsServiceFreeInstance(IntPtr instance);
    [DllImport("dnsapi.dll")] static extern uint DnsServiceRegister(IntPtr request, IntPtr cancel);
    [DllImport("dnsapi.dll")] static extern uint DnsServiceDeRegister(IntPtr request, IntPtr cancel);

    readonly RegisterComplete _callback; // kept alive for the native side
    readonly IntPtr _request;
    readonly IntPtr _instance;

    public WindowsMdns(string instanceName, string serviceType, ushort port, IDictionary<string, string> txt)
    {
        _callback = (_, _, inst) => { if (inst != IntPtr.Zero) DnsServiceFreeInstance(inst); };
        var host = Environment.MachineName + ".local";
        var full = $"{instanceName}.{serviceType.TrimEnd('.')}.local";
        _instance = DnsServiceConstructInstance(full, host, IntPtr.Zero, IntPtr.Zero, port, 0, 0,
            (uint)txt.Count, txt.Keys.ToArray(), txt.Values.ToArray());
        if (_instance == IntPtr.Zero) throw new InvalidOperationException("DnsServiceConstructInstance failed");

        var req = new DNS_SERVICE_REGISTER_REQUEST
        {
            Version = DNS_QUERY_REQUEST_VERSION1,
            pServiceInstance = _instance,
            pRegisterCompletionCallback = Marshal.GetFunctionPointerForDelegate(_callback),
        };
        _request = Marshal.AllocHGlobal(Marshal.SizeOf<DNS_SERVICE_REGISTER_REQUEST>());
        Marshal.StructureToPtr(req, _request, false);
        var rc = DnsServiceRegister(_request, IntPtr.Zero);
        if (rc != DNS_REQUEST_PENDING)
        {
            DnsServiceFreeInstance(_instance);
            Marshal.FreeHGlobal(_request);
            throw new InvalidOperationException($"DnsServiceRegister failed ({rc})");
        }
    }

    public void Dispose()
    {
        try { DnsServiceDeRegister(_request, IntPtr.Zero); } catch { }
        // The request is freed after a short delay so the deregistration callback cannot touch freed memory.
        var req = _request;
        _ = Task.Delay(2000).ContinueWith(_ => Marshal.FreeHGlobal(req));
        GC.KeepAlive(_callback);
    }
}
