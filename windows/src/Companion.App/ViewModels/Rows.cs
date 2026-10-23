using Avalonia.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using GameController.Companion.App.Controls;

namespace GameController.Companion.App.ViewModels;

public sealed partial class NavItem : ObservableObject
{
    public required string Key { get; init; }
    public required string Title { get; init; }
    public required string Icon { get; init; }
    [ObservableProperty] private string? _badge;
    [ObservableProperty] private bool _badgeIsAttention;
}

public sealed partial class PhoneRow : ObservableObject
{
    public required string DeviceId { get; init; }
    [ObservableProperty] private string _name = "";
    /// <summary>Linked to the companion over Wi-Fi (lamps, game state, BeamNG commands).</summary>
    [ObservableProperty] private bool _wifi;
    /// <summary>Connected to Windows as the GameCtl Pad Bluetooth controller.</summary>
    [ObservableProperty] private bool _bluetooth;
    /// <summary>False for a Bluetooth pad that matches no paired phone (nothing to forget).</summary>
    public bool IsPaired { get; init; } = true;
    [ObservableProperty] private string _address = "";
    [ObservableProperty] private string _profile = "";
    [ObservableProperty] private long _events;
    [ObservableProperty] private string _pairedText = "";
    [ObservableProperty] private string _lastSeenText = "";

    public bool Connected => Wifi || Bluetooth;
    public string StatusText => (Wifi, Bluetooth) switch
    {
        (true, true) => "Wi-Fi + Bluetooth",
        (true, false) => "Wi-Fi",
        (false, true) => "Bluetooth only",
        _ => "Offline",
    };
    partial void OnWifiChanged(bool value) { OnPropertyChanged(nameof(StatusText)); OnPropertyChanged(nameof(Connected)); }
    partial void OnBluetoothChanged(bool value) { OnPropertyChanged(nameof(StatusText)); OnPropertyChanged(nameof(Connected)); }
}

public sealed partial class OutputRow : ObservableObject
{
    public required string Key { get; init; }
    public required string Title { get; init; }
    public required string Icon { get; init; }
    /// <summary>What this output is for, in one line.</summary>
    public required string Purpose { get; init; }
    [ObservableProperty] private NodeState _state;
    [ObservableProperty] private string _status = "";
    [ObservableProperty] private string _detail = "";
    /// <summary>Set-up steps shown when the output is not ready.</summary>
    [ObservableProperty] private string _setup = "";
    [ObservableProperty] private string? _setupLink;
    /// <summary>Show <see cref="Setup"/> even while the output is ready (a how-to rather than a fault).</summary>
    [ObservableProperty] private bool _setupIsHint;
    /// <summary>Off because an optional driver is not installed: reads "Optional", not as a fault.</summary>
    [ObservableProperty] private bool _optional;

    public string StateWord => State switch
    {
        NodeState.Live => "In use",
        NodeState.Ready => "Ready",
        NodeState.Attention => "Needs setup",
        NodeState.Failed => "Failed",
        _ => Optional ? "Optional" : "Off",
    };

    public bool NeedsSetup => (SetupIsHint || State is NodeState.Attention or NodeState.Failed or NodeState.Off) && !string.IsNullOrEmpty(Setup);

    partial void OnStateChanged(NodeState value)
    {
        OnPropertyChanged(nameof(StateWord));
        OnPropertyChanged(nameof(NeedsSetup));
        OnPropertyChanged(nameof(LampBrush));
    }

    partial void OnSetupChanged(string value) => OnPropertyChanged(nameof(NeedsSetup));
    partial void OnSetupIsHintChanged(bool value) => OnPropertyChanged(nameof(NeedsSetup));
    partial void OnOptionalChanged(bool value) => OnPropertyChanged(nameof(StateWord));

    public IBrush LampBrush => State switch
    {
        NodeState.Live => Palette.Electric,
        NodeState.Ready => Palette.Green,
        NodeState.Attention => Palette.Amber,
        NodeState.Failed => Palette.Red,
        _ => Palette.InkFaint,
    };
}

public sealed record ActivityRow(string Time, string Phone, string Control, string What, string Icon);

public sealed record LogRow(string Time, string Level, string Message)
{
    public IBrush LevelBrush => Level switch { "error" => Palette.Red, "warn" => Palette.Amber, _ => Palette.InkFaint };
    public string LevelWord => Level switch { "error" => "ERROR", "warn" => "WARN", _ => "INFO" };
}

/// <summary>Signal colours for code-side bindings, read from the shared tokens in Styles/Rig.axaml.</summary>
public static class Palette
{
    public static readonly IBrush Electric = Tokens.Brush("Electric", "#FF2E7E");
    public static readonly IBrush Amber = Tokens.Brush("Amber", "#FFB020");
    public static readonly IBrush Green = Tokens.Brush("Green", "#34E07F");
    public static readonly IBrush Red = Tokens.Brush("Red", "#FF4B3A");
    public static readonly IBrush Ink = Tokens.Brush("Ink", "#F2F4F8");
    public static readonly IBrush InkMuted = Tokens.Brush("InkMuted", "#A0A9BC");
    public static readonly IBrush InkFaint = Tokens.Brush("InkFaint", "#7C869C");
}
