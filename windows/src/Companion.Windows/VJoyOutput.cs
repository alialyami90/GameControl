using System.Reflection;
using System.Runtime.InteropServices;
using System.Runtime.Versioning;
using GameController.Companion.Input;

namespace GameController.Companion.Windows;

/// <summary>
/// vJoy through its own vJoyInterface.dll (installed with the driver; the maintained fork is
/// BrunnerInnovation/vJoy 2.2.x). The DLL is found in the vJoy install folder at runtime, so the
/// companion starts fine without vJoy and reports <c>vjoy:false</c>.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class VJoyOutput : IVJoyOutput, IDisposable
{
    const string Dll = "vJoyInterface";
    enum VjdStat { Own = 0, Free = 1, Busy = 2, Miss = 3, Unknown = 4 }

    [DllImport(Dll, CallingConvention = CallingConvention.Cdecl)][return: MarshalAs(UnmanagedType.Bool)] static extern bool vJoyEnabled();
    [DllImport(Dll, CallingConvention = CallingConvention.Cdecl)] static extern VjdStat GetVJDStatus(uint rID);
    [DllImport(Dll, CallingConvention = CallingConvention.Cdecl)][return: MarshalAs(UnmanagedType.Bool)] static extern bool AcquireVJD(uint rID);
    [DllImport(Dll, CallingConvention = CallingConvention.Cdecl)] static extern void RelinquishVJD(uint rID);
    [DllImport(Dll, CallingConvention = CallingConvention.Cdecl)][return: MarshalAs(UnmanagedType.Bool)] static extern bool ResetVJD(uint rID);
    [DllImport(Dll, CallingConvention = CallingConvention.Cdecl)] static extern int GetVJDButtonNumber(uint rID);
    [DllImport(Dll, CallingConvention = CallingConvention.Cdecl)][return: MarshalAs(UnmanagedType.Bool)] static extern bool GetVJDAxisExist(uint rID, uint axis);
    [DllImport(Dll, CallingConvention = CallingConvention.Cdecl)][return: MarshalAs(UnmanagedType.Bool)] static extern bool GetVJDAxisMax(uint rID, uint axis, ref int max);
    [DllImport(Dll, CallingConvention = CallingConvention.Cdecl)][return: MarshalAs(UnmanagedType.Bool)] static extern bool GetVJDAxisMin(uint rID, uint axis, ref int min);
    [DllImport(Dll, CallingConvention = CallingConvention.Cdecl)][return: MarshalAs(UnmanagedType.Bool)] static extern bool SetBtn([MarshalAs(UnmanagedType.Bool)] bool value, uint rID, byte nBtn);
    [DllImport(Dll, CallingConvention = CallingConvention.Cdecl)][return: MarshalAs(UnmanagedType.Bool)] static extern bool SetAxis(int value, uint rID, uint axis);

    static readonly Dictionary<string, uint> AxisUsage = new(StringComparer.OrdinalIgnoreCase)
    {
        ["X"] = 0x30, ["Y"] = 0x31, ["Z"] = 0x32, ["RX"] = 0x33, ["RY"] = 0x34, ["RZ"] = 0x35, ["SL0"] = 0x36, ["SL1"] = 0x37,
    };

    static int _resolverSet;
    readonly object _gate = new();
    List<int> _devices = new();
    readonly HashSet<int> _owned = new();
    readonly Dictionary<int, int> _buttonCount = new();
    readonly Dictionary<(int, uint), (int Min, int Max)> _axisRange = new();

    public bool Available { get; private set; }
    public IReadOnlyList<int> Devices { get { lock (_gate) return _devices.ToList(); } }
    public string Status { get; private set; } = "not checked";

    public VJoyOutput()
    {
        if (Interlocked.Exchange(ref _resolverSet, 1) == 0)
            NativeLibrary.SetDllImportResolver(typeof(VJoyOutput).Assembly, Resolve);
        Refresh();
    }

    static IntPtr Resolve(string name, Assembly asm, DllImportSearchPath? path)
    {
        if (name != Dll) return IntPtr.Zero;
        if (NativeLibrary.TryLoad("vJoyInterface.dll", asm, path, out var h)) return h;
        var arch = Environment.Is64BitProcess ? "x64" : "x86";
        foreach (var root in new[]
        {
            Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),
            Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86),
        })
        {
            var p = Path.Combine(root, "vJoy", arch, "vJoyInterface.dll");
            if (File.Exists(p) && NativeLibrary.TryLoad(p, out h)) return h;
        }
        return IntPtr.Zero;
    }

    /// <summary>
    /// Lists the vJoy devices we could use (free, or already ours). A device is only acquired the
    /// first time a binding uses it, so other feeders (and other games' tools) can keep the rest.
    /// </summary>
    public bool Refresh()
    {
        lock (_gate)
        {
            var before = string.Join(",", _devices);
            try
            {
                if (!vJoyEnabled())
                {
                    Available = false; _devices.Clear(); Status = "vJoy driver installed but disabled";
                    return before != "";
                }
                Available = true;
                var now = new List<int>();
                for (int id = 1; id <= 16; id++)
                {
                    var st = GetVJDStatus((uint)id);
                    if (st == VjdStat.Own || st == VjdStat.Free) now.Add(id);
                    else _owned.Remove(id); // taken by another feeder, or removed in "Configure vJoy"
                }
                _devices = now;
                Status = now.Count == 0 ? "vJoy installed, no free device (configure one in \"Configure vJoy\")" : $"devices {string.Join(", ", now)}";
            }
            catch (DllNotFoundException)
            {
                Available = false; _devices.Clear(); Status = "vJoy not installed";
            }
            catch (EntryPointNotFoundException)
            {
                Available = false; _devices.Clear(); Status = "vJoyInterface.dll is an unsupported version";
            }
            return before != string.Join(",", _devices);
        }
    }

    /// <summary>Takes the device on first use; false when another program got it first.</summary>
    bool Owned(int id)
    {
        if (_owned.Contains(id)) return true;
        if (!Available || id is < 1 or > 16) return false;
        // Asked directly rather than from the last refresh, so a device another feeder just freed works at once.
        var st = GetVJDStatus((uint)id);
        if (st != VjdStat.Own && !(st == VjdStat.Free && AcquireVJD((uint)id))) return false;
        if (!_devices.Contains(id)) { _devices.Add(id); _devices.Sort(); }
        ResetVJD((uint)id);
        _buttonCount[id] = GetVJDButtonNumber((uint)id);
        CenterAxes(id);
        _owned.Add(id);
        Status = $"devices {string.Join(", ", _devices)} (using {string.Join(", ", _owned.Order())})";
        return true;
    }

    /// <summary>A neutral start; the binding engine then parks each bound axis at its own rest (unipolar 0).</summary>
    void CenterAxes(int id)
    {
        foreach (var usage in AxisUsage.Values)
            if (GetVJDAxisExist((uint)id, usage)) SetAxisRaw(id, usage, 0.5);
    }

    public void SetButton(int device, int button, bool down)
    {
        lock (_gate)
        {
            if (!Owned(device)) return;
            if (_buttonCount.TryGetValue(device, out var n) && button > n) return;
            SetBtn(down, (uint)device, (byte)button);
        }
    }

    public void SetAxis(int device, string axis, double position)
    {
        if (!AxisUsage.TryGetValue(axis, out var usage)) return;
        lock (_gate)
        {
            if (!Owned(device)) return;
            SetAxisRaw(device, usage, position);
        }
    }

    void SetAxisRaw(int device, uint usage, double position)
    {
        if (!_axisRange.TryGetValue((device, usage), out var r))
        {
            int min = 0, max = 0;
            if (!GetVJDAxisMin((uint)device, usage, ref min)) min = 0;
            if (!GetVJDAxisMax((uint)device, usage, ref max) || max <= min) max = 0x8000;
            _axisRange[(device, usage)] = r = (min, max);
        }
        int value = r.Min + (int)Math.Round(Math.Clamp(position, 0, 1) * (r.Max - r.Min));
        SetAxis(value, (uint)device, usage);
    }

    public void Dispose()
    {
        lock (_gate)
        {
            try { foreach (var d in _owned) { ResetVJD((uint)d); RelinquishVJD((uint)d); } } catch { }
            _owned.Clear();
            _devices.Clear();
        }
    }
}
