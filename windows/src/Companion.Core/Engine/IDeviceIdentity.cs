namespace GameController.Companion.Engine;

/// <summary>Looks up the USB/HID VID+PID Windows reports for a virtual device (for BeamNG input maps).</summary>
public interface IDeviceIdentity
{
    /// <param name="device">"vjoy1" or "hid" (the phone's "GameCtl Pad" Bluetooth HID device).</param>
    /// <returns>Lowercase PID+VID hex such as "bead1234", or null if not found.</returns>
    string? VidPid(string device);
}

public sealed class DefaultDeviceIdentity : IDeviceIdentity
{
    /// <summary>vJoy always enumerates as VID 0x1234, PID 0xBEAD.</summary>
    public string? VidPid(string device) => device.StartsWith("vjoy", StringComparison.OrdinalIgnoreCase) ? "bead1234" : null;
}
