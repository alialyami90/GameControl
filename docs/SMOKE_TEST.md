# GameCtl end-to-end test

One pass through everything: the phone app, the Bluetooth route, the Wi-Fi companion, the BeamNG mod, and the editor.
It takes about 60 minutes. Control, profile and screen names are written exactly as they appear on the phone.
Nothing in this script changes your BeamNG settings except one new input map file, which step C4 names.

**What to have ready**

- The Android phone or tablet (Android 9 or newer) with `GameCtl.apk` installed, on the same Wi-Fi as the PC.
- The PC with `GameCtl-companion-win-x64\GameCtl.Companion.exe`, Bluetooth, and BeamNG.drive installed.
- `gamecontroller.zip` (the BeamNG mod).
- Optional: vJoy (https://github.com/BrunnerInnovation/vJoy/releases), device 1 enabled with 128 buttons and all axes.
  Without it, the "vJoy" checks in B4 and C4 are skipped, everything else still runs.

**How to report.** Keep a note with the step number and what you saw for every step that did not match.
The script also asks you to copy a few lines from the BeamNG console; paste those in whatever they say.
A step that matches needs no note.

---

## Part 0: Install (5 min)

0.1 Copy `GameCtl.apk` to the phone and open it. Allow "Install unknown apps" for your file manager if asked.
    Open **GameCtl**: it runs in landscape and shows the profile list with five built-in profiles:
    **Generic gamepad**, **Keyboard keys**, **BeamNG.drive**, **BeamNG.drive (Bluetooth pad)**, **MudRunner**.

0.2 On the PC run `GameCtl.Companion.exe`. Allow it through the Windows firewall on private networks.
    It appears in the tray; its window shows a 6-digit PIN and the PC's IP address.

0.3 Copy `gamecontroller.zip` into `Documents\BeamNG.drive\<version>\mods\`. Don't start BeamNG yet.

---

## Part A: Bluetooth route, no PC software (10 min)

A1 On the phone tap the connection chip at the top right of the profile list, then **Bluetooth controller > Enable**.
   Allow the "Nearby devices" permission and allow the phone to be visible.
   If **Enable** says the phone does not support it, write that down and skip to Part B: everything else still works through the companion.

A2 On the PC: **Settings > Bluetooth & devices > Add device > Bluetooth**, pick **GameCtl Pad**. Windows pairs it.
   The phone's Bluetooth badge turns green and shows the PC's name.

A3 On the PC press `Win+R`, run `joy.cpl`, select **GameCtl Pad > Properties**. On the phone open **Generic gamepad**.
   - Tap **B1** to **B12**: the matching button lights in joy.cpl and goes out when you let go.
   - Tap **T13**: it stays lit on the phone and in joy.cpl; tap again, both go out.
   - Drag **Throttle** up and down: the Slider axis follows; let go, it stays where you left it.
   - Drag **Z axis** and let go: it springs back to centre.
   - **Mode** switch 1 / 2 / 3: a short press of button 17, 18, 19.
   - Turn **Dial** both ways: short presses of 20 and 21.

A4 Open Notepad on the PC and on the phone open **Keyboard keys**. Tap **Enter**, **Tab**, **Space**, the arrows, then **Copy** and
   **Paste** on some selected text: they act as the real keys. Hold **Push to talk** (F13): nothing visible, that is correct.

A5 Hold **B1** on the phone, press the phone's power button to lock the screen, unlock and reopen GameCtl.
   In joy.cpl button 1 is **not** lit (the app releases everything when it leaves the screen).

A6 Turn Bluetooth off on the PC. Within a few seconds the phone's badge turns red. Tap **B1**: both badges flash **NO LINK**
   once with a double tick, and nothing else happens. Turn Bluetooth back on; pick the PC under **Paired PCs** if it does not reconnect by itself within 10 seconds.

---

## Part B: Wi-Fi companion (10 min)

B1 On the phone open the connection chip, then **PC companion**. The PC appears in the list within 5 seconds (same Wi-Fi).
   If it does not, type the IP shown in the companion window.

B2 Tap the PC. Enter a wrong PIN first: the field turns red and says the PIN was wrong, the companion window shows a failed attempt,
   and the PIN on the PC does **not** change. Enter the right PIN: the sixth digit submits by itself, the phone says
   "Connected to <PC name>", and the companion's **Phones** list shows the phone.

B3 Open **Keyboard keys** on the phone with Notepad in front on the PC. Repeat A4: the same keys arrive, now through the companion.
   On the companion window the signal path lights up on each tap.

B4 (vJoy installed) In `joy.cpl` select **vJoy Device > Properties**. Open **Generic gamepad** on the phone with Bluetooth **off**
   on the phone (so the profile uses the companion). Repeat the A3 checks against the vJoy device.

B5 Hold **B1** on the phone and close the companion from its tray icon. The button goes out in joy.cpl, the phone shows the PC
   as disconnected within about 6 seconds, and a message says so. Start the companion again: the phone reconnects on its own
   without asking for the PIN.

B6 In the companion window **Forget** the phone. The phone drops to disconnected. Reconnect: it asks for the PIN again.

---

## Part C: BeamNG.drive (25 min)

Both links can stay up. Steps C1-C3 and C5-C12 use the **BeamNG.drive** profile, which has two pages, **Drive** and **4x4**
(the tabs at the top; on a small phone they are a < > pager). C4 uses **BeamNG.drive (Bluetooth pad)**.

**Where to look.** In BeamNG press the ` key (left of 1) to open the console and type `gameController` in its filter box.
The same lines are in `beamng.log` in the BeamNG user folder (**Options > Other > Open user folder**).

**Cars.** **Gavril D-Series** (4WD, low range, diff locks, ESC, cruise; its part-time transfer case means **Center diff** is
expected to be hidden), a police car for the light bar (for example the **Gavril Grand Marshal** police version), and a car
without ESC (for example the **Ibishu Pigeon**).

C1 **Connection.** Start BeamNG with the companion running. Within about 3 seconds a message in BeamNG says
   "Connected to the Game Controller companion", the companion window shows **BeamNG: connected**, and the phone's BeamNG
   profile shows the game tag. Close the companion: BeamNG says it disconnected. Start it again: it reconnects within about 3 seconds.

C2 **What the mod found.** Get into each of the three cars once. Each time the console shows a line that starts
   `vehicle <name>:` followed by `has.esc=…`, `has.cruise=…` and others. **Copy all three lines into your report**, whatever they say.
   (`unknown` means the mod couldn't tell, so the phone keeps that control visible.) On the phone, controls the car does not have
   are dimmed.

C3 **Game actions.** In the D-Series, on the **Drive** page: switch the gearbox to manual (in game, Q), then press **Shift up**
   and **Shift down**. Hold **Recover** for two seconds and let go: the car rewinds while you hold it. Then try **Reset**,
   **Slow-mo** (on, then off), hold **Look back**, and on the **4x4** page **Center cam**. Each one does what its keyboard key does.
   A console line "action failed in game engine Lua, running it in vehicle Lua (this is normal for some actions)" is fine.
   Write down any line that says "action failed in vehicle Lua" or "action not available".

C4 **Bind in game.** Open the **BeamNG.drive (Bluetooth pad)** profile, tap **Edit**, then **Bind in game**. A dialog lists every
   button and the BeamNG action it will get; confirm. About a second later a result row appears under the button:
   - "Bound N of N" with the device name means it worked.
   - "Saved N bindings … BeamNG could not confirm they are active" means the file was written but BeamNG doesn't see the
     controller yet. Check that vJoy is installed or the phone is paired over Bluetooth, then tap **Bind in game** again.
   - Tap **Bind in game** twice quickly: the second attempt shows "BeamNG is still writing the last request" and the button stays usable.

   In BeamNG close and reopen **Options > Controls** (it only refreshes when opened), pick the vJoy device or "GameCtl Pad", and
   check its buttons are bound. Tap **Done**, go back to play, and press **Horn** and **Hazard** in this profile: they work.
   In the user folder, `settings/inputmaps/` has a new file ending in `_gamecontroller.json`; your other files there are unchanged.

C5 **4WD, diff locks, range.** In the D-Series, on the **4x4** page of **BeamNG.drive**, press **4WD**, **Low range**,
   **Front diff** and **Rear diff**. The dash or the Drivetrain app (add it with **UI Apps**) shows each change, and the control's
   lamp on the phone follows. Turn **4WD** off and on again: it comes back in the mode it was in.

C6 **ESC.** In the D-Series, on the **4x4** page, press **ESC / TC** a few times: the game shows the ESC mode name at the top
   each time, and the phone's lamp is off for the mode that turns ESC off. In the Ibishu Pigeon the `vehicle …` line from C2
   must say `has.esc=unknown`, not `has.esc=true`.

C7 **Cruise control.** In the D-Series, on the **Drive** page, drive at about 50 km/h and press **Set**: the car holds its speed
   and **Cruise** lights up. Turn the **Cruise speed** dial one click each way: the target changes by 5 km/h. Press **Cruise** to turn it off.

C8 **Ignition and Start.** On the **Drive** page press **Ignition** to turn the engine off, then hold **Start**: the engine cranks
   and starts, and stops cranking when you let go. The console shows `starter: mainController.setStarter` or
   `starter: ignition level fallback`. Write down which one.

C9 **Rest of the Drive page.** **Low beam**, **High beam**, **Fog**, **Light bar** (police car), **Left**, **Right**, **Hazard**,
   **Horn**, **Park brake**, the **Gearbox** switch (P/R/N/D/S/M), **Cam prev**, **Cam next**, **Pause**. Each one works and its lamp
   follows the game. Change something from the keyboard instead (for example H for the lights): the phone's lamp follows within a second.

C10 **Changing cars.** Switch car with **Ctrl+E**, spawn a new car from the vehicle menu, and reload the current one with **Ctrl+R**.
    After each, the phone's lamps match the new car within a second.

C11 **Nothing stays pressed.**
    - Hold **Horn**, and while holding it switch car with **Ctrl+E**: the first car's horn stops.
    - Hold **Recover** in one car, and while holding it switch car with **Ctrl+E**: the first car stops rewinding.
    - Hold **Horn** and spawn a new car from the vehicle menu: no horn is left on.
    - Hold **Horn** and close the companion: the horn stops.
    - Hold **Horn** and switch the phone's Wi-Fi off: the horn stops within about 8 seconds.

C12 **Low frame rate.** Press **Ctrl+F** to show the frame rate and get it under 20 fps (spawn a lot of traffic, or raise the graphics
    settings). Drive for two minutes. The phone's speed and lamps keep updating, and the console does not show
    "vehicle Lua went quiet, reloading the vehicle part".

C13 **OutGauge fallback (no mod).** Disable the mod in BeamNG's mod manager, enable **Options > Other > OutGauge** (IP 127.0.0.1,
    port 4444) and restart BeamNG. In the **BeamNG.drive** profile the high beam, signal, handbrake and gear lamps still follow the
    game; the game-command buttons are dimmed. Enable the mod again afterwards.

---

## Part D: Both routes at once (5 min)

D1 With Bluetooth **and** the companion connected, open **Generic gamepad** (its output is **Auto**: Bluetooth when paired).
   In `joy.cpl` the presses arrive on **GameCtl Pad**, not on vJoy.

D2 Hold **Throttle** half way, then turn Bluetooth off on the PC. The GameCtl Pad axis goes to minimum, and the same value
   continues on vJoy (if installed) without any button being tapped. Turn Bluetooth back on: the slider carries on over Bluetooth.

D3 Set the **Mode** switch to 2, turn Bluetooth off, move it to 3: joy.cpl (vJoy) shows one press of button 19 and **no** press of 17.

D4 Open **BeamNG.drive**: it always uses the companion (its output is **PC companion**), even with Bluetooth connected.

---

## Part E: Editor and profiles (5 min)

E1 On the profile list tap **New profile**, name it, pick **Custom**. It opens in the editor with an empty 8x4 page.

E2 Add a **Button**, a **Toggle**, a **Switch**, a **Slider** and a **Dial** from the palette. Drag each to a new place; drag the corner
   handle to resize (on a phone-width screen use the Width/Height steppers in the inspector). A switch can't be narrower than its
   number of positions and a dial is always 2x2.

E3 Select the button, set its action to **Key > F5** and its icon from the library. Set the toggle to **Gamepad button 3**.
   Add a second page, rename it, delete it, then **Undo** from the snackbar.

E4 Press the phone's **Back** button: the editor asks whether to discard. Choose to stay, tap **Done**. Open the profile: the
   controls work through whichever link is up (F5 in Notepad, button 3 in joy.cpl).

E5 Open the profile's menu and **Export**: a `.gcprofile.json` file is saved. **Import** it back: a copy appears with a new name.

E6 Open **BeamNG.drive > Edit**, move one control, then open the profile's menu and **Restore original**: the built-in layout comes
   back. Duplicate it first if you want to keep your edits.

---

## Report

Send:
1. The three `vehicle …` lines from C2.
2. Which `starter:` line you saw in C8.
3. For every step that did not match: the step number, what happened, and (for Part C) the console lines around it.
4. The phone model and Android version, and whether Bluetooth HID worked (A1).
