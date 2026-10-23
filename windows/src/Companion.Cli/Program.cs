using GameController.Companion;
using GameController.Companion.Config;
using GameController.Companion.Input;
using GameController.Companion.Windows;

// Console host: runs the full companion without the tray UI. On Windows it injects real input;
// elsewhere (or with --dry-run) it prints what it would inject, for end-to-end tests.
var argv = args.ToList();
string? Opt(string name)
{
    int i = argv.IndexOf(name);
    return i >= 0 && i + 1 < argv.Count ? argv[i + 1] : null;
}
bool Flag(string name) => argv.Contains(name);

if (Flag("--help") || Flag("-h"))
{
    Console.WriteLine("""
        GameCtl Companion (console)

          --config <path>   settings file (default: %APPDATA%\GameController\companion.json)
          --pin <6 digits>  use a fixed pairing PIN instead of a random one
          --name <name>     PC name shown to phones
          --dry-run         print input instead of injecting it (always on outside Windows)
          --no-mdns         do not advertise over mDNS
          --list-hid        list HID devices with VID/PID and exit (Windows)
        """);
    return 0;
}

if (Flag("--list-hid"))
{
    if (!OperatingSystem.IsWindows()) { Console.WriteLine("--list-hid needs Windows"); return 1; }
    foreach (var d in HidDeviceIdentity.Enumerate()) Console.WriteLine($"{d.VidPid}  {d.Product}");
    return 0;
}

var store = new ConfigStore(Opt("--config"));
if (Opt("--name") is { } name) store.Config.ServerName = name;
if (Flag("--no-mdns")) store.Config.EnableMdns = false;

bool dry = Flag("--dry-run") || !OperatingSystem.IsWindows();
InputRouter router;
HostOptions options;
IDisposable[] owned = Array.Empty<IDisposable>();
if (dry || !OperatingSystem.IsWindows())
{
    var rec = new RecordingOutputs { OnEvent = s => Console.WriteLine($"  INJECT {s}") };
    router = new InputRouter(rec, rec, rec);
    options = new HostOptions();
}
else
{
    var w = WindowsPlatform.CreateRouter(store.Config);
    router = w.Router;
    options = WindowsPlatform.Options();
    owned = new IDisposable[] { w.VJoy, w.Vigem };
    Console.WriteLine($"vJoy: {w.VJoy.Status}. ViGEm: {w.Vigem.Status}.");
}

using var host = new CompanionHost(store, router, options);
host.Logged += e => Console.WriteLine($"{e.At:HH:mm:ss} {e.Level,-5} {e.Message}");
router.Activity += a => Console.WriteLine($"{a.At:HH:mm:ss.fff} input {a.Source} / {a.ControlId}: {a.What}");
host.Pairing.PinChanged += () => Console.WriteLine($"Pairing PIN is now {host.Pairing.Pin}");
if (Opt("--pin") is { } pin) host.Pairing.UsePin(pin);
host.Start();
foreach (var s in host.Services) Console.WriteLine($"  {s.State,-7} {s.Name}: {s.Detail}");
Console.WriteLine($"Pairing PIN {host.Pairing.Pin}");

var done = new TaskCompletionSource();
Console.CancelKeyPress += (_, e) => { e.Cancel = true; done.TrySetResult(); };
AppDomain.CurrentDomain.ProcessExit += (_, _) => done.TrySetResult();
await done.Task;
Console.WriteLine("Stopping; releasing held input.");
host.Dispose();
foreach (var o in owned) o.Dispose();
return 0;
