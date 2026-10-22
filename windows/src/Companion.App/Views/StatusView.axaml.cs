using Avalonia;
using Avalonia.Controls;
using GameController.Companion.App.ViewModels;

namespace GameController.Companion.App.Views;

public partial class StatusView : UserControl
{
    MainViewModel? _vm;

    public StatusView()
    {
        InitializeComponent();
        DataContextChanged += (_, _) =>
        {
            if (_vm != null) { _vm.InputFired -= Fire; _vm.GameEchoed -= Echo; }
            _vm = DataContext as MainViewModel;
            if (_vm != null) { _vm.InputFired += Fire; _vm.GameEchoed += Echo; }
        };
        SizeChanged += (_, e) => Arrange(e.NewSize.Width < 700);
    }

    bool? _compact;

    /// <summary>On a narrow window the PIN hint drops under the PIN instead of squeezing beside it.</summary>
    void Arrange(bool compact)
    {
        if (_compact == compact) return;
        _compact = compact;
        Grid.SetRow(PinHintBlock, compact ? 1 : 0);
        Grid.SetColumn(PinHintBlock, compact ? 0 : 1);
        Grid.SetColumnSpan(PinHintBlock, compact ? 3 : 1);
        PinHintBlock.Margin = compact ? new Thickness(0, 14, 0, 0) : new Thickness(56, 0, 24, 0);
        PinRule.IsVisible = !compact;
        Grid.SetRow(Legend, compact ? 1 : 0);
        Grid.SetColumn(Legend, compact ? 0 : 1);
        Grid.SetColumnSpan(Legend, compact ? 2 : 1);
        Legend.Margin = compact ? new Thickness(0, 8, 0, 0) : new Thickness(0);
    }

    void Fire(string phone, string output) => Signal.Fire(phone, output);
    void Echo(string output) => Signal.Echo(output);
}
