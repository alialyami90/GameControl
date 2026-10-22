using Avalonia.Controls;

namespace GameController.Companion.App.Views;

public partial class PhonesView : UserControl
{
    public PhonesView()
    {
        InitializeComponent();
        SizeChanged += (_, e) =>
        {
            // Content width (window minus the 236 px rail): a 1120 px window keeps every column.
            Classes.Set("compact", e.NewSize.Width < 640);
            Classes.Set("medium", e.NewSize.Width < 860);
        };
    }
}
