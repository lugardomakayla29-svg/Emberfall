# HANDOFF: current state (rewrite this file; do not append)

Last updated: 2026-10-06 21:50 CT by Koda

## MODE: OWNER AWAY
The owner is not at the computer. Koda and Vesper run everything. Koda may merge PRs that are green, small and proven.
Never merge anything that is red, that touches a boss or the mixin package without Koda's proof, or that lacks a DONE-WHEN proof.
Message the owner only if something is broken or a decision is genuinely theirs.

## Goal
Ship the Emberfall jar. Done: party scaling, Expedition Gate party (#51), EmberTester visible with 10 skins (#58).

## In flight
| Item | Owner | State |
|---|---|---|
| Vesper batch 1 (V1 to V6), see INBOX 2026-10-07 02:40 CT | Vesper | not started; first thing to do |
| EmberTester spawn egg, auto-join a run, difficulty bump + sound | Koda | next |
| Rift Expedition + character select, Tiki replacement, Broodtide, Devourer | Koda | queued, in that order after the bots |
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
INBOX entry 2026-10-07 02:40 CT, tasks V1 to V6 in order, one PR each.
