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
    }

    void Fire(string phone, string output) => Signal.Fire(phone, output);
    void Echo(string output) => Signal.Echo(output);
}
