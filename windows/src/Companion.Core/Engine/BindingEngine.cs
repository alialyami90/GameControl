using GameController.Companion.BeamNG;
using GameController.Companion.Input;
using GameController.Companion.Protocol;

namespace GameController.Companion.Engine;

public enum ApplyResult { Applied, Stale, Repeat, UnknownControl, RoutedOverHid }

/// <summary>
/// Turns control events from one phone session into input, following the semantics in
/// PROTOCOL.md ("How each control kind sends values"). One instance per session key, so the
/// sequence table, idempotency table and held inputs are all per phone.
/// </summary>
public sealed class BindingEngine
{
    public const int AutoReleaseMs = 1000;
    const int MaxDetentsPerEvent = 20;

    sealed class ControlState
    {
        public long LastSeq = long.MinValue;
        public double? LastApplied;
        public long LastUpdateMs;
        public readonly List<Holdable> Holds = new();
        public bool MacroRunning;
    }

    readonly object _gate = new();
    readonly InputRouter _router;
    readonly ICommandSink _commands;
    readonly string _source;
    Dictionary<string, Binding> _bindings = new();
    readonly Dictionary<string, ControlState> _state = new();

    public BindingEngine(InputRouter router, ICommandSink commands, string source)
    {
        _router = router; _commands = commands; _source = source;
    }

    public int BindingCount { get { lock (_gate) return _bindings.Count; } }
    public string? ProfileId { get; private set; }

    /// <summary>Replaces the active bindings. Anything this session held is released first.</summary>
    public void SetBindings(string? profileId, IEnumerable<Binding> bindings)
    {
        lock (_gate)
        {
            ReleaseAllLocked();
            var map = new Dictionary<string, Binding>(StringComparer.Ordinal);
            foreach (var b in bindings) map[b.Id] = b;
            // A control whose meaning changed must not be skipped as a "repeat" of its old value.
            foreach (var (id, st) in _state)
                if (!map.ContainsKey(id) || !_bindings.TryGetValue(id, out var old) || !SameShape(old, map[id]))
                    st.LastApplied = null;
            _bindings = map;
            ProfileId = profileId;
        }
    }

    static bool SameShape(Binding a, Binding b) =>
        a.Kind == b.Kind && a.RoutedOverHid == b.RoutedOverHid &&
        a.Action.ToString() == b.Action.ToString() &&
        string.Join("|", a.Positions) == string.Join("|", b.Positions) &&
        a.Cw.ToString() == b.Cw.ToString() && a.Ccw.ToString() == b.Ccw.ToString();

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
                    bool first = st.LastApplied is null;
                    double prev = st.LastApplied ?? 0;
                    st.LastApplied = value;
                    ApplyLevel(b, b.Action, st, prev, value, first);
                    return ApplyResult.Applied;
                }
                case ControlKind.Latching:
                {
                    if (st.LastApplied == value) return ApplyResult.Repeat;
                    bool first = st.LastApplied is null;
                    double prev = st.LastApplied ?? 0;
                    st.LastApplied = value;
                    ApplyLevel(b, b.Action, st, prev, value, first);
                    return ApplyResult.Applied;
                }
                case ControlKind.Switch:
                case ControlKind.Radio:
                {
                    int index = (int)Math.Round(value);
                    if (st.LastApplied == index) return ApplyResult.Repeat;
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

    /// <summary>Auto-releases held inputs of momentary controls that went silent (lost release packets).</summary>
    public void Tick(long nowMs)
    {
        lock (_gate)
        {
            foreach (var (id, st) in _state)
            {
                if (st.Holds.Count == 0 || nowMs - st.LastUpdateMs < AutoReleaseMs) continue;
                if (!_bindings.TryGetValue(id, out var b) || b.Kind != ControlKind.Momentary) continue;
                ReleaseHolds(id, st, "auto-released (no update for 1 s)");
                st.LastApplied = 0;
            }
        }
    }

    /// <summary>Releases only what this session holds (phone disconnect, profile change).</summary>
    public void ReleaseAll()
    {
        lock (_gate) ReleaseAllLocked();
    }

    void ReleaseAllLocked()
    {
        foreach (var (id, st) in _state)
        {
            if (st.Holds.Count == 0) continue;
            ReleaseHolds(id, st, "released");
            if (_bindings.TryGetValue(id, out var b) && b.Kind == ControlKind.Momentary) st.LastApplied = 0;
        }
    }

    void ReleaseHolds(string id, ControlState st, string why)
    {
        for (int i = st.Holds.Count - 1; i >= 0; i--) _router.Up(st.Holds[i]);
        st.Holds.Clear();
        _router.Report(_source, id, why);
    }

    static bool On(double v) => v >= 0.5;

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
                            _router.Report(_source, b.Id, $"{KeyNames(a)} down");
                        }
                        else if (!On(cur) && st.Holds.Count > 0)
                        {
                            for (int i = st.Holds.Count - 1; i >= 0; i--) _router.Up(st.Holds[i]);
                            st.Holds.Clear();
                            _router.Report(_source, b.Id, $"{KeyNames(a)} up");
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
                if (On(cur) && st.Holds.Count == 0) { _router.Down(h); st.Holds.Add(h); _router.Report(_source, b.Id, $"{a} down"); }
                else if (!On(cur) && st.Holds.Count > 0)
                {
                    foreach (var held in st.Holds) _router.Up(held);
                    st.Holds.Clear();
                    _router.Report(_source, b.Id, $"{a} up");
                }
                break;
            }

            case ActionType.VJoyAxis:
            {
                double pos = a.Range == AxisRange.Unipolar ? cur : (cur + 1) / 2;
                _router.VJoyAxis(a.Device, a.Axis, pos);
                _router.Report(_source, b.Id, $"{a} {Math.Clamp(pos, 0, 1):P0}");
                break;
            }

            case ActionType.XInputAxis:
            {
                double v = a.Axis is "LT" or "RT"
                    ? (a.Range == AxisRange.Bipolar ? (cur + 1) / 2 : cur)
                    : (a.Range == AxisRange.Unipolar ? cur * 2 - 1 : cur);
                _router.XboxAxis(a.Axis, v);
                _router.Report(_source, b.Id, $"{a} {v:0.00}");
                break;
            }

            case ActionType.Macro:
                if (rising) RunMacro(b, a, st);
                break;

            case ActionType.BeamNG:
                switch (BeamNgCommands.Classify(a.Command, _commands.KnownCommands))
                {
                    case CommandStyle.Edge:
                        if (rising) SendCommand(b, a.Command, 1);
                        break;
                    default:
                        // A setter is idempotent, so the very first value is sent even when it is "off":
                        // that brings the game in line with a latch the phone restored.
                        if (b.Kind == ControlKind.Slider ? (cur != prev || first) : (On(cur) != On(prev) || first))
                            SendCommand(b, a.Command, b.Kind == ControlKind.Slider ? cur : (On(cur) ? 1 : 0));
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
                _router.Report(_source, b.Id, $"{a} {pos:P0}");
                break;
            }
            case ActionType.XInputAxis:
            {
                double pos = count <= 1 ? 0 : (double)index / (count - 1);
                double v = a.Axis is "LT" or "RT" ? pos : pos * 2 - 1;
                _router.XboxAxis(a.Axis, v);
                _router.Report(_source, b.Id, $"{a} {v:0.00}");
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
                _router.Report(_source, b.Id, $"{a} pulse");
                break;
            case ActionType.XInputButton:
                _ = _router.Pulse(Holdable.Xbox(a.XButton));
                _router.Report(_source, b.Id, $"{a} pulse");
                break;
            case ActionType.Macro: RunMacro(b, a, st); break;
            case ActionType.BeamNG:
                var style = BeamNgCommands.Classify(a.Command, _commands.KnownCommands);
                if (style == CommandStyle.Indexed && index is { } i) SendCommand(b, a.Command, i);
                else if (BeamNgCommands.IsHeld(a.Command))
                {
                    // A held command (horn, action.*) fired from a switch or rotary is a short press.
                    SendCommand(b, a.Command, 1);
                    _ = Task.Delay(InputRouter.PulseMs).ContinueWith(_ => SendCommand(b, a.Command, 0), TaskScheduler.Default);
                }
                else SendCommand(b, a.Command, 1);
                break;
        }
    }

    void Tap(Binding b, ActionSpec a)
    {
        _ = _router.TapKeys(a.Keys);
        _router.Report(_source, b.Id, $"{KeyNames(a)} tap");
    }

    void SendCommand(Binding b, string command, double value)
    {
        if (!_commands.Connected) { _router.Report(_source, b.Id, $"BeamNG {command} skipped (mod not connected)"); return; }
        _commands.SendCommand(command, value);
        _router.Report(_source, b.Id, $"BeamNG {command} = {value:0.##}");
    }

    void RunMacro(Binding b, ActionSpec a, ControlState st)
    {
        if (st.MacroRunning) return;
        st.MacroRunning = true;
        _router.Report(_source, b.Id, $"macro start ({a.Steps.Count} steps)");
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
            finally
            {
                lock (_gate) st.MacroRunning = false;
            }
        });
    }

    static string KeyNames(ActionSpec a) => string.Join("+", a.Keys.Select(k => k.Name));
}
