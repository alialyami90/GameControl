# Lead developer: round 3 replies (Android)

## MUST
- A28 Fixed. `InputEngine.onValue` parks on the old route only for MOMENTARY and SLIDER (`parkable` guard); switch/radio/latching continue on the new route untouched. PROTOCOL.md route-change paragraph now says so. Test `a28_switchRouteFlipDoesNotTapPositionZeroOnCompanion` (gp_mode, companion → HID flip, no companion event).
- A24 Closed by A28.

## SHOULD
- A29 Fixed. `syncValue` returns early unless the value is a release, so a game-initiated ON never presses a HID button; the doc comment is updated. Test `a29_gameInitiatedOnDoesNotPressHidButton`.
- A30 / U28 (VM side) Fixed. `applyGameBindings` sets `bindResult` to busy with an empty message when the request goes out and starts a 10 s timeout that turns it into a red "No answer from BeamNG. Is the game running with the mod enabled?". The mod's reply cancels the timeout; a `busy:true, ok:false` reply keeps `busy=true` but carries the message "BeamNG is still writing the last request. Try again in a second." so the UI shows it as a rejection, not progress. Preview and apply read the editor's unsaved profile first (`bindProfile`), then the repository. The UI designer handles the button and row rendering.
- A31 Fixed. `onControlValue` only counts a dropped input when the value is not a release (`InputEngine.isRelease`), so the NO LINK flash fires once per press or slider move, not on releases.
- A27 Fixed. With `-PcoreOnly=true`, `settings.gradle.kts` switches the root build file to `build-core.gradle.kts`, which declares only `kotlin.jvm` and `kotlin.plugin.serialization`, so `./gradlew -PcoreOnly=true :core:test` resolves nothing from Google's Maven. The normal root file is unchanged (the Android plugin must share Kotlin's classpath).
- A16, A18, A19, A25, A26 Still open; carried to the SHOULD backlog for after the first APK.
