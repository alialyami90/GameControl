using Avalonia;

namespace GameController.Companion.App;

static class Program
{
    const string ShowEventName = @"Local\GameControllerCompanion.Show";

    [STAThread]
    public static int Main(string[] args)
    {
        // One companion per PC: a second copy could not bind the ports anyway. Starting it again
        // brings the running copy's window forward instead.
        using var single = new Mutex(true, @"Local\GameControllerCompanion", out bool first);
        if (!first && !args.Contains("--allow-second"))
        {
            if (OperatingSystem.IsWindows())
                try { using var show = EventWaitHandle.OpenExisting(ShowEventName); show.Set(); } catch { }
            return 0;
        }
        if (OperatingSystem.IsWindows())
            App.ShowSignal = new EventWaitHandle(false, EventResetMode.AutoReset, ShowEventName);

        // Whatever ends the process, held keys and buttons must not stay down in Windows.
        AppDomain.CurrentDomain.UnhandledException += (_, _) => App.EmergencyRelease();
        TaskScheduler.UnobservedTaskException += (_, e) => e.SetObserved();

        App.StartMinimized = args.Contains("--minimized");
        try
        {
            return BuildAvaloniaApp().StartWithClassicDesktopLifetime(args, Avalonia.Controls.ShutdownMode.OnExplicitShutdown);
        }
        finally
        {
            App.EmergencyRelease();
        }
    }

    public static AppBuilder BuildAvaloniaApp() =>
        AppBuilder.Configure<App>()
            .UsePlatformDetect()
            .LogToTrace();
}
