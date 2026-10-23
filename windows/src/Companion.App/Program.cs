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
        App.SecondInstance = !first;
        if (OperatingSystem.IsWindows() && first)
            App.ShowSignal = new EventWaitHandle(false, EventResetMode.AutoReset, ShowEventName);

        // Whatever ends the process, held keys and buttons must not stay down in Windows.
        AppDomain.CurrentDomain.UnhandledException += (_, e) => { App.WriteCrash("process", e.ExceptionObject); App.EmergencyRelease(); };
        // A fire-and-forget task that faulted: log it, never let it end the process.
        TaskScheduler.UnobservedTaskException += (_, e) => { App.WriteCrash("background task", e.Exception); e.SetObserved(); };

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
