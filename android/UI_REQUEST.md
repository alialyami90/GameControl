# Screens the Lead developer needs from the UI designer (v2, matches MainActivity.kt)

All stateless composables in com.gamecontroller.ui.screens, taking core/model types plus lambdas.
MainActivity.kt already calls these exact signatures, so please match names and parameter order.

```kotlin
@Composable fun ProfilesScreen(
    profiles: List<Profile>, connection: ConnectionUi,
    onOpen: (String) -> Unit, onCreate: (String, GameId) -> Unit, onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,            // on a builtIn profile this means "restore original layout"
    onImport: () -> Unit, onExport: (String) -> Unit, onOpenConnection: () -> Unit,
)

@Composable fun ConnectScreen(
    connection: ConnectionUi, servers: List<DiscoveredServer>, hid: HidUi,
    onConnect: (host: String) -> Unit, onSubmitPin: (String) -> Unit, onDisconnect: () -> Unit,
    onRescan: () -> Unit,
    onHidEnable: () -> Unit,               // asks permission + makes the phone discoverable for pairing
    onHidConnect: (address: String) -> Unit, onHidDisconnect: () -> Unit,
    onBack: () -> Unit,
)
// Two panels: "Bluetooth controller (no PC software)" showing hid.status / hostName / bondedHosts / message,
// with short pairing steps (Windows Settings > Bluetooth > Add device > "GameCtl Pad"); and
// "PC companion (Wi-Fi)" with discovered servers, manual IP field, PIN entry when status == PAIRING,
// capabilities chips, latency.

@Composable fun PlayScreen(state: PlayState, onControlValue: (String, Float) -> Unit,
    onRotaryStep: (String, Int) -> Unit, onPageChange: (Int) -> Unit, onEdit: () -> Unit, onBack: () -> Unit)
// HUD shows both links: state.hid (Bluetooth) and state.connection (Wi-Fi).

@Composable fun EditorScreen(
    state: EditorState,
    beamngCommands: List<String>,          // commands reported by the BeamNG mod (may be empty) for the command picker
    onSelect: (String?) -> Unit, onAddControl: (ControlType, Int, Int) -> Unit,
    onMove: (String, Int, Int) -> Unit, onResize: (String, Int, Int) -> Unit, onDelete: (String) -> Unit,
    onUpdate: (Control) -> Unit, onAddPage: () -> Unit, onRenamePage: (Int, String) -> Unit,
    onDeletePage: (Int) -> Unit, onPageChange: (Int) -> Unit,
    onGridChange: (columns: Int, rows: Int) -> Unit,   // current page grid, 4..12 x 2..8
    onProfileChange: ((Profile) -> Profile) -> Unit,   // rename profile, change game, change output route
    onApplyGameBindings: () -> Unit,       // show when profile.game == BEAMNG: "Bind in game"
    onDiscard: () -> Unit, onDone: () -> Unit,
)
```

Inspector fields: label, icon (IconPicker), accent, size; Action editor for action / positions[i].action / cw / ccw
covering none, key combo (HidKeys.ALL_NAMES from com.gamecontroller.hid) + trigger (hold/tap/press),
gamepad button (VJoyButton: device, button 1-128; note buttons 1-64 also work over Bluetooth),
gamepad axis (VJoyAxis: axis X Y Z RX RY RZ SL0 SL1, range), macro steps, BeamNG command (free text + beamngCommands
suggestions), XInput button/axis. Plus feedbackKey, visibleWhen, positionKey + per-position stateValue,
slider springReturn / range / vertical, and `bindTo` (BeamNG input action name for "Bind in game", only meaningful
with a gamepad-button action). Profile settings: name, game, output route (Auto / Bluetooth only / PC companion only).

Icons: IconLibrary ids are referenced by core/profiles/BuiltInProfiles.kt.
