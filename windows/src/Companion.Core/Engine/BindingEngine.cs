using GameController.Companion.BeamNG;
using GameController.Companion.Input;
using GameController.Companion.Protocol;

namespace GameController.Companion.Engine;

public enum ApplyResult { Applied, Stale, UnknownControl, RoutedOverHid }

/// <summary>
/// Turns control events from one phone session into input, following the semantics in
/// PROTOCOL.md ("How each control kind sends values"). One instance per session key, so the
/// sequence table and held inputs are per phone. Duplicates are recognised by sequence number
/// only, never by value: the game can change state on its own, so the same value sent again
/// with a new sequence number is a real request.
/// </summary>
public sealed class BindingEngine
{
    public const int AutoReleaseMs = 1000;
    const int MaxDetentsPerEvent = 20;

    sealed class ControlState
    {
        public long LastSeq = long.MinValue;
        /// <summary>Last value applied; momentary and slider edges are computed from it.</summary>
        public double? LastApplied;
        public long LastUpdateMs;
        public readonly List<Holdable> Holds = new();
        /// <summary>BeamNG commands this control holds at 1 (horn, recover, action.*); they get a 0 on release.</summary>
        public readonly List<string> HeldCommands = new();
        /// <summary>The axis this control last moved, so a session end can park it.</summary>
        public ActionSpec? Axis;
        public bool MacroRunning;
        /// <summary>The binding changed while the control was pressed (see <see cref="IsOneShot"/>).</summary>
        public bool Rebound;

        public bool Holding => Holds.Count > 0 || HeldCommands.Count > 0;
    }

    readonly object _gate = new();
    readonly InputRouter _router;
    readonly ICommandSink _commands;
    readonly string _source;
    readonly string _sourceId;
    Dictionary<string, Binding> _bindings = new();
    readonly Dictionary<string, ControlState> _state = new();

    public BindingEngine(InputRouter router, ICommandSink commands, string source, string? sourceId = null)
    {
        _router = router; _commands = commands; _source = source; _sourceId = sourceId ?? source;
    }

    void Report(string id, string what, ActionType target = ActionType.None) => _router.Report(_sourceId, _source, id, what, target);

    public int BindingCount { get { lock (_gate) return _bindings.Count; } }
    public string? ProfileId { get; private set; }

    /// <summary>
    /// Replaces the active bindings. Controls whose binding is unchanged keep what they hold (a
    /// latched hold stays down across an edit); controls that were removed or changed are released.
    /// </summary>
    public void SetBindings(string? profileId, IEnumerable<Binding> bindings)
    {
        lock (_gate)
        {
            var map = new Dictionary<string, Binding>(StringComparer.Ordinal);
            foreach (var b in bindings) map[b.Id] = b;
            foreach (var (id, st) in _state)
            {
                bool same = map.TryGetValue(id, out var now) && _bindings.TryGetValue(id, out var old) && SameShape(old, now);
                if (same) continue;
                // Park the axis too: a slider rebound to another axis must not leave the old one deflected.
                ReleaseControl(id, st, "released (binding changed)", parkAxis: true);
                st.Rebound = st.LastApplied is > 0;
                st.LastApplied = null;
            }
            // New or changed axis bindings start at rest (unipolar 0, bipolar centre), so a throttle does
            // not read 50 % until its slider first moves.
            foreach (var (id, b) in map)
            {
                if (b.RoutedOverHid || b.Action.Type is not (ActionType.VJoyAxis or ActionType.XInputAxis)) continue;
                if (_bindings.TryGetValue(id, out var old) && SameShape(old, b)) continue;
                ParkAxis(b.Action);
            }
            _bindings = map;
            ProfileId = profileId;
        }
    }

    static bool SameShape(Binding a, Binding b) =>
        a.Kind == b.Kind && a.RoutedOverHid == b.RoutedOverHid &&
        a.Action.Signature() == b.Action.Signature() &&
        a.Positions.Count == b.Positions.Count && a.Positions.Zip(b.Positions).All(p => p.First.Signature() == p.Second.Signature()) &&
        a.Cw.Signature() == b.Cw.Signature() && a.Ccw.Signature() == b.Ccw.Signature();

    public ApplyResult Apply(string id, long seq, double value, long nowMs)
    {
        if (double.IsNaN(value) || double.IsInfinity(value)) return ApplyResult.Stale;
        lock (_gate)
        {
            if (!_bindings.TryGetValue(id, out var b)) return ApplyResult.UnknownControl;
            if (b.RoutedOverHid) return ApplyResult.RoutedOverHid;

            if (!_state.TryGetValue(id, out var st)) _state[id] = st = new ControlState();
            if (seq <= st.LastSeq) return ApplyResult.Stale;
            st.LastSeq = seq;
            st.LastUpdateMs = nowMs;

            switch (b.Kind)
            {
                case ControlKind.Momentary:
                case ControlKind.Slider:
                {
                    // Continuous inputs are re-sent while held, so their edges come from the last value.
                    if (st.Rebound)
                    {
                        // The first event after a binding edit is the phone's 250 ms resend of a press the
                        // user already made: holds follow it, but a one-shot must not fire for it.
                        st.Rebound = false;
                        if (IsOneShot(b.Action)) { st.LastApplied = value; return ApplyResult.Applied; }
                    }
                    bool first = st.LastApplied is null;
                    double prev = st.LastApplied ?? 0;
                    st.LastApplied = value;
                    ApplyLevel(b, b.Action, st, prev, value, first);
                    return ApplyResult.Applied;
                }
                case ControlKind.Latching:
                {
                    // Every latching event is a deliberate change: treat it as an edge in its own
                    // direction, so "on" after the game switched itself off still switches it on.
                    st.LastApplied = value;
                    ApplyLevel(b, b.Action, st, On(value) ? 0 : 1, value, first: true);
                    return ApplyResult.Applied;
                }
                case ControlKind.Switch:
                case ControlKind.Radio:
                {
                    int index = (int)Math.Round(value);
                    if (index < 0 || index >= b.Positions.Count) return ApplyResult.Stale;
                    st.LastApplied = index;
                    ApplyPosition(b, b.Positions[index], st, index, b.Positions.Count);
                    return ApplyResult.Applied;
                }
                case ControlKind.Rotary:
                {
                    int detents = Math.Clamp((int)Math.Round(value), -MaxDetentsPerEvent, MaxDetentsPerEvent);
                    var action = detents > 0 ? b.Cw : b.Ccw;
                    for (int i = 0; i < Math.Abs(detents); i++) ApplyOnce(b, action, st, null);
                    return ApplyResult.Applied;
                }
            }
            return ApplyResult.Stale;
        }
    }

    /// <summary>Auto-releases what momentary controls hold once they go silent (lost release packets).</summary>
    public void Tick(long nowMs)
    {
        lock (_gate)
        {
            foreach (var (id, st) in _state)
            {
                if (nowMs - st.LastUpdateMs < AutoReleaseMs) continue;
                if (!_bindings.TryGetValue(id, out var b) || b.Kind != ControlKind.Momentary) continue;
                // Also when nothing is held: a one-shot (toggle, press key, macro) whose release was lost
                // must see the next press as a new rising edge.
                if (st.Holding) ReleaseControl(id, st, "auto-released (no update for 1 s)", parkAxis: false);
                if (st.LastApplied is > 0) st.LastApplied = 0;
                st.Rebound = false; // the press it guarded is long over
            }
        }
    }

    /// <summary>
    /// Session end (or "Release all input"): every key and button this phone holds goes up, held
    /// BeamNG commands get value 0, and axes it moved go back to rest (bipolar centre, unipolar 0).
    /// </summary>
    public void ReleaseAll()
    {
        lock (_gate)
            foreach (var (id, st) in _state)
            {
                ReleaseControl(id, st, "released", parkAxis: true);
                if (_bindings.TryGetValue(id, out var b) && b.Kind is ControlKind.Momentary or ControlKind.Slider) st.LastApplied = null;
            }
    }

    void ReleaseControl(string id, ControlState st, string why, bool parkAxis)
    {
        bool any = st.Holding;
        for (int i = st.Holds.Count - 1; i >= 0; i--) _router.Up(st.Holds[i]);
        st.Holds.Clear();
        foreach (var cmd in st.HeldCommands)
            if (_commands.Connected) _commands.SendCommand(cmd, 0);
        st.HeldCommands.Clear();
        if (parkAxis && st.Axis is { } axis)
        {
            ParkAxis(axis);
            st.Axis = null;
            any = true;
        }
        if (any) Report(id, why);
    }

    void ParkAxis(ActionSpec a)
    {
        if (a.Type == ActionType.VJoyAxis)
            _router.VJoyAxis(a.Device, a.Axis, a.Range == AxisRange.Unipolar ? 0 : 0.5);
        else if (a.Type == ActionType.XInputAxis)
            _router.XboxAxis(a.Axis, 0); // sticks centre, triggers released
    }

    static bool On(double v) => v >= 0.5;

    /// <summary>Actions that fire once on a press rather than following the control's level.</summary>
    bool IsOneShot(ActionSpec a) => a.Type switch
    {
        ActionType.Key => a.Trigger != KeyTrigger.Hold,
        ActionType.Macro => true,
        ActionType.BeamNG => BeamNgCommands.Classify(a.Command, _commands.KnownCommands) == CommandStyle.Edge,
        _ => false,
    };

    /// <summary>momentary, latching and slider: the action follows the control's value.</summary>
    void ApplyLevel(Binding b, ActionSpec a, ControlState st, double prev, double cur, bool first)
    {
        bool rising = !On(prev) && On(cur), falling = On(prev) && !On(cur);
        switch (a.Type)
        {
            case ActionType.Key:
                switch (a.Trigger)
                {
                    case KeyTrigger.Hold:
                        if (On(cur) && st.Holds.Count == 0)
                        {
                            foreach (var k in a.Keys) { var h = Holdable.Key(k); _router.Down(h, k); st.Holds.Add(h); }
                            Report(b.Id, $"{KeyNames(a)} down", ActionType.Key);
                        }
                        else if (!On(cur) && st.Holds.Count > 0)
                        {
                            for (int i = st.Holds.Count - 1; i >= 0; i--) _router.Up(st.Holds[i]);
                            st.Holds.Clear();
                            Report(b.Id, $"{KeyNames(a)} up", ActionType.Key);
                        }
                        break;
                    case KeyTrigger.Tap:
                        if (rising || falling) Tap(b, a);
                        break;
                    case KeyTrigger.Press:
                        if (rising) Tap(b, a);
                        break;
                }
                break;

            case ActionType.VJoyButton:
            case ActionType.XInputButton:
            {
                var h = a.Type == ActionType.VJoyButton ? Holdable.VJoy(a.Device, a.Button) : Holdable.Xbox(a.XButton);
                if (On(cur) && st.Holds.Count == 0) { _router.Down(h); st.Holds.Add(h); Report(b.Id, $"{a} down", a.Type); }
                else if (!On(cur) && st.Holds.Count > 0)
                {
                    foreach (var held in st.Holds) _router.Up(held);
                    st.Holds.Clear();
                    Report(b.Id, $"{a} up", a.Type);
                }
                break;
            }

            case ActionType.VJoyAxis:
            {
                double pos = a.Range == AxisRange.Unipolar ? cur : (cur + 1) / 2;
                _router.VJoyAxis(a.Device, a.Axis, pos);
                st.Axis = a;
                Report(b.Id, $"{a} {Math.Clamp(pos, 0, 1):P0}", a.Type);
                break;
            }

            case ActionType.XInputAxis:
            {
                double v = a.Axis is "LT" or "RT"
                    ? (a.Range == AxisRange.Bipolar ? (cur + 1) / 2 : cur)
                    : (a.Range == AxisRange.Unipolar ? cur * 2 - 1 : cur);
                _router.XboxAxis(a.Axis, v);
                st.Axis = a;
                Report(b.Id, $"{a} {v:0.00}", a.Type);
                break;
            }

            case ActionType.Macro:
                if (rising) RunMacro(b, a, st);
                break;

            case ActionType.BeamNG:
                switch (BeamNgCommands.Classify(a.Command, _commands.KnownCommands))
                {
                    case CommandStyle.Edge:
                        // A toggle follows the switch it is bound to: a latching control flips it on every
                        // event (on and off), a momentary one only when pressed.
                        if (b.Kind == ControlKind.Latching ? (rising || falling) : rising) SendCommand(b, a.Command, 1);
                        break;
                    default:
                        // A setter is idempotent, so the very first value is sent even when it is "off":
                        // that brings the game in line with a latch the phone restored.
                        if (b.Kind == ControlKind.Slider ? (cur != prev || first) : (On(cur) != On(prev) || first))
                        {
                            double value = b.Kind == ControlKind.Slider ? cur : (On(cur) ? 1 : 0);
                            SendCommand(b, a.Command, value);
                            // Held: horn, starter, recover, action.*, and any setter a momentary control drives
                            // (flash-to-pass). A latched setter is the user's intent and is never zeroed.
                            if (BeamNgCommands.IsHeld(a.Command) || b.Kind == ControlKind.Momentary)
                            {
                                if (value > 0) { if (!st.HeldCommands.Contains(a.Command)) st.HeldCommands.Add(a.Command); }
                                else st.HeldCommands.Remove(a.Command);
                            }
                        }
                        break;
                }
                break;
        }
    }

    /// <summary>switch and radio: fire the selected position once.</summary>
    void ApplyPosition(Binding b, ActionSpec a, ControlState st, int index, int count)
    {
        switch (a.Type)
        {
            case ActionType.VJoyAxis:
            {
                double pos = count <= 1 ? 0 : (double)index / (count - 1);
                _router.VJoyAxis(a.Device, a.Axis, pos);
                st.Axis = a;
                Report(b.Id, $"{a} {pos:P0}", a.Type);
                break;
            }
            case ActionType.XInputAxis:
            {
                double pos = count <= 1 ? 0 : (double)index / (count - 1);
                double v = a.Axis is "LT" or "RT" ? pos : pos * 2 - 1;
                _router.XboxAxis(a.Axis, v);
                st.Axis = a;
                Report(b.Id, $"{a} {v:0.00}", a.Type);
                break;
            }
            default:
                ApplyOnce(b, a, st, index);
                break;
        }
    }

    /// <summary>A single discrete firing: tap, pulse, macro run or one command.</summary>
    void ApplyOnce(Binding b, ActionSpec a, ControlState st, int? index)
    {
        switch (a.Type)
        {
            case ActionType.Key: Tap(b, a); break;
            case ActionType.VJoyButton:
                _ = _router.Pulse(Holdable.VJoy(a.Device, a.Button));
                Report(b.Id, $"{a} pulse", a.Type);
                break;
            case ActionType.XInputButton:
                _ = _router.Pulse(Holdable.Xbox(a.XButton));
                Report(b.Id, $"{a} pulse", a.Type);
                break;
            case ActionType.Macro: RunMacro(b, a, st); break;
            case ActionType.BeamNG:
                var style = BeamNgCommands.Classify(a.Command, _commands.KnownCommands);
                if (style == CommandStyle.Indexed && index is { } i) SendCommand(b, a.Command, i);
                else if (BeamNgCommands.IsHeld(a.Command))
                {
                    // A held command (horn, action.*) fired from a switch or rotary is a short press. It counts as
                    // held until the release goes out, so a session end inside those 80 ms still sends the 0.
                    var cmd = a.Command;
                    SendCommand(b, cmd, 1);
                    if (!st.HeldCommands.Contains(cmd)) st.HeldCommands.Add(cmd);
                    _ = Task.Delay(InputRouter.PulseMs).ContinueWith(_ =>
                    {
                        try { lock (_gate) if (st.HeldCommands.Remove(cmd)) SendCommand(b, cmd, 0); }
                        catch (Exception e) { Report(b.Id, $"BeamNG {cmd} release failed: {e.Message}"); }
                    }, TaskScheduler.Default);
                }
                else SendCommand(b, a.Command, 1);
                break;
        }
    }

    void Tap(Binding b, ActionSpec a)
    {
        _ = _router.TapKeys(a.Keys);
        Report(b.Id, $"{KeyNames(a)} tap", ActionType.Key);
    }

    void SendCommand(Binding b, string command, double value)
    {
        if (!_commands.Connected) { Report(b.Id, $"BeamNG {command} skipped (mod not connected)"); return; }
        _commands.SendCommand(command, value);
        Report(b.Id, $"BeamNG {command} = {value:0.##}", ActionType.BeamNG);
    }

    void RunMacro(Binding b, ActionSpec a, ControlState st)
    {
        if (st.MacroRunning) return;
        st.MacroRunning = true;
        Report(b.Id, $"macro start ({a.Steps.Count} steps)", ActionType.Key);
        _ = Task.Run(async () =>
        {
            try
            {
                foreach (var step in a.Steps)
                {
                    if (step.Keys.Count > 0) await _router.TapKeys(step.Keys, step.HoldMs).ConfigureAwait(false);
                    else await Task.Delay(step.DelayMs).ConfigureAwait(false);
                }
            }
            catch (Exception e) { Report(b.Id, $"macro stopped: {e.Message}"); }
            finally
            {
                lock (_gate) st.MacroRunning = false;
            }
        });
    }

    static string KeyNames(ActionSpec a) => string.Join("+", a.Keys.Select(k => k.Name));
}
