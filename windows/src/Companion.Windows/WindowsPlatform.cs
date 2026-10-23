using System.Runtime.Versioning;
using GameController.Companion.Config;
using GameController.Companion.Input;
using GameController.Companion.Protocol;

namespace GameController.Companion.Windows;

/// <summary>Builds the real Windows outputs and host options in one place for the tray app and console host.</summary>
[SupportedOSPlatform("windows")]
public static class WindowsPlatform
{
    public static (InputRouter Router, SendInputKeyboard Keyboard, VJoyOutput VJoy, ViGEmOutput Vigem) CreateRouter(CompanionConfig config)
    {
        var kb = new SendInputKeyboard();
        var vjoy = new VJoyOutput();
        var vigem = new ViGEmOutput(config.EnableVigem);
        return (new InputRouter(kb, vjoy, vigem), kb, vjoy, vigem);
    }

    public static HostOptions Options() => new()
    {
        Identity = new HidDeviceIdentity(),
        BluetoothPads = new WindowsBluetoothPads(),
        PlatformMdns = c => new WindowsMdns(c.ServerName, ProtocolInfo.MdnsService, (ushort)c.WsPort,
            new Dictionary<string, string> { ["v"] = ProtocolInfo.Version.ToString(), ["name"] = c.ServerName, ["udp"] = c.UdpPort.ToString() }),
    };
}
