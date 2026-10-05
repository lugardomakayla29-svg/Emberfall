# STATUS (update when you finish or claim something)

## Done and proven
- Weapons 1-10 levels + 8 ultimates, tomes, QoL (delivered as 0.1.2-ultimates)
- Relic foundation (24 relics, unlocks persist across restarts)
- Chests (paid + free: elite, boss, shrine), HUD, Testificate merchants
- Expedition Gate (plate loop fixed, return beside the gate)
- EmberTester: joins as a real player, hidden from tab list, picks character/weapon/tomes itself (bot_brain_test 5/5)
- EmberTester walking maths (BotWalk 21/21, BotPlan 17/17), path scout, pilot (built, NOT yet proven live)

## In progress
- EmberTester live walk test: the summoned foe/scout vanish in the test; cause not found yet (`tools/testbot/bot_walk_test.js`)

- Party scaling: PLAN only, in review (PR #19, `docs/PLAN_party_scaling.md`, issue #13). No code. Two-bot measurements are done (join: PR #25; pickups: PR #28). Order now: a five-bot measurement (the owner's test size), then the pure `PartyScaling` class + `PartyScalingCheck`, then party forming at the gate. `MAX_PARTY = 10` as one constant; every test and measurement uses 5 players; `HOSTILE_CAP` growth stops at its 5-player value until measured.

## Known limitations (decided, not bugs)
- In a party, the two boss Silver bonuses (+50 Guardian, +150 Devourer) go to every member of the run, including one who did no damage. Accepted for v1 (Koda, PR #19); a per-player damage tally would need a boss `hurt` hook and its own approved plan.
- Joining a player who already holds relics into a running run wipes their relics but keeps their gold (measured with two bots, PR #25). Today only the missing join command prevents it, so the party plan enforces "no late join" in `tryStartFrom` and tests it.

## Not started
- Endgame swarm + optional escape portal + silver multiplier
- Party join near the gate (10+ players), fair scaling by party size
- Bot: shrines, merchant, shop, run-end answers; cost measured INSIDE a run; party fill to 9
- Creative tab overhaul, non-op `/emberfall` test
- Armour sets and weapons shop (END GAME, last)

## Unverified everywhere
Nothing has been looked at on a real client (HUD, ultimates, merchant GUI, chest animation).
