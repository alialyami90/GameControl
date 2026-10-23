using Avalonia;
using Avalonia.Controls;
using Avalonia.Media;

namespace GameController.Companion.App.Controls;

/// <summary>
/// A 24-unit stroke icon (2 px round stroke at 24 px), the same drawing rules as the phone app's
/// icon library. <see cref="Kind"/> names an entry in <see cref="Icons"/>.
/// </summary>
public sealed class RigIcon : Control
{
    public static readonly StyledProperty<string?> KindProperty = AvaloniaProperty.Register<RigIcon, string?>(nameof(Kind));
    public static readonly StyledProperty<IBrush?> ForegroundProperty =
        AvaloniaProperty.Register<RigIcon, IBrush?>(nameof(Foreground));
    public static readonly StyledProperty<double> SizeProperty = AvaloniaProperty.Register<RigIcon, double>(nameof(Size), 20);

    static RigIcon()
    {
        AffectsRender<RigIcon>(KindProperty, ForegroundProperty);
        AffectsMeasure<RigIcon>(SizeProperty);
    }

    public string? Kind { get => GetValue(KindProperty); set => SetValue(KindProperty, value); }
    public IBrush? Foreground { get => GetValue(ForegroundProperty); set => SetValue(ForegroundProperty, value); }
    public double Size { get => GetValue(SizeProperty); set => SetValue(SizeProperty, value); }

    protected override Size MeasureOverride(Size availableSize) => new(Size, Size);

    public override void Render(DrawingContext context)
    {
        if (Kind == null || !Icons.TryGet(Kind, out var parts)) return;
        var brush = Foreground ?? Brushes.White;
        double s = Math.Min(Bounds.Width, Bounds.Height) / 24.0;
        var pen = new Pen(brush, 2, lineCap: PenLineCap.Round, lineJoin: PenLineJoin.Round);
        using (context.PushTransform(Matrix.CreateScale(s, s)))
            foreach (var (geometry, filled) in parts)
                context.DrawGeometry(filled ? brush : null, filled ? null : pen, geometry);
    }
}

/// <summary>Icon sheet for the companion: 24-unit grid, drawn to match the phone app's tell-tales.</summary>
public static class Icons
{
    static readonly Dictionary<string, (Geometry, bool)[]> Map = new();

    static void Add(string name, params (string d, bool filled)[] parts) =>
        Map[name] = parts.Select(p => (Geometry.Parse(p.d), p.filled)).ToArray();

    static Icons()
    {
        const bool S = false, F = true;
        Add("status", ("M4.5 17a7.5 7.5 0 1 1 15 0", S), ("M12 17l3.8-4.6", S), ("M12 15.4a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 0 1 0-3.2z", F));
        Add("phone", ("M8.5 3h7A1.5 1.5 0 0 1 17 4.5v15a1.5 1.5 0 0 1-1.5 1.5h-7A1.5 1.5 0 0 1 7 19.5v-15A1.5 1.5 0 0 1 8.5 3z", S), ("M11 17.5h2", S));
        Add("outputs", ("M9 3v4.5M15 3v4.5", S), ("M6.5 7.5h11V11a5.5 5.5 0 0 1-11 0z", S), ("M12 16.5V21", S));
        Add("log", ("M9 6.5h11M9 12h11M9 17.5h7", S), ("M4.5 6.5h.01M4.5 12h.01M4.5 17.5h.01", S));
        Add("settings", ("M4 7h9M17 7h3M4 12h3M11 12h9M4 17h11M19 17h1", S), ("M15 5v4M9 10v4M17 15v4", S));
        Add("keyboard", ("M4 6.5h16a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1z", S),
            ("M7 10h.01M10 10h.01M13 10h.01M16 10h.01M8 14h8", S));
        Add("joystick", ("M12 3.5a2.5 2.5 0 1 1 0 5a2.5 2.5 0 0 1 0-5z", S), ("M12 8.5v6.5", S), ("M5.5 15h13l1 4.5h-15z", S));
        Add("gamepad", ("M7.5 8h9a4 4 0 0 1 3.9 3.2l.8 4.4a2.4 2.4 0 0 1-4.1 2.1L15.4 16H8.6l-1.7 1.7a2.4 2.4 0 0 1-4.1-2.1l.8-4.4A4 4 0 0 1 7.5 8z", S),
            ("M8 10.8v3M6.5 12.3h3", S), ("M15.5 11.5h.01M17.5 13.5h.01", S));
        Add("car", ("M4 16.5v-4l2.2-5h11.6l2.2 5v4z", S), ("M4 12.5h16", S), ("M6 16.5v2M18 16.5v2", S), ("M7.5 14.5h.01M16.5 14.5h.01", S));
        Add("telemetry", ("M3 12.5h4l2.2-5.5 4.2 10 2.3-4.5H21", S));
        Add("pc", ("M4 4.5h16a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1z", S), ("M8.5 20h7M12 15.5V20", S));
        Add("shield", ("M12 3l7.5 2.8v5.7c0 4.3-3.2 7.4-7.5 9c-4.3-1.6-7.5-4.7-7.5-9V5.8z", S), ("M9 12l2 2 4-4", S));
        Add("warning", ("M12 4l8.7 15H3.3z", S), ("M12 10v3.5M12 16.5h.01", S));
        Add("stop", ("M7 5.5h10A1.5 1.5 0 0 1 18.5 7v10a1.5 1.5 0 0 1-1.5 1.5H7A1.5 1.5 0 0 1 5.5 17V7A1.5 1.5 0 0 1 7 5.5z", S));
        Add("refresh", ("M19.5 12a7.5 7.5 0 1 1-2.2-5.3", S), ("M19.5 4.5v4h-4", S));
        Add("close", ("M6.5 6.5l11 11M17.5 6.5l-11 11", S));
        Add("folder", ("M3.5 7V18a1 1 0 0 0 1 1h15a1 1 0 0 0 1-1V9a1 1 0 0 0-1-1h-7.5L10 5.5H4.5a1 1 0 0 0-1 1z", S));
        Add("check", ("M5 12.5l4.5 4.5L19 7.5", S));
        Add("wheel", ("M12 3.5a8.5 8.5 0 1 1 0 17a8.5 8.5 0 0 1 0-17z", S), ("M3.8 11.2L9.8 12M20.2 11.2L14.2 12M12 14.2v6", S),
            ("M12 9.8a2.2 2.2 0 1 1 0 4.4a2.2 2.2 0 0 1 0-4.4z", F));
        Add("tray", ("M4 14.5h4l1.5 2.5h5l1.5-2.5h4", S), ("M4 14.5V18a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3.5l-2.5-8h-11z", S));
        Add("bluetooth", ("M7 7.5l10 9-5 4V3.5l5 4-10 9", S));
        Add("link", ("M10 14a3.5 3.5 0 0 0 5 0l3-3a3.5 3.5 0 0 0-5-5l-1 1", S), ("M14 10a3.5 3.5 0 0 0-5 0l-3 3a3.5 3.5 0 0 0 5 5l1-1", S));
    }

    public static bool TryGet(string name, out (Geometry, bool)[] parts) => Map.TryGetValue(name, out parts!);
}
