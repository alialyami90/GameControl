using Avalonia;

namespace GameController.Companion.App;

static class Program
{
    [STAThread]
    public static int Main(string[] args)
    {
        // One companion per PC: a second copy could not bind the ports anyway.
        using var single = new Mutex(true, @"Local\GameControllerCompanion", out bool first);
        if (!first && !args.Contains("--allow-second")) return 0;

        App.StartMinimized = args.Contains("--minimized");
        return BuildAvaloniaApp().StartWithClassicDesktopLifetime(args, Avalonia.Controls.ShutdownMode.OnExplicitShutdown);
    }

    public static AppBuilder BuildAvaloniaApp() =>
        AppBuilder.Configure<App>()
            .UsePlatformDetect()
            .LogToTrace();
}
