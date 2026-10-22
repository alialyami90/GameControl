using Avalonia;
using Avalonia.Controls;
using Avalonia.Media;

namespace GameController.Companion.App.Controls;

public enum PlateShape { Slant, Cut }

/// <summary>
/// The two silhouettes of the Race Front-End world (DESIGN.md "Shapes"): the slant, a parallelogram
/// leaning forward by 0.21 × height, for navigation, buttons, tags and title plates; and the cap,
/// a panel with its top-right corner cut off, for grouped content and nodes.
/// </summary>
public static class Shapes
{
    public const double Lean = 0.21, CutSize = 14, Corner = 6;

    public static StreamGeometry Slant(Rect r, double lean = Lean)
    {
        double d = Math.Min(r.Height * lean, r.Width / 3);
        var g = new StreamGeometry();
        using var c = g.Open();
        c.BeginFigure(new Point(r.Left + d, r.Top), true);
        c.LineTo(new Point(r.Right, r.Top));
        c.LineTo(new Point(r.Right - d, r.Bottom));
        c.LineTo(new Point(r.Left, r.Bottom));
        c.EndFigure(true);
        return g;
    }

    public static StreamGeometry Cut(Rect r, double cut = CutSize, double corner = Corner)
    {
        cut = Math.Min(cut, Math.Min(r.Width, r.Height) / 2);
        corner = Math.Min(corner, Math.Min(r.Width, r.Height) / 4);
        var g = new StreamGeometry();
        using var c = g.Open();
        var arc = new Size(corner, corner);
        c.BeginFigure(new Point(r.Left + corner, r.Top), true);
        c.LineTo(new Point(r.Right - cut, r.Top));
        c.LineTo(new Point(r.Right, r.Top + cut));
        c.LineTo(new Point(r.Right, r.Bottom - corner));
        c.ArcTo(new Point(r.Right - corner, r.Bottom), arc, 0, false, SweepDirection.Clockwise);
        c.LineTo(new Point(r.Left + corner, r.Bottom));
        c.ArcTo(new Point(r.Left, r.Bottom - corner), arc, 0, false, SweepDirection.Clockwise);
        c.LineTo(new Point(r.Left, r.Top + corner));
        c.ArcTo(new Point(r.Left + corner, r.Top), arc, 0, false, SweepDirection.Clockwise);
        c.EndFigure(true);
        return g;
    }
}

/// <summary>A decorator that paints a slanted or cut-corner plate behind its child. Flat: no shadows.</summary>
public class Plate : Decorator
{
    public static readonly StyledProperty<PlateShape> ShapeProperty = AvaloniaProperty.Register<Plate, PlateShape>(nameof(Shape));
    public static readonly StyledProperty<IBrush?> FillProperty = AvaloniaProperty.Register<Plate, IBrush?>(nameof(Fill));
    public static readonly StyledProperty<IBrush?> StrokeProperty = AvaloniaProperty.Register<Plate, IBrush?>(nameof(Stroke));
    public static readonly StyledProperty<double> StrokeThicknessProperty = AvaloniaProperty.Register<Plate, double>(nameof(StrokeThickness), 1);
    public static readonly StyledProperty<bool> DashedProperty = AvaloniaProperty.Register<Plate, bool>(nameof(Dashed));

    static Plate() => AffectsRender<Plate>(ShapeProperty, FillProperty, StrokeProperty, StrokeThicknessProperty, DashedProperty);

    public PlateShape Shape { get => GetValue(ShapeProperty); set => SetValue(ShapeProperty, value); }
    public IBrush? Fill { get => GetValue(FillProperty); set => SetValue(FillProperty, value); }
    public IBrush? Stroke { get => GetValue(StrokeProperty); set => SetValue(StrokeProperty, value); }
    public double StrokeThickness { get => GetValue(StrokeThicknessProperty); set => SetValue(StrokeThicknessProperty, value); }
    public bool Dashed { get => GetValue(DashedProperty); set => SetValue(DashedProperty, value); }

    public override void Render(DrawingContext context)
    {
        double t = Stroke != null ? StrokeThickness : 0;
        var r = new Rect(Bounds.Size).Deflate(t / 2);
        if (r.Width <= 0 || r.Height <= 0) return;
        var g = Shape == PlateShape.Slant ? Shapes.Slant(r) : Shapes.Cut(r);
        var pen = Stroke != null && t > 0 ? new Pen(Stroke, t, Dashed ? new DashStyle(new double[] { 3, 3 }, 0) : null) : null;
        context.DrawGeometry(Fill, pen, g);
        base.Render(context);
    }
}
