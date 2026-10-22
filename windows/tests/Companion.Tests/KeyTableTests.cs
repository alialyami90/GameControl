using GameController.Companion.Input;

namespace GameController.Companion.Tests;

public class KeyTableTests
{
    [Theory]
    [InlineData("A", 0x1E, false)]
    [InlineData("Q", 0x10, false)]
    [InlineData("M", 0x32, false)]
    [InlineData("D1", 0x02, false)]
    [InlineData("D0", 0x0B, false)]
    [InlineData("F1", 0x3B, false)]
    [InlineData("F10", 0x44, false)]
    [InlineData("F11", 0x57, false)]
    [InlineData("F13", 0x64, false)]
    [InlineData("F23", 0x6E, false)]
    [InlineData("F24", 0x76, false)]
    [InlineData("LCTRL", 0x1D, false)]
    [InlineData("RCTRL", 0x1D, true)]
    [InlineData("UP", 0x48, true)]
    [InlineData("NUMPAD8", 0x48, false)]
    [InlineData("NUMPAD0", 0x52, false)]
    [InlineData("NUMPAD_ENTER", 0x1C, true)]
    [InlineData("NUMPAD_DIVIDE", 0x35, true)]
    [InlineData("SLASH", 0x35, false)]
    [InlineData("PRINTSCREEN", 0x37, true)]
    [InlineData("NUMPAD_MULTIPLY", 0x37, false)]
    [InlineData("GRAVE", 0x29, false)]
    [InlineData("APPS", 0x5D, true)]
    public void Scancodes_match_set_1(string name, int scan, bool extended)
    {
        var k = KeyTable.Get(name);
        Assert.Equal(scan, k.Scan);
        Assert.Equal(extended, k.Extended);
    }

    [Fact]
    public void Every_protocol_key_name_is_known()
    {
        var names = new List<string>();
        for (char c = 'A'; c <= 'Z'; c++) names.Add(c.ToString());
        for (int i = 0; i <= 9; i++) { names.Add($"D{i}"); names.Add($"NUMPAD{i}"); }
        for (int i = 1; i <= 24; i++) names.Add($"F{i}");
        names.AddRange(("ESC TAB CAPSLOCK SPACE ENTER BACKSPACE LSHIFT RSHIFT LCTRL RCTRL LALT RALT LWIN RWIN APPS " +
            "UP DOWN LEFT RIGHT INSERT DELETE HOME END PAGEUP PAGEDOWN MINUS EQUALS LBRACKET RBRACKET SEMICOLON " +
            "APOSTROPHE GRAVE BACKSLASH COMMA PERIOD SLASH NUMPAD_ADD NUMPAD_SUBTRACT NUMPAD_MULTIPLY NUMPAD_DIVIDE " +
            "NUMPAD_DECIMAL NUMPAD_ENTER NUMLOCK PRINTSCREEN SCROLLLOCK PAUSE").Split(' '));
        foreach (var n in names) Assert.True(KeyTable.TryGet(n, out _), n);
        Assert.Equal(names.Count, KeyTable.Names.Count);
    }

    [Fact]
    public void Protocol_extended_list_is_extended()
    {
        foreach (var n in "RCTRL RALT LWIN RWIN APPS UP DOWN LEFT RIGHT INSERT DELETE HOME END PAGEUP PAGEDOWN NUMPAD_DIVIDE NUMPAD_ENTER PRINTSCREEN".Split(' '))
            Assert.True(KeyTable.Get(n).Extended, n);
    }

    [Fact]
    public void Lookup_is_case_insensitive() => Assert.True(KeyTable.TryGet("lctrl", out _));

    [Fact]
    public void No_two_keys_share_a_scancode()
    {
        var all = KeyTable.Names.Select(KeyTable.Get).ToList();
        Assert.Equal(all.Count, all.Select(k => (k.Scan, k.Extended)).Distinct().Count());
    }
}
