using GameController.Companion.Engine;

namespace GameController.Companion.Tests;

public class EngineTests
{
    const string Latch = """{"id":"l","kind":"latching","action":{"type":"vjoy_button","device":1,"button":7}}""";
    const string Horn = """{"id":"horn","kind":"momentary","action":{"type":"key","keys":["H"],"trigger":"hold"}}""";

    [Fact]
    public void Momentary_hold_key_follows_value_and_ignores_repeats()
    {
        var r = new Rig(Horn);
        r.Send("horn", 1); r.Send("horn", 1); r.Send("horn", 0); r.Send("horn", 0); r.Send("horn", 0);
        Assert.Equal(new[] { "key H down", "key H up" }, r.Out.Log);
    }

    [Fact]
    public void Combo_releases_in_reverse_order()
    {
        var r = new Rig("""{"id":"c","kind":"momentary","action":{"type":"key","keys":["LCTRL","LSHIFT","S"]}}""");
        r.Send("c", 1); r.Send("c", 0);
        Assert.Equal(new[] { "key LCTRL down", "key LSHIFT down", "key S down", "key S up", "key LSHIFT up", "key LCTRL up" }, r.Out.Log);
    }

    [Fact]
    public void Stale_sequence_is_dropped_per_control()
    {
        var r = new Rig(Horn, """{"id":"b","kind":"momentary","action":{"type":"vjoy_button","device":1,"button":2}}""");
        Assert.Equal(ApplyResult.Applied, r.SendSeq("horn", 10, 1));
        Assert.Equal(ApplyResult.Stale, r.SendSeq("horn", 9, 0));
        Assert.Equal(ApplyResult.Stale, r.SendSeq("horn", 10, 0));
        Assert.Equal(ApplyResult.Applied, r.SendSeq("b", 5, 1)); // other ids have their own table
        Assert.Equal(new[] { "key H down", "vjoy 1 btn 2 down" }, r.Out.Log);
    }

    [Fact]
    public void Momentary_auto_releases_after_one_second_without_updates()
    {
        var r = new Rig(Horn);
        r.Send("horn", 1);
        r.Now = 900; r.Engine.Tick(r.Now);
        Assert.Single(r.Out.Log);
        r.Now = 1001; r.Engine.Tick(r.Now);
        Assert.Equal(new[] { "key H down", "key H up" }, r.Out.Log);
        // A late repeat of the press starts a new hold, the late release is then harmless.
        r.Send("horn", 1); r.Send("horn", 0);
        Assert.Equal(4, r.Out.Log.Count);
    }

    [Fact]
    public void Keep_alive_resend_postpones_auto_release()
    {
        var r = new Rig(Horn);
        r.Send("horn", 1);
        for (r.Now = 250; r.Now <= 3000; r.Now += 250) { r.Send("horn", 1); r.Engine.Tick(r.Now); }
        Assert.Equal(new[] { "key H down" }, r.Out.Log);
    }

    [Fact]
    public void Latching_hold_is_never_auto_released()
    {
        var r = new Rig("""{"id":"l","kind":"latching","action":{"type":"vjoy_button","device":1,"button":7}}""");
        r.Send("l", 1);
        r.Now = 5000; r.Engine.Tick(r.Now);
        Assert.Equal(new[] { "vjoy 1 btn 7 down" }, r.Out.Log);
    }

    [Fact]
    public void Latching_tap_taps_on_every_new_sequence_even_with_the_same_value()
    {
        var r = new Rig("""{"id":"l","kind":"latching","action":{"type":"key","keys":["L"],"trigger":"tap"}}""");
        Assert.Equal(ApplyResult.Applied, r.Send("l", 1));
        Assert.Equal(ApplyResult.Applied, r.Send("l", 1));
        Assert.Equal(ApplyResult.Stale, r.SendSeq("l", 2, 1));
        Rig.Settle();
        Assert.Equal(new[] { "key L down", "key L up", "key L down", "key L up" }, r.Out.Log);
    }

    [Fact]
    public void Press_trigger_taps_only_on_rising_edge()
    {
        var r = new Rig("""{"id":"p","kind":"momentary","action":{"type":"key","keys":["P"],"trigger":"press"}}""");
        r.Send("p", 1); r.Send("p", 0); r.Send("p", 1); r.Send("p", 0);
        Rig.Settle();
        Assert.Equal(new[] { "key P down", "key P up", "key P down", "key P up" }, r.Out.Log);
    }

    [Fact]
    public void Switch_taps_selected_position_and_pulses_vjoy()
    {
        var r = new Rig("""{"id":"s","kind":"switch","positions":[{"type":"key","keys":["D1"],"trigger":"hold"},{"type":"vjoy_button","device":1,"button":3}]}""");
        r.Send("s", 0); r.Send("s", 0); r.Send("s", 1); r.Send("s", 5);
        Rig.Settle();
        // A repeated position is a new request (sequence-only dedupe), so D1 taps twice. Outputs run concurrently, so compare sorted.
        Assert.Equal(new[] { "key D1 down", "key D1 up", "key D1 down", "key D1 up", "vjoy 1 btn 3 down", "vjoy 1 btn 3 up" }.Order(), r.Out.Log.Order());
    }

    [Fact]
    public void Switch_with_axis_maps_index_to_fraction_of_range()
    {
        var a = """{"type":"vjoy_axis","device":1,"axis":"SL1"}""";
        var r = new Rig($$"""{"id":"r","kind":"radio","positions":[{{a}},{{a}},{{a}}]}""");
        r.Send("r", 2); r.Send("r", 1); r.Send("r", 0);
        Assert.Equal(new[] { "vjoy 1 axis SL1 1", "vjoy 1 axis SL1 0.5", "vjoy 1 axis SL1 0" }, r.Out.Log);
    }

    [Fact]
    public void Slider_maps_bipolar_and_unipolar()
    {
        var r = new Rig(
            """{"id":"bi","kind":"slider","action":{"type":"vjoy_axis","device":1,"axis":"X"}}""",
            """{"id":"uni","kind":"slider","action":{"type":"vjoy_axis","device":1,"axis":"SL0","range":"unipolar"}}""",
            """{"id":"lt","kind":"slider","action":{"type":"xinput_axis","axis":"LT"}}""",
            """{"id":"lx","kind":"slider","action":{"type":"xinput_axis","axis":"LX"}}""");
        int start = r.Out.Log.Count;
        r.Send("bi", -1); r.Send("bi", 0); r.Send("uni", 0.25); r.Send("lt", 0.5); r.Send("lx", -0.5);
        Assert.Equal(new[] { "vjoy 1 axis X 0", "vjoy 1 axis X 0.5", "vjoy 1 axis SL0 0.25", "xbox LT 0.5", "xbox LX -0.5" }, r.Out.Log.Skip(start));
    }

    [Fact]
    public void Profile_load_parks_new_axes_at_rest()
    {
        var r = new Rig(
            """{"id":"bi","kind":"slider","action":{"type":"vjoy_axis","device":1,"axis":"X"}}""",
            """{"id":"thr","kind":"slider","action":{"type":"vjoy_axis","device":1,"axis":"Z","range":"unipolar"}}""");
        Assert.Equal(new[] { "vjoy 1 axis X 0.5", "vjoy 1 axis Z 0" }, r.Out.Log.Order());
    }

    [Fact]
    public void Rebinding_a_slider_parks_the_old_axis()
    {
        var r = new Rig("""{"id":"s","kind":"slider","action":{"type":"vjoy_axis","device":1,"axis":"X","range":"unipolar"}}""");
        r.Send("s", 1);
        int start = r.Out.Log.Count;
        r.Load("""{"id":"s","kind":"slider","action":{"type":"vjoy_axis","device":1,"axis":"Z","range":"unipolar"}}""");
        Assert.Contains("vjoy 1 axis X 0", r.Out.Log.Skip(start));
    }

    [Fact]
    public void Range_change_counts_as_a_changed_binding()
    {
        var r = new Rig("""{"id":"s","kind":"slider","action":{"type":"vjoy_axis","device":1,"axis":"X","range":"unipolar"}}""");
        r.Send("s", 1);
        int start = r.Out.Log.Count;
        r.Load("""{"id":"s","kind":"slider","action":{"type":"vjoy_axis","device":1,"axis":"X","range":"bipolar"}}""");
        r.Engine.ReleaseAll();
        Assert.Equal("vjoy 1 axis X 0.5", r.Out.Log.Last());
        Assert.NotEqual(start, r.Out.Log.Count);
    }

    [Fact]
    public void Slider_holds_its_value_without_updates()
    {
        var r = new Rig("""{"id":"t","kind":"slider","action":{"type":"vjoy_axis","device":1,"axis":"Z"}}""");
        int start = r.Out.Log.Count;
        r.Send("t", 0.5);
        r.Now = 10_000; r.Engine.Tick(r.Now);
        Assert.Single(r.Out.Log.Skip(start));
    }

    [Fact]
    public void Rotary_taps_once_per_detent_as_separate_presses()
    {
        var r = new Rig("""{"id":"z","kind":"rotary","cw":{"type":"key","keys":["E"]},"ccw":{"type":"key","keys":["Q"]}}""");
        r.Send("z", 2); r.Send("z", -1);
        Rig.Settle();
        var log = r.Out.Log.ToList();
        Assert.Equal(2, log.Count(x => x == "key E down"));
        Assert.Equal(2, log.Count(x => x == "key E up"));
        Assert.Equal(1, log.Count(x => x == "key Q down"));
        // The second E press comes after the first E release.
        Assert.True(log.IndexOf("key E up") < log.LastIndexOf("key E down"));
    }

    [Fact]
    public void Macro_runs_steps_in_order_once_per_press()
    {
        var r = new Rig("""{"id":"m","kind":"momentary","action":{"type":"macro","steps":[{"keys":["E"],"holdMs":20},{"delayMs":30},{"keys":["LSHIFT","E"],"holdMs":20}]}}""");
        r.Send("m", 1); r.Send("m", 1); r.Send("m", 0);
        Rig.Settle(500);
        Assert.Equal(new[] { "key E down", "key E up", "key LSHIFT down", "key E down", "key E up", "key LSHIFT up" }, r.Out.Log);
    }

    [Fact]
    public void Xbox_button_hold_and_auto_release()
    {
        var r = new Rig("""{"id":"a","kind":"momentary","action":{"type":"xinput_button","button":"A"}}""");
        r.Send("a", 1);
        r.Now = 1500; r.Engine.Tick(r.Now);
        Assert.Equal(new[] { "xbox A down", "xbox A up" }, r.Out.Log);
    }

    [Fact]
    public void Hid_routed_controls_are_not_injected()
    {
        var r = new Rig("""{"id":"h","kind":"momentary","route":"hid","action":{"type":"vjoy_button","device":1,"button":1}}""");
        Assert.Equal(ApplyResult.RoutedOverHid, r.Send("h", 1));
        Assert.Empty(r.Out.Log);
    }

    [Fact]
    public void Unknown_control_is_reported()
    {
        var r = new Rig(Horn);
        Assert.Equal(ApplyResult.UnknownControl, r.Send("nope", 1));
    }

    [Fact]
    public void Release_all_frees_only_this_session_and_shared_keys_stay_down()
    {
        var a = new Rig(Horn);
        var b = new BindingEngine(a.Router, a.Commands, "other");
        b.SetBindings("p", new[] { GameController.Companion.Protocol.BindingParser.ParseBinding(System.Text.Json.JsonDocument.Parse(Horn).RootElement)! });
        a.Send("horn", 1);
        b.Apply("horn", 1, 1, 0);
        a.Engine.ReleaseAll();
        Assert.Equal(new[] { "key H down" }, a.Out.Log); // still held by the other phone
        b.ReleaseAll();
        Assert.Equal(new[] { "key H down", "key H up" }, a.Out.Log);
    }

    [Fact]
    public void Changed_binding_releases_its_hold_on_resync()
    {
        var r = new Rig(Horn);
        r.Send("horn", 1);
        r.Load(Horn.Replace("\"H\"", "\"J\""));
        Assert.Equal(new[] { "key H down", "key H up" }, r.Out.Log);
    }

    [Fact]
    public void Removed_binding_releases_its_hold_on_resync()
    {
        var r = new Rig(Horn, Latch);
        r.Send("horn", 1);
        r.Load(Latch);
        Assert.Equal(new[] { "key H down", "key H up" }, r.Out.Log);
    }

    [Fact]
    public void Unchanged_latched_hold_survives_resync()
    {
        var r = new Rig(Latch, Horn);
        r.Send("l", 1);
        r.Load(Latch, Horn.Replace("\"H\"", "\"J\""));
        r.Send("l", 0);
        Assert.Equal(new[] { "vjoy 1 btn 7 down", "vjoy 1 btn 7 up" }, r.Out.Log);
    }

    [Fact]
    public void Session_end_parks_axes()
    {
        var r = new Rig(
            """{"id":"t","kind":"slider","action":{"type":"vjoy_axis","device":1,"axis":"Z","range":"unipolar"}}""",
            """{"id":"s","kind":"slider","action":{"type":"vjoy_axis","device":1,"axis":"X","range":"bipolar"}}""",
            """{"id":"x","kind":"slider","action":{"type":"xinput_axis","axis":"LX"}}""");
        r.Send("t", 0.8); r.Send("s", 0.5); r.Send("x", -0.4);
        int before = r.Out.Log.Count;
        r.Engine.ReleaseAll();
        var parked = r.Out.Log.Skip(before).ToList();
        Assert.Contains("vjoy 1 axis Z 0", parked);
        Assert.Contains("vjoy 1 axis X 0.5", parked);
        Assert.Contains("xbox LX 0", parked);
    }

    [Fact]
    public void Sequence_table_survives_resync()
    {
        var tap = """{"id":"l","kind":"latching","action":{"type":"key","keys":["L"],"trigger":"tap"}}""";
        var r = new Rig(tap);
        r.SendSeq("l", 5, 1);
        r.Load(tap);
        Assert.Equal(ApplyResult.Stale, r.SendSeq("l", 5, 1));
        Assert.Equal(ApplyResult.Applied, r.SendSeq("l", 6, 1));
    }

    [Fact]
    public void NaN_is_ignored()
    {
        var r = new Rig(Horn);
        Assert.Equal(ApplyResult.Stale, r.Send("horn", double.NaN));
    }
}
