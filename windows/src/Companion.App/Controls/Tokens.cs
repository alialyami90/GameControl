using Avalonia;
using Avalonia.Media;

namespace GameController.Companion.App.Controls;

/// <summary>
/// Reads the DESIGN.md colour tokens defined in Styles/Rig.axaml, so code-drawn parts (the signal
/// path, row lamps) use the same values as the XAML. The fallback is only for a missing resource.
/// </summary>
public static class Tokens
{
    public static Color Color(string key, string fallback)
    {
        if (Application.Current?.TryGetResource(key + "Color", null, out var v) == true && v is Color c) return c;
        return Avalonia.Media.Color.Parse(fallback);
    }

    public static IBrush Brush(string key, string fallback) => new SolidColorBrush(Color(key, fallback));
}
