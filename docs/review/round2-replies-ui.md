# Round 2 replies: UI (UI designer)

Screenshots, all rendered from the current sources, are in .impeccable/review/: editor_small.png and editor_small_sel.png (640×360), editor_full.png, connect_pin.png (portrait, Bluetooth unsupported), connect_pin_wide.png, connect_done.png, play_small.png, plus the existing set.

## MUST
- U11 Goes to the companion developer. DESIGN.md (frontmatter tokens) and .impeccable/design.json (motion, component CSS) are the source of truth. The Windows surface brief now says Race Front-End too. I'll answer any token questions they send.
- U12 Fixed. Restoring a built-in profile now opens the dialog with your copy ("Restore the original … layout? Your changes on this phone will be lost. Duplicate it first to keep them."), with Restore and Duplicate instead. Deleting a control or a page takes effect at once and shows a snackbar with Undo, which restores the whole profile through onProfileChange { before }.
- U13 Fixed. Below 840dp the palette becomes a 56dp icon rail (names stay in the accessibility label). The inspector, or the page and profile settings opened from a new gear button in the top bar, slides over the canvas from the right (max 340dp, 80% of the width) over a scrim that closes it. At 640dp the canvas gets about 560dp, so an 8-column page has about 60dp cells. The Editing tag is hidden on narrow screens.
- U14 Fixed. firstFreeCell returns null on a full page. Palette items are then disabled (with a "Page is full" state description), and a strip above the canvas says "Page is full: add a page or add rows." with an Add a row button (calls onGridChange with rows + 1, up to 8). Nothing stacks at (0,0) any more.
- U15 Fixed for SlantTabs: every tab is a 48dp-tall target, the plate stays 36dp, and labels ellipsize (max 160dp). SlantButton puts the click on the outer 48dp box, with a pressed state on the plate. The resize handle is a 48dp target around a small plate. The gp_mode 46dp segments and 1×1 switches are core data, so they need minSpan in core (I can see the BeamNG cruise knob is already 2×2, thanks).

## SHOULD
- U16 Partly done. Both HUD link badges are one 48dp target that opens Connect via a new optional PlayScreen parameter, `onOpenConnection: (() -> Unit)? = null`. Please pass it. The "NO LINK" red flash needs a signal from you: expose something like PlayState.droppedAt (a timestamp or a counter) and I'll flash the badge for 600ms with a double haptic.
- U17 Fixed. The wrong-PIN message is now the field's isError and supporting text, not the panel header. The PIN shows in readout type grouped "482 913" with a NumberPassword keyboard. Pair enables at 6 digits and the sixth digit submits automatically. A disabled PRIMARY SlantButton now draws as a faint outline everywhere, not 40% alpha magenta.
- U18 Not done. It needs a result from the VM, e.g. EditorState.bindResult (ok, bound, total, device, message, time). Give me that field and I'll add the confirm sheet and the persistent result row.
- U19 Fixed. Pending is now a hollow outlined band with sparse slashes, and confirmed stays a solid stripe, so a still frame (animations off) is unmistakable.
- U20 Fixed. The profiles grid has 120dp of bottom padding, so the New profile button no longer covers the last card.
- U21 Needs you. Back handling lives in MainActivity. I recommend system Back behaves like X: ask to discard when dirty, with Done as the only save. I can add a BackHandler inside EditorScreen if you'd rather keep it in the UI layer (it isn't in my desktop harness, which is why I haven't).
- U22 Partly done. The HUD, its row, LinkBadge, StateTag and the editor top bar use heightIn(min) instead of fixed heights. Chips are unchanged.
- U23 Fixed. The host IP is InkMuted. Host rows are on PanelRaised with a seam, so CONNECT in electric is 4.77:1. Inspector "NONE" is InkMuted.
- U24 Fixed. With Bluetooth unsupported, the PC panel leads and Bluetooth shrinks to one line under it. Connected Bluetooth now shows the PC name and what Windows gets (gamepad, keyboard) instead of an empty panel. Both Disconnect buttons are QUIET.
- U25 Not done. It needs the live key set from the companion in EditorState (e.g. stateKeys: List<String>). With that I'll turn the three Game link fields into pickers with free text as the fallback.
- U26 Fixed: Readout merges for TalkBack; BUSY lamps use Amber, not Yellow; tab labels ellipsize; the HUD hides the game tag when the profile name already says it. Strings are still hard-coded (left for after v1).
- U27 Windows, so the companion developer.

## The three questions
1. Connection is the first thing the Profiles header shows: both link badges, as one target that opens Connect. I'd rather not gate Profiles on a green lamp, because building layouts offline is a real job. A stronger option is a first-run empty state that leads with Connect.
2. No. Once the companion relays the key set (U25), a BeamNG profile should offer a picker, with free text only as an "advanced" fallback.
3. The gear. It's the only electric element that changes while driving. The active tab is electric but small and only changes when the driver acts. A control lit electric is the user's choice and is identified by its stripe and icon, not by the accent.

## Follow-up after the Lead developer's data hooks
- U16 Done. PlayHud watches PlayState.linkDrops. On a change, both link badges turn red for 600ms with "NO LINK" (red seam, red lamp; TalkBack hears "Press not sent: no link"), with a double haptic tick. The badges open Connect via onOpenConnection.
- U18 Done. EditorScreen has a new optional parameter `onPreviewGameBindings: (() -> Pair<String, List<Pair<String, Int>>>)? = null`. When passed, Bind in game first opens a confirm dialog ("Bind N controls in BeamNG?", the device, and each button → action), and Bind calls onApplyGameBindings. The button is disabled unless EditorState.canBindInGame (the hint says why) and shows "Binding…" while bindResult.busy. A persistent result row stays under it: lamp green, amber (partial or unknown actions) or red; "Bound count of requested · HH:mm" or "Already bound"; the device; unknown actions; the mod's message on failure; and "file written, restart BeamNG" when ok=false and written=true. See .impeccable/review/editor_bind_result.png.
- U21 Done. ui/screens/EditorBack.kt wraps androidx.activity.compose.BackHandler. System Back in the editor now does what X does: it asks to discard when dirty and never saves.
- U25 Done. New ui/components/KeyField.kt is a field with a dropdown of EditorState.stateKeys, filtered by what's typed (the "Only available when" field prefers has.* keys). With no live keys the picker is greyed and the hint says to start the game with the companion linked. Free text still works.
