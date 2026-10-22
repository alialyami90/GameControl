using System.Buffers.Binary;
using GameController.Companion.BeamNG;

namespace GameController.Companion.Tests;

public class OutGaugeTests
{
    static byte[] Packet(byte gear, float speed, float rpm, float fuel, uint dash, uint show)
    {
        var p = new byte[96];
        "beam"u8.CopyTo(p.AsSpan(4));
        p[10] = gear;
        BinaryPrimitives.WriteSingleLittleEndian(p.AsSpan(12), speed);
        BinaryPrimitives.WriteSingleLittleEndian(p.AsSpan(16), rpm);
        BinaryPrimitives.WriteSingleLittleEndian(p.AsSpan(28), fuel);
        BinaryPrimitives.WriteUInt32LittleEndian(p.AsSpan(40), dash);
        BinaryPrimitives.WriteUInt32LittleEndian(p.AsSpan(44), show);
        return p;
    }

    [Fact]
    public void Maps_gear_numbers_and_lamps()
    {
        uint all = OutGauge.DL_FULLBEAM | OutGauge.DL_HANDBRAKE | OutGauge.DL_TC | OutGauge.DL_SIGNAL_L | OutGauge.DL_SIGNAL_R | OutGauge.DL_ABS;
        var v = OutGauge.Parse(Packet(3, 12.5f, 2500, 0.4f, all, OutGauge.DL_HANDBRAKE | OutGauge.DL_SIGNAL_L | OutGauge.DL_SIGNAL_R))!;
        Assert.Equal("2", v["gear"]);
        Assert.Equal(12.5, v["speed"]);
        Assert.Equal(2500.0, v["rpm"]);
        Assert.Equal(0.4, (double)v["fuel"], 3);
        Assert.Equal(true, v["parkingbrake"]);
        Assert.Equal(false, v["lights.high"]);
        Assert.Equal(true, v["hazard"]);
        Assert.Equal(true, v["engine.running"]);
    }

    [Theory]
    [InlineData(0, "R")]
    [InlineData(1, "N")]
    [InlineData(2, "1")]
    public void Gear_byte(byte raw, string gear) => Assert.Equal(gear, OutGauge.Parse(Packet(raw, 0, 0, 0, 0, 0))!["gear"]);

    [Fact]
    public void Lamps_the_game_does_not_drive_are_not_reported()
    {
        var v = OutGauge.Parse(Packet(1, 0, 0, 0, OutGauge.DL_HANDBRAKE, 0xFFFF))!;
        Assert.True((bool)v["parkingbrake"]!);
        Assert.False(v.ContainsKey("lights.high"));
        Assert.False(v.ContainsKey("hazard"));
    }

    [Fact]
    public void No_declared_lamps_means_trust_every_lamp()
    {
        var v = OutGauge.Parse(Packet(1, 0, 0, 0, 0, OutGauge.DL_FULLBEAM))!;
        Assert.True((bool)v["lights.high"]!);
        Assert.False((bool)v["hazard"]!);
    }

    [Fact]
    public void Short_packets_are_rejected() => Assert.Null(OutGauge.Parse(new byte[40]));
}
