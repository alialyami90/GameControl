using System.Text.Json;
using GameController.Companion.Engine;
using GameController.Companion.Input;
using GameController.Companion.Protocol;

namespace GameController.Companion.Tests;

sealed class FakeCommands : ICommandSink
{
    public bool Connected { get; set; } = true;
    public IReadOnlyCollection<string> KnownCommands { get; set; } = Array.Empty<string>();
    public List<(string Command, double Value)> Sent { get; } = new();
    public void SendCommand(string command, double value) { lock (Sent) Sent.Add((command, value)); }
}

sealed class Rig
{
    public RecordingOutputs Out { get; } = new();
    public FakeCommands Commands { get; } = new();
    public InputRouter Router { get; }
    public BindingEngine Engine { get; }
    long _seq;
    public long Now;

    public Rig(params string[] bindingsJson)
    {
        Router = new InputRouter(Out, Out, Out);
        Engine = new BindingEngine(Router, Commands, "test");
        Load(bindingsJson);
    }

    public void Load(params string[] bindingsJson) =>
        Engine.SetBindings("p", bindingsJson.Select(j => BindingParser.ParseBinding(JsonDocument.Parse(j).RootElement)!).ToList());

    public ApplyResult Send(string id, double val) => Engine.Apply(id, ++_seq, val, Now);
    public ApplyResult SendSeq(string id, long seq, double val) => Engine.Apply(id, seq, val, Now);

    /// <summary>Waits for taps and pulses (which release on a timer) to finish.</summary>
    public static void Settle(int ms = 400) => Thread.Sleep(ms);
}
