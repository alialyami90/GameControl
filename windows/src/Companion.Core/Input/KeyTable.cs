namespace GameController.Companion.Input;

/// <summary>A key as Windows SendInput needs it: a set-1 scancode plus the extended-key flag.</summary>
public readonly record struct ScanKey(string Name, ushort Scan, bool Extended);

/// <summary>Maps protocol key names (PROTOCOL.md "Key names") to set-1 scancodes.</summary>
public static class KeyTable
{
    static readonly Dictionary<string, ScanKey> Keys = Build();

    public static IReadOnlyCollection<string> Names => Keys.Keys;

    public static bool TryGet(string name, out ScanKey key) =>
        Keys.TryGetValue(name.Trim().ToUpperInvariant(), out key);

    public static ScanKey Get(string name) =>
        TryGet(name, out var k) ? k : throw new KeyNotFoundException($"Unknown key name '{name}'");

    static Dictionary<string, ScanKey> Build()
    {
        var d = new Dictionary<string, ScanKey>(StringComparer.OrdinalIgnoreCase);
        void N(string name, ushort scan) => d[name] = new ScanKey(name, scan, false);
        void E(string name, ushort scan) => d[name] = new ScanKey(name, scan, true);

        // Letters, by keyboard row.
        string row1 = "QWERTYUIOP", row2 = "ASDFGHJKL", row3 = "ZXCVBNM";
        for (int i = 0; i < row1.Length; i++) N(row1[i].ToString(), (ushort)(0x10 + i));
        for (int i = 0; i < row2.Length; i++) N(row2[i].ToString(), (ushort)(0x1E + i));
        for (int i = 0; i < row3.Length; i++) N(row3[i].ToString(), (ushort)(0x2C + i));

        // Digits: 1-9 are 0x02-0x0A, 0 is 0x0B.
        for (int i = 1; i <= 9; i++) N($"D{i}", (ushort)(0x01 + i));
        N("D0", 0x0B);

        // Function keys.
        for (int i = 1; i <= 10; i++) N($"F{i}", (ushort)(0x3A + i));
        N("F11", 0x57); N("F12", 0x58);
        for (int i = 13; i <= 23; i++) N($"F{i}", (ushort)(0x64 + i - 13));
        N("F24", 0x76);

        N("ESC", 0x01); N("TAB", 0x0F); N("CAPSLOCK", 0x3A); N("SPACE", 0x39);
        N("ENTER", 0x1C); N("BACKSPACE", 0x0E);

        N("LSHIFT", 0x2A); N("RSHIFT", 0x36); N("LCTRL", 0x1D); E("RCTRL", 0x1D);
        N("LALT", 0x38); E("RALT", 0x38); E("LWIN", 0x5B); E("RWIN", 0x5C); E("APPS", 0x5D);

        E("UP", 0x48); E("DOWN", 0x50); E("LEFT", 0x4B); E("RIGHT", 0x4D);
        E("INSERT", 0x52); E("DELETE", 0x53); E("HOME", 0x47); E("END", 0x4F);
        E("PAGEUP", 0x49); E("PAGEDOWN", 0x51);

        N("MINUS", 0x0C); N("EQUALS", 0x0D); N("LBRACKET", 0x1A); N("RBRACKET", 0x1B);
        N("SEMICOLON", 0x27); N("APOSTROPHE", 0x28); N("GRAVE", 0x29); N("BACKSLASH", 0x2B);
        N("COMMA", 0x33); N("PERIOD", 0x34); N("SLASH", 0x35);

        ushort[] numpad = { 0x52, 0x4F, 0x50, 0x51, 0x4B, 0x4C, 0x4D, 0x47, 0x48, 0x49 };
        for (int i = 0; i <= 9; i++) N($"NUMPAD{i}", numpad[i]);
        N("NUMPAD_ADD", 0x4E); N("NUMPAD_SUBTRACT", 0x4A); N("NUMPAD_MULTIPLY", 0x37);
        E("NUMPAD_DIVIDE", 0x35); N("NUMPAD_DECIMAL", 0x53); E("NUMPAD_ENTER", 0x1C); N("NUMLOCK", 0x45);

        E("PRINTSCREEN", 0x37); N("SCROLLLOCK", 0x46);
        // Pause is really E1 1D 45, which SendInput cannot express. E0 45 is what
        // DirectInput reports as DIK_PAUSE (0xC5), so that is what games bind to.
        E("PAUSE", 0x45);
        return d;
    }
}
