namespace GameController.Companion.BeamNG;

/// <summary>How a BeamNG command reacts to control values (PROTOCOL.md "Edge rules").</summary>
public enum CommandStyle
{
    /// <summary>Flips or fires once: sent only on the rising edge, with value 1.</summary>
    Edge,
    /// <summary>Follows the control: sent with the value on every change (starter, horn, recover, setters).</summary>
    Level,
    /// <summary>Takes the selected switch position as its value (gearbox.mode).</summary>
    Indexed,
}

public static class BeamNgCommands
{
    static readonly HashSet<string> LevelCommands = new(StringComparer.Ordinal) { "starter", "horn", "recover" };

    /// <summary>Features that have both a <c>.toggle</c> and an idempotent setter.</summary>
    static readonly HashSet<string> Setters = new(StringComparer.Ordinal)
    {
        "ignition", "lights.low", "lights.high", "lights.fog", "lights.light_bar", "signal.left", "signal.right",
        "hazard", "parkingbrake", "esc", "tcs", "diff.front", "diff.center", "diff.rear", "awd", "range",
        "cruise", "slowmo", "pause",
    };

    /// <summary>Commands that mean "pressed" while 1 and must be released with 0.</summary>
    public static bool IsHeld(string command) =>
        LevelCommands.Contains(command) || command.StartsWith("action.", StringComparison.Ordinal);

    /// <param name="modCommands">The list the mod reported, used to recognise setters it added.</param>
    public static CommandStyle Classify(string command, IReadOnlyCollection<string>? modCommands = null)
    {
        if (command == "gearbox.mode") return CommandStyle.Indexed;
        if (command.EndsWith(".toggle", StringComparison.Ordinal)) return CommandStyle.Edge;
        if (LevelCommands.Contains(command) || Setters.Contains(command)) return CommandStyle.Level;
        // Raw BeamNG input actions ("action.look_back") are held like a key: press and release both go through.
        if (command.StartsWith("action.", StringComparison.Ordinal)) return CommandStyle.Level;
        if (modCommands != null && modCommands.Contains(command + ".toggle")) return CommandStyle.Level;
        return CommandStyle.Edge; // one-shots: reset, gear.up, camera.next, ...
    }
}
