using System.Buffers.Binary;
using System.Text;

namespace GameController.Companion.BeamNG;

/// <summary>
/// Parses the OutGauge UDP packet (LFS format, which BeamNG emits when OutGauge is enabled in
/// Options > Other) into protocol state keys (PROTOCOL.md "OutGauge mapping").
/// </summary>
public static class OutGauge
{
    public const int MinPacket = 92; // 96 when the optional ID field is present

    public const uint DL_SHIFT = 0x0001, DL_FULLBEAM = 0x0002, DL_HANDBRAKE = 0x0004, DL_TC = 0x0010,
        DL_SIGNAL_L = 0x0020, DL_SIGNAL_R = 0x0040, DL_SIGNAL_ANY = 0x0080, DL_ABS = 0x0400;

    public static Dictionary<string, object>? Parse(ReadOnlySpan<byte> p)
    {
        if (p.Length < MinPacket) return null;
        sbyte gearRaw = (sbyte)p[10];
        float speed = BinaryPrimitives.ReadSingleLittleEndian(p[12..]);
        float rpm = BinaryPrimitives.ReadSingleLittleEndian(p[16..]);
        float fuel = BinaryPrimitives.ReadSingleLittleEndian(p[28..]);
        uint dashLights = BinaryPrimitives.ReadUInt32LittleEndian(p[40..]);
        // Some sources leave DashLights at 0 ("nothing declared"); then trust every ShowLights bit we map.
        if (dashLights == 0) dashLights = uint.MaxValue;
        uint showLights = BinaryPrimitives.ReadUInt32LittleEndian(p[44..]);

        var v = new Dictionary<string, object>
        {
            ["gear"] = gearRaw switch { 0 => "R", 1 => "N", < 0 => "R", _ => (gearRaw - 1).ToString() },
            ["speed"] = Round(speed, 2),
            ["rpm"] = Math.Round(Finite(rpm)),
            ["fuel"] = Round(Math.Clamp(Finite(fuel), 0, 1), 3),
            ["engine.running"] = rpm > 50,
        };

        // Only report a lamp the game says it drives (DashLights), using its current state (ShowLights).
        void Lamp(uint flag, string key) { if ((dashLights & flag) != 0) v[key] = (showLights & flag) != 0; }
        Lamp(DL_FULLBEAM, "lights.high");
        Lamp(DL_HANDBRAKE, "parkingbrake");
        Lamp(DL_TC, "tcs.active");
        Lamp(DL_SIGNAL_L, "signal.left");
        Lamp(DL_SIGNAL_R, "signal.right");
        Lamp(DL_ABS, "abs.active");
        if ((dashLights & (DL_SIGNAL_L | DL_SIGNAL_R)) == (DL_SIGNAL_L | DL_SIGNAL_R))
            v["hazard"] = (showLights & (DL_SIGNAL_L | DL_SIGNAL_R)) == (DL_SIGNAL_L | DL_SIGNAL_R);
        return v;
    }

    public static string CarName(ReadOnlySpan<byte> p) =>
        p.Length < 8 ? "" : Encoding.ASCII.GetString(p.Slice(4, 4)).TrimEnd('\0');

    static double Finite(float f) => float.IsFinite(f) ? f : 0;
    static double Round(double d, int digits) => Math.Round(double.IsFinite(d) ? d : 0, digits);
}
