using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Markup.Xaml;
using Avalonia.Media.Imaging;
using Avalonia.Platform;
using GameController.Companion.App.Services;
using GameController.Companion.App.ViewModels;
using GameController.Companion.App.Views;
using GameController.Companion.Config;

namespace GameController.Companion.App;

public partial class App : Application
{
    public static bool StartMinimized { get; set; }
    /// <summary>Started with --allow-second while another copy runs: it keeps its own log file.</summary>
    public static bool SecondInstance { get; set; }
    /// <summary>Set by a second launch to bring this window forward (Windows only).</summary>
    public static EventWaitHandle? ShowSignal { get; set; }
    static App? _current;
    int _shutDown;
    WindowIcon? _iconLive, _iconIdle;

    CompanionHost? _host;
    static GameController.Companion.Logging.FileLog? _fileLog;
    IPlatform? _platform;
    MainViewModel? _vm;
    MainWindow? _window;
    TrayIcon? _tray;
    NativeMenuItem? _pinItem;
    bool _quitting;

    public override void Initialize() => AvaloniaXamlLoader.Load(this);

    public override void OnFrameworkInitializationCompleted()
    {
        if (ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop)
        {
            _current = this;
            // A bug in a view must not take the companion (and the input it holds) down with it.
            Avalonia.Threading.Dispatcher.UIThread.UnhandledException += (_, e) =>
            {
                _host?.Log("error", $"Unexpected error in the window: {e.Exception.Message}");
                WriteCrash("window", e.Exception);
                e.Handled = true;
            };

            _fileLog = new GameController.Companion.Logging.FileLog(GameController.Companion.Logging.FileLog.DefaultPath(SecondInstance));
            var store = new ConfigStore();
            _platform = PlatformFactory.Create(store.Config);
            _host = new CompanionHost(store, _platform.Router, _platform.Options);
            _host.Logged += e => _fileLog?.Write(e.At, e.Level, e.Message);
            _host.Log("info", $"GameCtl Companion {MainViewModel.Version} starting; settings in {store.Path}");
            _vm = new MainViewModel(_host, _platform);
            _host.Start();

            _window = new MainWindow { DataContext = _vm };
            _window.Closing += (_, e) =>
            {
                // Closing the window keeps the companion running in the tray; Quit (or Windows
                // shutting down or signing out) really exits.
                if (_quitting) return;
                if (e.CloseReason == WindowCloseReason.OSShutdown) { Shutdown(); return; }
                e.Cancel = true;
                _window.Hide();
            };
            _vm.ShowRequested += ShowWindow;
            _vm.QuitRequested += () => Quit(desktop);

            BuildTray();
            _host.Pairing.PinChanged += () => Avalonia.Threading.Dispatcher.UIThread.Post(UpdateTray);
            _host.SessionsChanged += () => Avalonia.Threading.Dispatcher.UIThread.Post(UpdateTray);
            _host.BluetoothChanged += () => Avalonia.Threading.Dispatcher.UIThread.Post(UpdateTray);
            UpdateTray();

            if (!(StartMinimized || store.Config.StartMinimized)) ShowWindow();
            _ = _vm.CheckFirewallAsync();
            desktop.Exit += (_, _) => Shutdown();
            desktop.ShutdownRequested += (_, _) => Shutdown();
            ListenForShowSignal();
        }
        base.OnFrameworkInitializationCompleted();
    }

    void ListenForShowSignal()
    {
        var signal = ShowSignal;
        if (signal == null) return;
        var t = new Thread(() =>
        {
            while (Volatile.Read(ref _shutDown) == 0)
            {
                try { if (signal.WaitOne(1000)) Avalonia.Threading.Dispatcher.UIThread.Post(ShowWindow); }
                catch (ObjectDisposedException) { return; }
            }
        }) { IsBackground = true, Name = "show-signal" };
        t.Start();
    }

    /// <summary>Full details of an unexpected exception, to the log file (the window only shows the message).</summary>
    public static void WriteCrash(string where, object? exception)
    {
        try { _fileLog?.Write(DateTimeOffset.Now, "error", $"Unhandled exception ({where}): {exception}"); } catch { }
    }

    /// <summary>Last-chance release from a crash handler or the end of Main. Safe to call twice.</summary>
    public static void EmergencyRelease()
    {
        var app = _current;
        if (app == null) return;
        try { app._host?.ReleaseEverything(); } catch { }
        app.Shutdown();
    }

    void ShowWindow()
    {
        if (_window == null) return;
        _window.Show();
        if (_window.WindowState == WindowState.Minimized) _window.WindowState = WindowState.Normal;
        _window.Activate();
    }

    void BuildTray()
    {
        var menu = new NativeMenu();
        var open = new NativeMenuItem("Open GameCtl Companion");
        open.Click += (_, _) => ShowWindow();
        _pinItem = new NativeMenuItem("Pairing PIN") { IsEnabled = false };
        var release = new NativeMenuItem("Release all held input");
        release.Click += (_, _) => _host?.ReleaseEverything();
        var quit = new NativeMenuItem("Quit GameCtl Companion");
        quit.Click += (_, _) => _vm?.QuitCommand.Execute(null);
        menu.Add(open);
        menu.Add(_pinItem);
        menu.Add(new NativeMenuItemSeparator());
        menu.Add(release);
        menu.Add(quit);

        _tray = new TrayIcon { Menu = menu, IsVisible = true };
        _tray.Clicked += (_, _) => ShowWindow();
        TrayIcon.SetIcons(this, new TrayIcons { _tray });
    }

    void UpdateTray()
    {
        if (_tray == null || _host == null) return;
        var phones = _host.Phones();
        int n = phones.Count, btOnly = phones.Count(p => p.Wifi == null);
        var pin = _host.Pairing.Pin;
        var name = $"GameCtl Companion {MainViewModel.Version}";
        var tip = n == 0 ? $"{name}: waiting for a phone (PIN {pin})"
            : btOnly == n ? $"{name}: {n} phone{(n == 1 ? "" : "s")} on Bluetooth only (Wi-Fi not linked, PIN {pin})"
            : $"{name}: {n} phone{(n == 1 ? "" : "s")} connected" + (btOnly > 0 ? $", {btOnly} on Bluetooth only" : "");
        _tray.ToolTipText = tip.Length <= 127 ? tip : tip[..127]; // Windows cuts tray tips at 127 characters
        if (_pinItem != null) _pinItem.Header = $"Pairing PIN  {pin[..3]} {pin[3..]}";
        _iconLive ??= LoadIcon("tray.png");
        _iconIdle ??= LoadIcon("tray-idle.png");
        var icon = n > 0 ? _iconLive : _iconIdle;
        if (!ReferenceEquals(_tray.Icon, icon)) _tray.Icon = icon;
    }

    static WindowIcon LoadIcon(string asset)
    {
        using var s = AssetLoader.Open(new Uri($"avares://GameCtlCompanion/Assets/{asset}"));
        return new WindowIcon(new Bitmap(s));
    }

    void Quit(IClassicDesktopStyleApplicationLifetime desktop)
    {
        _quitting = true;
        _window?.Close();
        desktop.Shutdown();
    }

    void Shutdown()
    {
        if (Interlocked.Exchange(ref _shutDown, 1) == 1) return;
        // Each step on its own: a failure in one must never skip releasing input.
        try { if (_tray != null) _tray.IsVisible = false; } catch { }
        try { _vm?.Dispose(); } catch { }
        try { _host?.Dispose(); } // releases every held key and button
        catch { try { _platform?.Router.ReleaseAll(); } catch { } }
        try { _platform?.Dispose(); } catch { }
        try { ShowSignal?.Dispose(); } catch { }
        try { _host?.Log("info", "GameCtl Companion stopped"); _fileLog?.Dispose(); } catch { }
    }
}
