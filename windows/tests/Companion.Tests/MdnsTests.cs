using GameController.Companion.Net;

namespace GameController.Companion.Tests;

public class MdnsTests
{
    [Fact]
    public void Query_round_trips_through_the_parser()
    {
        var q = DnsWire.Query("_gamectl._tcp.local", DnsWire.TypePtr);
        var qs = DnsWire.ParseQuestions(q, out bool isResponse)!;
        Assert.False(isResponse);
        Assert.Equal(new DnsWire.Question("_gamectl._tcp.local", DnsWire.TypePtr), qs.Single());
    }

    [Fact]
    public void Compressed_names_are_followed()
    {
        // Header, then "local" at offset 12, then a question "_tcp" + pointer to "local".
        var p = new List<byte> { 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0 };
        int localAt = p.Count;
        p.AddRange(DnsWire.NameBytes("local"));
        int off = localAt;
        Assert.Equal("local", DnsWire.ReadName(p.ToArray(), ref off));
        var tail = new List<byte> { 4 };
        tail.AddRange("_tcp"u8.ToArray());
        tail.AddRange(new byte[] { 0xC0, (byte)localAt });
        int start = p.Count;
        p.AddRange(tail);
        off = start;
        Assert.Equal("_tcp.local", DnsWire.ReadName(p.ToArray(), ref off));
        Assert.Equal(p.Count, off);
    }

    [Fact]
    public void Pointer_loops_are_rejected()
    {
        var p = new byte[] { 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0xC0, 12 };
        int off = 12;
        Assert.Null(DnsWire.ReadName(p, ref off));
    }

    [Fact]
    public void Response_header_counts_records()
    {
        var r = DnsWire.Response(
            new[] { new DnsWire.Record("_gamectl._tcp.local", DnsWire.TypePtr, DnsWire.ClassIn, 120, DnsWire.NameBytes("PC._gamectl._tcp.local")) },
            new[] { new DnsWire.Record("PC._gamectl._tcp.local", DnsWire.TypeTxt, DnsWire.ClassIn, 120, DnsWire.Txt(new[] { "v=1" })) });
        Assert.Equal(0x84, r[2]);
        Assert.Equal(1, r[7]);
        Assert.Equal(1, r[11]);
    }
}
