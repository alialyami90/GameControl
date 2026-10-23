using System.Text.Json.Nodes;
using GameController.Companion.Config;
using GameController.Companion.Engine;

namespace GameController.Companion.Tests;

public class ConfigMigrationTests
{
    static string TempPath() => Path.Combine(Path.GetTempPath(), $"gc-mig-{Guid.NewGuid():N}.json");

    // Exactly what beta-1 and beta-2 wrote: camelCase, no "schema".
    static string Beta2File(string tokenHash) => $$"""
    {
      "serverName": "RIG-PC",
      "wsPort": 47800,
      "udpPort": 47801,
      "discoveryPort": 47802,
      "beamNgPort": 47803,
      "outGaugePort": 4445,
      "enableOutGauge": false,
      "enableVigem": false,
      "enableMdns": true,
      "startMinimized": true,
      "startWithWindows": true,
      "devices": [
        {
          "deviceId": "pixel-8",
          "deviceName": "Pixel 8",
          "tokenHash": "{{tokenHash}}",
          "pairedAt": "2026-10-01T10:00:00+00:00",
          "lastSeen": "2026-10-20T18:30:00+00:00"
        }
      ]
    }
    """;

    [Fact]
    public void Beta2_settings_and_paired_phones_survive_the_upgrade()
    {
        var path = TempPath();
        const string token = "beta2-token-abc";
        File.WriteAllText(path, Beta2File(CompanionConfig.HashToken(token)));

        var store = new ConfigStore(path);
        var c = store.Config;
        Assert.Equal("RIG-PC", c.ServerName);
        Assert.Equal(4445, c.OutGaugePort);
        Assert.False(c.EnableOutGauge);
        Assert.False(c.EnableVigem);
        Assert.True(c.StartMinimized);
        Assert.True(c.StartWithWindows);
        var dev = Assert.Single(c.Devices);
        Assert.Equal("Pixel 8", dev.DeviceName);
        Assert.Contains("upgraded", store.LoadNote);

        // The phone signs in with the token it already holds: no new pairing needed.
        var pairing = new Pairing(store);
        Assert.Equal(PairOutcome.Ok, pairing.Authenticate("pixel-8", "Pixel 8", null, token, out _));

        // The file now carries the schema, and reloading is a no-op.
        Assert.Equal(CompanionConfig.CurrentSchema, (int)JsonNode.Parse(File.ReadAllText(path))!["schema"]!);
        var again = new ConfigStore(path);
        Assert.Null(again.LoadNote);
        Assert.Single(again.Config.Devices);
    }

    [Fact]
    public void Unreadable_file_is_kept_aside_not_overwritten_silently()
    {
        var path = TempPath();
        File.WriteAllText(path, "{ \"devices\": [ { \"deviceId\": ");
        var store = new ConfigStore(path);
        Assert.Empty(store.Config.Devices);
        Assert.Contains("could not be read", store.LoadNote);
        var aside = Directory.GetFiles(Path.GetDirectoryName(path)!, Path.GetFileName(path) + ".unreadable-*");
        Assert.Single(aside);
        Assert.StartsWith("{ \"devices\"", File.ReadAllText(aside[0]));
    }

    [Fact]
    public void Bad_values_are_repaired_and_unusable_pairings_dropped()
    {
        var path = TempPath();
        File.WriteAllText(path, """
        { "schema": 1, "serverName": "", "wsPort": 0, "udpPort": "47801", "devices": [
          { "deviceId": "a", "deviceName": "", "tokenHash": "AA", "lastSeen": "2026-01-01T00:00:00Z" },
          { "deviceId": "a", "deviceName": "Newer", "tokenHash": "BB", "lastSeen": "2026-02-01T00:00:00Z" },
          { "deviceId": "", "tokenHash": "CC" },
          { "deviceId": "b", "tokenHash": "" } ], }
        """);
        var store = new ConfigStore(path);
        var c = store.Config;
        Assert.Equal(new CompanionConfig().WsPort, c.WsPort);
        Assert.Equal(47801, c.UdpPort);
        Assert.False(string.IsNullOrWhiteSpace(c.ServerName));
        var dev = Assert.Single(c.Devices);
        Assert.Equal("Newer", dev.DeviceName);
        Assert.Equal("Settings file repaired", store.LoadNote);
    }
}

public class FileLogTests
{
    [Fact]
    public void Writes_lines_and_keeps_one_rotated_file()
    {
        var dir = Path.Combine(Path.GetTempPath(), $"gc-log-{Guid.NewGuid():N}");
        var path = Path.Combine(dir, "companion.log");
        using (var log = new GameController.Companion.Logging.FileLog(path))
        {
            log.Write(DateTimeOffset.Now, "info", "hello");
            var big = new string('x', 2000);
            for (int i = 0; i < 600; i++) log.Write(DateTimeOffset.Now, "info", big);
            log.Write(DateTimeOffset.Now, "error", "last line");
        }
        Assert.True(File.Exists(path + ".1"));
        Assert.True(new FileInfo(path).Length <= GameController.Companion.Logging.FileLog.MaxBytes + 4096);
        Assert.Contains("last line", File.ReadAllText(path));
        Assert.Equal(new[] { "companion.log", "companion.log.1" }, Directory.GetFiles(dir).Select(Path.GetFileName).Order());
    }

    [Fact]
    public void An_unwritable_path_never_throws()
    {
        var dir = Path.Combine(Path.GetTempPath(), $"gc-log-{Guid.NewGuid():N}");
        Directory.CreateDirectory(Path.Combine(dir, "companion.log")); // a folder where the file should be
        using var log = new GameController.Companion.Logging.FileLog(Path.Combine(dir, "companion.log"));
        log.Write(DateTimeOffset.Now, "info", "dropped");
    }
}
