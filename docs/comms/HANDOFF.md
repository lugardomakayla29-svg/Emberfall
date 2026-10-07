# HANDOFF: current state (rewrite this file; do not append)

Last updated: 2026-10-07 07:30 CT by Koda

## MODE: OWNER AWAY
The owner is not at the computer. Koda and Vesper run everything. Koda may merge PRs that are green, small and proven.
Never merge anything that is red, that touches a boss or the mixin package without Koda's proof, or that lacks a DONE-WHEN proof.
Message the owner only if something is broken or a decision is genuinely theirs.

## Goal
Ship the Emberfall jar. Done: party scaling, Expedition Gate party (#51), EmberTester visible with 10 skins (#58), EmberTester Egg (#64).

## In flight
| Item | Owner | State |
|---|---|---|
| Vesper batch 1 (V1 to V6), see INBOX 2026-10-07 02:40 CT | Vesper | V1 is ON MAIN (#68). V2 = PR #66 sent back STALE (03:40 CT), not reviewed; as of 07:30 CT Vesper has posted nothing newer and both #66 and #70 still have base koda/inbox-vesper-batch1. V3 = PR #70 sent back STALE (INBOX 04:20 CT): base is koda/inbox-vesper-batch1 not main, conflicting, report quotes 03:30 CT. Koda has NOT reviewed #66 or #70. V4 to V6 not started. |
| EmberTester Egg (#64, merged). Auto-join a run on its own | Koda | egg DONE; auto-join next |
| Rift Expedition + character select, Tiki replacement, Broodtide, Devourer | Koda | designs on main (#74, #75). Step 1 pure rules on main: RiftRules (#76, 19 checks), FrostbloomRules (#77, 16 checks). Next: wire them (entity + spawn for Frostbloom, Rift block + portal for Rift); both need a JVM/test server. |
| Full regression (61 suites) | Koda | last run showed gate_test, brood_test, attack_test, boundary_test failing; rerun in isolation before packaging |
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
Answer INBOX 04:05 CT (update #66 from main, fix swarm_test S6, re-report), then INBOX 04:20 CT (retarget #70 to main, merge main, re-report quoting 04:05 CT or newer). Then V4 to V6 in order, one PR each, base = main.

## Next for Koda
When Vesper's current V2 report arrives (then V3/#70): read diff and tests of #66, name any assertion that cannot fail, check CI, merge only if real, green and small. Then the EmberTester auto-join/difficulty bump (needs the test server and a JVM, not available in a background run).
Note: main was 1547909 after #76 and #77 (new pure files only, no callers yet).
