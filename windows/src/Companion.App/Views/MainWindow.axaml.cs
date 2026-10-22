using Avalonia;
using Avalonia.Controls;

namespace GameController.Companion.App.Views;

public partial class MainWindow : Window
{
    bool _fitted;

    public MainWindow()
    {
        InitializeComponent();
        Opened += (_, _) => FitToScreen();
    }

    /// <summary>
    /// On first show, shrink to the screen's working area (a 1366×768 laptop at 150 % has only about
    /// 910×512 logical pixels) and keep the window fully on screen.
    /// </summary>
    void FitToScreen()
    {
        if (_fitted) return;
        _fitted = true;
        if (Screens.ScreenFromWindow(this) is not { } screen) return;
        var area = screen.WorkingArea.ToRect(screen.Scaling);
        double w = Math.Min(Width, area.Width - 16), h = Math.Min(Height, area.Height - 16);
        MinWidth = Math.Min(MinWidth, w);
        MinHeight = Math.Min(MinHeight, h);
        Width = w;
        Height = h;
        Position = new PixelPoint(
            (int)(screen.WorkingArea.X + (screen.WorkingArea.Width - w * screen.Scaling) / 2),
            (int)(screen.WorkingArea.Y + (screen.WorkingArea.Height - h * screen.Scaling) / 2));
    }
}
