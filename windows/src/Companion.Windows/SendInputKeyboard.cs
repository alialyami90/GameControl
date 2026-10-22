using System.Runtime.InteropServices;
using System.Runtime.Versioning;
using GameController.Companion.Input;

namespace GameController.Companion.Windows;

/// <summary>
/// Keyboard injection with SendInput and hardware scancodes (KEYEVENTF_SCANCODE), which
/// DirectInput and raw-input games see as a real keyboard. Virtual-key events are never used.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class SendInputKeyboard : IKeyboardOutput
{
    const uint INPUT_KEYBOARD = 1;
    const uint KEYEVENTF_EXTENDEDKEY = 0x0001, KEYEVENTF_KEYUP = 0x0002, KEYEVENTF_SCANCODE = 0x0008;

    [StructLayout(LayoutKind.Sequential)]
    struct KEYBDINPUT { public ushort wVk; public ushort wScan; public uint dwFlags; public uint time; public IntPtr dwExtraInfo; }

    [StructLayout(LayoutKind.Sequential)]
    struct MOUSEINPUT { public int dx; public int dy; public uint mouseData; public uint dwFlags; public uint time; public IntPtr dwExtraInfo; }

    // The union must be as large as its largest member (MOUSEINPUT) or SendInput rejects cbSize.
    [StructLayout(LayoutKind.Explicit)]
    struct InputUnion { [FieldOffset(0)] public MOUSEINPUT mi; [FieldOffset(0)] public KEYBDINPUT ki; }

    [StructLayout(LayoutKind.Sequential)]
    struct INPUT { public uint type; public InputUnion u; }

    [DllImport("user32.dll", SetLastError = true)]
    static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    /// <summary>Tags our events so a hook can tell them apart from the real keyboard.</summary>
    static readonly IntPtr ExtraInfo = new(0x47435452); // "GCTR"

    public bool Available => true;

    /// <summary>Raised when Windows refuses the input (usually UIPI: the game runs as administrator).</summary>
    public event Action<int>? Blocked;

    public void Send(ScanKey key, bool down)
    {
        uint flags = KEYEVENTF_SCANCODE;
        if (key.Extended) flags |= KEYEVENTF_EXTENDEDKEY;
        if (!down) flags |= KEYEVENTF_KEYUP;
        var input = new[]
        {
            new INPUT
            {
                type = INPUT_KEYBOARD,
                u = new InputUnion { ki = new KEYBDINPUT { wVk = 0, wScan = key.Scan, dwFlags = flags, dwExtraInfo = ExtraInfo } },
            },
        };
        if (SendInput(1, input, Marshal.SizeOf<INPUT>()) == 0) Blocked?.Invoke(Marshal.GetLastWin32Error());
    }
}
