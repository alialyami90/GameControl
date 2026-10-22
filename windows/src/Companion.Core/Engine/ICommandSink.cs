namespace GameController.Companion.Engine;

/// <summary>Where game-native commands go (today: the BeamNG mod link).</summary>
public interface ICommandSink
{
    bool Connected { get; }
    IReadOnlyCollection<string> KnownCommands { get; }
    void SendCommand(string command, double value);
}

public sealed class NullCommandSink : ICommandSink
{
    public bool Connected => false;
    public IReadOnlyCollection<string> KnownCommands => Array.Empty<string>();
    public void SendCommand(string command, double value) { }
}
