# UI contract (UI designer → Lead developer)

Visual direction: **Race Front-End**, chosen by ALI. Slanted plates, italic condensed numerals, one electric magenta accent on a blue-black ground.

## Entry points
- `com.gamecontroller.ui.theme.GcTheme { ... }` wraps the whole app (dark scheme, Barlow type scale, shapes).
- Screens (`com.gamecontroller.ui.screens`): `ProfilesScreen`, `ConnectScreen`, `PlayScreen`, `EditorScreen`, `IconPicker`, signatures exactly as in UI_REQUEST.md v2.
- Reusable: `ControlView` (any control type, `interactive=false` for previews), `LayoutMiniature(page)`, `PageTabs`, `SlantButton`, `SlantTabs`, `LinkBadge`/`BluetoothLinkBadge`/`PcLinkBadge`.

## Files the UI owns
- `ui/theme/`: `Color.kt` (palette object `Race`, `Accent.lit()`), `Type.kt` (`RaceTypography`, `RaceType`), `Shapes.kt` (`SlantShape`, `CapShape`), `Theme.kt`, `Motion.kt`, `Fonts.kt` (R.font refs).
- `ui/components/`: `ControlCap.kt` (cap + racing-stripe latch band), `CapFace.kt`, `Controls.kt` (momentary, latching, selector switch, radio keys, axis slider, rotary knob), `Hud.kt`, `Slant.kt`, `Form.kt`.
- `ui/screens/`: the five screens plus `Inspector.kt`, `ActionEditor.kt`.
- `ui/icons/IconLibrary.kt`: `IconLibrary[id]` → `ImageVector?`, `IconLibrary.entries`, `categories`, `search()`.
- `res/font/`: Barlow + Barlow Condensed (incl. italics), OFL licence in `android/FONT_LICENSE_OFL.txt`.

## Behaviour the screens rely on
- Play: momentary sends 1 on finger down and 0 on up; latching sends the toggled value on release; selector/radio send the position index; slider sends continuous values and the rest value on release when `springReturn`; rotary calls `onRotaryStep(±1)` per 15° or per tap on the left/right half.
- Indicators: `IndicatorState.lit` wins over the local value; `source = PENDING` draws marching slashes; selector/radio pick the active position from `IndicatorState.text` matching `Position.stateValue`.
- `PlayState.unavailable` controls are dimmed, dash-outlined and ignore input.
- HUD readouts appear only when `game` has `speed` (m/s), `rpm` or `gear`, so non-BeamNG games get a clean strip.
- Editor: add uses the first free cell; drag a control to move (snaps to cells, one `onMove` on release); drag the magenta corner tab to resize (one `onResize`); tapping empty grid deselects. Discard asks for confirmation only when `dirty`.
- Profiles: deleting a custom profile asks for confirmation; on a built-in it calls `onDelete` directly (restore original).

## Icon ids
- Lights: `low_beam`, `high_beam`, `fog_light`, `light_bar`, `hazard`, `signal_left`, `signal_right`, `interior_light`
- Driving: `ignition`, `starter`, `horn`, `cruise`, `parking_brake`, `esc`, `esc_off`, `tcs`, `abs`, `wiper`, `steering`, `fuel`, `engine`
- Drivetrain: `gearbox`, `gear_up`, `gear_down`, `diff_lock`, `differential`, `awd`, `low_range`, `suspension`, `winch`, `trailer`, `jack`
- Camera & tools: `camera`, `camera_cycle`, `look_back`, `photo`, `map`, `recover`, `reset`, `replay`, `slowmo`, `pause`
- Machines: `blade`, `blade_tilt`, `blade_angle`, `ripper`, `float_mode`, `bucket`, `quick_coupler`, `boom`, `stick`, `swing`, `tracks`, `hook`, `boom_extend`, `outriggers`, `beacon`, `work_light`, `plough`, `spreader`, `tow`, `direction`
- General: `gamepad`, `keyboard`, `power`, `bolt`, `flag`, `speed`, `timer`, `place`, `navigation`, `chat`, `mic`, `mic_off`, `volume_up`, `volume_down`, `music`, `screenshot`, `menu`, `settings`, `home`, `star`, `build`, `play`, `stop`, `fast_forward`, `skip_next`, `skip_previous`, `zoom_in`, `zoom_out`, `visibility`, `lightbulb`, `inventory`, `target`, `shield`, `radio`, `arrow_up`, `arrow_down`, `arrow_left`, `arrow_right`, `plus`, `minus`

## Joystick (ControlType.JOYSTICK)
ControlView takes `valueY` / `onValueY` for the forward-back axis. PlayScreen maps a joystick to "<id>.x" / "<id>.y" (Joystick.X / Joystick.Y) for both values and onControlValue, and sends 0 on both when it's released, cancelled or disposed. An unknown icon id renders the control's label instead of an icon.
