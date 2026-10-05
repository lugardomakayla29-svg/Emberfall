---
title: EMBERFALL boss concepts
summary: The Devourer (2nd boss, implemented and verified) and The Reflection (reserved
  concept the user explicitly wants kept, not built yet).
---

# Boss concepts

## The Devourer — 2nd boss (implemented, verified)

Inspired by Calamity's Devourer of Gods / vanilla Destroyer: a segmented burrowing worm with mini-clone spawns. Reuses Hydra's brain-entity + display-armor-stand-segment pattern.

**Structure**: head segment (`emberfall:devourer_brain`, real boss entity holding HP/AI) + 5 body segments (`emberfall:devourer_segment`, visual/hittable armor-stand plates trailing the head's position history). Head and body use minecraft-heads.com's "Worm" (ID 129527) and "Worm (body)" (ID 129528) textures — same contributor/set, both verified live (decoded to real `textures.minecraft.net` URLs, HTTP 200) before hardcoding. Mini-worms (`emberfall:devourer_spawn`) reuse the head texture at a smaller scale.

**Phase 1 — "The Hunt" (100%→66% HP)**: burrows underground, resurfaces near the player after a telegraphed particle warning (ground-crack crit + small-flame particles, no position change), does a horizontal dash across the arena, then dives again.

**Phase 2 — "The Swarm" (66%→33% HP)**: on entering this phase, spawns 2 independent mini-worm adds that hunt the player with a weaker burrow-dash. Minis don't respawn once killed.

**Phase 3 — "The Frenzy" (33%→0% HP)**: faster burrow cycle (shorter telegraph, quicker dash), adds a "Magma Eruption" AoE at each burst location. Spawns one more mini-worm each at the 20% and 10% HP breakpoints (max 4 minis total across the whole fight). Defeat pays out currency like Hydra.

**Bugs found and fixed during live testing** (all reproduced concretely via a temporary diagnostic `hurtServer` log before fixing, then re-verified clean with the log removed):

1. The dash could carry the boss off the platform edge into the void — added a solid-ground check before committing to a dash lane.
2. The telegraph's cosmetic "sink" (moving the entity down a tiny bit each tick to sell "cracking into the ground") actually embedded its hitbox in the floor and triggered real vanilla suffocation (`inWall`) damage — replaced with a pure particle/sound tell, no position change.
3. Burst targets were read from the entity's own (possibly already-sunk) current Y instead of a fixed arena-floor Y, so each burrow/resurface cycle drifted the "surface" height slightly lower — anchored all burst targets to the canonical `spawnOrigin.y` instead, and reset `fallDistance` on the noPhysics/gravity transitions to kill a related spurious fall-damage tick.
4. Post-death, vanilla keeps ticking a dying mob for its ~20-tick death animation; the boss's own phase-check/state-machine logic kept running in that window (confirmed via out-of-order log lines — a phase-3 transition logged *after* death/teardown had already run). Added a `defeated` guard at the top of both custom tick methods so no game logic runs again once `die()` has fired.

Final regression: exact HP math across a full phase 1→2→3→death cycle (no stray damage sources), correct mini-spawn counts at every threshold, and zero leaked segments/displays/minis after death.

## The Reflection — reserved concept (not built, explicitly saved for later)

The user liked this direction but chose The Devourer for boss #2 first. Explicitly asked to keep it safe rather than lose it — do not build without asking, but don't forget it either.

Inspired by Supreme Witch Calamitas / Cryogen: a spellcaster boss built around escalating, telegraphed bullet-hell projectile patterns (rings, spirals, homing bolts) rather than a gear/tank check. Signature mechanic: a **mirror-clone phase** where 2-3 illusions appear alongside the real boss, and the player has to correctly identify and hit the real one (visual/behavioral tell TBD at build time) while dodging patterns from all of them. Tests reading/movement more than raw damage output — a good contrast to Hydra (stationary tank) and The Devourer (mobile melee/burrow).

Other options considered and not chosen: "The Plague Colossus" (Golem-style rotating armor plates + Plantera-style vine-corrupted arena floor) and "Starforged Sentinel" (Moon Lord-style detachable weak-point orbs + Providence-style flight chase).

## Smoke Veil (reserved SlopPack move, held off, not built)

The user asked for this to be saved, not built yet. Do not build without asking. Source: `GoldenShortbow.castSmokeVeil` in SlopPack. A smoke burst at the player's feet (large smoke plus campfire smoke, an enderman teleport sound) spawns a cluster of `SmokeBombTrap` decoys, and gives the player Invisibility and Speed II for about 3.5 seconds. SlopPack fires it from a manual click combo with a 12 second cooldown. For Emberfall the idea is a passive reaction: trigger when health drops below about 35%, with a long cooldown, so mobs lose the player. Needs a design decision on which weapon owns it (or a general pool tome) before building.

## Saved for later: gold sinks (user asked to keep these)

Gold is the in-run currency (see [main.md](./main.md), "Weapons, slots, currencies and HUD"). Built: tome rerolls at 30, 60, 90, 120 gold. Slots are bought with Silver, not gold, and replacing a weapon is free: on 2026-09-29 the user chose **no weapon gold sinks for now**, so do not build weapon rerolls or paid swaps without asking. Held back on purpose, not built:

* **Gold chests (Megabonk style).** Breakable chests scattered around the arena that cost gold to open, with the price rising after every chest opened, so the player decides which to skip. Megabonk's IGN review notes most chests cost gold and the price climbs each time. Needs a new block or entity, a loot table and a placement rule (must respect the hub terrain rules: no caves, trees or structures).

* **Gold-cost shrines.** Greed, Curse and Challenge shrines already exist; making them cost gold with a rising price gives gold another sink without new objects.

* **Gold from breakable props.** Extra gold from destroyed props, shrines and chests. Needs those props to exist first.

## Expedition map rework (user, 2026-09-30) - BIG CHANGE OF PLANS
Runs move OUT of natural terrain and INTO a designed pocket-dimension map (fixes the terrain problems of in-place runs).

USER SPEC (verbatim numbers):
- Circle arena, 200 x 200 across. Base 6 blocks. Walls 5 thick and 20 high, jagged rock or basalt pillars, NOT climbable.
- The entire schematic and map must be UNBREAKABLE by any means.
- Mostly flat: treat it as a blank canvas / map. Add ramps of terrain and high hills (Megabonk style), trees, boulders (vanilla base ones; rocks built by hand), villager houses (plains style), professional layout, no jumbled or nonsensical structures. Simple is fine.
- Nothing procedural except: the 3 provided .schem shrines and the other structures the expedition spawns itself (chests etc, chests NOT to be implemented yet).
- Boss 1 idea: Ember Guardian with a foot-stomp rupture: cone-shaped ground crack from its foot, 6 blocks long, particles + camera shake. Try, and research replacements if something does not work or is too heavy.
- Devourer: YES to the coil ring. Length: "as long as you can, maybe 12 blocks; if it is already longer than 12, leave it; you decide if too long".

SHRINE REDEFINITION (three .schem files provided, WorldEdit Sponge v3, DataVersion 4671):
1. Challenge Shrine = free time; if filled, more Silver, Gold and XP (a good amount).
2. Boss Curse = bosses get 1.2x to 1.5x stats and 1.1x to 1.5x boss spawns (could spawn double); more rewards in-run and out-of-run.
3. Statue of Greed = +5 difficulty (more mob spawns and stats), a NEW player-chosen difficulty scale; harder = more rewards.
STRAY BLOCKS: GRASS BLOCKS are the user's copy markers and must be removed; AIR blocks in the file must be handled (do not overwrite terrain). Example screenshot: stray-blocks-example.png.
FILES: incoming_files/1d0a6c1b5_challenge_shrine.schem (5x11x7), c5887f4f2_curse_boss.schem (6x9x6, has a redstone_block core), 08d5134ae_shrine_of_greed.schem (3x4x4, skeleton skull with a block entity).

### Shrine trigger + Challenge detail (user, 2026-09-30, follow-up)
- TRIGGER: the player PUNCHES (left-click) the structure to activate it. Same for ALL three structures. (Today shrines trigger by walking within 2.5 blocks, see ShrineManager.TRIGGER_RADIUS.)
- CHALLENGE SHRINE is the one where you defeat a HORDE OF POWERFUL MOBS to get the rewards. The user wants the .schem to REPLACE the current plain pedestal (base block + shroomlight), because a real fight already exists behind it.
- Existing fight to build on (ShrineManager.startChallenge): 3 elite mobs (Reaver, Marksman, Magus, Sentinel, Bonecaller) at 1.4x stats within 3 blocks, 25 s limit (500 ticks), reward was one guaranteed strong Tome pick. New reward per spec: more Silver, Gold and XP.
- The Curse and Greed effects (player -20% max hp; +2 wave threat) are REPLACED by Boss Curse and Statue of Greed as specified above.

### Expedition map: MEASURED foundations (2026-09-30)
- BLOCK WRITE COST (temp MAPBENCH command, fresh world each, 188,568-block disc, 0 exceptions): 200/tick 1.98 ms avg, 500/tick 3.8-4.5, 1000/tick 6.6, 2000/tick 14.8, 3000/tick 23.1. Worst tick 92-359 ms at EVERY rate, and the slowtick log ties each to chunk load/unload (598 chunks in one tick at start; 5 of 375 ticks over 50 ms). Decision: build each slot's map ONCE ahead of time at about 500 blocks/tick with chunks preloaded, never at run start.
- ONLY 4 features write blocks during a run, all through RunManager.setBlockJournaled: shrine props, Hydra AcidDais, Umbral Magus cauldron, marker strip. BlockJournal persists and recovers after a crash, so the journal restores a run's changes; the map is not rebuilt per run.
- CLIMB PROOF (mapwork/climb_proof.py, flood fill over standable cells with the vanilla 1-block step rule): the vertical pillar wall reaches only y=0 of 17. The checker itself was validated on a deliberately climbable staircase (reaches y=15), so the pass is not vacuous. Limits: covers walking/jumping only. Pearls, chorus, elytra, placing blocks still need the protection layer plus a CIRCULAR ArenaBoundary (existing one is box-shaped and skips non-in-place arenas).
- TERRAIN (mapwork/layout.py): 5 named hills + 2 ramps, max neighbour step 1 (a 3-block cliff and a hill overlap were found by number and fixed), 70.7% flat.
- SCHEMATICS (mapwork/convert.py): Challenge 5x11x7 -> 3x11x5 (88 solid), Curse 6x9x6 -> 5x9x5 (48), Greed 3x4x4 -> 2x4x3 (10, keeps the skull block entity). Grass markers and air dropped.
- BUILD ENV: gradle needs JAVA_HOME=/tmp/jdk-25.0.4.1+1 (a bare ./gradlew silently exits 1 with no Java). MapBench (world/MapBench.java + 3 marked lines in EmberfallCommands, all tagged MAPBENCH) is TEMPORARY and must be stripped before packaging.

### MapProtection VERIFIED (2026-09-30)
- world/MapProtection.java, scoped to emberfall:expedition only. Hooks: PlayerBlockBreakEvents.BEFORE, AttackBlockCallback (this one actually stops a survival dig), UseBlockCallback (blocks, flint, fire charge, bone meal, hoe/axe/shovel), UseItemCallback (ALL buckets: BucketItem.use raycasts itself, so UseBlockCallback never sees a pour). lockRules() (MOB_GRIEFING, TNT_EXPLODES, FIRE_DAMAGE, FIRE_SPREAD off) is written but NOT called yet.
- bot/protect_test.js: 10/10 with a positive CONTROL for each route in the overworld (mine, place, fire, water all succeed there). Expedition: mining, placing, fire, water all refused.
- TEST LESSONS: (1) a survival bot PREDICTS a break, so bot.dig 'completed' and bot.blockAt lie; judge only by /execute if block. My first 'protection failed' reading was this illusion. (2) A fill in an unloaded dimension says 'That position is not loaded': /execute in <dim> run forceload add x z first. (3) A first-time water hole was found only because the control existed.
- OPEN: a vanilla NPE in the chunk DistanceManager (class_3204.method_14051) appeared at server stop / disconnect with a cross-dimension teleported, force-loaded player. Not seen with 16 clean regression suites. Not proven harmless.

### Explosion + fire protection VERIFIED (2026-09-30)
- CORRECTION: GameRules in 1.21.11 are ONE shared object for the whole server (ServerLevel.getGameRules), so a gamerule cannot be scoped to one dimension. MapProtection.lockRules was written and REMOVED before ever being called; calling it would have switched off griefing/TNT/fire for the overworld too.
- mixin/EmberfallExplosionMixin (registered in emberfall.mixins.json) on ServerExplosion: interactsWithBlocks() returns false and createFire() is cancelled when MapProtection.isProtected(level). Bytecode of explode(): hurtEntities() runs FIRST (players and mobs still take blast damage), then interactsWithBlocks() gates interactWithBlocks, then a SEPARATE 'fire' flag gates createFire, so both needed closing.
- bot/explosion_test.js: charged creeper + TNT, overworld control 25 planks -> 4, expedition 25 -> 25, floor whole. bot/fire_test.js: fireball, overworld control 23 fire blocks and the plank burned, expedition 0 fire and plank intact. The stone-floor and fire checks in explosion_test were NOT discriminating (control also left them whole), fire_test fixes that.
- Zombie doors: BreakDoorGoal calls removeBlock; canBreakDoors defaults false but vanilla spawn logic calls setCanBreakDoors(true) (Hard). HordeZombie and PlagueColossus now override setCanBreakDoors as a no-op. Class path is net.minecraft.world.entity.monster.zombie.Zombie (moved in 1.21.11; unzip is not installed, use python zipfile).
- Emberfall mob block writes are ONLY Umbral Magus cauldron and Hydra AcidDais, both journaled. No Emberfall mob explodes, ignites, or places fluid.
- NOT YET TESTED: pistons, dispensers, fluid spread, ender pearls/chorus onto the wall (needs circular boundary).

### CircleBoundary VERIFIED (2026-09-30)
- world/CircleMath.java (pure geometry, no game types, unit tested: 10/10 incl. box corner counts as outside, all 360 bearings land on r=86, centre gives no NaN) + world/CircleBoundary.java (tick hook in EmberfallMod after ArenaBoundary). Owns only non-inPlace arenas in emberfall:expedition. radiusFor(width)=min(88, width/2-2) so a small test arena is enforced too; the real 200-wide map keeps 88. Soft band overshoot 0..3 = inward velocity 0.35+over*0.15; beyond 3 or higher than floor+24 = teleport to nearest r-2 point at surfaceY.
- bot/circle_test.js 11/11 on a real /emberfall paste starter_arena run, 0 exceptions. NOTE starter_arena is only 21x7x21.
- TEST LESSON: a mineflayer bot keeps sending its own position and never obeys a server push, so a soft push must be judged from the entity_velocity packet (boundary_test.js already did this); position never changes. Hard teleports DO show in Pos.
- UNEXPLAINED: a player /tp'd to 10.5 out was seen with an implied overshoot of 2.5 (x=21.5), not 2.0. Conclusions unaffected, cause not found.
- NOT YET TESTED: ender pearl / chorus fruit onto the wall, pistons, dispensers, fluid spread. The guard has not been run on the real 200-wide map (it does not exist yet).

## Hydra replacement and Devourer expansion
Design draft (2026-09-30, nothing built): see [hydra-replacement-design.md](./hydra-replacement-design.md). Covers the Ember Guardian phases and the Devourer coil ring and menace pass, with open questions for the user.
