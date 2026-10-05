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

- Party scaling: PLAN merged (PR #19, issue #13). **Step 2 `PartyScaling` (pure maths + `PartyScalingCheck`, 27 checks) is PR #38, approved by Koda, not merged.** Nothing calls it, so no live behaviour changed. **The coefficients (0.5, 0.75, 0.6, 10) are the plan's guesses, not tuned: mobs x5.5 and bosses x7.75 health at 10 players may be unplayable. The check proves structure and edges, not balance.** The check hard-codes `HOSTILE_CAP = 40` and the interval floor 15, so it will not notice if `WaveDirector` changes them. Step 3 DESIGN is `docs/PLAN_party_scaling.md` section 12 (design only, no code, no go yet). Measurements: join PR #25, pickups PR #28, five bots PR #39 (open).

## Known limitations (decided, not bugs)
- In a party, the two boss Silver bonuses (+50 Guardian, +150 Devourer) go to every member of the run, including one who did no damage. Accepted for v1 (Koda, PR #19); a per-player damage tally would need a boss `hurt` hook and its own approved plan.
- Joining a player who already holds relics into a running run wipes their relics but keeps their gold (measured with two bots, PR #25). `/emberfall join` is an op-only debug command (the whole `/emberfall` tree needs permission level 2, read from source, not run as a non-op), and the gate path (`RunCommand.tryStartFrom`) always reserves a fresh map slot and starts a new instance, so it never joins an existing run (read from source, not run). The party plan must still enforce "no late join" in `tryStartFrom` and tests it.

## Not started
- Endgame swarm + optional escape portal + silver multiplier
- Party join near the gate (10+ players), fair scaling by party size
- Bot: shrines, merchant, shop, run-end answers; cost measured INSIDE a run; party fill to 9
- Creative tab overhaul, non-op `/emberfall` test
- Armour sets and weapons shop (END GAME, last)

## Unverified everywhere
Nothing has been looked at on a real client (HUD, ultimates, merchant GUI, chest animation).
