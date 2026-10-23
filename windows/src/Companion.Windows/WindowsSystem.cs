using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Runtime.Versioning;
using System.Security.Principal;
using Microsoft.Win32;

namespace GameController.Companion.Windows;

/// <summary>Windows integration: firewall rule, start with Windows, and elevation checks.</summary>
[SupportedOSPlatform("windows")]
public static class WindowsSystem
{
    public const string FirewallRuleName = "GameCtl Companion";
    const string LegacyFirewallRuleName = "Game Controller Companion"; // beta builds
    const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
    const string RunValue = "GameCtlCompanion";
    const string LegacyRunValue = "GameControllerCompanion"; // beta builds (GameControllerCompanion.exe)

    public static string ExePath => Environment.ProcessPath ?? Process.GetCurrentProcess().MainModule?.FileName ?? "";

    public static bool IsElevated()
    {
        using var id = WindowsIdentity.GetCurrent();
        return new WindowsPrincipal(id).IsInRole(WindowsBuiltInRole.Administrator);
    }

    /// <summary>
    /// True when our rule exists and allows this exe. A rule left behind by a copy in another folder
    /// does not count. The path is matched as text, so it works whatever language netsh prints in.
    /// </summary>
    public static bool FirewallRuleExists()
    {
        try
        {
            var p = Process.Start(new ProcessStartInfo("netsh", $"advfirewall firewall show rule name=\"{FirewallRuleName}\" verbose")
            { CreateNoWindow = true, UseShellExecute = false, RedirectStandardOutput = true });
            if (p == null) return false;
            var output = p.StandardOutput.ReadToEnd();
            p.WaitForExit(5000);
            return p.ExitCode == 0 && output.Contains(ExePath, StringComparison.OrdinalIgnoreCase);
        }
        catch (Exception) { return false; }
    }

    /// <summary>Adds an inbound allow rule for this exe on private networks. Shows a UAC prompt.</summary>
    public static bool AddFirewallRule()
    {
        // Replace any rule of ours that points at an old location, then add one for this exe (one UAC prompt).
        var args = $"/c netsh advfirewall firewall delete rule name=\"{LegacyFirewallRuleName}\" >nul 2>&1 & " +
                   $"netsh advfirewall firewall delete rule name=\"{FirewallRuleName}\" >nul 2>&1 & " +
                   $"netsh advfirewall firewall add rule name=\"{FirewallRuleName}\" dir=in action=allow program=\"{ExePath}\" enable=yes profile=private,domain";
        try
        {
            var p = Process.Start(new ProcessStartInfo("cmd.exe", args) { Verb = "runas", UseShellExecute = true, WindowStyle = ProcessWindowStyle.Hidden });
            p?.WaitForExit(15000);
            return p?.ExitCode == 0;
        }
        catch (System.ComponentModel.Win32Exception) { return false; } // the user said no to UAC
    }

    public static bool StartsWithWindows()
    {
        using var k = Registry.CurrentUser.OpenSubKey(RunKey);
        return k?.GetValue(RunValue) is string s && s.Contains(ExePath, StringComparison.OrdinalIgnoreCase);
    }

    public static void SetStartWithWindows(bool on)
    {
        using var k = Registry.CurrentUser.CreateSubKey(RunKey);
        if (on) k.SetValue(RunValue, $"\"{ExePath}\" --minimized");
        else k.DeleteValue(RunValue, throwOnMissingValue: false);
        k.DeleteValue(LegacyRunValue, throwOnMissingValue: false);
    }

    /// <summary>
    /// At start-up: a beta's Run entry points at GameControllerCompanion.exe, and a moved exe leaves a
    /// stale path. If the user asked to start with Windows (saved setting or a beta entry), point the
    /// entry at this exe; drop the beta entry either way.
    /// </summary>
    public static void RepairStartWithWindows(bool wanted)
    {
        using var k = Registry.CurrentUser.CreateSubKey(RunKey);
        bool legacy = k.GetValue(LegacyRunValue) != null;
        bool current = k.GetValue(RunValue) != null;
        if (wanted || legacy || current) SetStartWithWindows(true);
        else k.DeleteValue(LegacyRunValue, throwOnMissingValue: false);
    }

    [DllImport("user32.dll")] static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint pid);
    [DllImport("kernel32.dll", SetLastError = true)] static extern IntPtr OpenProcess(uint access, bool inherit, uint pid);
    [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr h);
    [DllImport("advapi32.dll", SetLastError = true)] static extern bool OpenProcessToken(IntPtr process, uint access, out IntPtr token);
    [DllImport("advapi32.dll", SetLastError = true)]
    static extern bool GetTokenInformation(IntPtr token, int cls, out int info, int len, out int retLen);

    /// <summary>
    /// True when the foreground app runs elevated and we do not. Windows (UIPI) silently drops
    /// SendInput aimed at it, so the UI warns the user to run the companion as administrator.
    /// </summary>
    public static bool ForegroundIsElevatedAboveUs(out string processName)
    {
        processName = "";
        if (IsElevated()) return false;
        var hwnd = GetForegroundWindow();
        if (hwnd == IntPtr.Zero) return false;
        GetWindowThreadProcessId(hwnd, out var pid);
        if (pid == 0 || pid == Environment.ProcessId) return false;
        try { processName = Process.GetProcessById((int)pid).ProcessName; } catch { }
        const uint PROCESS_QUERY_LIMITED_INFORMATION = 0x1000, TOKEN_QUERY = 0x0008;
        var h = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid);
        if (h == IntPtr.Zero) return false;
        try
        {
            if (!OpenProcessToken(h, TOKEN_QUERY, out var tok)) return Marshal.GetLastWin32Error() == 5; // access denied => elevated
            try
            {
                const int TokenElevation = 20;
                return GetTokenInformation(tok, TokenElevation, out int elevated, 4, out _) && elevated != 0;
            }
            finally { CloseHandle(tok); }
        }
        finally { CloseHandle(h); }
    }
}
