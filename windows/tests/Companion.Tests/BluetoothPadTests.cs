using GameController.Companion.Engine;

namespace GameController.Companion.Tests;

public class BluetoothPadTests
{
    // Interface paths as SetupAPI reports them for one classic-Bluetooth HID pad with two collections.
    const string Col1 = @"\\?\hid#{00001124-0000-1000-8000-00805f9b34fb}_vid&00020000_pid&0000&col01#8&2a7c1d3&0&0000#{4d1e55b2-f16f-11cf-88cb-001111000030}";
    const string Col2 = @"\\?\hid#{00001124-0000-1000-8000-00805f9b34fb}_vid&00020000_pid&0000&col02#8&2a7c1d3&0&0001#{4d1e55b2-f16f-11cf-88cb-001111000030}";
    const string Other = @"\\?\hid#{00001124-0000-1000-8000-00805f9b34fb}_vid&00020000_pid&0000&col01#8&19b0aa5&0&0000#{4d1e55b2-f16f-11cf-88cb-001111000030}";

    [Fact]
    public void Collections_of_one_pad_share_a_key() =>
        Assert.Equal(BluetoothPadKey.FromHidPath(Col1), BluetoothPadKey.FromHidPath(Col2));

    [Fact]
    public void Two_pads_get_different_keys() =>
        Assert.NotEqual(BluetoothPadKey.FromHidPath(Col1), BluetoothPadKey.FromHidPath(Other));
}
