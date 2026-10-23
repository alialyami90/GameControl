using System.Diagnostics;
using GameController.Companion.Config;
using GameController.Companion.Input;
using GameController.Companion.Windows;

namespace GameController.Companion.App.Services;

/// <summary>What the window needs from the OS. Windows gets the real thing; elsewhere a preview stand-in.</summary>
public interface IPlatform : IDisposable
{
    InputRouter Router { get; }
    HostOptions Options { get; }
    bool IsWindows { get; }
    string VJoyStatus { get; }
    string VigemStatus { get; }
    bool IsElevated { get; }
    bool? FirewallRuleExists();
    bool AddFirewallRule();
    bool StartsWithWindows { get; set; }
    /// <summary>Name of the foreground app if it runs elevated above us (input would be blocked), else null.</summary>
    string? BlockedForegroundApp();
    void OpenFolder(string path);
    void OpenLink(string url);
    /// <summary>True when Windows has "Show animations" turned off.</summary>
    bool ReduceMotion { get; }
}

public static class PlatformFactory
{
    public static IPlatform Create(CompanionConfig config) =>
        OperatingSystem.IsWindows() ? new WindowsAppPlatform(config) : new PreviewPlatform();
}

[System.Runtime.Versioning.SupportedOSPlatform("windows")]
sealed class WindowsAppPlatform : IPlatform
{
    readonly VJoyOutput _vjoy;
    readonly ViGEmOutput _vigem;

    public WindowsAppPlatform(CompanionConfig config)
    {
        var w = WindowsPlatform.CreateRouter(config);
        Router = w.Router;
        _vjoy = w.VJoy;
        _vigem = w.Vigem;
        Options = WindowsPlatform.Options();
        if (!App.SecondInstance) try { WindowsSystem.RepairStartWithWindows(config.StartWithWindows); } catch { } // a locked-down registry only loses the repair
    }

    public InputRouter Router { get; }
    public HostOptions Options { get; }
    public bool IsWindows => true;
    public string VJoyStatus => _vjoy.Status;
    public string VigemStatus => _vigem.Status;
    public bool IsElevated => WindowsSystem.IsElevated();
    public bool? FirewallRuleExists() => WindowsSystem.FirewallRuleExists();
    public bool AddFirewallRule() => WindowsSystem.AddFirewallRule();
    public bool StartsWithWindows { get => WindowsSystem.StartsWithWindows(); set => WindowsSystem.SetStartWithWindows(value); }
    public string? BlockedForegroundApp() => WindowsSystem.ForegroundIsElevatedAboveUs(out var name) ? name : null;
    public void OpenFolder(string path) => Process.Start(new ProcessStartInfo("explorer.exe", $"\"{path}\"") { UseShellExecute = true });
    public void OpenLink(string url) => Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
    public bool ReduceMotion => WindowsAnimations.Off();

    public void Dispose()
    {
        _vjoy.Dispose();
        _vigem.Dispose();
    }
}

[System.Runtime.Versioning.SupportedOSPlatform("windows")]
static class WindowsAnimations
{
    [System.Runtime.InteropServices.DllImport("user32.dll")]
    static extern bool SystemParametersInfo(uint action, uint param, out bool value, uint winIni);

    public static bool Off()
    {
        const uint SPI_GETCLIENTAREAANIMATION = 0x1042;
        return SystemParametersInfo(SPI_GETCLIENTAREAANIMATION, 0, out var on, 0) && !on;
    }
}

/// <summary>Non-Windows preview: input is recorded, not injected. Used for design review and development.</summary>
sealed class PreviewPlatform : IPlatform
{
    public PreviewPlatform()
    {
        var rec = new RecordingOutputs();
        Router = new InputRouter(rec, rec, new NullXInput());
    }

    public InputRouter Router { get; }
    public HostOptions Options { get; } = new();
    public bool IsWindows => false;
    public string VJoyStatus => "devices 1 (preview)";
    public string VigemStatus => "ViGEmBus not installed";
    public bool IsElevated => false;
    public bool? FirewallRuleExists() => null;
    public bool AddFirewallRule() => false;
    public bool StartsWithWindows { get; set; }
    public string? BlockedForegroundApp() => null;
    public void OpenFolder(string path) { }
    public void OpenLink(string url) { }
    public bool ReduceMotion => false;
    public void Dispose() { }
}
