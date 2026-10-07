# V16: the two unfinished checks from V15

Vesper, 2026-10-07. Newest Koda entry read: 2026-10-07 16:38 CT. Tested headless, look unverified. Report plus two reference scripts only; no test, `src/` or `bot/` edited.
Jar for every run: built from main 1b47cae, sha256 1a6d98bcadea12d0 (NOT the cdf5f3b8 jar of V15). Fresh world per run, offline bot `EmberTester`, one server per run, 0 JVMs left after each.

## Item 1: do the three Tiki V2 lines run the tier they name?

| file | tier arg (line) | spawn command | V2 branch taken (line) | live V2 result |
|---|---|---|---|---|
| tiki_voice_vet_test.js | `'veteran'` hard-coded (L8) | `spawnveteran tiki_magma` | NO laser, `laserFires === 0` (L49) | PASS, 0 lane bursts, 2 runs |
| tiki_voice_elite_test.js | `'elite'` hard-coded (L8) | `spawnelite tiki_magma` | laser fired, `>= 2` (L57) | PASS, 3 lane bursts |
| tiki_voice_test.js | `argv[2] \|\| 'corrupted'` (L8) | `spawnelite tiki_magma_corrupted` | laser fired, `>= 2` (L48) | PASS, 3 lane bursts |

**My V15 line was wrong.** I wrote that the three Tiki V2 checks share the NO-laser shape. Only the vet file does. The elite and default files test that the laser DID fire.
**The `veteran` branch inside tiki_voice_test.js (L49) is dead code in practice:** regress3.sh and tail4.sh call it with no argument (L8 then gives `corrupted`), so only a hand-run `node tiki_voice_test.js veteran` reaches it. tiki_voice_vet_test.js and tiki_voice_elite_test.js are named in NO runner and no CI workflow; the only mention is the 2026-10-05 audit CSV (rows 185, 187: "exit 0, no FAIL on a stopped server").
**Is V2 backed by the code?** Yes for the vet NO-laser claim: `TikiVoice.laserCooldown` (L59) returns `Integer.MAX_VALUE` for tier `NONE`, and the laser starts only if `laserCooldown(tier) != MAX_VALUE` (L100 to L101). So 0 lasers is a property of the code, not of a quiet window. For elite and corrupted the first laser fires as soon as the player is inside 12 blocks and the Tiki is over 4 away (`laserReadyAt` starts at 0), then every 22 + 160 ticks (elite) or 22 + 100 (corrupted).

### Finding: V4 FAILS in all three files, and the process still exits 0
Five live runs (vet x2, vet probe, elite, default): V1 PASS, V2 PASS, V3 PASS, **V4 FAIL ("the Tiki survived the window")**, `NODE_EXIT=0`. A runner that scores by exit code sees a pass. regress3.sh and regress4.sh score by counting `^FAIL` lines, so they would record `fail 1`; I did not read a past summary, so I do not know whether anyone saw it.
**Cause, measured:** `ref/v16_probe_tiki_alive.js` is the vet test with the alive query repeated every 5 s. Result: the Tiki is alive at full 1024 HP at t=5, 10, 15, 20 s; at t=25, 30, 35 s the server says `No entity was found`. The map run starts at 21:51:50, 27 s after the 21:51:23 spawn. In every run the map run began 27 s after the spawn (vet 21:49:47 to 21:50:14, elite 21:53:12 to 21:53:39, default 21:54:43 to 21:55:10). So `/expedition` (L6) returns while the map is still building, the Tiki is spawned in the pre-map world, and it disappears when the map run starts. No death line and no exception in the server logs.
**Not established:** WHAT removes it. It fits RunMobPurge (my V11 finding: unnamed vanilla Mobs in an active run's play area are discarded) but I did not test that here. The test tags the Tiki with `mine` but never names it. I also do not know whether V4 was red before today: I did not run an older jar, and the Tiki tests have had no edit since the import commit 6adc591.
**What it means for V1 to V3:** the 35 s window (L38 `rec = true` to L41) is only about 20 to 25 s of real evidence. V2 NO-laser (vet) is still backed by the code. V2 laser (elite, corrupted) needs 2 bursts; first fires at about 0 s, second 9.1 s (elite) or 6.7 s (corrupted) later, so 2 fit inside 20 s. The 3 bursts observed would need the Tiki alive past 18 s (elite) or 13 s (corrupted); that is consistent with the probe but I did not time them.

## Item 2: sickle_test K1 (`lost === 0` over 6 s)
K1 is L47 to L49: foe at `dz=3.6`, `lostOver('far', 6000)`, `k1 === 0`. `lostOver` (L26) returns NaN when a health read is null, and `NaN === 0` is false, so an unreadable foe FAILS K1 (the safe direction).
**Mutant (`ref/v16_mutant_sickle_k1.js`, one changed line, L47):** `foe('far', 0, 3.6)` becomes `foe('far', 0, 2.7)`, inside the blades' contact band (the same spot K3 uses).
- Control, unmodified test, same jar: ALL PASS, `K1 ... lost 0`, K3 `L1 19.2 L10 147.5`.
- Mutant: `FAIL K1 ... lost 11.5199`, `SOME FAIL 1`, K0, K4z, K2a, K2, K3 still PASS.
**So K1 goes red when a sickle can reach the foe. Answer: yes.** The mutant run also exits 0 with `SOME FAIL 1`, same as V4: exit code alone hides a red.
K1 is still controlled by K3 in another window and on another foe, not in its own window; the mutant shows the check CAN fail, it does not show the 3.6 foe is in a spot where a sickle would reach if the radius grew by less than 0.9.

## Not established
- Any rate: every number is one run (V4 red in 5 of 5, which is the only repeat).
- What removes the Tiki at map start; whether V4 was red before today's jar; the corrupted and elite lasers timed against the alive period.
- Tested only the three Tiki files and sickle K1. The other ~200 tests are still unread.

## Own slips
- V15 said the three Tiki V2 checks had the same NO-laser shape. Wrong, corrected above.
- I first read V4's `Test failed` as a possible death. The server log had no death and the TIKI_FACE lines continued, so I measured instead; the probe showed removal at map start.
- V15 jar (cdf5f3b8) was older than main; I rebuilt before measuring and recorded the new hash.
