using GameController.Companion.BeamNG;

namespace GameController.Companion.Tests;

public class BeamNgRuleTests
{
    static string B(string kind, string cmd) => "{\"id\":\"c\",\"kind\":\"" + kind + "\",\"action\":{\"type\":\"beamng\",\"command\":\"" + cmd + "\"}}";

    [Theory]
    [InlineData("lights.low.toggle", CommandStyle.Edge)]
    [InlineData("reset", CommandStyle.Edge)]
    [InlineData("camera.next", CommandStyle.Edge)]
    [InlineData("horn", CommandStyle.Level)]
    [InlineData("starter", CommandStyle.Level)]
    [InlineData("recover", CommandStyle.Level)]
    [InlineData("lights.low", CommandStyle.Level)]
    [InlineData("action.look_back", CommandStyle.Level)]
    [InlineData("gearbox.mode", CommandStyle.Indexed)]
    public void Classify(string cmd, CommandStyle style) => Assert.Equal(style, BeamNgCommands.Classify(cmd));

    [Fact]
    public void Mod_reported_toggle_makes_its_setter_a_level_command() =>
        Assert.Equal(CommandStyle.Level, BeamNgCommands.Classify("winch", new[] { "winch.toggle" }));

    [Fact]
    public void Toggle_on_momentary_fires_only_on_rising_edge()
    {
        var r = new Rig(B("momentary", "hazard.toggle"));
        r.Send("c", 1); r.Send("c", 1); r.Send("c", 0);
        Assert.Equal(new[] { ("hazard.toggle", 1.0) }, r.Commands.Sent);
    }

    [Fact]
    public void Horn_passes_both_edges()
    {
        var r = new Rig(B("momentary", "horn"));
        r.Send("c", 1); r.Send("c", 1); r.Send("c", 0);
        Assert.Equal(new[] { ("horn", 1.0), ("horn", 0.0) }, r.Commands.Sent);
    }

    [Fact]
    public void Latching_setter_sends_state_and_first_off()
    {
        var r = new Rig(B("latching", "lights.low"));
        r.Send("c", 0); r.Send("c", 1); r.Send("c", 1); r.Send("c", 0);
        Assert.Equal(new[] { ("lights.low", 0.0), ("lights.low", 1.0), ("lights.low", 1.0), ("lights.low", 0.0) }, r.Commands.Sent);
    }

    [Fact]
    public void Gearbox_mode_switch_sends_index()
    {
        var r = new Rig("""{"id":"g","kind":"switch","positions":[{"type":"beamng","command":"gearbox.mode"},{"type":"beamng","command":"gearbox.mode"},{"type":"beamng","command":"gearbox.mode"},{"type":"beamng","command":"gearbox.mode"}]}""");
        r.Send("g", 3); r.Send("g", 3); r.Send("g", 1);
        Assert.Equal(new[] { ("gearbox.mode", 3.0), ("gearbox.mode", 3.0), ("gearbox.mode", 1.0) }, r.Commands.Sent);
    }

    [Fact]
    public void Held_command_on_rotary_is_pressed_and_released()
    {
        var r = new Rig("""{"id":"z","kind":"rotary","cw":{"type":"beamng","command":"action.look_back"},"ccw":{"type":"beamng","command":"camera.prev"}}""");
        r.Send("z", 1); r.Send("z", -1);
        Rig.Settle();
        Assert.Equal(new[] { ("action.look_back", 1.0), ("camera.prev", 1.0), ("action.look_back", 0.0) }, r.Commands.Sent);
    }

    [Fact]
    public void Held_command_gets_zero_on_session_end()
    {
        var r = new Rig(B("latching", "action.parkingbrake_hold"));
        r.Send("c", 1);
        r.Engine.ReleaseAll();
        Assert.Equal(new[] { ("action.parkingbrake_hold", 1.0), ("action.parkingbrake_hold", 0.0) }, r.Commands.Sent);
    }

    [Fact]
    public void Silent_momentary_horn_is_auto_released()
    {
        var r = new Rig(B("momentary", "horn"));
        r.Send("c", 1);
        r.Now = 1500; r.Engine.Tick(r.Now);
        Assert.Equal(new[] { ("horn", 1.0), ("horn", 0.0) }, r.Commands.Sent);
    }

    [Fact]
    public void Changed_binding_releases_held_command()
    {
        var r = new Rig(B("momentary", "horn"));
        r.Send("c", 1);
        r.Load(B("momentary", "action.look_back"));
        Assert.Equal(new[] { ("horn", 1.0), ("horn", 0.0) }, r.Commands.Sent);
    }

    [Fact]
    public void Momentary_setter_is_held_and_zeroed()
    {
        var r = new Rig(B("momentary", "lights.high"));
        r.Send("c", 1);
        r.Now = 1500; r.Engine.Tick(r.Now);
        Assert.Equal(new[] { ("lights.high", 1.0), ("lights.high", 0.0) }, r.Commands.Sent);
        r.Send("c", 1);
        r.Engine.ReleaseAll();
        Assert.Equal(("lights.high", 0.0), r.Commands.Sent.Last());
    }

    [Fact]
    public void Toggle_on_latching_fires_on_every_event()
    {
        var r = new Rig(B("latching", "hazard.toggle"));
        r.Send("c", 1); r.Send("c", 0); r.Send("c", 1);
        Assert.Equal(3, r.Commands.Sent.Count);
        Assert.All(r.Commands.Sent, x => Assert.Equal(("hazard.toggle", 1.0), x));
    }

    [Fact]
    public void Pulsed_held_command_is_released_by_a_session_end_inside_the_pulse()
    {
        var r = new Rig("""{"id":"z","kind":"rotary","cw":{"type":"beamng","command":"horn"},"ccw":{"type":"beamng","command":"horn"}}""");
        r.Send("z", 1);
        r.Engine.ReleaseAll();
        Rig.Settle();
        Assert.Equal(new[] { ("horn", 1.0), ("horn", 0.0) }, r.Commands.Sent);
    }

    [Fact]
    public void Lost_release_does_not_swallow_the_next_toggle()
    {
        var r = new Rig(B("momentary", "hazard.toggle"));
        r.Send("c", 1);                       // press; every copy of the release is lost
        r.Now = 1500; r.Engine.Tick(r.Now);   // silence
        r.Send("c", 1);                       // the next press is a new rising edge
        Assert.Equal(2, r.Commands.Sent.Count);
    }

    [Fact]
    public void Binding_edit_while_held_does_not_refire_on_the_resend()
    {
        var r = new Rig(B("momentary", "hazard.toggle"));
        r.Send("c", 1);
        r.Load(B("momentary", "camera.next"));
        r.Send("c", 1);                       // the phone's 250 ms resend of the same press
        r.Send("c", 0);
        r.Send("c", 1);                       // a real new press
        Assert.Equal(new[] { ("hazard.toggle", 1.0), ("camera.next", 1.0) }, r.Commands.Sent);
    }

    [Fact]
    public void Rebind_then_lost_release_does_not_swallow_the_next_press()
    {
        var r = new Rig(B("momentary", "hazard.toggle"));
        r.Send("c", 1);
        r.Load(B("momentary", "camera.next"));   // edited while held; every copy of the release is lost
        r.Now = 1500; r.Engine.Tick(r.Now);
        r.Send("c", 1);                          // a real new press
        Assert.Equal(new[] { ("hazard.toggle", 1.0), ("camera.next", 1.0) }, r.Commands.Sent);
    }

    [Fact]
    public void Setters_are_not_zeroed_on_session_end()
    {
        var r = new Rig(B("latching", "lights.low"));
        r.Send("c", 1);
        r.Engine.ReleaseAll();
        Assert.Equal(new[] { ("lights.low", 1.0) }, r.Commands.Sent);
    }

    [Fact]
    public void Nothing_is_sent_without_the_mod()
    {
        var r = new Rig(B("momentary", "horn"));
        r.Commands.Connected = false;
        r.Send("c", 1);
        Assert.Empty(r.Commands.Sent);
    }
}
