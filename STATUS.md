# STATUS (update when you finish or claim something)

## Done and proven
- Weapons 1-10 levels + 8 ultimates, tomes, QoL (delivered as 0.1.2-ultimates)
- Relic foundation (24 relics, unlocks persist across restarts)
- Chests (paid + free: elite, boss, shrine), HUD, Testificate merchants
- Expedition Gate (plate loop fixed, return beside the gate)
- Endgame: Final Swarm + escape portal + silver multiplier; Expedition Gate party join (max 10, countdown 3 s)
- Party scaling by party size (PartyScaling), EmberTester bot: shrine, merchant, shop and run-end answers, walks with a path scout
- Creative tab with summoners, chests, Testificate and the EmberTester Egg; non-op test (`tab_test` C5)
- EmberTester: joins as a real player, hidden from tab list, picks character/weapon/tomes itself (bot_brain_test 5/5)
- EmberTester walking: BotWalk 21/21, BotPlan 17/17, path scout and pilot proven live (bot_walk_test 8/8, bot_brain_test 5/5, bot_join 12/12)

## In progress
- Landing Vesper's V1 (Final Swarm 5.0x at 300 s, SwarmCheck 39/39) on main; it was first merged into a side branch by mistake (see INBOX 03:20).
- Vesper batch 1: V2 (after boss 1 harder), V3 chest respawn, V4 sound audit, V5 patch notes, V6 review (docs/comms/INBOX.md).
- Koda: bot joins a run by itself at the start, Rift hub + character select (waiting for the owner's proposal text), Tiki replacement, Broodtide, Devourer, Guardian rework with the Excalibur chest textures.

- Final Swarm ramp (V1, Vesper): the multiplier eases in and reaches 5.0x at 300 s instead of 980 s (`FinalSwarm.stepsAfter`, `SwarmCheck` 39/39, old constants fail by name). Look and feel unplaytested.
- Party scaling is MERGED (PR #44 `PartyScaling`, wired to horde cap, spawn interval and boss health; 15 s grace; solo unchanged, 5 players ~3x). PROVEN live: PARTYSIZE 1 then PARTYFROZEN 2 with one egg bot.
- EmberTester: visible to humans (PR #58, unlisted tab entry, 10 bundled skins), EmberTester Egg in the creative tab (PR #64, joins the run you are in, only in the first 15 s). Real-client look UNVERIFIED.

## Known limitations (decided, not bugs)
- Party scaling v1 will NOT scale (Koda, issue #13, decided): the **plain horde Tiki** (`TikiMagma.spawn` overwrites `MAX_HEALTH` through `Slime#setSize`, so the two-call pattern does not fit; the elite Tiki IS scaled through `statMultiplier`), the **elite spawn interval** (elites already scale by health; both would double-count) and the **Final Swarm** multiplier (own ramp and crowd size). Mobs spawned by other systems (Bonecaller minions, Umbral summons, Broodlings, Pink Slime, `MobSpawner`) are not covered either. None of this exists in code yet; it is the plan's scope.
- In a party, the two boss Silver bonuses (+50 Guardian, +150 Devourer) go to every member of the run, including one who did no damage. Accepted for v1 (Koda, PR #19); a per-player damage tally would need a boss `hurt` hook and its own approved plan.
- Joining a player who already holds relics into a running run wipes their relics but keeps their gold (measured with two bots, PR #25). `/emberfall join` is an op-only debug command (the whole `/emberfall` tree needs permission level 2, read from source, not run as a non-op), and the gate path (`RunCommand.tryStartFrom`) always reserves a fresh map slot and starts a new instance, so it never joins an existing run (read from source, not run). The party plan must still enforce "no late join" in `tryStartFrom` and tests it.

## Not started
- Rift Expedition hub and new character select (needs the owner's proposal text)
- Tiki Magma replacement (owner: remove it entirely, a fresh polar-opposite mob)
- Broodtide boss, Devourer overhaul, Ember Guardian rework, Excalibur chest textures
- A bot that joins a run on its own, fills party slots up to 10 (the Egg is the manual version)
- Armour sets and weapons shop (END GAME, last)

## Unverified everywhere
Nothing has been looked at on a real client (HUD, ultimates, merchant GUI, chest animation).
