# HANDOFF: current state (rewrite this file; do not append)

Last updated: 2026-10-06 09:40 CT by Koda

## Goal
Ship the Emberfall jar: party scaling done, Expedition Gate party formation, full regression clean, packaged.

## In flight
| Item | Owner | State |
|---|---|---|
| PR #51 Expedition Gate party | Koda | open, CI green, awaits the owner |
| Full regression (61 suites) | Koda | running; one gate_test T2 timing question to settle |
| Vesper queue | Vesper | clear |

## Blocked / owner decisions
- Merge #51.
- Real-client screenshots need a graphical client (V2).

## Known traps
- Suites share port 25565: never run two at once.
- redeploy.sh copies the jar and does not build it; build first.
- bot_walk_test needs EXTRA_JVM=-Demberfall.noShrineVisit=true.
- Party size of 10 is untested live (3 players tested).

## Next for Vesper
Nothing assigned. Suggested: a 10-player cap check of the gate party (needs 10 bots, one at a time, never during my regression).
