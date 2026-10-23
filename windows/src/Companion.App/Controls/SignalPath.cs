using Avalonia;
using Avalonia.Controls;
using Avalonia.Media;
using Avalonia.Threading;

namespace GameController.Companion.App.Controls;

public enum NodeState { Off, Ready, Live, Attention, Failed }

/// <summary>One box on the signal path: a phone on the left, an output on the right.</summary>
public sealed record SignalNode(string Key, string Title, string Detail, string Icon, NodeState State, IReadOnlyList<LinkTag>? Links = null);

/// <summary>A link a phone node shows as a small tag ("WI-FI", "BT"): filled when up, hollow when not.</summary>
public sealed record LinkTag(string Label, bool Up);

/// <summary>
/// The companion's signature: wires from each phone, through this PC, to each output. When an
/// input passes, an electric spark runs down the wires it took; when the game reports state back,
/// a green (confirmed) spark runs the other way. With animations off, the wires flash instead of
/// travelling. Nodes are cut-corner caps and the PC is a slanted plate, as in DESIGN.md.
/// </summary>
public sealed class SignalPath : Control
{
    const double NodeW = 214, NodeH = 58, HubW = 200, HubH = 92, Gap = 12;
    const double TravelMs = 420, HeatMs = 900;

    public static readonly StyledProperty<IReadOnlyList<SignalNode>> PhonesProperty =
        AvaloniaProperty.Register<SignalPath, IReadOnlyList<SignalNode>>(nameof(Phones), Array.Empty<SignalNode>());
    public static readonly StyledProperty<IReadOnlyList<SignalNode>> OutputsProperty =
        AvaloniaProperty.Register<SignalPath, IReadOnlyList<SignalNode>>(nameof(Outputs), Array.Empty<SignalNode>());
    public static readonly StyledProperty<string> HubTitleProperty = AvaloniaProperty.Register<SignalPath, string>(nameof(HubTitle), "This PC");
    public static readonly StyledProperty<string> HubDetailProperty = AvaloniaProperty.Register<SignalPath, string>(nameof(HubDetail), "");
    public static readonly StyledProperty<bool> ReduceMotionProperty = AvaloniaProperty.Register<SignalPath, bool>(nameof(ReduceMotion));

    static SignalPath() => AffectsRender<SignalPath>(PhonesProperty, OutputsProperty, HubTitleProperty, HubDetailProperty);

    public IReadOnlyList<SignalNode> Phones { get => GetValue(PhonesProperty); set => SetValue(PhonesProperty, value); }
    public IReadOnlyList<SignalNode> Outputs { get => GetValue(OutputsProperty); set => SetValue(OutputsProperty, value); }
    public string HubTitle { get => GetValue(HubTitleProperty); set => SetValue(HubTitleProperty, value); }
    public string HubDetail { get => GetValue(HubDetailProperty); set => SetValue(HubDetailProperty, value); }
    public bool ReduceMotion { get => GetValue(ReduceMotionProperty); set => SetValue(ReduceMotionProperty, value); }

    sealed record Spark(string Phone, string Output, bool Inbound, DateTime Start);

    readonly List<Spark> _sparks = new();
    readonly Dictionary<string, DateTime> _heat = new();
    readonly DispatcherTimer _timer;

    // Palette (kept in code because the control draws itself).
    // Palette from the shared tokens (Styles/Rig.axaml), read once when the control is first used.
    static readonly Color Ground = Tokens.Color("Ground", "#0A0D14"), Panel = Tokens.Color("Panel", "#111622"),
        PanelRaised = Tokens.Color("PanelRaised", "#171D2B"), Cap = Tokens.Color("Cap", "#1C2333"), CapHigh = Tokens.Color("CapHigh", "#262F44"),
        Line = Tokens.Color("Line", "#2B3448"), LineStrong = Tokens.Color("LineStrong", "#465170"),
        Ink = Tokens.Color("Ink", "#F2F4F8"), InkMuted = Tokens.Color("InkMuted", "#A0A9BC"), InkFaint = Tokens.Color("InkFaint", "#7C869C"),
        Electric = Tokens.Color("Electric", "#FF2E7E"), Amber = Tokens.Color("Amber", "#FFB020"),
        Green = Tokens.Color("Green", "#34E07F"), Red = Tokens.Color("Red", "#FF4B3A");

    // Names and numbers in italic condensed; detail lines upright.
    static readonly Typeface TitleFace = new(new FontFamily("avares://GameCtlCompanion/Assets/Fonts#Barlow Condensed SemiBold"), FontStyle.Italic);
    static readonly Typeface DetailFace = new(new FontFamily("avares://GameCtlCompanion/Assets/Fonts#Barlow"));
    static readonly Typeface HubFace = new(new FontFamily("avares://GameCtlCompanion/Assets/Fonts#Barlow Condensed ExtraBold"), FontStyle.Italic);

    public SignalPath()
    {
        _timer = new DispatcherTimer(TimeSpan.FromMilliseconds(16), DispatcherPriority.Render, (_, _) => Tick());
        ClipToBounds = false;
    }

    /// <summary>An input went from <paramref name="phone"/> to <paramref name="output"/>.</summary>
    public void Fire(string phone, string output) => Add(new Spark(phone, output, false, DateTime.UtcNow));

    /// <summary>The game reported state through <paramref name="output"/> (BeamNG, telemetry).</summary>
    public void Echo(string output) => Add(new Spark("*", output, true, DateTime.UtcNow));

    void Add(Spark s)
    {
        if (_sparks.Count > 64) _sparks.RemoveAt(0);
        _sparks.Add(s);
        var now = DateTime.UtcNow;
        _heat["out:" + s.Output] = now;
        if (s.Phone == "*") foreach (var p in Phones) _heat["ph:" + p.Key] = now;
        else _heat["ph:" + s.Phone] = now;
        _heat["hub"] = now;
        if (!_timer.IsEnabled) _timer.Start();
        InvalidateVisual();
    }

    void Tick()
    {
        var now = DateTime.UtcNow;
        _sparks.RemoveAll(s => (now - s.Start).TotalMilliseconds > TravelMs);
        bool hot = _heat.Values.Any(t => (now - t).TotalMilliseconds < HeatMs);
        if (_sparks.Count == 0 && !hot) _timer.Stop();
        InvalidateVisual();
    }

    protected override Size MeasureOverride(Size availableSize)
    {
        int rows = Math.Max(Math.Max(Phones.Count, 1), Outputs.Count);
        double h = rows * NodeH + (rows - 1) * Gap;
        return new Size(double.IsInfinity(availableSize.Width) ? 760 : availableSize.Width, Math.Max(h, HubH));
    }

    // ------------------------------------------------------------ geometry

    Rect HubRect()
    {
        // Whatever the nodes leave, minus room for the wires.
        double w = Math.Clamp(Bounds.Width - 2 * NodeWidth() - 120, 112, HubW);
        double x = Bounds.Width * 0.5 - w / 2 - 10;
        return new Rect(x, (Bounds.Height - HubH) / 2, w, HubH);
    }

    Rect Column(int i, int count, bool right)
    {
        double total = count * NodeH + (count - 1) * Gap;
        double y = (Bounds.Height - total) / 2 + i * (NodeH + Gap);
        double w = NodeWidth();
        return new Rect(right ? Bounds.Width - w : 0, y, w, NodeH);
    }

    /// <summary>Nodes shrink with the window but never below 156 px; narrow nodes switch to two-line titles.</summary>
    double NodeWidth() => Math.Clamp(Bounds.Width * 0.3, 156, NodeW);

    static (Point a, Point c1, Point c2, Point b) Curve(Point a, Point b)
    {
        double dx = (b.X - a.X) * 0.55;
        return (a, new Point(a.X + dx, a.Y), new Point(b.X - dx, b.Y), b);
    }

    static Point At((Point a, Point c1, Point c2, Point b) c, double t)
    {
        double u = 1 - t;
        return new Point(
            u * u * u * c.a.X + 3 * u * u * t * c.c1.X + 3 * u * t * t * c.c2.X + t * t * t * c.b.X,
            u * u * u * c.a.Y + 3 * u * u * t * c.c1.Y + 3 * u * t * t * c.c2.Y + t * t * t * c.b.Y);
    }

    static StreamGeometry CurveGeometry((Point a, Point c1, Point c2, Point b) c)
    {
        var g = new StreamGeometry();
        using var ctx = g.Open();
        ctx.BeginFigure(c.a, false);
        ctx.CubicBezierTo(c.c1, c.c2, c.b);
        ctx.EndFigure(false);
        return g;
    }

    double Heat(string key, DateTime now) =>
        _heat.TryGetValue(key, out var t) ? Math.Clamp(1 - (now - t).TotalMilliseconds / HeatMs, 0, 1) : 0;

    static Color Mix(Color a, Color b, double t) => Color.FromArgb(
        (byte)(a.A + (b.A - a.A) * t), (byte)(a.R + (b.R - a.R) * t), (byte)(a.G + (b.G - a.G) * t), (byte)(a.B + (b.B - a.B) * t));

    // ------------------------------------------------------------ drawing

    public override void Render(DrawingContext ctx)
    {
        var now = DateTime.UtcNow;
        var hub = HubRect();
        var phones = Phones.Count > 0 ? Phones : new[] { new SignalNode("", "No phone yet", "Pair one with the PIN", "phone", NodeState.Off) };
        var phoneRects = phones.Select((p, i) => Column(i, phones.Count, false)).ToList();
        var outRects = Outputs.Select((o, i) => Column(i, Outputs.Count, true)).ToList();

        var phoneCurves = phoneRects.Select(r => Curve(new Point(r.Right, r.Center.Y), new Point(hub.Left, hub.Center.Y))).ToList();
        var outCurves = outRects.Select(r => Curve(new Point(hub.Right, hub.Center.Y), new Point(r.Left, r.Center.Y))).ToList();

        // Wires first, nodes on top.
        for (int i = 0; i < phones.Count; i++)
        {
            // The wire is the Wi-Fi link to this PC; a Bluetooth-only phone reaches Windows around it.
            bool linked = phones[i].Links is { } tags ? tags.Any(t => t.Label == "WI-FI" && t.Up) : phones[i].State is NodeState.Live or NodeState.Ready;
            DrawWire(ctx, phoneCurves[i], linked, Heat("ph:" + phones[i].Key, now), Electric);
        }
        for (int i = 0; i < Outputs.Count; i++)
        {
            var o = Outputs[i];
            bool linked = o.State is NodeState.Live or NodeState.Ready;
            bool inbound = o.Key is "beamng" or "telemetry";
            DrawWire(ctx, outCurves[i], linked, Heat("out:" + o.Key, now), inbound && o.State == NodeState.Live ? Green : Electric);
        }

        if (!ReduceMotion)
            foreach (var s in _sparks)
            {
                double t = (now - s.Start).TotalMilliseconds / TravelMs;
                if (t > 1) continue;
                int oi = Outputs.ToList().FindIndex(o => o.Key == s.Output);
                if (oi < 0) continue;
                var color = s.Inbound ? Green : Electric;
                if (!s.Inbound)
                {
                    int pi = phones.ToList().FindIndex(p => p.Key == s.Phone);
                    if (t < 0.5) { if (pi >= 0) DrawSpark(ctx, phoneCurves[pi], t * 2, color); }
                    else DrawSpark(ctx, outCurves[oi], (t - 0.5) * 2, color);
                }
                else
                {
                    if (t < 0.5) DrawSpark(ctx, outCurves[oi], 1 - t * 2, color);
                    else for (int pi = 0; pi < phoneCurves.Count; pi++) DrawSpark(ctx, phoneCurves[pi], 1 - (t - 0.5) * 2, color);
                }
            }

        for (int i = 0; i < phones.Count; i++) DrawNode(ctx, phoneRects[i], phones[i], Heat("ph:" + phones[i].Key, now), dashed: Phones.Count == 0);
        for (int i = 0; i < Outputs.Count; i++) DrawNode(ctx, outRects[i], Outputs[i], Heat("out:" + Outputs[i].Key, now), dashed: false);
        DrawHub(ctx, hub, Heat("hub", now));
    }

    static void DrawWire(DrawingContext ctx, (Point, Point, Point, Point) c, bool linked, double heat, Color hot)
    {
        var g = CurveGeometry(c);
        var baseColor = linked ? LineStrong : Line;
        if (!linked)
        {
            ctx.DrawGeometry(null, new Pen(new SolidColorBrush(baseColor), 2, new DashStyle(new double[] { 2, 3 }, 0), PenLineCap.Round), g);
            return;
        }
        ctx.DrawGeometry(null, new Pen(new SolidColorBrush(Ground), 6, lineCap: PenLineCap.Round), g);
        ctx.DrawGeometry(null, new Pen(new SolidColorBrush(Mix(baseColor, hot, heat * 0.85)), 2.5, lineCap: PenLineCap.Round), g);
        if (heat > 0.01)
            ctx.DrawGeometry(null, new Pen(new SolidColorBrush(Color.FromArgb((byte)(70 * heat), hot.R, hot.G, hot.B)), 8, lineCap: PenLineCap.Round), g);
    }

    static void DrawSpark(DrawingContext ctx, (Point, Point, Point, Point) c, double t, Color color)
    {
        t = Math.Clamp(t, 0, 1);
        // A short comet: bright head, fading tail along the wire.
        const int segs = 8;
        for (int i = 0; i < segs; i++)
        {
            double t0 = t - i * 0.03, t1 = t - (i + 1) * 0.03;
            if (t1 < 0) break;
            var a = At(c, t0);
            var b = At(c, t1);
            byte alpha = (byte)(255 * (1 - (double)i / segs));
            ctx.DrawLine(new Pen(new SolidColorBrush(Color.FromArgb(alpha, color.R, color.G, color.B)), 4 - i * 0.3, lineCap: PenLineCap.Round), a, b);
        }
        var head = At(c, t);
        ctx.DrawEllipse(new SolidColorBrush(Color.FromArgb(90, color.R, color.G, color.B)), null, head, 9, 9);
        ctx.DrawEllipse(new SolidColorBrush(color), null, head, 4, 4);
    }

    static (Color lamp, string word) Lamp(NodeState s) => s switch
    {
        NodeState.Live => (Electric, "In use"),
        NodeState.Ready => (Green, "Ready"),
        NodeState.Attention => (Amber, "Needs setup"),
        NodeState.Failed => (Red, "Failed"),
        _ => (InkFaint, "Off"),
    };

    void DrawNode(DrawingContext ctx, Rect r, SignalNode n, double heat, bool dashed)
    {
        var (lamp, _) = Lamp(n.State);
        // A cap: cut top-right corner, tonal fill, 1px seam. Lit is a wash and a stripe, never a lift.
        var shape = Shapes.Cut(r.Deflate(0.5));
        if (dashed)
            ctx.DrawGeometry(new SolidColorBrush(Panel), new Pen(new SolidColorBrush(LineStrong), 1.5, new DashStyle(new double[] { 3, 3 }, 0)), shape);
        else
        {
            var fill = n.State is NodeState.Off
                ? (IBrush)new SolidColorBrush(PanelRaised)
                : new LinearGradientBrush
                {
                    StartPoint = new RelativePoint(0, 0, RelativeUnit.Relative), EndPoint = new RelativePoint(0, 1, RelativeUnit.Relative),
                    GradientStops = { new GradientStop(n.State == NodeState.Live ? Mix(CapHigh, Electric, 0.10 + 0.18 * heat) : CapHigh, 0), new GradientStop(Cap, 1) },
                };
            var border = n.State switch
            {
                NodeState.Live => Mix(LineStrong, Electric, 0.45 + 0.55 * heat),
                NodeState.Attention => Mix(Line, Amber, 0.65),
                NodeState.Failed => Red,
                NodeState.Ready => LineStrong,
                _ => Line,
            };
            ctx.DrawGeometry(fill, new Pen(new SolidColorBrush(border), 1), shape);
            // Racing stripe along the bottom of a node that is carrying input or confirmed by the game.
            if (n.State == NodeState.Live)
                ctx.DrawRectangle(new SolidColorBrush(lamp), null, new Rect(r.X + 6, r.Bottom - 3, r.Width - 12, 2));
        }

        // Icon well
        var well = new Rect(r.X + 10, r.Y + (r.Height - 38) / 2, 38, 38);
        ctx.DrawRectangle(new SolidColorBrush(Ground), null, new RoundedRect(well, 4));
        var iconColor = n.State switch { NodeState.Live => Electric, NodeState.Off => InkFaint, NodeState.Attention => Amber, NodeState.Failed => Red, _ => Ink };
        DrawIcon(ctx, n.Icon, new Rect(well.X + 8, well.Y + 8, 22, 22), iconColor);

        double tx = well.Right + 10;
        var titleInk = n.State == NodeState.Off ? InkMuted : Ink;
        if (n.Links is { Count: > 0 } links)
        {
            // Phone: the name gets the whole first line; the link tags lead the second, then the detail.
            double full = r.Right - tx - 14;
            DrawText(ctx, n.Title, TitleFace, r.Width >= 190 ? 16 : 14.5, titleInk, new Point(tx, r.Y + 8), full);
            double used = DrawLinkTags(ctx, links, new Point(tx, r.Y + 32));
            if (full - used > 40)
                DrawText(ctx, n.Detail, DetailFace, 12.5, InkMuted, new Point(tx + used + 6, r.Y + 31), full - used - 6);
            return;
        }

        double tw = r.Right - tx - 26;
        if (r.Width >= 190)
        {
            DrawText(ctx, n.Title, TitleFace, 16, titleInk, new Point(tx, r.Y + 8), tw);
            DrawText(ctx, n.Detail, DetailFace, 12.5, InkMuted, new Point(tx, r.Y + 31), tw);
        }
        else
        {
            // Narrow: the name gets both lines; the detail is on the Outputs and Phones pages.
            var t = Layout(n.Title, TitleFace, 14.5, titleInk, tw, 2);
            ctx.DrawText(t, new Point(tx, r.Y + (r.Height - t.Height) / 2));
        }

        // Status lamp: shape changes with state so colour is never the only signal.
        var lc = new Point(r.Right - 22, r.Y + 17);
        var lampBrush = new SolidColorBrush(lamp);
        switch (n.State)
        {
            case NodeState.Live:
            case NodeState.Ready:
                ctx.DrawEllipse(lampBrush, null, lc, 4.5, 4.5);
                if (n.State == NodeState.Live) ctx.DrawEllipse(new SolidColorBrush(Color.FromArgb((byte)(60 + 100 * heat), lamp.R, lamp.G, lamp.B)), null, lc, 8, 8);
                break;
            case NodeState.Attention:
                var tri = new StreamGeometry();
                using (var g = tri.Open())
                {
                    g.BeginFigure(new Point(lc.X, lc.Y - 5.5), true);
                    g.LineTo(new Point(lc.X + 5.5, lc.Y + 4.5));
                    g.LineTo(new Point(lc.X - 5.5, lc.Y + 4.5));
                    g.EndFigure(true);
                }
                ctx.DrawGeometry(lampBrush, null, tri);
                break;
            case NodeState.Failed:
                ctx.DrawRectangle(lampBrush, null, new Rect(lc.X - 4.5, lc.Y - 4.5, 9, 9));
                break;
            default:
                ctx.DrawEllipse(null, new Pen(lampBrush, 1.5), lc, 4, 4);
                break;
        }
    }

    static readonly Typeface TagFace = new(new FontFamily("avares://GameCtlCompanion/Assets/Fonts#Barlow Condensed SemiBold"));

    /// <summary>Small slanted tags, left to right from <paramref name="topLeft"/>: green fill when up, hollow
    /// outline when not. Returns the width they took.</summary>
    static double DrawLinkTags(DrawingContext ctx, IReadOnlyList<LinkTag> links, Point topLeft)
    {
        double x = topLeft.X;
        foreach (var t in links)
        {
            var ft = Layout(t.Label, TagFace, 10.5, t.Up ? Ground : InkFaint, 60, 1);
            double w = Math.Max(30, ft.Width + 14);
            var rect = new Rect(x, topLeft.Y, w, 15);
            var g = Shapes.Slant(rect);
            if (t.Up) ctx.DrawGeometry(new SolidColorBrush(Green), null, g);
            else ctx.DrawGeometry(null, new Pen(new SolidColorBrush(InkFaint), 1), g);
            ctx.DrawText(ft, new Point(rect.X + (w - ft.Width) / 2, rect.Y + (15 - ft.Height) / 2));
            x += w + 4;
        }
        return x - 4 - topLeft.X;
    }

    /// <summary>This PC: a slanted title plate, the centre of the path.</summary>
    void DrawHub(DrawingContext ctx, Rect r, double heat)
    {
        // Lit, not lifted: input passing washes the plate and lights its edge; the type stays readable.
        var plate = Shapes.Slant(r.Deflate(1));
        ctx.DrawGeometry(new SolidColorBrush(Mix(CapHigh, Electric, heat * 0.22)), new Pen(new SolidColorBrush(Mix(LineStrong, Electric, heat)), 2), plate);
        DrawIcon(ctx, "pc", new Rect(r.Center.X - 13, r.Y + 12, 26, 26), Mix(Ink, Electric, heat));
        DrawText(ctx, HubTitle.ToUpperInvariant(), HubFace, 19, Ink, new Point(r.X + 22, r.Y + 42), r.Width - 44, center: true);
        DrawText(ctx, HubDetail, DetailFace, 12, InkMuted, new Point(r.X + 22, r.Y + 66), r.Width - 44, center: true);
    }

    static void DrawIcon(DrawingContext ctx, string name, Rect r, Color color)
    {
        if (!Icons.TryGet(name, out var parts)) return;
        var brush = new SolidColorBrush(color);
        var pen = new Pen(brush, 2, lineCap: PenLineCap.Round, lineJoin: PenLineJoin.Round);
        double s = r.Width / 24;
        using (ctx.PushTransform(Matrix.CreateScale(s, s) * Matrix.CreateTranslation(r.X, r.Y)))
            foreach (var (g, filled) in parts) ctx.DrawGeometry(filled ? brush : null, filled ? null : pen, g);
    }

    static FormattedText Layout(string text, Typeface face, double size, Color color, double maxWidth, int lines) =>
        new(text, System.Globalization.CultureInfo.CurrentUICulture, FlowDirection.LeftToRight, face, size, new SolidColorBrush(color))
        {
            MaxTextWidth = Math.Max(1, maxWidth),
            MaxLineCount = lines,
            Trimming = TextTrimming.CharacterEllipsis,
        };

    static void DrawText(DrawingContext ctx, string text, Typeface face, double size, Color color, Point at, double maxWidth, bool center = false)
    {
        var ft = Layout(text, face, size, color, maxWidth, 1);
        if (center) at = new Point(at.X + (maxWidth - Math.Min(ft.Width, maxWidth)) / 2, at.Y);
        ctx.DrawText(ft, at);
    }
}
