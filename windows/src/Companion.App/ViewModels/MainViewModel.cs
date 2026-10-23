using System.Collections.ObjectModel;
using Avalonia.Threading;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GameController.Companion.App.Controls;
using GameController.Companion.App.Services;
using GameController.Companion.Engine;
using GameController.Companion.Input;
using GameController.Companion.Net;
using GameController.Companion.Protocol;

namespace GameController.Companion.App.ViewModels;

public sealed partial class MainViewModel : ObservableObject, IDisposable
{
    const int MaxActivity = 60, MaxLog = 400;

    readonly IPlatform _platform;
    readonly DispatcherTimer _slow;
    DateTime _lastBeamNgEcho, _lastTelemetryEcho;
    readonly Dictionary<string, DateTime> _lastOutputUse = new();
    readonly List<Action> _unsubscribe = new();

    // Axis updates arrive at up to 60 Hz per slider: they are merged per control and shown at most 10 times a second.
    readonly object _axisGate = new();
    readonly Dictionary<(string, string), InputActivity> _pendingAxis = new();
    bool _axisFlushQueued;
    (string Source, string Control)? _topAxisRow;

    public CompanionHost Host { get; }

    /// <summary>Raised on the UI thread for the signal path: (phone id, output key).</summary>
    public event Action<string, string>? InputFired;
    /// <summary>Raised on the UI thread when the game reports state through an output.</summary>
    public event Action<string>? GameEchoed;
    public event Action? QuitRequested;
    public event Action? ShowRequested;

    public ObservableCollection<NavItem> Nav { get; } = new();
    public ObservableCollection<PhoneRow> Phones { get; } = new();
    public ObservableCollection<OutputRow> Outputs { get; } = new();
    public ObservableCollection<ActivityRow> Activity { get; } = new();
    public ObservableCollection<LogRow> Log { get; } = new();

    [ObservableProperty] private NavItem? _selectedNav;
    [ObservableProperty] private string _page = "status";

    partial void OnSelectedNavChanged(NavItem? value) { if (value != null) Page = value.Key; }
    [ObservableProperty] private string _pin = "";
    [ObservableProperty] private string _serverName = "";
    [ObservableProperty] private string _addresses = "";
    [ObservableProperty] private string _portsText = "";
    [ObservableProperty] private int _connectedCount;
    /// <summary>Phones linked over Wi-Fi (the companion's own link); <see cref="ConnectedCount"/> also counts Bluetooth.</summary>
    [ObservableProperty] private int _wifiCount;
    [ObservableProperty] private string? _bluetoothOnlyBanner;
    [ObservableProperty] private IReadOnlyList<SignalNode> _phoneNodes = Array.Empty<SignalNode>();
    [ObservableProperty] private IReadOnlyList<SignalNode> _outputNodes = Array.Empty<SignalNode>();
    [ObservableProperty] private string _hubDetail = "";
    [ObservableProperty] private string? _blockedBanner;
    [ObservableProperty] private string? _firewallBanner;
    [ObservableProperty] private string? _serviceBanner;
    [ObservableProperty] private bool _reduceMotion;
    [ObservableProperty] private string _railHealth = "";
    [ObservableProperty] private bool _railHealthy;

    // Settings
    [ObservableProperty] private string _editServerName = "";
    [ObservableProperty] private bool _startWithWindows;
    [ObservableProperty] private bool _startMinimized;
    [ObservableProperty] private bool _enableVigem;
    [ObservableProperty] private bool _enableOutGauge;
    [ObservableProperty] private bool _enableMdns;
    [ObservableProperty] private string _outGaugePortText = "";
    [ObservableProperty] private string? _settingsNote;

    public bool IsWindows => _platform.IsWindows;
    public string ConfigPath => Host.Store.Path;
    public string LogPath => Path.Combine(Path.GetDirectoryName(ConfigPath) ?? ".", App.SecondInstance ? "companion-second.log" : "companion.log");
    /// <summary>The product version from Directory.Build.props (InformationalVersion), as shown to people.</summary>
    public static string Version { get; } =
        typeof(MainViewModel).Assembly.GetCustomAttributes(typeof(System.Reflection.AssemblyInformationalVersionAttribute), false)
            .OfType<System.Reflection.AssemblyInformationalVersionAttribute>().FirstOrDefault()?.InformationalVersion.Split('+')[0]
        ?? typeof(MainViewModel).Assembly.GetName().Version?.ToString(3) ?? "1.0.0";
    public bool HasPhones => Phones.Count > 0;
    public bool HasConnected => ConnectedCount > 0;
    partial void OnConnectedCountChanged(int value) => OnPropertyChanged(nameof(HasConnected));
    public bool HasActivity => Activity.Count > 0;
    /// <summary>The PIN is what the Wi-Fi link needs, so it stays the thing to look at until one exists.</summary>
    public bool PinIsAttention => WifiCount == 0;
    public string PinHint => WifiCount == 0
        ? $"Open the phone app, tap Connect, pick {ServerName} and type this PIN."
        : "To add another phone, type this PIN on it. It changes after each pairing.";

    public MainViewModel(CompanionHost host, IPlatform platform)
    {
        Host = host;
        _platform = platform;
        ReduceMotion = platform.ReduceMotion;

        Nav.Add(new NavItem { Key = "status", Title = "Status", Icon = "status" });
        Nav.Add(new NavItem { Key = "phones", Title = "Phones", Icon = "phone" });
        Nav.Add(new NavItem { Key = "outputs", Title = "Outputs", Icon = "outputs" });
        Nav.Add(new NavItem { Key = "log", Title = "Log", Icon = "log" });
        Nav.Add(new NavItem { Key = "settings", Title = "Settings", Icon = "settings" });
        SelectedNav = Nav[0];

        Outputs.Add(new OutputRow { Key = "keyboard", Title = "Keyboard", Icon = "keyboard", Purpose = "Key presses as hardware scancodes, which DirectInput and raw-input games read." });
        Outputs.Add(new OutputRow { Key = "vjoy", Title = "vJoy joystick", Icon = "joystick", Purpose = "Up to 128 buttons and 8 axes on a virtual joystick, so controls do not use up keyboard keys." });
        Outputs.Add(new OutputRow { Key = "xbox", Title = "Xbox pad", Icon = "gamepad", Purpose = "A virtual Xbox 360 controller for games that only read XInput. Optional." });
        Outputs.Add(new OutputRow { Key = "beamng", Title = "BeamNG mod", Icon = "car", Purpose = "Game commands and live vehicle state, both ways, through the GameCtl for BeamNG mod." });
        Outputs.Add(new OutputRow { Key = "telemetry", Title = "Telemetry", Icon = "telemetry", Purpose = "OutGauge dashboard data (speed, gear, lamps) when the mod is not installed." });

        Action pin = () => Ui(RefreshPin), phones = () => Ui(RefreshPhones), outputs = () => Ui(RefreshOutputs);
        Action<LogEntry> logged = e => Ui(() => AddLog(e));
        Action<IReadOnlyDictionary<string, System.Text.Json.Nodes.JsonNode?>, string> game = (_, source) => Ui(() => Echo(source));
        Action<InputActivity> activity = OnActivity;
        Host.Pairing.PinChanged += pin; _unsubscribe.Add(() => Host.Pairing.PinChanged -= pin);
        Host.SessionsChanged += phones; _unsubscribe.Add(() => Host.SessionsChanged -= phones);
        Host.Pairing.DevicesChanged += phones; _unsubscribe.Add(() => Host.Pairing.DevicesChanged -= phones);
        Host.StatusChanged += outputs; _unsubscribe.Add(() => Host.StatusChanged -= outputs);
        Host.Logged += logged; _unsubscribe.Add(() => Host.Logged -= logged);
        Host.GameState += game; _unsubscribe.Add(() => Host.GameState -= game);
        Host.Router.Activity += activity; _unsubscribe.Add(() => Host.Router.Activity -= activity);
        Host.BluetoothChanged += phones; _unsubscribe.Add(() => Host.BluetoothChanged -= phones);

        LoadSettings();
        RefreshPin();
        RefreshPhones();
        RefreshOutputs();

        _slow = new DispatcherTimer(TimeSpan.FromSeconds(2), DispatcherPriority.Background, (_, _) => SlowTick());
        _slow.Start();
        SlowTick();
    }

    static void Ui(Action a)
    {
        if (Dispatcher.UIThread.CheckAccess()) a();
        else Dispatcher.UIThread.Post(a);
    }

    // ------------------------------------------------------------ refresh

    void RefreshPin()
    {
        var p = Host.Pairing.Pin;
        Pin = p.Length == 6 ? $"{p[..3]} {p[3..]}" : p;
        ServerName = Host.Config.ServerName;
        RefreshAddresses();
        PortsText = $"Phone link TCP {Host.Config.WsPort}  ·  input UDP {Host.Config.UdpPort}";
        OnPropertyChanged(nameof(PinHint));
    }

    /// <summary>Also run every 2 s: the PC may join Wi-Fi after the companion starts.</summary>
    void RefreshAddresses()
    {
        var addrs = MdnsAdvertiser.AdvertisedAddresses();
        Addresses = addrs.Count == 0 ? "No network connection" : string.Join("  ·  ", addrs.Select(a => a.ToString()));
        HubDetail = addrs.FirstOrDefault()?.ToString() ?? "offline";
    }

    void RefreshPhones()
    {
        var sessions = Host.Sessions.ToList();
        var pads = Host.BluetoothPads;
        var linked = Host.Phones();
        var known = Host.Config.Devices.ToList();

        // Which paired row a Bluetooth pad belongs to: the Wi-Fi phone it was merged with, else the
        // only paired phone. Anything left gets a row of its own.
        var padFor = new Dictionary<string, BluetoothPad>();
        var spare = new List<BluetoothPad>();
        foreach (var l in linked.Where(l => l.Bluetooth != null))
        {
            if (l.Wifi != null) padFor[l.Wifi.DeviceId] = l.Bluetooth!;
            else spare.Add(l.Bluetooth!);
        }
        if (spare.Count > 0 && padFor.Count == 0 && sessions.Count == 0 && known.Count == 1)
        {
            padFor[known[0].DeviceId] = spare[0];
            spare.RemoveAt(0);
        }

        var rows = new List<PhoneRow>();
        foreach (var d in known.OrderByDescending(d => d.LastSeen))
        {
            var s = sessions.FirstOrDefault(x => x.DeviceId == d.DeviceId);
            bool bt = padFor.ContainsKey(d.DeviceId);
            rows.Add(new PhoneRow
            {
                DeviceId = d.DeviceId,
                Name = d.DeviceName,
                Wifi = s != null,
                Bluetooth = bt,
                Address = s?.RemoteAddress.ToString() ?? (bt ? "Bluetooth controller" : ""),
                Profile = s?.ProfileId is { Length: > 0 } pid ? pid : (s != null ? "No profile loaded yet" : ""),
                Events = s != null ? Interlocked.Read(ref s.EventCount) : 0,
                PairedText = d.PairedAt.LocalDateTime.ToString("d MMM yyyy"),
                LastSeenText = s != null || bt ? "now" : Ago(d.LastSeen),
            });
        }
        foreach (var pad in spare)
            rows.Add(new PhoneRow
            {
                DeviceId = "bt:" + pad.Id,
                Name = pad.Name,
                Bluetooth = true,
                IsPaired = false,
                Address = "Bluetooth controller",
                Profile = "Not linked over Wi-Fi",
                LastSeenText = "now",
            });
        Phones.Clear();
        foreach (var r in rows) Phones.Add(r);

        WifiCount = sessions.Count;
        ConnectedCount = linked.Count;
        PhoneNodes = linked.Select(l =>
        {
            var links = new[] { new LinkTag("WI-FI", l.Wifi != null), new LinkTag("BT", l.Bluetooth != null) };
            if (l.Wifi is { } s)
                return new SignalNode(l.Key, s.DeviceName,
                    s.ProfileId is { Length: > 0 } p ? $"{s.Engine.BindingCount} controls · {p}" : s.RemoteAddress.ToString(),
                    "phone", NodeState.Live, links);
            return new SignalNode(l.Key, l.Name, "Bluetooth · Wi-Fi not linked", "bluetooth", NodeState.Ready, links);
        }).ToList();
        BluetoothOnlyBanner = linked.Any(l => l.Wifi == null)
            ? "A phone is connected over Bluetooth, so its controls reach Windows directly. Lamps, game state and BeamNG commands need the Wi-Fi link too: in the app tap Connect, pick this PC and type the PIN."
            : null;
        Nav[1].Badge = linked.Count > 0 ? linked.Count.ToString() : null;
        OnPropertyChanged(nameof(HasPhones));
        OnPropertyChanged(nameof(PinIsAttention));
        OnPropertyChanged(nameof(PinHint));
        RefreshRailHealth();
    }

    static string Ago(DateTimeOffset t)
    {
        var d = DateTimeOffset.Now - t;
        if (d.TotalMinutes < 1) return "just now";
        if (d.TotalHours < 1) return $"{(int)d.TotalMinutes} min ago";
        if (d.TotalDays < 1) return $"{(int)d.TotalHours} h ago";
        return t.LocalDateTime.ToString("d MMM");
    }

    static bool NotInstalled(string status) => status.Contains("not installed", StringComparison.OrdinalIgnoreCase);

    void RefreshOutputs()
    {
        var caps = Host.Capabilities();
        bool recently(string key) => _lastOutputUse.TryGetValue(key, out var t) && (DateTime.UtcNow - t).TotalSeconds < 5;
        NodeState ready(bool ok, string key) => !ok ? NodeState.Attention : recently(key) ? NodeState.Live : NodeState.Ready;

        var kb = Outputs[0];
        kb.State = ready(Host.Router.Keyboard.Available, "keyboard");
        kb.Status = Host.Router.Keyboard.Available ? "Scancode input" : "Not available";
        kb.Detail = Host.Router.Keyboard.Available ? "SendInput, works with any game window in focus" : "Keyboard input only works on Windows";
        kb.Setup = "";

        var vj = Outputs[1];
        bool vjoyOk = caps["vjoy"]?.GetValue<bool>() == true;
        // Not installed is a choice (keys work without it); installed but unusable needs the user.
        bool vjoyMissing = !vjoyOk && NotInstalled(_platform.VJoyStatus);
        vj.Optional = vjoyMissing;
        vj.State = vjoyMissing ? NodeState.Off : ready(vjoyOk, "vjoy");
        var devs = Host.Router.VJoy.Devices;
        vj.Status = vjoyOk ? (devs.Count == 1 ? $"Device {devs[0]}" : $"Devices {string.Join(", ", devs)}") : vjoyMissing ? "Not installed (optional)" : "Needs configuring";
        vj.Detail = vjoyOk ? "Buttons and axes ready" : vjoyMissing ? "Only for profiles that use joystick buttons or axes" : _platform.VJoyStatus;
        vj.Setup = vjoyOk ? "" : (vjoyMissing ? "Keys work without it. " : "") + "Install vJoy 2.2 (the BrunnerInnovation release for Windows 10 and 11), open Configure vJoy, enable device 1 with 32 or more buttons and all axes, then come back here. The companion picks it up within a few seconds.";
        vj.SetupLink = vjoyOk ? null : "https://github.com/BrunnerInnovation/vJoy/releases";

        var xb = Outputs[2];
        bool vigemOk = caps["vigem"]?.GetValue<bool>() == true;
        bool vigemMissing = Host.Config.EnableVigem && !vigemOk && NotInstalled(_platform.VigemStatus);
        xb.Optional = vigemMissing;
        xb.State = !Host.Config.EnableVigem || vigemMissing ? NodeState.Off : ready(vigemOk, "xbox");
        xb.Status = !Host.Config.EnableVigem ? "Turned off" : vigemMissing ? "Not installed (optional)" : !vigemOk ? "ViGEmBus unavailable" : Host.Router.XInput.InUse ? "Pad plugged in" : "Plugs in on first use";
        xb.Detail = !Host.Config.EnableVigem ? "Turn it on in Settings" : vigemOk ? "Virtual Xbox 360 controller" : vigemMissing ? "Only for games that read Xbox controllers only" : _platform.VigemStatus;
        xb.Setup = Host.Config.EnableVigem && !vigemOk ? "Only needed for games that ignore joysticks and read Xbox controllers only. Install the last ViGEmBus release (1.22), then restart the companion." : "";
        xb.SetupLink = Host.Config.EnableVigem && !vigemOk ? "https://github.com/nefarius/ViGEmBus/releases" : null;

        var bn = Outputs[3];
        bool modOk = Host.BeamNg?.Connected == true;
        bn.State = modOk ? NodeState.Live : NodeState.Off;
        bn.Status = modOk ? (Host.BeamNg?.ModVersion is { } v ? $"Mod {v} connected" : "Mod connected") : "Game not running";
        bn.Detail = modOk ? $"{Host.BeamNg?.KnownCommands.Count ?? 0} commands, live vehicle state" : "Waiting for BeamNG.drive";
        bn.Optional = !modOk;
        bn.Setup = modOk ? "" : "For BeamNG.drive only. Copy gamecontroller.zip into Documents\\BeamNG.drive\\current\\mods (or BeamNG's user folder \\mods), start the game and load a vehicle. It connects to this PC by itself.";

        var tm = Outputs[4];
        bool ogOn = Host.Config.EnableOutGauge, ogLive = Host.OutGaugeActive, ogFailed = ogOn && Host.OutGaugeFailed;
        int ogPort = Host.Config.OutGaugePort;
        const string modWins = "While the mod is connected, the mod's data is used instead.";
        tm.State = !ogOn ? NodeState.Off : ogFailed ? NodeState.Attention : ogLive ? NodeState.Live : NodeState.Ready;
        tm.Status = !ogOn ? "Turned off" : ogFailed ? $"Port {ogPort} is in use by another program (for example SimHub)"
            : ogLive ? "Receiving" : $"Listening on UDP {ogPort}";
        tm.Detail = Host.OutGaugeLastPacket is { } last
            ? $"Last packet: {(int)last.Ago.TotalSeconds}s ago, from {last.From}"
            : ogFailed ? "Close the other program or change the OutGauge port in Settings, then restart the companion" : "No packet yet";
        tm.Setup = !ogOn ? "" : modOk ? modWins : ogLive && !ogFailed ? ""
            : $"In BeamNG open Options > Other, tick OutGauge, IP 127.0.0.1, port {ogPort}, then drive a car (press Ctrl+R once if it was already loaded). It only sends while you're in a vehicle, not in menus. {modWins}";
        tm.SetupIsHint = true;

        OutputNodes = Outputs.Select(o => new SignalNode(o.Key, o.Title, o.Status, o.Icon, o.State)).ToList();

        var failed = Host.Services.Where(s => s.State == ServiceState.Failed).ToList();
        ServiceBanner = failed.Count > 0
            ? $"{string.Join(", ", failed.Select(f => f.Name))} could not start: {failed[0].Detail.Split(": ").Last()}. Another program may be using the port. Close it, or change the port in the settings file, then restart the companion."
            : Host.Store.WriteBlocked != null ? Host.Store.LoadNote : null;
        Nav[2].Badge = Outputs.Count(o => o.State == NodeState.Attention) is > 0 and var n ? n.ToString() : null;
        Nav[2].BadgeIsAttention = true;
        RefreshRailHealth();
    }

    void RefreshRailHealth()
    {
        bool servicesOk = Host.Services.All(s => s.State != ServiceState.Failed);
        RailHealthy = servicesOk;
        RailHealth = !servicesOk ? "A listener failed" : ConnectedCount switch
        {
            0 => "Waiting for a phone",
            1 => WifiCount == 0 ? "1 phone · Bluetooth only" : "1 phone connected",
            var n => WifiCount < n ? $"{n} phones · {n - WifiCount} Bluetooth only" : $"{n} phones connected",
        };
    }

    void SlowTick()
    {
        RefreshOutputs();
        RefreshAddresses();
        var devices = Host.Config.Devices.ToList();
        foreach (var row in Phones)
        {
            var s = Host.Sessions.FirstOrDefault(x => x.DeviceId == row.DeviceId);
            if (s != null) row.Events = Interlocked.Read(ref s.EventCount);
            else if (devices.FirstOrDefault(d => d.DeviceId == row.DeviceId) is { } d) row.LastSeenText = Ago(d.LastSeen);
        }

        var blocked = _platform.BlockedForegroundApp();
        BlockedBanner = blocked == null ? null :
            $"{blocked} is running as administrator, so Windows blocks the key presses the companion sends it. Close the companion and start it with Run as administrator.";
    }

    /// <summary>netsh can take seconds, so it runs off the UI thread.</summary>
    public async Task CheckFirewallAsync()
    {
        bool? exists;
        try { exists = await Task.Run(_platform.FirewallRuleExists); }
        catch (Exception) { exists = null; }
        FirewallBanner = exists == false
            ? "Windows Firewall may block phones from reaching this PC. Allow the companion on private networks once and you will not be asked again."
            : null;
    }

    // ------------------------------------------------------------ live feeds

    static string OutputKey(ActionType t) => t switch
    {
        ActionType.Key or ActionType.Macro => "keyboard",
        ActionType.VJoyButton or ActionType.VJoyAxis => "vjoy",
        ActionType.XInputButton or ActionType.XInputAxis => "xbox",
        ActionType.BeamNG => "beamng",
        _ => "",
    };

    void OnActivity(InputActivity a)
    {
        if (a.Target is not (ActionType.VJoyAxis or ActionType.XInputAxis)) { Ui(() => AddActivity(a)); return; }
        lock (_axisGate)
        {
            _pendingAxis[(a.SourceId, a.ControlId)] = a;
            if (_axisFlushQueued) return;
            _axisFlushQueued = true;
        }
        _ = Task.Delay(100).ContinueWith(_ => Ui(FlushAxes), TaskScheduler.Default);
    }

    void FlushAxes()
    {
        List<InputActivity> batch;
        lock (_axisGate)
        {
            batch = _pendingAxis.Values.OrderBy(a => a.At).ToList();
            _pendingAxis.Clear();
            _axisFlushQueued = false;
        }
        foreach (var a in batch) AddActivity(a);
    }

    void AddActivity(InputActivity a)
    {
        var key = OutputKey(a.Target);
        if (key != "")
        {
            bool wasLive = _lastOutputUse.TryGetValue(key, out var t) && (DateTime.UtcNow - t).TotalSeconds < 5;
            _lastOutputUse[key] = DateTime.UtcNow;
            InputFired?.Invoke(a.SourceId, key);
            if (!wasLive) RefreshOutputs();
        }
        var icon = key switch { "keyboard" => "keyboard", "vjoy" => "joystick", "xbox" => "gamepad", "beamng" => "car", _ => "link" };
        var row = new ActivityRow(a.At.ToString("HH:mm:ss.f"), a.Source, a.ControlId, a.What, icon);
        bool axis = a.Target is ActionType.VJoyAxis or ActionType.XInputAxis;
        // A moving slider updates its own row at the top instead of filling the list.
        if (axis && Activity.Count > 0 && _topAxisRow == (a.SourceId, a.ControlId)) { Activity[0] = row; return; }
        Activity.Insert(0, row);
        _topAxisRow = axis ? (a.SourceId, a.ControlId) : null;
        while (Activity.Count > MaxActivity) Activity.RemoveAt(Activity.Count - 1);
        if (Activity.Count == 1) OnPropertyChanged(nameof(HasActivity));
    }

    void Echo(string source)
    {
        // Game state can arrive 10-30 times a second; one spark every 700 ms is enough to read.
        var now = DateTime.UtcNow;
        if (source == "beamng")
        {
            if ((now - _lastBeamNgEcho).TotalMilliseconds < 700) return;
            _lastBeamNgEcho = now;
            GameEchoed?.Invoke("beamng");
        }
        else
        {
            if ((now - _lastTelemetryEcho).TotalMilliseconds < 700) return;
            _lastTelemetryEcho = now;
            GameEchoed?.Invoke("telemetry");
        }
    }

    void AddLog(LogEntry e)
    {
        Log.Insert(0, new LogRow(e.At.ToString("HH:mm:ss"), e.Level, e.Message));
        while (Log.Count > MaxLog) Log.RemoveAt(Log.Count - 1);
    }

    // ------------------------------------------------------------ commands

    [RelayCommand]
    void NewPin() => Host.Pairing.RotatePin();

    [RelayCommand]
    void ReleaseAll() => Host.ReleaseEverything();

    [RelayCommand]
    async Task Forget(PhoneRow? row)
    {
        if (row == null) return;
        try
        {
            // Off the UI thread: a locked settings file is retried for a moment before giving up.
            await Task.Run(() => Host.ForgetDevice(row.DeviceId));
            Host.Log("info", $"Forgot {row.Name}. It needs the PIN to connect again.");
        }
        catch (Exception e)
        {
            Host.Log("error", $"Could not forget {row.Name}: the settings file could not be saved ({e.Message}).");
        }
    }

    [RelayCommand]
    async Task AllowFirewall()
    {
        bool ok;
        try { ok = await Task.Run(_platform.AddFirewallRule); }
        catch (Exception e) { Host.Log("warn", $"Could not add the firewall rule: {e.Message}"); ok = false; }
        Host.Log(ok ? "info" : "warn", ok ? "Firewall rule added for private networks" : "The firewall rule was not added (the Windows prompt was declined or failed)");
        await CheckFirewallAsync();
    }

    [RelayCommand]
    void OpenConfigFolder() => Try("open the settings folder", () => _platform.OpenFolder(Path.GetDirectoryName(ConfigPath) ?? "."));

    [RelayCommand]
    void OpenLink(string? url) { if (!string.IsNullOrEmpty(url)) Try($"open {url}", () => _platform.OpenLink(url)); }

    void Try(string what, Action action)
    {
        try { action(); }
        catch (Exception e) { Host.Log("warn", $"Could not {what}: {e.Message}"); }
    }

    [RelayCommand]
    void Go(string key) => SelectedNav = Nav.FirstOrDefault(n => n.Key == key) ?? SelectedNav;

    [RelayCommand]
    void Show() => ShowRequested?.Invoke();

    [RelayCommand]
    void Quit() => QuitRequested?.Invoke();

    [RelayCommand]
    void ClearLog() => Log.Clear();

    void LoadSettings()
    {
        var c = Host.Config;
        EditServerName = c.ServerName;
        StartMinimized = c.StartMinimized;
        EnableVigem = c.EnableVigem;
        EnableOutGauge = c.EnableOutGauge;
        EnableMdns = c.EnableMdns;
        OutGaugePortText = c.OutGaugePort.ToString();
        try { StartWithWindows = _platform.StartsWithWindows; } catch { StartWithWindows = c.StartWithWindows; }
    }

    [RelayCommand]
    async Task SaveSettings()
    {
        var name = EditServerName.Trim();
        if (name.Length == 0) { SettingsNote = "Give this PC a name so phones can tell it apart."; return; }
        if (!int.TryParse(OutGaugePortText, out var port) || port is < 1 or > 65535) { SettingsNote = "The OutGauge port must be a number from 1 to 65535."; return; }

        var c = Host.Config;
        bool restart = c.EnableVigem != EnableVigem || c.EnableOutGauge != EnableOutGauge || c.EnableMdns != EnableMdns
                       || c.OutGaugePort != port || c.ServerName != name;
        // Read the form on the UI thread; the save (with its short retry on a locked file) runs off it.
        bool startMinimized = StartMinimized, vigem = EnableVigem, outGauge = EnableOutGauge, mdns = EnableMdns, startWithWindows = StartWithWindows;
        try
        {
            await Task.Run(() => Host.Store.Update(x =>
            {
                x.ServerName = name;
                x.StartMinimized = startMinimized;
                x.EnableVigem = vigem;
                x.EnableOutGauge = outGauge;
                x.EnableMdns = mdns;
                x.OutGaugePort = port;
                x.StartWithWindows = startWithWindows;
            }));
        }
        catch (Exception e)
        {
            SettingsNote = Host.Store.WriteBlocked != null
                // Blocked at start-up: no retry can succeed before a restart, so say what the banner says.
                ? $"Not saved: {Host.Store.LoadNote}"
                // Usually the file is locked for a moment by antivirus or a sync client.
                : $"Not saved: {Host.Store.Path} could not be written ({e.Message}). Try again in a moment.";
            Host.Log("error", SettingsNote);
            return;
        }
        try { _platform.StartsWithWindows = StartWithWindows; }
        catch (Exception e) { Host.Log("warn", $"Could not change start with Windows: {e.Message}"); }
        SettingsNote = restart ? "Saved. Restart the companion to apply the name, discovery and output changes." : "Saved.";
        RefreshPin();
    }

    public void Dispose()
    {
        _slow.Stop();
        foreach (var off in _unsubscribe) off();
        _unsubscribe.Clear();
    }
}
