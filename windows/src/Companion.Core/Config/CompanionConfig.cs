using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace GameController.Companion.Config;

public sealed class PairedDevice
{
    public string DeviceId { get; set; } = "";
    public string DeviceName { get; set; } = "";
    /// <summary>SHA-256 of the token, so the settings file never holds a usable credential.</summary>
    public string TokenHash { get; set; } = "";
    public DateTimeOffset PairedAt { get; set; }
    public DateTimeOffset LastSeen { get; set; }
}

public sealed class CompanionConfig
{
    public string ServerName { get; set; } = Environment.MachineName;
    public int WsPort { get; set; } = Protocol.ProtocolInfo.WsPort;
    public int UdpPort { get; set; } = Protocol.ProtocolInfo.UdpPort;
    public int DiscoveryPort { get; set; } = Protocol.ProtocolInfo.DiscoveryPort;
    public int BeamNgPort { get; set; } = Protocol.ProtocolInfo.BeamNgPort;
    public int OutGaugePort { get; set; } = Protocol.ProtocolInfo.OutGaugePort;
    public bool EnableOutGauge { get; set; } = true;
    public bool EnableVigem { get; set; } = true;
    public bool EnableMdns { get; set; } = true;
    public bool StartMinimized { get; set; }
    public bool StartWithWindows { get; set; }
    public List<PairedDevice> Devices { get; set; } = new();

    public static string HashToken(string token) =>
        Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(token)));
}

/// <summary>Loads and saves the settings file atomically. Thread-safe.</summary>
public sealed class ConfigStore
{
    static readonly JsonSerializerOptions Options = new()
    {
        WriteIndented = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.Never,
    };

    readonly object _gate = new();
    public string Path { get; }
    public CompanionConfig Config { get; private set; }

    public ConfigStore(string? path = null)
    {
        Path = path ?? DefaultPath();
        Config = Load(Path);
    }

    public static string DefaultPath()
    {
        var dir = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "GameController");
        return System.IO.Path.Combine(dir, "companion.json");
    }

    static CompanionConfig Load(string path)
    {
        try
        {
            if (File.Exists(path))
                return JsonSerializer.Deserialize<CompanionConfig>(File.ReadAllText(path), Options) ?? new CompanionConfig();
        }
        catch (Exception)
        {
            // A corrupt file falls back to defaults; the next save rewrites it.
        }
        return new CompanionConfig();
    }

    /// <summary>Reads under the same lock as <see cref="Update"/>, so lists are never seen mid-change.</summary>
    public T Read<T>(Func<CompanionConfig, T> read)
    {
        lock (_gate) return read(Config);
    }

    public void Update(Action<CompanionConfig> change)
    {
        lock (_gate)
        {
            change(Config);
            Save();
        }
    }

    public void Save()
    {
        lock (_gate)
        {
            var dir = System.IO.Path.GetDirectoryName(Path);
            if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
            var tmp = Path + ".tmp";
            File.WriteAllText(tmp, JsonSerializer.Serialize(Config, Options));
            File.Move(tmp, Path, overwrite: true);
        }
    }
}
