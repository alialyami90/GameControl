using System.Globalization;
using Avalonia.Data.Converters;

namespace GameController.Companion.App.Controls;

public static class Converters
{
    /// <summary>True when the bound value's string equals the parameter.</summary>
    public static readonly IValueConverter Is = new FuncValueConverter<object?, object?, bool>((v, p) => string.Equals(v?.ToString(), p?.ToString(), StringComparison.Ordinal));

    public static readonly IValueConverter NotEmpty = new FuncValueConverter<object?, bool>(v => v switch
    {
        null => false,
        string s => s.Length > 0,
        _ => true,
    });
}
