---
title: EMBERFALL (Fabric mod project)
summary: Megabonk-style 3D roguelike Fabric mod (MC 1.21.11) the user is building
  — weapons with shop-bought slots, tomes, three currencies, a bottom-left HUD, enemies,
  bosses, shrines and a physical hub. See boss-concepts.md for boss designs and the
  gold sinks saved for later.
---

# EMBERFALL

A Fabric mod (Minecraft 1.21.11), package `com.solme.emberfall`, modid `emberfall`. A Megabonk-style 3D roguelike: weapon archetypes with tome-based ability trees, horde combat, bosses, and shrines. Separate project from the paused **SlopPack** (Paper plugin) — SlopPack mechanics get adapted into EMBERFALL as inspiration, not ported verbatim.

## Current systems (built and verified)

* **Playtest report 1 (2026-10-01)**: see [playtest-report-1](playtest-report-1.md) for the user long list of new work.
* **Map shrines (built and verified 2026-10-01)**: the 3 shrines of the static map (Challenge, Boss Curse, Statue of Greed) replace the old proximity shrines on map runs. LEFT CLICK on the structure (or its invisible Interaction click box, which also catches thin blocks like the lightning rod) within reach opens a small window (`ShrineScreen`, packets `OpenShrinePayload` / `ChooseShrinePayload`, all re-validated on the server). Once per shrine per run. Code: `shrine/MapShrines.java`, `shrine/RunModifiers.java` (pure numbers). Boss Curse tiers I to IV: boss health and damage x1.2/1.3/1.4/1.5, boss adds x1.1/1.2/1.3/1.5, Silver +15% per tier; applied in `GuardianBossFight` / `DevourerBossFight` via `applyCurse` (the bosses' damage constants are scaled by `damageScale`). Statue of Greed +1 to +5: +2 threat per step (cap 20), Silver +10% per step. Challenge: free, 3/5/8 foes at x1.4 stats, 60 s, clear it for 15/30/55 Gold, 20/40/75 XP pickups and +25 Silver flat; failing costs nothing. Proven in game: `bot/shrine_test.js` 18/18 (window decoded from the raw packet, structure never broken, far clicks ignored, Guardian spawns at exactly 900 hp under Curse IV, Greed +5 raises threat by 10), `bot/shrine_payout_test.js` 3/3 (15 Gold, XP, base 10 + bonus 25 = 35 Silver from the run-end log). Debug: `/emberfall shrinestate <slot>`, `pickupstate <player>`. NOT VERIFIED: how the window looks on a real client. NOT DONE: the old `ShrineManager` (proximity) is still in the code but not used on map runs; curse and greed Silver multiplier is applied, Devourer curse (spawn count) not yet exercised in a live test.
* **Shrines**: Greed/Curse/Challenge, proximity-triggered, custom minecraft-heads.com visuals, balanced effects.

* **Ember Guardian (first boss, replaces the Hydra; built and verified 2026-10-01)**: 600 hp invisible Silverfish brain with 4 display parts, 4 pylon mobs (40 hp) that gate it (invulnerable while any is lit; one re-lights at 66% and 33%; zero placeable spots = never gated, no soft-lock), stalks the player (hops onto 2+ block ledges). Loop: phase 1 Fan/Sparks; phase 2 adds Sweeping Beam (with a blackstone cover pillar) and Magma Ring (safe radius 11); phase 3 adds Cinderfall (4 rows, 7 lanes of 3 blocks, strip 14 deep, every row over the same ground, open lane moves one lane per row, orange preview of the next row, waits up to 3 s for an airborne target) and ring safe radius 7, gap 18 ticks. At 5% hp a calm beat cancels the attack and drops the last pylon. Code: `entity/EmberGuardian.java`, `entity/CinderPylon.java`, `boss/GuardianBossFight.java` (renamed from HydraBossFight). Design history: [hydra-replacement-design.md](./hydra-replacement-design.md). Package: emberfall-0.1.2-guardian.zip with WHATS_NEW.txt. NOT verified on a real client: how it looks, lane readability, ring edge readability, 600 hp length.

* **Weapons** (all 8, each with its tier-3 capstone Tome done and verified): Broadsword (Steady Hand), Twin Daggers (Bleeding Edge), War Halberd (Sundering Wake), Gravechain (Grave Anchor), Hunting Bow (Hunter's Instinct), Arcane Staff (Arcane Convergence), Spectral Sickles (Widening Gyre), Ashen Beacon (Undying Embers).

* **Tome pool** (refined 2026-09-28): 29 tomes, all genuinely working effects — no "does nothing alone, just carries the tag" filler left. Fixed the underlying bug that caused the filler in the first place: `PlayerBuild.tagCount` was counting distinct tome IDs instead of summed stacks, so a tag's synergy bonus wrongly required owning every family member. Now sums stacks. Retired 6 dead tomes and turned the survivors into real independent mechanics (on-kill status-spread for Fire/Frost/Poison, a cadence-based guaranteed chain for Lightning, an independent smaller on-kill chain-detonate for Explosive, an on-kill Absorption shield for Lifesteal, a real companion stat buff for Summon).

* **Horde fillers**: HordeZombie/HordeSkeleton/HordeSpider, each with a Veteran tier (2.5x HP, stat/damage buff) plus one reactive ability — Zombie: enrage speed burst on hit; Skeleton: 3-arrow Volley fan; Spider: ranged Web Shot (Slowness+Mining Fatigue). Wired into WaveDirector's weighted spawn pool.

* **Frankenstein Slime**: death-split mechanic, correctly triggered from `remove(RemovalReason)` (not a tick-method check — see Conventions below).

* **Tiki Magma** (3-tier: Fodder/Elite/Corrupted), rebuilt 2026-09-29 on displays only (no invisible carrier entities). The real MagmaCube is the hitbox. Fodder = real mob cube + a real second cube (`TikiCube`, no AI, damage forwarded to the mob) + one rotating-skin head + a roof = 4 entities (was 9). Elite = 3 flush heads, Corrupted = 4 flush heads with a locked idol skin, each with a roof. Skins rotate across 4 minecraft-heads.com Tiki Mask textures, and the pole sways in a traveling sine wave. Head step is 0.5 x head scale x mob scale (measured flush: 0.80 elite, 1.05 corrupted). The roof is one flat wide dark-oak slab; a true wedge would cost one more entity. Not verified on a graphical client.

## v0.1.1 (first-playtest fixes, 2026-09-28)

The first real-client playtest of v0.1.0 reported: very buggy, an unwanted separate-dimension arena, and missing textures on everything. Fixed in v0.1.1:

* **Missing textures** had two root causes: the 8 weapon items + marker block had no `assets/emberfall/items/*.json` model definitions (required on 1.21.4+), and all 20 custom entity types had no client renderer registered. Headless bot tests cannot catch either, since they never render.

* **In-place runs**: `/expedition` runs where the player stands in the Overworld (no dimension, no structure pasted; natural-terrain ring, radius 28, via `TerrainScanner`). `BlockJournal` records every edited block plus all 6 neighbours and the cell two above (vanilla deletes dependent neighbours such as the top half of tall grass) and restores them on teardown. It is persisted to `world/data/emberfall_journals` for crash recovery. Restore skips cells that already match their snapshot.

* **Real-world safety**: `EmberfallHostilityMixin` on `LivingEntity.canAttack` makes emberfall-namespace mobs target players only (vanilla Zombie AI hunts villagers, iron golems and turtles); chain/blast effects only hit emberfall hostiles; the horde cap counts only emberfall mobs; the Umbral Magus cauldron only goes on safe air.

* **Boss pacing**: First boss (Ember Guardian; was the Hydra) at 10:00 (was a 45s placeholder), Devourer 5:00 into tier 2. Overridable with `-Demberfall.bossAtTicks` / `-Demberfall.devourerAfterTicks` for compressed test runs.

Verification: 20+ terrain-restore runs on varied terrain with zero lost blocks (kelp growth and grass/dirt spread are natural world drift, proven by a no-run control). One earlier run lost a single `short_grass` that never reproduced in 6 repeats and is unexplained. **Not verified on a graphical client**; how the mobs and weapons look is still unconfirmed.

### Test and deploy lessons

* Never copy the jar while a JVM is running or still starting. A `comm == java` check can read 0 in the gap before the JVM appears, and a second launch then fails with `session.lock: already locked`. Wait for `Done (` and confirm exactly one java PID.

* Mineflayer client-side entity and position tracking gave wrong results. Prefer server-side yes/no queries (`/execute if entity ...`, replies look like `Test passed. Count: 1`) or temporary server log lines.

* Always run a no-change control alongside a diff test; it separated real damage from world drift.

* Long node runs can outlive a tool timeout. Run them in a detached tmux session writing to a file. `grep` block-buffers through a pipe, so an empty output file does not mean nothing has happened.

## Physical hub (2026-09-28) - RETIRED 2026-10-08

**Retired.** A Rift is now the way into a run (see `docs/design/RIFT_EXPEDITION.md`). The hub builder, the busts, the shop keeper, the Departure Plate and the Ember Hearth recipe are gone. An old placed Hearth is kept as a cold block: breaking it (or any removal) tears down the hub it built and drops a Rift Shard. The section below is the history of how it worked.

Replaces the typed `/expedition`, `/character` and `/shop` flow. One placeable **Ember Hearth** block builds the whole hub on already-flat ground (never levels or clears terrain).

* **Layout** (default 9x9, `HubSiteAnalyzer.RADIUS=4`): 8 character busts on a radius-3 ring facing the Hearth, a shop keeper next to it, and a **Departure Plate** on the opposite side. Each bust is a minecraft-heads player head with lore as a floating hologram. 28 entities in all: 19 visible plus 9 invisible click targets.

* **Interaction**: right-click a bust to select that character, right-click the keeper to open the shop, step on the plate to start an expedition. The plate is its own block (`DepartureBlock`, `entityInside`) with a 3 second per-player cooldown so lingering does not spam. Verified for a non-op player.

* **Removal**: breaking the Hearth by any route removes every entity and restores every journaled cell to its exact original block. Hub journals live in `world/data/emberfall_hub_journals`, separate from run journals.

* **Site rules**: if the terrain check fails the placement is refused and the nearest good spot is shown, with no auto-relocate. On strictly flat ground a 13x13 hub practically never exists (about 0.05% of sites), 9x9 is about 0.3%, 7x7 about 0.8%, so the search has to scan wide.

* **Typed commands after the hub**: `/character` and `/shop` are operator-only. Bare `/expedition` tells non-ops to use the hub. `/expedition leave` stays public, because a child command's requirement is AND-ed with its parent's, so gating the root would trap players inside a run. It has no physical replacement yet.

* **Mob attack sound audit (2026-09-29)**: checked every attack method for a sound. Almost everything already had one (wind-up and impact). Two real gaps filled: Hydra head fireballs were silent (now `BLAZE_SHOOT`, volume 0.9, pitch 0.9 to 1.15, kept clear of the brain's low ravager slam register), and the Bonecaller's 3s sewage breath had a wind-up groan but silence while active (now a `HUSK_AMBIENT` pulse every 10 ticks, 6 per breath, pitch 0.5 to 0.65). Verified on the wire: 3 breaths of exactly 6 pulses at 500ms gaps; 17 head shots at exactly 3.00s gaps. Deliberately not touched: Tiki tantrum (has sound and ring), Sentinel ripple (visual only, stomp has the sound), Reaver melee (vanilla hit sound), horde Veterans (all have sounds).

* **Sound test method**: `sound_effect` packets from `level.playSound` carry a registry id, not a name, and minecraft-data's table is off by one from this server (husk.ambient 837 there, 836 on the wire; blaze.shoot 179 vs 178). Identify sounds by fingerprint (volume, pitch range, timing) instead of by name. `bot/snd_test.js bone|hydra` does this.

* **Hydra rig gotcha (historical, the Hydra classes are deleted)**: a plain `/summon emberfall:hydra_brain` builds a bare brain with no heads (only `HydraBossFight.spawn` calls `spawnRig`). A `HydraHead` needs only a player within 24 blocks to fire, so `/summon emberfall:hydra_head` tests a head alone. The `/emberfall boss` command still targets the old `Dimensions.EXPEDITION` arena and carves an acid dais, so avoid it for quick checks.

* **Player-attack reach rings (2026-09-29)**: `Fx.impactRing` (12 points, not forced past the client limiter, drawn on impact, never a wind-up) shows the true reach of the two area weapons that landed invisibly. War Halberd cleave draws CRIT at `cleaveRadius` (grows with Sundering Wake); Arcane Staff nova draws WITCH at the nova radius. Verified on the wire: halberd radius 2.500 centred on the target at about 10 packets/s; staff 13 novas in 40s, each radius 3.500. The bow pierce beam, sickle blades, chain pile and beacon pulse already show their reach, so they were left alone. Test tool: `bot/ring_test.js <character> <seconds>`. AutoAttackSystem only ticks players inside a run, and test mobs drift, so the script holds them with a repeating tp.

* **Not verified on a graphical client**: layout, skins and the plate's look are unconfirmed.

### Build and test lessons (hub)

* A quiet Gradle build hid a stale jar and I tested old code. Build without `-q`, confirm `compileJava` ran, and check the jar is newer than the sources before deploying.

* Test permission changes with a non-op bot (`PlainPlayer`); only `EmberTester` is opped.

* Check `/emberfall blockat <pos>` for real block ids. `execute if block` answers "Test failed" for any block that is not the one asked about.

## Attack telegraphs (2026-09-28)

Every area attack now warns the player at its true danger size and shape, in one shared visual language (`combat/Fx`).

* **Helpers**: `telegraphRing` (16 points), `telegraphFill` (outer ring at the true radius plus an inner ring growing to meet it, so timing is readable), `telegraphCone` (15 points per draw), `telegraphLine` (lane for lunges), `telegraphTarget` (ring under a single victim). All are forced past the client particle limiter so "minimal particles" players still see them.

* **Warning and damage share constants**, so they cannot drift: Sentinel `CRASH_RADIUS` 8 and roar `ROAR_RANGE` 8 at 45 degrees, Reaver `CLEAVE_RADIUS` 4.5, `LAST_BLAZE_RADIUS` 6 and `LUNGE_REACH` 8, Hydra `SLAM_RADIUS`, Bonecaller `SEWAGE_CONE_*` (angle derived from the dot threshold) plus a ring under the curse victim.

* **Verified on the wire** with a bot reading raw `world_particles` packets: the stomp ring sits on exactly r=8.0 with nothing beyond it, the roar cone reaches exactly 8.00 and its arc ends at exactly 45.00 degrees, and every packet is `alwaysShow` and `longDistance`.

* **Cost rule**: a cone redrawn every 4 ticks through the Bonecaller's 3 second breath cost 23 packets/s. Outlines are now drawn only during the wind-up (the active attack has its own particles). Bonecaller dust fell from 429 to 235 packets per 30s.

* **Added 2026-09-29** (`Fx.telegraphSquare`, `telegraphSquareFill`, both 16 points per square):

  * **Squares, not rings, where damage is an AABB.** A ring under-warns at the corners (up to 1.41x further out). Plague Colossus death Rupture: hazard square drawn only while under 25% health, at half body width plus `RUPTURE_BASE_RADIUS * (1 + bloat)`. It has no wind-up (it fires from `remove()`), so this is a standing outline, the one deliberate exception to the wind-up-only cost rule (about 40 packets/s). Verified in 8 windows, worst error 0.064 blocks while the mob grew.

  * **Umbral Magus**: cauldron blast is a 10x10 square that fills over its 20-tick fuse (verified live: outer half-extent 5.000, inner 1,2,3,4 growing). Defensive Burst is an 8x8 square filling over its 10-tick wind-up. Orb volley marks the target.

  * **Blightfeather Marksman**: Laser gets a lane from the mob toward the target at the beam's true `LASER_RANGE` 22 and `LASER_HALF_WIDTH` 0.9 (verified: rails exactly 1.800 apart, centreline 0.000 degrees off the player). Fan and Homing volleys are tracked projectiles with no lane to dodge, so they mark the target instead.

  * **Boil-Ridden Marksman left alone on purpose**: it fires instantly with no wind-up, so any warning would need an invented delay.

* **Horde Veterans need no warning (checked 2026-09-29)**: Zombie enrage is a self-buff on being hit, Skeleton Volley and Spider Web Shot are instant aimed projectiles with no wind-up or area. Same reasoning as the Boil-Ridden Marksman.

* **Now verified on the wire**: Defensive Burst outer square at exactly 4.000 with a growing inner square (fired 5 times with the player 2.5 blocks away, since it triggers inside 4 blocks); target markers are 16-point rings of radius 0.90 on the player (24 seen). Attack-warning pass is complete for every mob that has a wind-up or an area attack.

* **Not verified on a graphical client**: how the rings, squares and lanes look is unconfirmed.

* **Bug found and fixed while testing**: `BlightfeatherMarksman.spawnSpinArrow` built its arrows with an empty pickup stack. Every stuck arrow made its chunk fail to save (96 "Failed to encode ... 0 minecraft:air" errors). Now uses a real arrow item (still `Pickup.DISALLOWED`). Verified 0 errors after a forced `save-all flush`.

### Test lessons (telegraphs)

* A creative-mode player is never a valid mob target; ability tests need survival with resistance and regeneration.

* Ability mobs move and leap. Measure against server-side `Pos` sampled during capture, and hold a mob still with `movement_speed` 0.

* `pgrep java` also counts the Gradle daemon; trust the single `Done (` log line.

## Weapons, slots, currencies and HUD (2026-09-29)

The run became a Megabonk-style loop: several weapons fire at once, slots are bought in the shop, and three currencies feed it. Each item below was checked with a headless bot on a fresh world.

* **Two 3-stack weapon capstones**, only offered while the player owns that weapon (filter in `TomeOfferGenerator`, the 8 older tomes unchanged):

  * **Piercing Laser** (Arcane Staff): 10 tick charge, 26 block beam, 12 damage, 30 damage execute under 30% health, hits each target once. Cooldown 9s at stack 1, 6.3s at stack 3, which also forks the beam.

  * **Spin Barrage** (Hunting Bow): VIRTUAL bolts, so no real arrows and no player velocity override, and nothing that can stay on the ground. Damage was weak because flat-fired bolts died on hillsides; a bolt now climbs up to `SWEEP_CLIMB_BUDGET` 3 blocks over rising ground, and damage is 6.0 per bolt. A 4-bolt ring only lands a few hits on a lone far target, so it is a close-range crowd tool.

* **Three currencies.**

  * **XP**: a virtual ground marker with no entity and no vanilla orb (`EmberfallNoOrbMixin`), auto-magnets, ding sound, action bar "+N".

  * **Gold**: in-run only, dropped by kills (`KillRewards`, only when a run player is the killer), auto-collected, wiped when the run ends. `PickupSystem.spendGold` is the sink API.

  * **Silver**: the existing meta currency, paid at run end, spent in the shop.

  * `PickupSystem` stores records per level with no entities (cap 200, then merges), so it costs almost nothing.

* **Weapon slots** (`item/Loadout`): up to 4, each with its OWN streak, Steady Hand flag, Grave Anchor pile and cooldown. `AutoAttackSystem.tickPlayer` loops the slots, briefly puts each weapon's item in the main hand so vanilla `player.attack` reads its damage, speed and reach, then restores the hand in a `finally`. Measured: halberd alone about 5 damage/s, halberd plus a bow in slot 2 about 8.5, so cadence is independent.

* **Slot shop** (`progression/SlotUnlocks`, SavedData): start with 1 weapon and 1 tome slot. Weapon slots 2, 3, 4 cost 100, 300, 700 Silver; tome slots cost 75, 250, 600. They ride the existing shop upgrade list (ids `slot_weapon`, `slot_tome`), so no client screen changed. Tome slots count DISTINCT tomes: a held tome just stacks, a new one needs a free slot (`PlayerBuild.canTake`, enforced in the offer generator and again when a pick resolves).

* **Weapon offers**: every 5th level adds a weapon offer after the tome pick, never instead of it. Full slots open a replace step listing carried weapons. Skip and timeout keep every weapon. Replacing a weapon is free; the user chose **no weapon gold sinks for now**.

* **Gold rerolls**: free charges bought with Silver are spent first; otherwise a tome reroll costs 30, 60, 90, 120 gold. Gold is taken only after a genuinely new offer set exists, so an empty pool costs nothing.

* **HUD panel, bottom left** (`client/LoadoutHud`, `network/HudSync`): two rows of 4 cells, weapons above tomes with stacks, locked cells dimmed with a padlock. The server sends only when the state changes (0 extra packets while idle). The wire layout is decoded byte for byte, but **how it draws is not verified**: there is no graphical client in the sandbox, so it needs a look beside the hotbar at your GUI scale.

* **Saved for later, not built** (see [boss-concepts.md](./boss-concepts.md)): gold chests, gold-cost shrines, gold from breakable props, Smoke Veil.

### Debugging pass (2026-09-29)

* **Bug: a shrine reward leaked between runs.** `TomeOfferGenerator.guaranteedWeaponOffer` (set by the Greed shrine and the Challenge reward) survived leaving a run and disconnecting, so a later run's first offer was forced. Now cleared in `RunManager.leavePlayer`. Reproduced first (`guarantee_leak_test.js`), and the same-run behaviour still works (`guarantee_use_test.js`).

* **Bug: starting a run destroyed a player's item.** `PlayerWeapon.syncHand` wrote the weapon into the selected hotbar slot and nothing restored it, so a diamond sword there was gone for good and the weapon item stayed behind. The stash is kept once per run in `progression/DisplacedItems` (SavedData, so it survives a crash) and the weapon is pinned to hotbar slot 0 without touching the selected slot. `DisplacedItems.restore` strips every run weapon and returns the item (to its slot, else any free slot, else dropped, never destroyed). It runs on `ALLOW_DEATH` (BEFORE vanilla empties the inventory, so a run weapon never lands on the ground for anyone to take), on run exit, and on join when no run is active (crash recovery). It is safe to call twice.

* **Audited, no bug found:** about 30 per-player static maps. Every one is cleared by `RunManager.leavePlayer` or its own owner-side cleanup, except the shrine flag above.

### Test lessons (slots and economy)

* A pending Tome or weapon screen auto-resolves after `GRACE_WINDOW_MS` 8000. Grant gold BEFORE opening, act right after opening, and prove the screen opened before judging a refusal. A refusal and a silently ignored command look the same from outside.

* Live kills pay stray coins, so boundary tests must re-read the balance after opening and fail loudly instead of assuming an exact value.

* Slot purchases persist across bot sessions on one server, so run slot-count tests on a freshly redeployed world.

* Check a vanilla-behaviour expectation before blaming the fix: with `keep_inventory` off the player's own items are dropped at the death spot, so "item is back in slot 0" was the wrong assertion (`death_drop_test.js` checks both gamerules).

* To test crash recovery, `kill -9` the JVM after `/save-all flush`, restart on the SAME world (do not run `redeploy.sh`, it clears it), and check after rejoining (`crash_restore_test.js prep` then `check`).

* When patching a file with a script, `grep -c` the result afterwards, and read a command's real signature before writing a test around it.

## Boss designs

See [boss-concepts.md](./boss-concepts.md) for the full write-up of **The Devourer** (2nd boss, implemented and verified) and **The Reflection** (reserved concept, not yet built — explicitly saved so it isn't lost).

## Conventions worth remembering

* Custom hostile mobs must pass `isEmberfallHostile()` (namespace-gated to `emberfall:`) to be valid targets for any weapon system.

* Test dummies: `/summon emberfall:horde_zombie` with `Attributes:[{id:"minecraft:movement_speed",base:0.0}]` to hold still — never `NoAI`, which structurally blocks knockback/movement via `Mob.isEffectiveAi()`. For max\_health overrides specifically, inline `Attributes` NBT in `/summon` does NOT reliably stick — use a separate `/attribute ... base set` command right after summon instead.

* Any custom "constant contact damage" mechanic must snapshot/restore `deltaMovement` around `hurtServer` calls, since any non-`NO_KNOCKBACK` hurt call applies automatic knockback and halves existing velocity.

* Any once-per-death side effect (death-split, drop tables, etc.) must be triggered from `remove(Entity.RemovalReason)`, not an `isDeadOrDying()` check in a tick method — an instant-kill path can call `remove()` synchronously in the same tick, before the entity's next scheduled tick ever runs, so a tick-method check silently never fires for those paths.

* Multi-part bosses use a brain entity + display-armor-stand segments (proven pattern from Hydra), reused for The Devourer's segmented body and Tiki Magma's stacked segments.

* Live server testing uses a mineflayer bot (`emberfall/bot/`, Node.js) joining the dev server headlessly — requires the server started with `-Demberfall.testMode=true` (marks custom registries OPTIONAL in Fabric's registry-sync handshake) or a non-Fabric client gets kicked on connect.

* `PlayerBuild`/`CombatStats` are static in-memory maps keyed by player UUID that persist across a bot's disconnect/reconnect on the same live server (not just per-session) — leftover tome grants from an earlier test run will contaminate a fresh test unless the server is actually restarted or the run is properly left.

## Round 1 (2026-09-29): Hearth, no real death, music

* **Ember Hearth recipe** (REMOVED 2026-10-08, a Rift Shard is crafted instead) was shapeless: 1 magma block, 2 blaze powder, 1 gold ingot, 1 obsidian in any slots (extras block it). The item is a minecraft-heads Brazier player head; the placed block still uses the magma model.

* **No real death**: `RunEndHandler.ALLOW_DEATH` returns false for a run player. `endRunInsteadOfDying` heals, gives 5s resistance, and calls `finishRun("fallen")`, which reads stats before `leavePlayer` clears them, then sends `RunEndPayload` and the client opens `RunEndScreen`. `RunStats` counts kills. Because nobody dies, the sword and other items stay in the inventory (the old "sword dropped on death" assertion was replaced).

* **Music**: `music/ModSounds` (8 tracks with exact tick lengths) and `music/RunMusic` (per-player shuffle, full pass before any repeat). Started in `RunManager.joinPlayer`, stopped in `leavePlayer`, so every exit path is covered. Songs ship inside the jar (`assets/emberfall/sounds/music`, streamed). Debug: `/emberfall musicnow|musicskip <player>`.

* **Test lesson**: registering any new registry (here `SOUND_EVENT`) makes Fabric registry sync kick vanilla test bots. Add it to the OPTIONAL list in `EmberfallMod.enableHeadlessTestModeIfRequested`. Real players run the mod, so they are unaffected.

* Not verified without a graphical client: Hearth head drawing, Expedition Over screen layout, actual audio.

* **Broodmother rear-up pose (measured 2026-09-29)**: `bot/pose_probe.js` samples the rig displays around a fresh mother. The head display rests at up 0.84 above her feet and rises to 1.40 during the 1.2s lay, then returns to 0.84, so the lift runs and resets. The back sac cannot be isolated from the ground egg sacs (same head, same height band), so its heave is unmeasured. How it looks on a real client is not verified. Probe lessons: rig displays carry NO `emberfall_run` tag outside a run and the 1.21 item lives under `components`, not `item:{id}` NBT, so filter by type plus distance to the mother, never by tag or item NBT (that filter matched nothing and nearly read as 'no head').

## Boss design draft
A replacement for the Hydra and a Devourer expansion plan are drafted in [hydra-replacement-design.md](./hydra-replacement-design.md) (the Guardian half is now BUILT, see the Ember Guardian entry above; the Devourer half is still a proposal, not built).
