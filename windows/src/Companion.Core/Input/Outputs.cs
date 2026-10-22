namespace GameController.Companion.Input;

/// <summary>Injects keyboard input. On Windows this is SendInput with scancodes.</summary>
public interface IKeyboardOutput
{
    bool Available { get; }
    void Send(ScanKey key, bool down);
}

/// <summary>A vJoy virtual joystick driver (devices 1-16, buttons 1-128, 8 axes).</summary>
public interface IVJoyOutput
{
    bool Available { get; }
    IReadOnlyList<int> Devices { get; }
    /// <summary>Re-checks the driver; returns true when the set of devices changed.</summary>
    bool Refresh();
    void SetButton(int device, int button, bool down);
    /// <summary>Sets an axis to <paramref name="position"/> in 0..1 of its full range.</summary>
    void SetAxis(int device, string axis, double position);
}

/// <summary>A virtual Xbox 360 pad (ViGEm). Optional.</summary>
public interface IXInputOutput
{
    bool Available { get; }
    bool Refresh();
    void SetButton(string button, bool down);
    /// <summary>Sticks take -1..1, triggers 0..1.</summary>
    void SetAxis(string axis, double value);
    /// <summary>True once the virtual pad is plugged in (it is created on first use).</summary>
    bool InUse => false;
}

public sealed class NullKeyboard : IKeyboardOutput
{
    public bool Available => false;
    public void Send(ScanKey key, bool down) { }
}

public sealed class NullVJoy : IVJoyOutput
{
    public bool Available => false;
    public IReadOnlyList<int> Devices => Array.Empty<int>();
    public bool Refresh() => false;
    public void SetButton(int device, int button, bool down) { }
    public void SetAxis(int device, string axis, double position) { }
}

public sealed class NullXInput : IXInputOutput
{
    public bool Available => false;
    public bool Refresh() => false;
    public void SetButton(string button, bool down) { }
    public void SetAxis(string axis, double value) { }
}

/// <summary>Records everything instead of injecting it. Used by the console host on non-Windows and by tests.</summary>
public sealed class RecordingOutputs : IKeyboardOutput, IVJoyOutput, IXInputOutput
{
    readonly object _gate = new();
    readonly List<string> _log = new();
    public Action<string>? OnEvent { get; set; }

    public IReadOnlyList<string> Log { get { lock (_gate) return _log.ToList(); } }
    public void Clear() { lock (_gate) _log.Clear(); }

    void Add(string s) { lock (_gate) _log.Add(s); OnEvent?.Invoke(s); }

    bool IKeyboardOutput.Available => true;
    bool IVJoyOutput.Available => true;
    bool IXInputOutput.Available => true;
    public IReadOnlyList<int> Devices { get; } = new[] { 1, 2 };
    bool IVJoyOutput.Refresh() => false;
    bool IXInputOutput.Refresh() => false;

    public void Send(ScanKey key, bool down) => Add($"key {key.Name} {(down ? "down" : "up")}");
    void IVJoyOutput.SetButton(int device, int button, bool down) => Add($"vjoy {device} btn {button} {(down ? "down" : "up")}");
    void IVJoyOutput.SetAxis(int device, string axis, double position) => Add($"vjoy {device} axis {axis} {position:0.###}");
    void IXInputOutput.SetButton(string button, bool down) => Add($"xbox {button} {(down ? "down" : "up")}");
    void IXInputOutput.SetAxis(string axis, double value) => Add($"xbox {axis} {value:0.###}");
}
