using Avalonia.Controls;

namespace GameController.Companion.App.Views;

public partial class PhonesView : UserControl
{
    public PhonesView()
    {
        InitializeComponent();
        SizeChanged += (_, e) =>
        {
            Classes.Set("compact", e.NewSize.Width < 760);
            Classes.Set("medium", e.NewSize.Width < 960);
        };
    }
}
