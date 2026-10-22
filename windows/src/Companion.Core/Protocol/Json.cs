using System.Text.Json;
using System.Text.Json.Nodes;

namespace GameController.Companion.Protocol;

public static class ProtocolInfo
{
    public const int Version = 1;
    public const int WsPort = 47800;
    public const int UdpPort = 47801;
    public const int DiscoveryPort = 47802;
    public const int BeamNgPort = 47803;
    public const int OutGaugePort = 4444;
    public const string MdnsService = "_gamectl._tcp";
}

/// <summary>Small helpers for reading loosely-typed protocol JSON without throwing.</summary>
public static class JsonExt
{
    public static string? Str(this JsonElement e, string name) =>
        e.ValueKind == JsonValueKind.Object && e.TryGetProperty(name, out var p) && p.ValueKind == JsonValueKind.String ? p.GetString() : null;

    public static int Int(this JsonElement e, string name, int fallback) =>
        e.ValueKind == JsonValueKind.Object && e.TryGetProperty(name, out var p) && p.ValueKind == JsonValueKind.Number && p.TryGetDouble(out var d)
            ? (int)Math.Round(d) : fallback;

    public static long? Long(this JsonElement e, string name) =>
        e.ValueKind == JsonValueKind.Object && e.TryGetProperty(name, out var p) && p.ValueKind == JsonValueKind.Number && p.TryGetDouble(out var d)
            ? (long)d : null;

    public static double? Num(this JsonElement e, string name)
    {
        if (e.ValueKind != JsonValueKind.Object || !e.TryGetProperty(name, out var p)) return null;
        return p.ValueKind switch
        {
            JsonValueKind.Number => p.GetDouble(),
            JsonValueKind.True => 1,
            JsonValueKind.False => 0,
            _ => null,
        };
    }

    public static bool Bool(this JsonElement e, string name) =>
        e.ValueKind == JsonValueKind.Object && e.TryGetProperty(name, out var p) && p.ValueKind == JsonValueKind.True;

    /// <summary>Builds an outgoing message: <c>{"v":1,"type":type, ...}</c>.</summary>
    public static JsonObject Msg(string type)
    {
        return new JsonObject { ["v"] = ProtocolInfo.Version, ["type"] = type };
    }

    public static JsonObject Error(string code, string message) =>
        new() { ["v"] = ProtocolInfo.Version, ["type"] = "error", ["code"] = code, ["message"] = message };

    /// <summary>Converts a state value from the mod (bool, number, string) to a JsonNode, or null if unsupported.</summary>
    public static JsonNode? StateValue(JsonElement e) => e.ValueKind switch
    {
        JsonValueKind.True => JsonValue.Create(true),
        JsonValueKind.False => JsonValue.Create(false),
        JsonValueKind.Number => JsonValue.Create(e.GetDouble()),
        JsonValueKind.String => JsonValue.Create(e.GetString()),
        _ => null,
    };
}
