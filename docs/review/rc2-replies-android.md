# RC2 replies: Android, CI, docs, protocol (Lead developer)

- **M1** Fixed. `CompanionClient.disconnect()` and `lost()` now set DISCONNECTED / CONNECTING / ERROR before calling `onDisconnected`. Also `onDisconnected` now calls the new `InputEngine.onCompanionLost()`, which forgets companion holds whatever `companionConnected` reads, so the order can't regress. Test: `m1_companionLossForgetsLatchesEvenWhileLinkStillReadsUp`.
- **M2** Fixed. The guard picks one build-tools dir (`ls -d … | sort -V | tail -1`), runs apksigner, and compares "Signer #1 certificate SHA-256 digest" to the pinned release fingerprint (checked against the keystore with keytool). No DN matching. INSTALL says the fingerprint is pinned.
- **A18** Fixed. RELEASE_NOTES Known limits: "Keep GameCtl on screen while playing. Leaving it releases every control."
- **A19-a** Fixed. `nameOf` uses discovery only; the `lastServerName` fallback is gone, so a bad_token from a new PC at the old IP can't clear the old PC's named token.
- **A19-b** Fixed. Auto-follow counts DISCONNECTED, ERROR, SEARCHING and CONNECTING (when not connected) as idle.
- **A19-c** Fixed. Every incoming message refreshes liveness, not only pongs.
- **A25** Fixed by the UI designer (rc2-replies-ui.md).
- **R6-2 follow-up** Comment changed: shared jobs keep one number across machines; jobs only one machine has use 17-32 and may share a number with another machine's own job (24, 25/26), since each machine is bound on its own. INSTALL says the same. Per-page uniqueness is still tested.
- **R6-4** Fixed. `import` reads `schema` with `parseToJsonElement` before decoding. Comment updated (the betas still show a parse error; 1.0 onward shows "newer version"). Test: `r6_4_newerSchemaIsRefusedBeforeDecoding`.
- **Signing fallback** Fixed. Alias uses `takeIf { it.isNotBlank() } ?: "gamectl"`; new optional `GAMECTL_KEY_PASSWORD`, else the store password. Documented in INSTALL.
- **versionCode** Now 3 (betas were 1).
- **B-6** Fixed. PROTOCOL.md is titled "GameCtl protocol, version 1".
- **W-7 docs** INSTALL and SMOKE_TEST now say the window "GameCtl Companion". **W-3/W-5 docs** INSTALL mentions `companion-second.log` and that the PIN is masked.

## RC3
- **A-1** Fixed. RELEASE_NOTES: "Keep GameCtl on screen while playing. Leaving it lets go of held buttons and sliders; toggles stay on."
- **A-2** Fixed. The Welcome handler saves the PC's address as `lastServerHost`. Auto-follow fires only when `host` is null or equals `lastServerHost`, meaning the app is retrying that PC's old address. If the user picks PC B, `host` is B, so the last PC showing up in discovery no longer pulls the app back. (I keyed this on the welcomed address rather than `lastHost`, because `connect()` sets `lastHost` to B as soon as it's tapped.)
