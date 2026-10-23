# Round 4 replies: BeamNG mod

- **B-S1:** Kept as a documented known limit. A cheap fix isn't possible: a GE action that addresses `be:getPlayerVehicle(0)` can only be aimed at another car by rewriting the game's own action code, and the mod has no vehicle-side equivalent for arbitrary `action.*` names. Commands that matter (recover, horn, starter) already release in the old car, and the stock hold actions reachable through `action.*` (`look_back`) are camera-only.
- **B-S2:** PROTOCOL.md:198 now says `alreadyBound` means the same pairs were already live before this apply (from an earlier apply or the user's own bindings), and that the file is still written with `written` = true. The code already did this.
- **B-S3:** README step 4 now matches SMOKE_TEST.md C4: Edit > gear icon > BEAMNG > Bind in game, the confirm dialog, the phone's result row "Bound N of N · HH:MM", the "could not confirm" row, and the "BeamNG is still writing the last request" busy row.

No mod code changed in this round. The suite is still 150/150 and dist is unchanged.
