using System.Runtime.InteropServices;
using System.Runtime.Versioning;
using System.Text;
using GameController.Companion.Engine;
using Microsoft.Win32.SafeHandles;

namespace GameController.Companion.Windows;

/// <summary>
/// Finds the VID/PID of vJoy or of the phone's "GameCtl Pad" Bluetooth HID device by walking
/// the HID device interfaces (SetupAPI + HidD_GetAttributes), for BeamNG input-map file names.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class HidDeviceIdentity : IDeviceIdentity
{
    const int DIGCF_PRESENT = 0x2, DIGCF_DEVICEINTERFACE = 0x10;
    const uint FILE_SHARE_READ = 1, FILE_SHARE_WRITE = 2, OPEN_EXISTING = 3;

    [StructLayout(LayoutKind.Sequential)]
    struct SP_DEVICE_INTERFACE_DATA { public int cbSize; public Guid InterfaceClassGuid; public int Flags; public IntPtr Reserved; }

    [StructLayout(LayoutKind.Sequential)]
    struct HIDD_ATTRIBUTES { public int Size; public ushort VendorID; public ushort ProductID; public ushort VersionNumber; }

    [DllImport("hid.dll")] static extern void HidD_GetHidGuid(out Guid guid);
    [DllImport("hid.dll", SetLastError = true)][return: MarshalAs(UnmanagedType.U1)] static extern bool HidD_GetAttributes(SafeFileHandle h, ref HIDD_ATTRIBUTES a);
    [DllImport("hid.dll", SetLastError = true)][return: MarshalAs(UnmanagedType.U1)] static extern bool HidD_GetProductString(SafeFileHandle h, byte[] buffer, int length);

    [DllImport("setupapi.dll", SetLastError = true)] static extern IntPtr SetupDiGetClassDevs(ref Guid g, IntPtr enumerator, IntPtr parent, int flags);
    [DllImport("setupapi.dll", SetLastError = true)][return: MarshalAs(UnmanagedType.Bool)]
    static extern bool SetupDiEnumDeviceInterfaces(IntPtr set, IntPtr devInfo, ref Guid g, int index, ref SP_DEVICE_INTERFACE_DATA data);
    [DllImport("setupapi.dll", SetLastError = true, CharSet = CharSet.Unicode)][return: MarshalAs(UnmanagedType.Bool)]
    static extern bool SetupDiGetDeviceInterfaceDetail(IntPtr set, ref SP_DEVICE_INTERFACE_DATA data, IntPtr detail, int size, out int required, IntPtr devInfo);
    [DllImport("setupapi.dll")][return: MarshalAs(UnmanagedType.Bool)] static extern bool SetupDiDestroyDeviceInfoList(IntPtr set);

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    static extern SafeFileHandle CreateFile(string name, uint access, uint share, IntPtr sec, uint disposition, uint flags, IntPtr template);

    public sealed record HidInfo(string Product, ushort Vid, ushort Pid, string Path = "")
    {
        public string VidPid => $"{Pid:x4}{Vid:x4}";
    }

    public string? VidPid(string device)
    {
        if (device.StartsWith("vjoy", StringComparison.OrdinalIgnoreCase))
            return Find("vJoy")?.VidPid ?? "bead1234";
        if (device.Equals("hid", StringComparison.OrdinalIgnoreCase))
            return Find("GameCtl Pad")?.VidPid;
        return null;
    }

    public static HidInfo? Find(string productContains) =>
        Enumerate().FirstOrDefault(d => d.Product.Contains(productContains, StringComparison.OrdinalIgnoreCase));

    public static List<HidInfo> Enumerate()
    {
        var list = new List<HidInfo>();
        HidD_GetHidGuid(out var guid);
        var set = SetupDiGetClassDevs(ref guid, IntPtr.Zero, IntPtr.Zero, DIGCF_PRESENT | DIGCF_DEVICEINTERFACE);
        if (set == IntPtr.Zero || set == new IntPtr(-1)) return list;
        try
        {
            var data = new SP_DEVICE_INTERFACE_DATA { cbSize = Marshal.SizeOf<SP_DEVICE_INTERFACE_DATA>() };
            for (int i = 0; SetupDiEnumDeviceInterfaces(set, IntPtr.Zero, ref guid, i, ref data); i++)
            {
                SetupDiGetDeviceInterfaceDetail(set, ref data, IntPtr.Zero, 0, out int size, IntPtr.Zero);
                if (size <= 0) continue;
                var buf = Marshal.AllocHGlobal(size);
                try
                {
                    // SP_DEVICE_INTERFACE_DETAIL_DATA_W.cbSize is 8 on x64, 6 on x86.
                    Marshal.WriteInt32(buf, IntPtr.Size == 8 ? 8 : 6);
                    if (!SetupDiGetDeviceInterfaceDetail(set, ref data, buf, size, out _, IntPtr.Zero)) continue;
                    var path = Marshal.PtrToStringUni(buf + 4);
                    if (path != null && Read(path) is { } info) list.Add(info with { Path = path });
                }
                finally { Marshal.FreeHGlobal(buf); }
            }
        }
        finally { SetupDiDestroyDeviceInfoList(set); }
        return list;
    }

    static HidInfo? Read(string path)
    {
        // Access 0 is enough for attributes and strings, and works on devices opened exclusively.
        using var h = CreateFile(path, 0, FILE_SHARE_READ | FILE_SHARE_WRITE, IntPtr.Zero, OPEN_EXISTING, 0, IntPtr.Zero);
        if (h.IsInvalid) return null;
        var attr = new HIDD_ATTRIBUTES { Size = Marshal.SizeOf<HIDD_ATTRIBUTES>() };
        if (!HidD_GetAttributes(h, ref attr)) return null;
        var name = new byte[256];
        string product = HidD_GetProductString(h, name, name.Length) ? Encoding.Unicode.GetString(name).TrimEnd('\0').Split('\0')[0] : "";
        return new HidInfo(product, attr.VendorID, attr.ProductID);
    }
}

/// <summary>
/// The phone's Bluetooth controller as Windows sees it: present HID interfaces whose product string is
/// "GameCtl Pad". A classic Bluetooth HID device only has HID interfaces while its link is up, and
/// opening one must succeed, so a paired pad that is switched off or out of range is not listed.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class WindowsBluetoothPads : IBluetoothPads
{
    public const string PadName = "GameCtl Pad";

    public IReadOnlyList<BluetoothPad> Present() =>
        HidDeviceIdentity.Enumerate()
            .Where(d => d.Product.Contains(PadName, StringComparison.OrdinalIgnoreCase))
            .Select(d => new BluetoothPad(BluetoothPadKey.FromHidPath(d.Path), d.Product))
            .DistinctBy(p => p.Id)
            .ToList();
}
