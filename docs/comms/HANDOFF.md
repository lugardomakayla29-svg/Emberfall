# HANDOFF: current state (rewrite this file; do not append)

Last updated: 2026-10-08 07:55 CT by Koda (#152 rim colour, #153 Rift step 4 review, #154 inbox merged; two small asks out to Vesper)
The older text of this file (C5/C6, #94, boundary_test) was stale; the plan of record is docs/GAME_PLAN.md and the newest INBOX entries.

## MODE: OWNER AWAY
Koda and Vesper run everything. Koda merges PRs that are green, small and proven. Never merge anything red, anything touching a boss or the mixin package without Koda's proof, or anything without a DONE-WHEN proof. Message the owner only if something is broken or a decision is genuinely theirs.

## On main (verified 2026-10-08 07:50 CT: all 39 pure checks pass)
Rift steps 1 to 4 (RiftShape, RiftRules, RiftFx, shard + RiftManager + commands), rim colour (#152), GooGrid (pure), chest reveal sequence + server half + server wiring (#143, #147, #155), sound audit 2 and patch notes (#148), review of Rift step 4 and the sound proposals (#153).

## In flight
| Item | Owner | State |
|---|---|---|
| Tighten C3, D1, I2 refusal-text asserts (rift_manager_test.js, rift_inrun_test.js) | Vesper | asked in INBOX 2026-10-08 07:50 CT |
| RiftSpot.hasOpenAir minimum loaded count (MIN_SAMPLED 15) + pure check + mutant | Vesper | asked, same entry |
| Review of #155 (chest_reveal_test.js, reveal_grade.py; is clear() called on every run exit path?) | Vesper | asked, same entry |
| Broodtide: prototype A proven, finding 2 (weapons hit a hidden mob) fix designed, not built; prototype B, GooGrid wiring, body, Tide clock, phases | Koda | branches koda/devour-proto, koda/devour-target; needs a test server |
| Tiki Slime (replaces Tiki Magma; owner approved 10-06), then delete the 5 Tiki files and FrostbloomRules | Koda | design on koda/frostbloom-plan, not on main; needs a test server |
| Chest reveal CLIENT screen + client receiver for OpenChestRevealPayload | open | no client receiver or Screen exists; a real client gets a packet it cannot show. Needs a graphical client to judge |
| Character Select + gate retarget to the Rift (GAME_PLAN 2.5), Hearth removal (2.7) | Koda | not started; needs a test server |
| Branch koda/grader-fix (party_survival_grade fixes) | Koda | pushed, NOT confirmed merged; check before relying on it |
| Regression: boundary_test B0 (out of date vs the static map) and attack_test (no assertions, cannot fail, must not count as green) | Koda | unresolved; packaging blocked on them |

## Blocked / owner decisions
- Sound for death, run end, shrine choice, shop purchase: docs/sound_proposals_silent_events.md. Owner picks; nothing added.
- Real-client look and sound of the Rift, rim colour, chest reveal, bots: needs a graphical client.
- Chest texture pack licence: owner must confirm before public release.

## Known traps
- Suites share port 25565: never run two at once.
- CI math-checks runs each *Check.java in tools/testbot/relic_math; checks that need the Minecraft jar live in tools/testbot/codec and run by hand (ChestRevealCodecCheck, RiftParticleMapCheck): NOT protected by CI.
- A background run has no Minecraft jar and no test server: pure checks and mutant scripts only. JDK 25 is at /tmp/jdk when present.
- vanilla /fill caps at 32,768 blocks; creative restores used stacks.

## Next for Vesper
The three asks in INBOX 2026-10-08 07:50 CT, then STOP and do not poll.

## Next for Koda
Needs a live server: Broodtide fix for finding 2, Tiki Slime Step 0, Rift step 5, then boundary_test / attack_test.
