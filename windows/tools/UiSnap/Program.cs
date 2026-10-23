using GameController.Companion.Engine;
using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Text;
using Avalonia;
using Avalonia.Headless;
using Avalonia.Threading;
using GameController.Companion;
using GameController.Companion.App;
using GameController.Companion.App.Services;
using GameController.Companion.App.ViewModels;
using GameController.Companion.App.Views;
using GameController.Companion.Config;

// UiSnap <out dir>: screenshots of every page in two states (first run, and a live session with
// a simulated phone and BeamNG mod). The data is synthetic.
var outDir = args.Length > 0 ? args[0] : "snaps";
Directory.CreateDirectory(outDir);

AppBuilder.Configure<App>()
    .UseSkia()
    .UseHeadless(new AvaloniaHeadlessPlatformOptions { UseHeadlessDrawing = false })
    .SetupWithoutStarting();

static int FreeTcp() { var l = new TcpListener(IPAddress.Loopback, 0); l.Start(); int p = ((IPEndPoint)l.LocalEndpoint).Port; l.Stop(); return p; }
static int FreeUdp() { using var u = new UdpClient(0); return ((IPEndPoint)u.Client.LocalEndPoint!).Port; }

void Pump(int ms)
{
    var until = DateTime.UtcNow.AddMilliseconds(ms);
    while (DateTime.UtcNow < until)
    {
        Dispatcher.UIThread.RunJobs();
        AvaloniaHeadlessPlatform.ForceRenderTimerTick();
        Thread.Sleep(10);
    }
}

void Snap(MainWindow w, string name)
{
    // Headless has no render loop for self-animating controls; ask them to redraw.
    foreach (var v in Avalonia.VisualTree.VisualExtensions.GetVisualDescendants(w).OfType<GameController.Companion.App.Controls.SignalPath>()) v.InvalidateVisual();
    Pump(150);
    var frame = w.CaptureRenderedFrame();
    var path = Path.Combine(outDir, name + ".png");
    frame?.Save(path);
    Console.WriteLine(path);
}

var store = new ConfigStore(Path.Combine(Path.GetTempPath(), $"uisnap-{Guid.NewGuid():N}.json"));
var c = store.Config;
c.ServerName = "RIG-PC";
c.WsPort = FreeTcp(); c.UdpPort = FreeUdp(); c.DiscoveryPort = FreeUdp(); c.BeamNgPort = FreeTcp(); c.OutGaugePort = FreeUdp();
c.EnableMdns = false;
var platform = PlatformFactory.Create(c);
var pads = new SnapPads();
var host = new CompanionHost(store, platform.Router, new HostOptions { Identity = platform.Options.Identity, BluetoothPads = pads });
var vm = new MainViewModel(host, platform);
host.Pairing.UsePin("482913");
host.Start();

var win = new MainWindow { DataContext = vm, Width = 1120, Height = 760 };
win.Show();
Pump(300);

// 1. First run
Snap(win, "01-status-first-run");
vm.GoCommand.Execute("phones"); Snap(win, "02-phones-empty");
vm.GoCommand.Execute("outputs"); Snap(win, "03-outputs");
vm.GoCommand.Execute("settings"); Snap(win, "04-settings");
vm.GoCommand.Execute("status");

// 1b. The phone connects over Bluetooth only (controls work, Wi-Fi not linked yet).
pads.Now.Add(new BluetoothPad("pad-1", "GameCtl Pad"));
host.ScanBluetooth(); Pump(300);
Snap(win, "12-status-bluetooth-only");
vm.GoCommand.Execute("phones"); Snap(win, "13-phones-bluetooth-only");
vm.GoCommand.Execute("status");
// From here on the same phone also links over Wi-Fi, so live shots show both links.

// 2. Live: phone pairs, mod connects, inputs flow.
var ws = new ClientWebSocket();
Task Send(string s) => ws.SendAsync(Encoding.UTF8.GetBytes(s), WebSocketMessageType.Text, true, CancellationToken.None);
async Task Phone()
{
    await ws.ConnectAsync(new Uri($"ws://127.0.0.1:{c.WsPort}/ws"), CancellationToken.None);
    await Send("""{"v":1,"type":"hello","deviceId":"pixel-8","deviceName":"Pixel 8","appVersion":"1.0.0","pin":"482913"}""");
    var buf = new byte[8192];
    await ws.ReceiveAsync(buf, CancellationToken.None);
    _ = Task.Run(async () => { try { while (true) await ws.ReceiveAsync(buf, CancellationToken.None); } catch { } });
    await Send("""
    {"v":1,"type":"profile.sync","profileId":"BeamNG · Trail rig","game":"beamng","bindings":[
     {"id":"hazards","kind":"latching","action":{"type":"beamng","command":"hazard"}},
     {"id":"horn","kind":"momentary","action":{"type":"beamng","command":"horn"}},
     {"id":"diff_lock","kind":"latching","action":{"type":"vjoy_button","device":1,"button":5}},
     {"id":"low_range","kind":"latching","action":{"type":"key","keys":["LCTRL","R"],"trigger":"tap"}},
     {"id":"camera","kind":"rotary","cw":{"type":"key","keys":["C"],"trigger":"tap"},"ccw":{"type":"key","keys":["LSHIFT","C"],"trigger":"tap"}},
     {"id":"hand_throttle","kind":"slider","action":{"type":"vjoy_axis","device":1,"axis":"SL0","range":"unipolar"}}]}
    """);
}
Task.Run(Phone).Wait();
var mod = new TcpClient();
mod.Connect(IPAddress.Loopback, c.BeamNgPort);
var mw = new StreamWriter(mod.GetStream()) { AutoFlush = true, NewLine = "\n" };
mw.WriteLine("""{"v":1,"type":"hello","role":"beamng","modVersion":"1.0.0","gameVersion":"0.35"}""");
mw.WriteLine("""{"v":1,"type":"commands","list":["hazard.toggle","hazard","horn","reset"]}""");
mw.WriteLine("""{"v":1,"type":"state","full":true,"values":{"hazard":false,"gear":"2","speed":8.3}}""");
Pump(600);

long seq = 1;
Task Input(string id, double v) => Send($$"""{"v":1,"type":"input","s":{{seq++}},"id":"{{id}}","val":{{v}}}""");
Input("diff_lock", 1).Wait(); Pump(120);
Input("low_range", 1).Wait(); Pump(120);
Input("camera", 1).Wait(); Pump(120);
Input("hand_throttle", 0.4).Wait(); Pump(120);
Input("hazards", 1).Wait();
mw.WriteLine("""{"v":1,"type":"state","values":{"hazard":true}}""");
Pump(60);
Input("diff_lock", 0).Wait();
Pump(110); // mid-spark
Snap(win, "05-status-live");
Pump(1500);
Snap(win, "06-status-live-settled");
vm.GoCommand.Execute("phones"); Snap(win, "07-phones-live");
vm.GoCommand.Execute("outputs"); Snap(win, "08-outputs-live");
vm.GoCommand.Execute("log"); Snap(win, "09-log");

// Smallest supported window
vm.GoCommand.Execute("status");
win.Width = 820; win.Height = 560;
Pump(300);
Snap(win, "10-status-min-size");
vm.GoCommand.Execute("phones"); Pump(200); Snap(win, "11-phones-min-size");

host.Dispose();
return 0;

sealed class SnapPads : IBluetoothPads
{
    public List<BluetoothPad> Now { get; } = new();
    public IReadOnlyList<BluetoothPad> Present() => Now.ToList();
}
