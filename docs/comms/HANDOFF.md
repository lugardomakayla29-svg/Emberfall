# HANDOFF: current state (rewrite this file; do not append)

Last updated: 2026-10-07 12:10 CT by Koda (#92 A1-A3 + C7 merged; Vesper next on C5 + C6)

## MODE: OWNER AWAY
The owner is not at the computer. Koda and Vesper run everything. Koda may merge PRs that are green, small and proven.
Never merge anything that is red, that touches a boss or the mixin package without Koda's proof, or that lacks a DONE-WHEN proof.
Message the owner only if something is broken or a decision is genuinely theirs.

## Goal
Ship the Emberfall jar. Done: party scaling, Expedition Gate party (#51), EmberTester visible with 10 skins (#58), EmberTester Egg (#64).

## In flight
| Item | Owner | State |
|---|---|---|
| Vesper batch 1 (V1 to V6), see INBOX 2026-10-07 02:40 CT | Vesper | V1 (#68), V2 (#66), V3 (#70), V4 (#81) are ALL ON MAIN, each with CI green. Koda reviewed #66 and #70 before merging and #81 after (owner account merged it first); nothing to fix. #82 (V4 report) was folded into INBOX and should be closed. Open flake: swarm_test S3 (crowd 4, expected 6), Vesper to make it poll. V5 (patch notes) next, then V6 (reviews). |
| EmberTester Egg (#64, merged). Auto-join a run on its own | Koda | egg DONE; auto-join next |
| Rift Expedition + character select, Tiki replacement, Broodtide, Devourer | Koda | designs on main (#74, #75). Step 1 pure rules on main: RiftRules (#76, 19 checks), FrostbloomRules (#77, 16 checks). Next: wire them (entity + spawn for Frostbloom, Rift block + portal for Rift); both need a JVM/test server. |
| Full regression (61 suites) | Koda | Isolation reruns on the staged jar (built 03:03 CT), 07:30 CT: gate_test ALL PASS (T5 etc.), brood_test ALL PASS (7/7). Both passed ONCE alone, so earlier failures look like cross-suite interference, not proven gone (gate_test has flaked before). boundary_test FAILS alone at B0 'in-place arena box read none': it types /expedition and expects an in-place 57-wide arena, but RunCommand now only calls RunManager.startOnMap (static map, bounds 192 wide), so the test's expectation is out of date, not the game. NOT yet decided: rewrite B0 to the map's bounds, or retire the suite. attack_test ran 07:30 CT and printed NO PASS/FAIL lines: it defines check() but never calls it, and its header says it grades ATKDBG log lines, but ATKDBG exists nowhere in the game code (0 hits) and 0 times in the run's server log. So it has no assertions and cannot fail; it must NOT be counted as green. I scanned tools/testbot for the same flaw: attack_test is the only real suite with it (slots_test and brood_orphan print verdicts directly). Decision needed (Koda): rewrite attack_test to grade the Fan cone from a real signal (player hp or a new log line) or retire it. Packaging still blocked until boundary_test B0 and attack_test are resolved. |
| Vesper follow-up #92 (A1 to A3, C7), merged 12:10 CT | Vesper | DONE. Koda read the diff and checked the facts by hand (no JVM in that run); CI green. Next for Vesper: C5 + C6 in map_mob_stray_test.js. |
| Chest textures (#90, merged 11:20 CT) | Koda | Excalibur iron/copper/gold chest textures on main. Licence of the pack is unstated: owner must confirm before public release. |
| Packaging | Koda | blocked until regression is clean |

## Blocked / owner decisions
- The Rift proposal image cannot be read by Koda (no OCR). Needs the owner to paste its words. Do not guess.
- Real-client screenshots, and how the 10 bot skins actually look, need a graphical client.

## Known traps
- Suites share port 25565: never run two at once. Use /tmp/guarded_suite.sh, never launch a bare suite.
- emberfall/bot/redeploy.sh copies a STALE jar from emberfall/mod, not repo_stage. Use redeploy_stage.sh for repo_stage builds.
- CI math-checks runs each *Check.java from tools/testbot/relic_math. A check that reads a file by a root-relative path fails in CI.
- A mixin that cancels a packet can hide an entity: the client only builds a remote player if it already holds its PlayerInfo.
- bot_walk_test needs EXTRA_JVM=-Demberfall.noShrineVisit=true.
- Test-server ops: PlainPlayer, EmberTester, Mate1, Mate2. Use NoPermGuest for non-op tests.

## Next for Vesper
C5 + C6 in tools/testbot/map_mob_stray_test.js only (read the real play radius in S0; drop the duplicated `in1` in S1), a mutant for each, one PR, base main. See INBOX 2026-10-07 12:10 CT. No bot/, Tiki, boss or mixin edits.

## Next for Koda
Own queue: resolve boundary_test B0 (rewrite to the static map bounds or retire) and attack_test (no assertions: rewrite to grade the Fan cone from a real signal or retire; it must not count as green). Then the EmberTester auto-join/difficulty bump and the big systems (Rift, Frostbloom). All need a JVM/test server, which a background run does not have.
Note: main now includes #92 (952ce4f).
