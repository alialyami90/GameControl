using System.Runtime.Versioning;
using GameController.Companion.Input;
using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.Xbox360;

namespace GameController.Companion.Windows;

/// <summary>
/// Optional virtual Xbox 360 pad through ViGEmBus. ViGEmBus is archived upstream but its last
/// release still works on Windows 10/11; without it the companion just reports <c>vigem:false</c>.
/// The pad is plugged in on first use so games do not see a phantom controller.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class ViGEmOutput : IXInputOutput, IDisposable
{
    readonly object _gate = new();
    readonly bool _enabled;
    ViGEmClient? _client;
    IXbox360Controller? _pad;
    bool _connected;

    public bool Available { get; private set; }
    public string Status { get; private set; } = "not checked";

    public ViGEmOutput(bool enabled)
    {
        _enabled = enabled;
        Refresh();
    }

    public bool Refresh()
    {
        lock (_gate)
        {
            bool before = Available;
            if (!_enabled) { Available = false; Status = "turned off in settings"; return before; }
            if (_client != null) return false;
            try
            {
                _client = new ViGEmClient();
                Available = true;
                Status = "ViGEmBus ready";
            }
            catch (Exception e) when (e.GetType().Name.StartsWith("VigemBus", StringComparison.Ordinal))
            {
                Available = false;
                Status = e.GetType().Name == "VigemBusNotFoundException" ? "ViGEmBus not installed" : $"ViGEmBus unavailable ({e.GetType().Name})";
            }
            catch (Exception e)
            {
                Available = false;
                Status = $"ViGEmBus unavailable ({e.Message})";
            }
            return before != Available;
        }
    }

    public bool InUse { get { lock (_gate) return _connected; } }

    IXbox360Controller? Pad()
    {
        if (_client == null) return null;
        try
        {
            if (_pad == null) _pad = _client.CreateXbox360Controller();
            if (!_connected) { _pad.Connect(); _connected = true; }
            return _pad;
        }
        catch (Exception e)
        {
            // The bus can vanish (driver removed, too many pads); report it and try again on the next use.
            Status = "could not plug in the virtual pad: " + e.Message;
            return null;
        }
    }

    static Xbox360Button? Button(string name) => name.ToUpperInvariant() switch
    {
        "A" => Xbox360Button.A, "B" => Xbox360Button.B, "X" => Xbox360Button.X, "Y" => Xbox360Button.Y,
        "LB" => Xbox360Button.LeftShoulder, "RB" => Xbox360Button.RightShoulder,
        "BACK" => Xbox360Button.Back, "START" => Xbox360Button.Start, "GUIDE" => Xbox360Button.Guide,
        "LS" => Xbox360Button.LeftThumb, "RS" => Xbox360Button.RightThumb,
        "DPAD_UP" => Xbox360Button.Up, "DPAD_DOWN" => Xbox360Button.Down,
        "DPAD_LEFT" => Xbox360Button.Left, "DPAD_RIGHT" => Xbox360Button.Right,
        _ => null,
    };

    public void SetButton(string button, bool down)
    {
        lock (_gate)
        {
            if (Button(button) is not { } b || Pad() is not { } pad) return;
            pad.SetButtonState(b, down);
        }
    }

    public void SetAxis(string axis, double value)
    {
        lock (_gate)
        {
            if (Pad() is not { } pad) return;
            switch (axis.ToUpperInvariant())
            {
                case "LT": pad.SetSliderValue(Xbox360Slider.LeftTrigger, (byte)Math.Round(Math.Clamp(value, 0, 1) * 255)); break;
                case "RT": pad.SetSliderValue(Xbox360Slider.RightTrigger, (byte)Math.Round(Math.Clamp(value, 0, 1) * 255)); break;
                case "LX": pad.SetAxisValue(Xbox360Axis.LeftThumbX, Stick(value)); break;
                case "LY": pad.SetAxisValue(Xbox360Axis.LeftThumbY, Stick(value)); break;
                case "RX": pad.SetAxisValue(Xbox360Axis.RightThumbX, Stick(value)); break;
                case "RY": pad.SetAxisValue(Xbox360Axis.RightThumbY, Stick(value)); break;
            }
        }
    }

    static short Stick(double v) => (short)Math.Round(Math.Clamp(v, -1, 1) * 32767);

    public void Dispose()
    {
        lock (_gate)
        {
            try { if (_connected) _pad?.Disconnect(); } catch { }
            _client?.Dispose();
            _client = null; _pad = null; _connected = false;
        }
    }
}
