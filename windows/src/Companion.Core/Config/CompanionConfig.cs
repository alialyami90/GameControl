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
    /// <summary>The settings file layout. Beta builds wrote no number (read as 0); their layout is the same as 1.</summary>
    public const int CurrentSchema = 1;
    public int Schema { get; set; } = CurrentSchema;
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

    /// <summary>
    /// Brings a file from an older build (or one edited by hand) to the current layout: fills in what is
    /// missing, puts out-of-range ports back to their defaults, and drops pairings that could never sign in.
    /// Returns true when anything changed.
    /// </summary>
    public bool Migrate()
    {
        bool changed = false;
        var d = new CompanionConfig();
        int Port(int v, int fallback) { if (v is >= 1 and <= 65535) return v; changed = true; return fallback; }
        WsPort = Port(WsPort, d.WsPort);
        UdpPort = Port(UdpPort, d.UdpPort);
        DiscoveryPort = Port(DiscoveryPort, d.DiscoveryPort);
        BeamNgPort = Port(BeamNgPort, d.BeamNgPort);
        OutGaugePort = Port(OutGaugePort, d.OutGaugePort);
        if (string.IsNullOrWhiteSpace(ServerName)) { ServerName = d.ServerName; changed = true; }
        if (Devices == null) { Devices = new(); changed = true; }
        int before = Devices.Count;
        Devices = Devices.Where(x => x != null && !string.IsNullOrEmpty(x.DeviceId) && !string.IsNullOrEmpty(x.TokenHash))
                         .GroupBy(x => x.DeviceId).Select(g => g.OrderByDescending(x => x.LastSeen).First()).ToList();
        foreach (var x in Devices) if (string.IsNullOrEmpty(x.DeviceName)) { x.DeviceName = x.DeviceId; changed = true; }
        changed |= Devices.Count != before;
        if (Schema != CurrentSchema) { Schema = CurrentSchema; changed = true; }
        return changed;
    }

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
        // Tolerant reading: a hand-edited file may differ in case or quote its numbers.
        PropertyNameCaseInsensitive = true,
        NumberHandling = JsonNumberHandling.AllowReadingFromString,
        ReadCommentHandling = JsonCommentHandling.Skip,
        AllowTrailingCommas = true,
    };

    readonly object _gate = new();
    public string Path { get; }
    public CompanionConfig Config { get; private set; }
    /// <summary>What happened while loading, for the log: a migration, or an unreadable file that was set aside. Null when nothing to say.</summary>
    public string? LoadNote { get; private set; }

    /// <summary>
    /// Set when the file on disk must not be overwritten: it could not be read (locked by antivirus or a
    /// sync client), could not be set aside, or comes from a newer version. Saves then fail with this
    /// reason, so pairings in the real file are never replaced by the defaults in memory.
    /// </summary>
    public string? WriteBlocked { get; private set; }

    readonly Func<string, string> _readFile;
    readonly Action<string, string> _copyFile;

    public ConfigStore(string? path = null) : this(path, null, null) { }

    internal ConfigStore(string? path, Func<string, string>? readFile, Action<string, string>? copyFile)
    {
        _readFile = readFile ?? File.ReadAllText;
        _copyFile = copyFile ?? ((from, to) => File.Copy(from, to, overwrite: true));
        Path = path ?? DefaultPath();
        Config = Load(Path, out var note);
        LoadNote = note;
    }

    public static string DefaultPath()
    {
        var dir = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "GameController");
        return System.IO.Path.Combine(dir, "companion.json");
    }

    CompanionConfig Load(string path, out string? note)
    {
        note = null;
        if (!File.Exists(path)) return new CompanionConfig();

        // Antivirus, backup and sync clients (OneDrive) hold the file for a moment: 6 reads, 1.5 s of waits
        // in all (100 + 200 + ... + 500 ms). This runs once at start-up, before the window is shown, so the
        // wait is kept short; a lock that outlasts it leaves the file untouched (WriteBlocked) instead.
        string json;
        for (int attempt = 1; ; attempt++)
        {
            try { json = _readFile(path); break; }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException)
            {
                if (attempt >= 6)
                {
                    WriteBlocked = $"{path} could not be read ({e.Message}), so it is left untouched";
                    note = $"The settings file could not be read ({e.Message}). Paired phones and settings are not lost, but this session uses defaults and saves nothing. Close whatever holds the file, then restart the companion.";
                    return new CompanionConfig();
                }
                Thread.Sleep(100 * attempt);
            }
        }

        CompanionConfig config;
        bool hadSchema;
        try
        {
            var node = System.Text.Json.Nodes.JsonNode.Parse(json, documentOptions: new() { CommentHandling = JsonCommentHandling.Skip, AllowTrailingCommas = true });
            // A file without "schema" is from a beta build.
            hadSchema = node is System.Text.Json.Nodes.JsonObject o && o.Any(kv => string.Equals(kv.Key, "schema", StringComparison.OrdinalIgnoreCase));
            config = JsonSerializer.Deserialize<CompanionConfig>(json, Options) ?? new CompanionConfig();
        }
        catch (Exception e) // the file is already in memory, so anything here is its content (bad JSON, a repeated key, a wrong type)
        {
            // Really unreadable: keep it next to the new one. If even that fails, never save over it.
            var aside = $"{path}.unreadable-{DateTime.Now:yyyyMMdd-HHmmss}";
            try { _copyFile(path, aside); }
            catch (Exception copy)
            {
                WriteBlocked = $"{path} is unreadable and could not be set aside ({copy.Message}), so it is left untouched";
                note = $"The settings file could not be read ({e.Message}) or copied aside ({copy.Message}). It is left untouched; this session uses defaults and saves nothing.";
                return new CompanionConfig();
            }
            note = $"The settings file could not be read ({e.Message}); defaults are in use and the old file was kept as {aside}";
            return new CompanionConfig();
        }

        if (!hadSchema) config.Schema = 0;
        if (config.Schema > CompanionConfig.CurrentSchema)
        {
            // Written by a newer companion: use what we understand, change nothing on disk.
            WriteBlocked = $"{path} was written by a newer version of the companion (settings schema {config.Schema}), so it is left untouched";
            note = $"The settings file is from a newer version of the companion (schema {config.Schema}). Paired phones work, but changes made here are not saved. Install the newer version to change settings.";
            return config;
        }
        int from = config.Schema;
        if (config.Migrate())
        {
            Config = config;
            try { Save(); note = from < CompanionConfig.CurrentSchema ? $"Settings upgraded from an earlier version ({config.Devices.Count} paired phone{(config.Devices.Count == 1 ? "" : "s")} kept)" : "Settings file repaired"; }
            catch (Exception e) { note = $"Settings upgraded in memory but could not be saved ({e.Message})"; }
        }
        return config;
    }

    /// <summary>Reads under the same lock as <see cref="Update"/>, so lists are never seen mid-change.</summary>
    public T Read<T>(Func<CompanionConfig, T> read)
    {
        lock (_gate) return read(Config);
    }

    /// <summary>
    /// Applies a change and saves it. If the save fails the change is rolled back, so memory never
    /// holds something the file does not (no ghost devices after a failed pairing), and the error is rethrown.
    /// </summary>
    public void Update(Action<CompanionConfig> change)
    {
        lock (_gate)
        {
            var before = JsonSerializer.Serialize(Config, Options);
            change(Config);
            try { Save(); }
            catch
            {
                Config = JsonSerializer.Deserialize<CompanionConfig>(before, Options) ?? Config;
                throw;
            }
        }
    }

    public void Save()
    {
        lock (_gate)
        {
            if (WriteBlocked != null) throw new IOException(WriteBlocked);
            var dir = System.IO.Path.GetDirectoryName(Path);
            if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
            var tmp = Path + ".tmp";
            var json = JsonSerializer.Serialize(Config, Options);
            // Antivirus and sync clients lock the file for a moment; retry briefly before giving up.
            for (int attempt = 1; ; attempt++)
            {
                try
                {
                    File.WriteAllText(tmp, json);
                    File.Move(tmp, Path, overwrite: true);
                    return;
                }
                catch (Exception e) when (e is IOException or UnauthorizedAccessException && attempt < 3)
                {
                    Thread.Sleep(100 * attempt);
                }
            }
        }
    }
}
