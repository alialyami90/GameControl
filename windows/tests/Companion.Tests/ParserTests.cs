using System.Text.Json;
using GameController.Companion.Protocol;

namespace GameController.Companion.Tests;

public class ParserTests
{
    static Binding? Parse(string json, List<string>? w = null) => BindingParser.ParseBinding(JsonDocument.Parse(json).RootElement, w);

    [Fact]
    public void Parses_every_action_type()
    {
        var b = Parse("""
            {"id":"g","kind":"switch","positions":[
            {"type":"key","keys":["LCTRL","H"],"trigger":"tap"},
            {"type":"vjoy_button","device":2,"button":128},
            {"type":"vjoy_axis","device":1,"axis":"sl0","range":"unipolar"},
            {"type":"macro","steps":[{"keys":["E"],"holdMs":50},{"delayMs":120}]},
            {"type":"beamng","command":"lights.low.toggle"},
            {"type":"xinput_button","button":"dpad_up"},
            {"type":"xinput_axis","axis":"LT"},
            {"type":"none"}]}
            """)!;
        Assert.Equal(ControlKind.Switch, b.Kind);
        var p = b.Positions;
        Assert.Equal(ActionType.Key, p[0].Type);
        Assert.Equal(KeyTrigger.Tap, p[0].Trigger);
        Assert.Equal(new[] { "LCTRL", "H" }, p[0].Keys.Select(k => k.Name));
        Assert.Equal((2, 128), (p[1].Device, p[1].Button));
        Assert.Equal(("SL0", AxisRange.Unipolar), (p[2].Axis, p[2].Range));
        Assert.Equal(2, p[3].Steps.Count);
        Assert.Equal(120, p[3].Steps[1].DelayMs);
        Assert.Equal("lights.low.toggle", p[4].Command);
        Assert.Equal("DPAD_UP", p[5].XButton);
        Assert.Equal(AxisRange.Unipolar, p[6].Range); // triggers default to 0..1
        Assert.Equal(ActionType.None, p[7].Type);
    }

    [Fact]
    public void Unknown_action_type_and_fields_are_ignored()
    {
        var b = Parse("""{"id":"x","kind":"momentary","future":1,"action":{"type":"teleport","where":"moon"}}""")!;
        Assert.Equal(ActionType.None, b.Action.Type);
    }

    [Fact]
    public void Out_of_range_vjoy_becomes_none_with_warning()
    {
        var w = new List<string>();
        var b = Parse("""{"id":"x","kind":"momentary","action":{"type":"vjoy_button","device":17,"button":1}}""", w)!;
        Assert.Equal(ActionType.None, b.Action.Type);
        Assert.Single(w);
    }

    [Fact]
    public void Unknown_key_is_dropped_with_warning()
    {
        var w = new List<string>();
        var b = Parse("""{"id":"x","kind":"momentary","action":{"type":"key","keys":["LSHIFT","WHAT"]}}""", w)!;
        Assert.Equal(new[] { "LSHIFT" }, b.Action.Keys.Select(k => k.Name));
        Assert.Equal(KeyTrigger.Hold, b.Action.Trigger);
        Assert.Single(w);
    }

    [Fact]
    public void Missing_id_or_unknown_kind_is_skipped()
    {
        Assert.Null(Parse("""{"kind":"momentary"}"""));
        Assert.Null(Parse("""{"id":"x","kind":"joystick"}"""));
    }

    [Fact]
    public void Hid_route_is_read() =>
        Assert.True(Parse("""{"id":"x","kind":"momentary","route":"hid","action":{"type":"vjoy_button","device":1,"button":1}}""")!.RoutedOverHid);
}
