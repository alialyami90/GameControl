using System.Text.Json;
using GameController.Companion.Input;

namespace GameController.Companion.Protocol;

public enum ControlKind { Momentary, Latching, Switch, Radio, Slider, Rotary }

public enum ActionType { None, Key, VJoyButton, VJoyAxis, Macro, BeamNG, XInputButton, XInputAxis }

public enum KeyTrigger { Hold, Tap, Press }

public enum AxisRange { Bipolar, Unipolar }

/// <summary>One macro step: either press <see cref="Keys"/> for <see cref="HoldMs"/>, or wait <see cref="DelayMs"/>.</summary>
public sealed record MacroStep(IReadOnlyList<ScanKey> Keys, int HoldMs, int DelayMs);

/// <summary>What a control does. Parsed leniently: anything unknown becomes <see cref="ActionType.None"/>.</summary>
public sealed class ActionSpec
{
    public static readonly ActionSpec None = new() { Type = ActionType.None };

    public ActionType Type { get; init; }
    public IReadOnlyList<ScanKey> Keys { get; init; } = Array.Empty<ScanKey>();
    public KeyTrigger Trigger { get; init; } = KeyTrigger.Hold;
    public int Device { get; init; } = 1;
    public int Button { get; init; }
    public string Axis { get; init; } = "";
    public AxisRange Range { get; init; } = AxisRange.Bipolar;
    public IReadOnlyList<MacroStep> Steps { get; init; } = Array.Empty<MacroStep>();
    public string Command { get; init; } = "";
    public string XButton { get; init; } = "";

    /// <summary>Every field that changes what the action does, for "did this binding change?" checks.</summary>
    public string Signature() =>
        $"{Type}|{string.Join("+", Keys.Select(k => k.Name))}|{Trigger}|{Device}|{Button}|{Axis}|{Range}|{Command}|{XButton}|" +
        string.Join(";", Steps.Select(s => $"{string.Join("+", s.Keys.Select(k => k.Name))}/{s.HoldMs}/{s.DelayMs}"));

    public override string ToString() => Type switch
    {
        ActionType.Key => $"key {string.Join("+", Keys.Select(k => k.Name))} ({Trigger.ToString().ToLowerInvariant()})",
        ActionType.VJoyButton => $"vJoy {Device} button {Button}",
        ActionType.VJoyAxis => $"vJoy {Device} axis {Axis}",
        ActionType.Macro => $"macro ({Steps.Count} steps)",
        ActionType.BeamNG => $"BeamNG {Command}",
        ActionType.XInputButton => $"Xbox {XButton}",
        ActionType.XInputAxis => $"Xbox {Axis}",
        _ => "none",
    };
}

public sealed class Binding
{
    public required string Id { get; init; }
    public ControlKind Kind { get; init; }
    public ActionSpec Action { get; init; } = ActionSpec.None;
    public IReadOnlyList<ActionSpec> Positions { get; init; } = Array.Empty<ActionSpec>();
    public ActionSpec Cw { get; init; } = ActionSpec.None;
    public ActionSpec Ccw { get; init; } = ActionSpec.None;

    /// <summary>True when the phone sends this control over Bluetooth HID itself ("route":"hid"); the companion must not inject it.</summary>
    public bool RoutedOverHid { get; init; }
}

public static class BindingParser
{
    public static readonly HashSet<string> VJoyAxes = new(StringComparer.OrdinalIgnoreCase)
        { "X", "Y", "Z", "RX", "RY", "RZ", "SL0", "SL1" };

    public static readonly HashSet<string> XInputButtons = new(StringComparer.OrdinalIgnoreCase)
        { "A", "B", "X", "Y", "LB", "RB", "BACK", "START", "LS", "RS", "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "GUIDE" };

    public static readonly HashSet<string> XInputAxes = new(StringComparer.OrdinalIgnoreCase)
        { "LX", "LY", "RX", "RY", "LT", "RT" };

    public static bool TryParseKind(string? s, out ControlKind kind)
    {
        switch (s)
        {
            case "momentary": kind = ControlKind.Momentary; return true;
            case "latching": kind = ControlKind.Latching; return true;
            case "switch": kind = ControlKind.Switch; return true;
            case "radio": kind = ControlKind.Radio; return true;
            case "slider": kind = ControlKind.Slider; return true;
            case "rotary": kind = ControlKind.Rotary; return true;
            default: kind = ControlKind.Momentary; return false;
        }
    }

    /// <summary>Parses one Binding. Returns null (with a warning) when it has no id or an unknown kind.</summary>
    public static Binding? ParseBinding(JsonElement e, ICollection<string>? warnings = null)
    {
        if (e.ValueKind != JsonValueKind.Object) return null;
        var id = e.Str("id");
        if (string.IsNullOrEmpty(id)) { warnings?.Add("binding without id skipped"); return null; }
        if (!TryParseKind(e.Str("kind"), out var kind)) { warnings?.Add($"{id}: unknown kind '{e.Str("kind")}' skipped"); return null; }

        var positions = new List<ActionSpec>();
        if (e.TryGetProperty("positions", out var pos) && pos.ValueKind == JsonValueKind.Array)
            foreach (var p in pos.EnumerateArray()) positions.Add(ParseAction(p, id, warnings));

        return new Binding
        {
            Id = id,
            Kind = kind,
            Action = e.TryGetProperty("action", out var a) ? ParseAction(a, id, warnings) : ActionSpec.None,
            Positions = positions,
            Cw = e.TryGetProperty("cw", out var cw) ? ParseAction(cw, id, warnings) : ActionSpec.None,
            Ccw = e.TryGetProperty("ccw", out var ccw) ? ParseAction(ccw, id, warnings) : ActionSpec.None,
            RoutedOverHid = e.Str("route") == "hid",
        };
    }

    public static ActionSpec ParseAction(JsonElement e, string controlId = "?", ICollection<string>? warnings = null)
    {
        if (e.ValueKind != JsonValueKind.Object) return ActionSpec.None;
        switch (e.Str("type"))
        {
            case "key":
            {
                var keys = ParseKeys(e, controlId, warnings);
                if (keys.Count == 0) return ActionSpec.None;
                var trigger = e.Str("trigger") switch { "tap" => KeyTrigger.Tap, "press" => KeyTrigger.Press, _ => KeyTrigger.Hold };
                return new ActionSpec { Type = ActionType.Key, Keys = keys, Trigger = trigger };
            }
            case "vjoy_button":
            {
                int dev = e.Int("device", 1), btn = e.Int("button", 0);
                if (dev is < 1 or > 16 || btn is < 1 or > 128)
                { warnings?.Add($"{controlId}: vJoy device {dev} button {btn} out of range"); return ActionSpec.None; }
                return new ActionSpec { Type = ActionType.VJoyButton, Device = dev, Button = btn };
            }
            case "vjoy_axis":
            {
                int dev = e.Int("device", 1);
                var axis = (e.Str("axis") ?? "").ToUpperInvariant();
                if (dev is < 1 or > 16 || !VJoyAxes.Contains(axis))
                { warnings?.Add($"{controlId}: vJoy device {dev} axis '{axis}' invalid"); return ActionSpec.None; }
                return new ActionSpec { Type = ActionType.VJoyAxis, Device = dev, Axis = axis, Range = ParseRange(e) };
            }
            case "macro":
            {
                var steps = new List<MacroStep>();
                if (e.TryGetProperty("steps", out var st) && st.ValueKind == JsonValueKind.Array)
                    foreach (var s in st.EnumerateArray())
                    {
                        if (s.ValueKind != JsonValueKind.Object) continue;
                        var keys = ParseKeys(s, controlId, warnings);
                        int delay = Math.Clamp(s.Int("delayMs", 0), 0, 10_000);
                        int hold = Math.Clamp(s.Int("holdMs", 40), 1, 10_000);
                        if (keys.Count > 0) steps.Add(new MacroStep(keys, hold, 0));
                        else if (delay > 0) steps.Add(new MacroStep(Array.Empty<ScanKey>(), 0, delay));
                    }
                return steps.Count == 0 ? ActionSpec.None : new ActionSpec { Type = ActionType.Macro, Steps = steps };
            }
            case "beamng":
            {
                var cmd = e.Str("command");
                return string.IsNullOrWhiteSpace(cmd) ? ActionSpec.None : new ActionSpec { Type = ActionType.BeamNG, Command = cmd.Trim() };
            }
            case "xinput_button":
            {
                var b = (e.Str("button") ?? "").ToUpperInvariant();
                if (!XInputButtons.Contains(b)) { warnings?.Add($"{controlId}: Xbox button '{b}' invalid"); return ActionSpec.None; }
                return new ActionSpec { Type = ActionType.XInputButton, XButton = b };
            }
            case "xinput_axis":
            {
                var ax = (e.Str("axis") ?? "").ToUpperInvariant();
                if (!XInputAxes.Contains(ax)) { warnings?.Add($"{controlId}: Xbox axis '{ax}' invalid"); return ActionSpec.None; }
                // Triggers are naturally 0..1, sticks -1..1, unless the action says otherwise.
                var range = e.Str("range") switch
                {
                    "unipolar" => AxisRange.Unipolar,
                    "bipolar" => AxisRange.Bipolar,
                    _ => ax is "LT" or "RT" ? AxisRange.Unipolar : AxisRange.Bipolar,
                };
                return new ActionSpec { Type = ActionType.XInputAxis, Axis = ax, Range = range };
            }
            default:
                return ActionSpec.None;
        }
    }

    static AxisRange ParseRange(JsonElement e) => e.Str("range") == "unipolar" ? AxisRange.Unipolar : AxisRange.Bipolar;

    static List<ScanKey> ParseKeys(JsonElement e, string controlId, ICollection<string>? warnings)
    {
        var list = new List<ScanKey>();
        if (!e.TryGetProperty("keys", out var keys) || keys.ValueKind != JsonValueKind.Array) return list;
        foreach (var k in keys.EnumerateArray())
        {
            var name = k.ValueKind == JsonValueKind.String ? k.GetString() : null;
            if (name != null && KeyTable.TryGet(name, out var sk)) { if (!list.Contains(sk)) list.Add(sk); }
            else warnings?.Add($"{controlId}: unknown key '{name}'");
        }
        return list;
    }
}
