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

    CompanionHost? _host;
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
            var store = new ConfigStore();
            _platform = PlatformFactory.Create(store.Config);
            _host = new CompanionHost(store, _platform.Router, _platform.Options);
            _vm = new MainViewModel(_host, _platform);
            _host.Start();

            _window = new MainWindow { DataContext = _vm };
            _window.Closing += (_, e) =>
            {
                // Closing the window keeps the companion running in the tray; Quit really exits.
                if (_quitting) return;
                e.Cancel = true;
                _window.Hide();
            };
            _vm.ShowRequested += ShowWindow;
            _vm.QuitRequested += () => Quit(desktop);

            BuildTray();
            _host.Pairing.PinChanged += () => Avalonia.Threading.Dispatcher.UIThread.Post(UpdateTray);
            _host.SessionsChanged += () => Avalonia.Threading.Dispatcher.UIThread.Post(UpdateTray);
            UpdateTray();

            if (!(StartMinimized || store.Config.StartMinimized)) ShowWindow();
            _ = Task.Run(() => Avalonia.Threading.Dispatcher.UIThread.Post(_vm.CheckFirewall));
            desktop.Exit += (_, _) => Shutdown();
        }
        base.OnFrameworkInitializationCompleted();
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
        var open = new NativeMenuItem("Open Game Controller");
        open.Click += (_, _) => ShowWindow();
        _pinItem = new NativeMenuItem("Pairing PIN") { IsEnabled = false };
        var release = new NativeMenuItem("Release all held input");
        release.Click += (_, _) => _host?.ReleaseEverything();
        var quit = new NativeMenuItem("Quit");
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
        int n = _host.Sessions.Count;
        var pin = _host.Pairing.Pin;
        _tray.ToolTipText = n == 0 ? $"Game Controller: waiting for a phone (PIN {pin})" : $"Game Controller: {n} phone{(n == 1 ? "" : "s")} connected";
        if (_pinItem != null) _pinItem.Header = $"Pairing PIN  {pin[..3]} {pin[3..]}";
        var asset = n > 0 ? "tray.png" : "tray-idle.png";
        using var s = AssetLoader.Open(new Uri($"avares://GameControllerCompanion/Assets/{asset}"));
        _tray.Icon = new WindowIcon(new Bitmap(s));
    }

    void Quit(IClassicDesktopStyleApplicationLifetime desktop)
    {
        _quitting = true;
        _window?.Close();
        desktop.Shutdown();
    }

    void Shutdown()
    {
        if (_tray != null) _tray.IsVisible = false;
        _vm?.Dispose();
        _host?.Dispose(); // releases every held key and button
        _platform?.Dispose();
    }
}
