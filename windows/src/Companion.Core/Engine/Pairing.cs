using System.Security.Cryptography;
using GameController.Companion.Config;

namespace GameController.Companion.Engine;

public enum PairOutcome { Ok, BadPin, BadToken, RateLimited, Missing, SaveFailed }

/// <summary>PIN pairing and token checks, with the 5-tries-then-60-s lockout from PROTOCOL.md.</summary>
public sealed class Pairing
{
    public const int MaxBadPins = 5;
    public static readonly TimeSpan Lockout = TimeSpan.FromSeconds(60);

    readonly object _gate = new();
    readonly ConfigStore _store;
    readonly Func<DateTimeOffset> _now;
    int _badPins;
    DateTimeOffset _lockedUntil = DateTimeOffset.MinValue;

    public string Pin { get; private set; } = NewPin();
    public event Action? PinChanged;
    public event Action? DevicesChanged;
    /// <summary>Why the last <see cref="PairOutcome.SaveFailed"/> happened, for the log.</summary>
    public string? SaveError { get; private set; }

    public Pairing(ConfigStore store, Func<DateTimeOffset>? now = null)
    {
        _store = store;
        _now = now ?? (() => DateTimeOffset.Now);
    }

    public DateTimeOffset LockedUntil { get { lock (_gate) return _lockedUntil; } }

    static string NewPin() => RandomNumberGenerator.GetInt32(0, 1_000_000).ToString("D6");

    public static string NewToken() => Base64Url(RandomNumberGenerator.GetBytes(32));

    public static string NewSessionKey()
    {
        const string alphabet = "abcdefghijkmnpqrstuvwxyz23456789";
        Span<char> c = stackalloc char[8];
        for (int i = 0; i < c.Length; i++) c[i] = alphabet[RandomNumberGenerator.GetInt32(alphabet.Length)];
        return new string(c);
    }

    static string Base64Url(byte[] b) => Convert.ToBase64String(b).TrimEnd('=').Replace('+', '-').Replace('/', '_');

    /// <summary>Fixes the PIN (console host <c>--pin</c>, for scripted tests). Must be 6 digits.</summary>
    public void UsePin(string pin)
    {
        if (pin.Length != 6 || !pin.All(char.IsAsciiDigit)) throw new ArgumentException("PIN must be 6 digits", nameof(pin));
        lock (_gate) { Pin = pin; FixedPin = true; }
        PinChanged?.Invoke();
    }

    public bool FixedPin { get; private set; }

    public void RotatePin()
    {
        lock (_gate) Pin = NewPin();
        PinChanged?.Invoke();
    }

    /// <summary>A hand-edited or corrupt hash in the config matches nothing instead of throwing.</summary>
    static byte[] FromHex(string? hex)
    {
        try { return Convert.FromHexString(hex ?? ""); }
        catch (FormatException) { return Array.Empty<byte>(); }
    }

    /// <summary>Checks a hello. On a good PIN, a new token is issued in <paramref name="token"/>.</summary>
    public PairOutcome Authenticate(string deviceId, string deviceName, string? pin, string? token, out string? issuedToken)
    {
        issuedToken = null;
        if (!string.IsNullOrEmpty(token))
        {
            var hash = Convert.FromHexString(CompanionConfig.HashToken(token));
            var dev = _store.Read(c => c.Devices.FirstOrDefault(d => d.DeviceId == deviceId &&
                CryptographicOperations.FixedTimeEquals(FromHex(d.TokenHash), hash)));
            if (dev == null)
            {
                // Fall through to the PIN if the phone also sent one (e.g. after "forget device" on the PC).
                if (string.IsNullOrEmpty(pin)) return PairOutcome.BadToken;
            }
            else
            {
                issuedToken = token;
                // "Last seen" is cosmetic: a settings file that cannot be written must not refuse the phone.
                try { _store.Update(c => { dev.LastSeen = _now(); if (!string.IsNullOrWhiteSpace(deviceName)) dev.DeviceName = deviceName; }); }
                catch (Exception) { }
                DevicesChanged?.Invoke();
                return PairOutcome.Ok;
            }
        }

        if (string.IsNullOrEmpty(pin)) return PairOutcome.Missing;

        bool rotate = false;
        lock (_gate)
        {
            if (_now() < _lockedUntil) return PairOutcome.RateLimited;
            if (!CryptographicOperations.FixedTimeEquals(System.Text.Encoding.ASCII.GetBytes(pin.PadRight(6)), System.Text.Encoding.ASCII.GetBytes(Pin)))
            {
                if (++_badPins >= MaxBadPins) { _badPins = 0; _lockedUntil = _now() + Lockout; }
                return PairOutcome.BadPin;
            }
            _badPins = 0;
            rotate = true;
        }

        var newToken = NewToken();
        try
        {
            _store.Update(c =>
            {
                c.Devices.RemoveAll(d => d.DeviceId == deviceId);
                c.Devices.Add(new PairedDevice
                {
                    DeviceId = deviceId,
                    DeviceName = string.IsNullOrWhiteSpace(deviceName) ? "Phone" : deviceName,
                    TokenHash = CompanionConfig.HashToken(newToken),
                    PairedAt = _now(),
                    LastSeen = _now(),
                });
            });
        }
        catch (Exception e)
        {
            // Nothing was paired: the store rolled back, the PIN stays valid, and the phone is told why.
            SaveError = e.Message;
            return PairOutcome.SaveFailed;
        }
        issuedToken = newToken;
        DevicesChanged?.Invoke();
        // A PIN is single-use: once a phone has paired, show a fresh one.
        if (rotate && !FixedPin) RotatePin();
        return PairOutcome.Ok;
    }

    public void Forget(string deviceId)
    {
        _store.Update(c => c.Devices.RemoveAll(d => d.DeviceId == deviceId));
        DevicesChanged?.Invoke();
    }
}
