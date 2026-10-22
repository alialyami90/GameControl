using GameController.Companion.Config;
using GameController.Companion.Engine;

namespace GameController.Companion.Tests;

public class PairingTests
{
    static (Pairing P, ConfigStore S, Func<DateTimeOffset, DateTimeOffset> Set) Make()
    {
        var path = Path.Combine(Path.GetTempPath(), $"gc-test-{Guid.NewGuid():N}.json");
        var store = new ConfigStore(path);
        var now = DateTimeOffset.Parse("2026-01-01T00:00:00Z");
        var p = new Pairing(store, () => now);
        return (p, store, t => now = t);
    }

    [Fact]
    public void Failed_save_pairs_nothing_and_keeps_the_pin()
    {
        var (p, store, _) = Make();
        Directory.CreateDirectory(store.Path); // the settings file cannot be written
        var pin = p.Pin;
        Assert.Equal(PairOutcome.SaveFailed, p.Authenticate("dev1", "Pixel", pin, null, out var token));
        Assert.Null(token);
        Assert.Empty(store.Config.Devices); // rolled back: no ghost device
        Assert.Equal(pin, p.Pin);
        Assert.NotNull(p.SaveError);
    }

    [Fact]
    public void Good_pin_pairs_and_issues_a_token_that_works_later()
    {
        var (p, store, _) = Make();
        var pin = p.Pin;
        Assert.Equal(PairOutcome.Ok, p.Authenticate("dev1", "Pixel", pin, null, out var token));
        Assert.NotNull(token);
        Assert.NotEqual(pin, p.Pin); // single-use PIN
        Assert.DoesNotContain(token!, File.ReadAllText(store.Path)); // only the hash is stored
        Assert.Equal(PairOutcome.Ok, p.Authenticate("dev1", "Pixel", null, token, out var again));
        Assert.Equal(token, again);
        // Reload from disk: still paired.
        var p2 = new Pairing(new ConfigStore(store.Path));
        Assert.Equal(PairOutcome.Ok, p2.Authenticate("dev1", "Pixel", null, token, out _));
    }

    [Fact]
    public void Token_is_bound_to_the_device_id()
    {
        var (p, _, _) = Make();
        p.Authenticate("dev1", "A", p.Pin, null, out var token);
        Assert.Equal(PairOutcome.BadToken, p.Authenticate("dev2", "B", null, token, out _));
    }

    [Fact]
    public void Five_bad_pins_lock_pairing_for_a_minute()
    {
        var (p, _, set) = Make();
        var t0 = DateTimeOffset.Parse("2026-01-01T00:00:00Z");
        var wrong = p.Pin == "000000" ? "111111" : "000000";
        for (int i = 0; i < 5; i++) Assert.Equal(PairOutcome.BadPin, p.Authenticate("d", "d", wrong, null, out _));
        Assert.Equal(PairOutcome.RateLimited, p.Authenticate("d", "d", p.Pin, null, out _));
        set(t0.AddSeconds(61));
        Assert.Equal(PairOutcome.Ok, p.Authenticate("d", "d", p.Pin, null, out _));
    }

    [Fact]
    public void Forget_revokes_the_token()
    {
        var (p, _, _) = Make();
        p.Authenticate("dev1", "A", p.Pin, null, out var token);
        p.Forget("dev1");
        Assert.Equal(PairOutcome.BadToken, p.Authenticate("dev1", "A", null, token, out _));
    }

    [Fact]
    public void Session_keys_are_eight_chars()
    {
        var k = Pairing.NewSessionKey();
        Assert.Equal(8, k.Length);
        Assert.NotEqual(k, Pairing.NewSessionKey());
    }
}
