namespace GameController.Companion.Engine;

/// <summary>A phone connected to this PC as the "GameCtl Pad" Bluetooth HID controller.</summary>
/// <param name="Id">Stable per physical device (all HID collections of one pad share it).</param>
public sealed record BluetoothPad(string Id, string Name);

/// <summary>
/// Lists the GameCtl Pad devices Windows currently has connected. Bluetooth input goes straight from
/// the phone to Windows and never through the companion, so this is how the window knows about it.
/// </summary>
public interface IBluetoothPads
{
    IReadOnlyList<BluetoothPad> Present();
}

public sealed class NoBluetoothPads : IBluetoothPads
{
    public IReadOnlyList<BluetoothPad> Present() => Array.Empty<BluetoothPad>();
}

/// <summary>One phone as the window shows it: its Wi-Fi session, its Bluetooth pad, or both.</summary>
public sealed record LinkedPhone(PhoneSession? Wifi, BluetoothPad? Bluetooth)
{
    public string Name => Wifi?.DeviceName ?? Bluetooth?.Name ?? "Phone";
    public string Key => Wifi?.DeviceId ?? "bt:" + Bluetooth!.Id;
}

public static class PhoneLinks
{
    /// <summary>
    /// Pairs Wi-Fi sessions with Bluetooth pads so a phone with both links counts once. Every phone
    /// presents the same "GameCtl Pad" name and Android does not expose its Bluetooth address, so the
    /// pairing is by order: with one phone (the normal case) it is exact; extra pads or sessions
    /// show on their own.
    /// </summary>
    public static List<LinkedPhone> Merge(IReadOnlyList<PhoneSession> sessions, IReadOnlyList<BluetoothPad> pads)
    {
        var list = new List<LinkedPhone>();
        int n = Math.Max(sessions.Count, pads.Count);
        for (int i = 0; i < n; i++)
            list.Add(new LinkedPhone(i < sessions.Count ? sessions[i] : null, i < pads.Count ? pads[i] : null));
        return list;
    }
}

public static class BluetoothPadKey
{
    /// <summary>
    /// One key per physical pad from a HID interface path. A composite pad (keyboard + gamepad) has one
    /// interface per top-level collection; they differ only in "&amp;colNN" and the last part of the
    /// instance id, e.g. <c>\\?\hid#{00001124-...}_vid&amp;0002...&amp;col02#8&amp;2a7c1d3&amp;0&amp;0001#{4d1e55b2-...}</c>.
    /// </summary>
    public static string FromHidPath(string path)
    {
        var p = System.Text.RegularExpressions.Regex.Replace(path.ToLowerInvariant(), "&col[0-9a-f]{2}", "");
        var parts = p.Split('#');
        if (parts.Length < 3) return p;
        var instance = parts[2];
        int amp = instance.LastIndexOf('&');
        return parts[1] + "#" + (amp > 0 ? instance[..amp] : instance);
    }
}
