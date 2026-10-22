using GameController.Companion.Protocol;

namespace GameController.Companion.Input;

/// <summary>Something that can be held down: a key, a vJoy button or an Xbox button.</summary>
public readonly record struct Holdable(ActionType Kind, int Device, int Code, string Name)
{
    public static Holdable Key(ScanKey k) => new(ActionType.Key, 0, k.Scan | (k.Extended ? 0x100 : 0), k.Name);
    public static Holdable VJoy(int device, int button) => new(ActionType.VJoyButton, device, button, $"vJoy{device}:{button}");
    public static Holdable Xbox(string button) => new(ActionType.XInputButton, 0, button.GetHashCode(StringComparison.OrdinalIgnoreCase), button.ToUpperInvariant());
    public override string ToString() => Name;
}

/// <summary>A user-visible note about input that was just injected (for the live monitor in the UI).</summary>
/// <param name="SourceId">The phone's device id.</param>
/// <param name="Target">Which output it went to; <see cref="ActionType.None"/> for releases and notes.</param>
public readonly record struct InputActivity(DateTimeOffset At, string SourceId, string Source, string ControlId, string What, ActionType Target);

/// <summary>
/// The single place where input reaches Windows. Holds are reference-counted, so two
/// controls (or two phones) holding LCTRL release it only when both let go.
/// Thread-safe; all output calls are serialized under one lock to keep key order intact.
/// </summary>
public sealed class InputRouter
{
    public const int TapMs = 40;
    public const int PulseMs = 80;

    readonly object _gate = new();
    readonly Dictionary<Holdable, int> _refs = new();
    readonly Dictionary<Holdable, ScanKey> _keys = new();

    public IKeyboardOutput Keyboard { get; }
    public IVJoyOutput VJoy { get; }
    public IXInputOutput XInput { get; }

    public event Action<InputActivity>? Activity;

    public InputRouter(IKeyboardOutput keyboard, IVJoyOutput vjoy, IXInputOutput xinput)
    {
        Keyboard = keyboard; VJoy = vjoy; XInput = xinput;
    }

    public void Report(string sourceId, string source, string controlId, string what, ActionType target = ActionType.None) =>
        Activity?.Invoke(new InputActivity(DateTimeOffset.Now, sourceId, source, controlId, what, target));

    public void Down(Holdable h, ScanKey? key = null)
    {
        lock (_gate)
        {
            _refs.TryGetValue(h, out var n);
            _refs[h] = n + 1;
            if (key is { } k) _keys[h] = k;
            if (n == 0) Emit(h, true);
        }
    }

    public void Up(Holdable h)
    {
        lock (_gate)
        {
            if (!_refs.TryGetValue(h, out var n)) return;
            if (n <= 1) { _refs.Remove(h); Emit(h, false); _keys.Remove(h); }
            else _refs[h] = n - 1;
        }
    }

    public void KeysDown(IReadOnlyList<ScanKey> keys)
    {
        foreach (var k in keys) Down(Holdable.Key(k), k);
    }

    /// <summary>Releases in reverse order so modifiers come up last.</summary>
    public void KeysUp(IReadOnlyList<ScanKey> keys)
    {
        for (int i = keys.Count - 1; i >= 0; i--) Up(Holdable.Key(keys[i]));
    }

    /// <summary>Gap between two taps of the same key, so the game sees two separate presses.</summary>
    public const int TapGapMs = 20;

    readonly Dictionary<Holdable, Task> _busy = new();

    /// <summary>
    /// Presses and releases keys. A tap of a key that is still mid-tap waits for the earlier one to
    /// finish, so two quick toggles (or a rotary turned two detents) reach the game as two presses.
    /// </summary>
    public Task TapKeys(IReadOnlyList<ScanKey> keys, int holdMs = TapMs) =>
        Chain(keys.Select(Holdable.Key).ToList(), async () =>
        {
            KeysDown(keys);
            try { await Task.Delay(holdMs).ConfigureAwait(false); }
            finally { KeysUp(keys); }
        });

    public Task Pulse(Holdable h, int ms = PulseMs) =>
        Chain(new[] { h }, async () =>
        {
            Down(h);
            try { await Task.Delay(ms).ConfigureAwait(false); }
            finally { Up(h); }
        });

    Task Chain(IReadOnlyList<Holdable> on, Func<Task> run)
    {
        lock (_gate)
        {
            var prior = on.Select(h => _busy.TryGetValue(h, out var t) ? t : Task.CompletedTask).Where(t => !t.IsCompleted).ToArray();
            var task = Run(prior, run);
            foreach (var h in on) _busy[h] = task;
            return task;
        }
    }

    static async Task Run(Task[] prior, Func<Task> run)
    {
        if (prior.Length > 0)
        {
            try { await Task.WhenAll(prior).ConfigureAwait(false); } catch { }
            await Task.Delay(TapGapMs).ConfigureAwait(false);
        }
        await run().ConfigureAwait(false);
    }

    public void VJoyAxis(int device, string axis, double position)
    {
        // Like Emit: a driver that fails (device unplugged, vJoy reinstalled) drops the update, never the session.
        lock (_gate) try { VJoy.SetAxis(device, axis, Math.Clamp(position, 0, 1)); } catch (Exception) { }
    }

    public void XboxAxis(string axis, double value)
    {
        bool trigger = axis is "LT" or "RT";
        lock (_gate) try { XInput.SetAxis(axis, Math.Clamp(value, trigger ? 0 : -1, 1)); } catch (Exception) { }
    }

    /// <summary>Releases everything that is held (used on shutdown).</summary>
    public void ReleaseAll()
    {
        lock (_gate)
        {
            foreach (var h in _refs.Keys.ToList()) Emit(h, false);
            _refs.Clear(); _keys.Clear();
        }
    }

    public IReadOnlyCollection<Holdable> Held { get { lock (_gate) return _refs.Keys.ToList(); } }

    void Emit(Holdable h, bool down)
    {
        try
        {
            switch (h.Kind)
            {
                case ActionType.Key:
                    if (_keys.TryGetValue(h, out var k)) Keyboard.Send(k, down);
                    break;
                case ActionType.VJoyButton: VJoy.SetButton(h.Device, h.Code, down); break;
                case ActionType.XInputButton: XInput.SetButton(h.Name, down); break;
            }
        }
        catch (Exception)
        {
            // A missing driver must never take the network loop down; capabilities already tell the phone.
        }
    }
}
