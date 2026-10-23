# 1.0 release candidate 3 review (dev 52b6f47, CI run 39892680845 green)

**Verdict: signed off for 1.0. 0 open comments.**

Tests pass:

| Part | Result |
|---|---|
| Android core | 33/33 |
| Windows | 134/134, and 141/141 with reviewer probes |
| BeamNG mod | 182/182 on 5 runs |

The mod zip is identical to source. The Release build of the companion has 0 warnings.

## RC2 items
| Item | Status | Evidence |
|---|---|---|
| M4 duplicate key crashes the companion | Fixed (verified) | The parse step catches all exceptions (CompanionConfig.cs:147) and only the read step retries IO. A probe with "serverName" twice sets the file aside. Test: A_repeated_key_is_treated_as_unreadable_not_a_crash. |
| A-1 release notes wording | Fixed (verified) | RELEASE_NOTES.md:48: "Leaving it lets go of held buttons and sliders; toggles stay on." This matches releaseAll. |
| A-2 auto-follow overrides the user's pick | Fixed (verified) | Keyed on the persisted lastServerHost (CompanionClient.kt:152, 396-397). The tap-B-while-retrying case doesn't follow, and a missing value stays safe. |
| W-8 retry length | Accepted, documented | CompanionConfig.cs:119-121: "6 reads, 1.5 s of waits in all … a lock that outlasts it leaves the file untouched (WriteBlocked) instead." |
| W-9 save note | Fixed (verified) | "Not saved: " plus the banner text when WriteBlocked is set (MainViewModel.cs:533-537). |
| W-10 second-instance log path | Fixed (verified) | MainViewModel.cs:79 |
| W-11 misplaced doc comment | Fixed (verified) | CompanionHost.cs:241-247 |
| B-3 quick retry after the second Ctrl+R | Fixed (verified) | `attachTries = 0` when the car answers (gameControllerLink.lua:367). Removing that line fails the test "Ctrl+R 2". |
| B-8 socket warning untested | Fixed (verified) | Breaking socketWarned fails "socket failure warned once". |

No regressions found in the RC3 diff.

## Accepted limits, all stated in user docs
- **The app must stay on screen**, and toggles stay on when you leave it (RELEASE_NOTES).
- **The app's text is English only** (RELEASE_NOTES).
- **BeamNG: a held game-engine action** is released in the car you switched to (beamng-mod README Known limits, RELEASE_NOTES).
- **Shared buttons on machine-specific jobs** (INSTALL.md:102).
- **The companion's 1.5 s start-up read retry** (code comment).

## Before and after publishing (checks, not open comments)
- **The release job proves the signing key.** It compares the APK's SHA-256 to the pinned fingerprint and refuses on a mismatch, so a green v1.0.0 run is the proof.
- **What this review can't cover:** real Bluetooth HID, vJoy, ViGEm and SendInput output, and BeamNG in game. ALI's beta tests covered these on beta-1 and beta-2. Running SMOKE_TEST.md on the 1.0 build, especially A2a, Part F and C10, is recommended.

**Open count: 0.**
