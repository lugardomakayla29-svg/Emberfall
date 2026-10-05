---
title: Emberfall playtest report 1 (2026-10-01)
summary: The user's first long playtest report after the coil build. Covers the post-run
  armor and weapon shop, mob fixes, weapon AOE and ultimates, the kraken boss, the
  creative tab and village houses. Lists what was received, what was not, and findings
  from reading the SlopPack jar.
---

# Playtest report 1 (user, 2026-10-01)

The user played the OLD build, the one that still had the Hydra. The expedition is "almost perfect". This note is the full list, in the user's words as closely as possible, so nothing is lost between sessions. Project overview: [EMBERFALL](main.md). Boss designs: [boss-concepts](boss-concepts.md).

## A. Out-of-expedition armor sets and weapons (new feature)

* After an expedition the shop lets the player spend Silver on things unrelated to expeditions: armor sets and weapons kept for the base game. Inspired by SlopPack.

* Five sets for five classes, matching SlopPack: MELEE, MAGE, ARCHER, TANK, SUMMONER. Each gets an Emberfall character as its equivalent.

* They are the most expensive items. Locked until every other shop item has been unlocked once. Weapon and tome slots need at least +2 unlocks. After that the options are no longer greyed out.

* Each set has an **Ultimate** that triggers from an event, not a key combo: shooting a mob 5 times, a stack buildup, pressing shift. Nothing like Shift + left click.

* Full set = full Ultimate. A 2/4 or 3/4 set gives the base armor stats and a lesser ability. The user wants example players who would choose 1/4, 2/4 or 3/4 on purpose.

* Weapons are counterparts of the expedition weapons but different. Any class can hold any weapon, with reduced benefit or a missing trigger. The matching weapon is recommended.

* Every set and weapon must be unique and powerful in its own way.

* **Must never work in expeditions.** Entering with one gives an error asking the player to remove it. If one is smuggled in: forced out of the expedition with a menacing warning. Three strikes. On the third, a taunt ("Play stupid games, win stupid prizes" or similar), then a huge escalating horde (lots of fodder and elites, bosses) for 5 minutes, then screams, combustion and thrashing. Terrify the player.

* Purpose: a real threat to the world outside the expedition, in the base game.

### Findings from the SlopPack jar (read from class strings, not decompiled)

* Classes in `AbilityClass`: MELEE, MAGE, ARCHER, TANK, SUMMONER, each tied to a mob (Cinderbrand Reaver, Umbral Magus, Blightfeather Marksman, Corrupted Sentinel, Bonecaller Necromancer).

* Abilities found: Blazing Momentum, Eclipse Step, Solarite Aura, Sunbrand stacks (melee); Arcane Resonance, Mana Shell, Star Fall, Star Bit, Amethyst Steve (mage); Predator's Instinct, Storm of Arrows, Executioner's Volley, Wind Burst, High Noon (archer); Colossus Warplate, Iron Fortress, Immovable Object, Resolve stacks (tank); Call of the Wild, Primal Bond, Alpha Instinct, Bone Circle, Bone Shield (summoner).

* SlopPack uses Sneak + Jump and Sneak + Right-click for several of these. The user does NOT want key combos, so the Emberfall versions need event triggers.

* The exact numbers are in `ArmorSetAbilityListener` constants and are not yet read.

## B. Mobs

* **Bonecaller Necromancer** should be a horseman on a zombie horse like in SlopPack. The current one "got hallucinated".

* **Umbral Magus** summons cannot be hurt by the mod's auto-weapons. Audit every summoner in the mod.

* **Corrupted mobs** are weak and hallucinated. Improve or replace. All special mobs get smarter AI than fodder and a larger scale. Do NOT scale the Corrupted Sentinel.

* **Pink slime** replaces the Frankenstein Slime. The Umbral Magus pulls out a cauldron, pink liquid rises slowly and overflows with hot boiling particles, then a pink slime emerges and grows to 5 blocks. Menacing name. Moves: spit pink slime balls, leave fading pink trails, a very high leap and ground slam, projectiles. Reference: <https://minecraft.wiki/w/Dungeons:Conjured_Slime>

* **Names**: elite, veteran and named mobs show plain white text. Give them colour or gradient.

* **Tiki line-up**: add a levitation ability (float and chase, stronger with tier), fix the dark oak slab sitting on top of the fodder, make every player head face the player, and add a signature move per tier. Right now they are immobile, broken and harmless.

* **Witch fodder**: nearly perfect but they run away. Make them approach to a set distance, sway and strafe as a taunt or dodge, cackle when the player struggles or gets hit, back up when the player is close. Learn from skeleton AI, do not reuse an invisible skeleton.

* All fodder line-ups: expand and improve.

## C. Weapons (all need AOE, level growth and an Ultimate)

* **Broadsword**: bigger, longer cone AOE hitting many.

* **Twin Daggers (Duelist)**: slicing animations, two floating daggers left and right that follow and do ninja moves on many enemies, range grows per level.

* **War Halberd**: a build-up War Slam, 5 block circle, big damage.

* **Hunting Bow (Ranger)**: fine, improve if wanted.

* **Arcane Staff**: purple fiery projectiles instead of crit ones, more AOE strikes, fractional multi-shot (1.5 means a 50% chance of a second projectile, each can proc the AOE).

* **Gravechain**: multi-target, extra chains using the same fractional math.

* **Spectral Sickles**: a FULL filled circle, not hollow, range grows per level. Occasionally two sickles circle, pull mobs into a pile, slice for 5 to 10 seconds, break, then cooldown and repeat.

* **Ashen Beacon**: fire grows per level, firing sounds, an extra projectile every 1 to 3 levels, homing fire that prefers groups, and a lantern that rises, spins and saps life from mobs within 16 blocks.

## D. Boss

Redo the Ember Guardian as a lava kraken built from layered magma cubes, one or several tentacles.

## E. Creative tab

Every mob, custom item and block in the mod.

## F. Map

The village houses look off. Use villager houses or better handmade ones. See the house analysis below.

### House analysis (from `mapwork/props.py` house())

* Roof stairs run x -5..5 but the gable fill only x -4..4, so the roof overhangs the short walls by one block with nothing under it (the floating lip in the screenshots).

* No ridge cap logic, so the top edge reads as raw stair steps.

* Walls are one flat plank sheet, log only on the four corners. Windows are a bare glass pane with no frame or sill.

* The interior is one empty room with no light or furniture.

## Received later

The two files listed as not received below were sent on 2026-10-02 (see the end of this note).

## Not received (now resolved)

* The rosie slime texture .zip (not in `incoming_files/`).

* The pasted tentacle images.

* The screenshot shows a village house roof problem and the old HUD (LV, Gold, Silver, Kills); no new HUD complaint was stated.

## POSTPONED, END GAME, LAST THING TO BUILD (user clarification, 2026-10-02)

Section A (the out-of-expedition armor sets and weapons) is the LAST item on the list. Do not start it until everything else is done. The user will supply or approve the open design questions when that time comes.

Changes to section A from this clarification:

* **All Emberfall characters get their own armor set and weapon**, not just five. The "5 classes" idea is replaced by: every Emberfall character gets a unique set plus weapon (SlopPack's five classes stay as inspiration only).

* **Armor sets ARE allowed in expeditions** going forward. This reverses the earlier "armor must not work in expeditions" rule.

* **Weapons**: the user is NOT sure whether out-of-expedition weapons (the ones acquired from the mod and used in the base game) should be blocked in expeditions. Undecided. Ask again when section A starts. The 3-strike smuggling punishment only matters if weapons stay blocked, so it is on hold too.

* The three questions I asked (order, base-game threat, lesser ability for 2/4 and 3/4 sets) are also postponed until the user has an idea. They are not a priority now.

## Work order now

Expedition related things first: village houses, mob fixes, weapon AOE and level growth, pink slime (Umbral Magus), kraken boss, creative tab. Section A last.

## Files received (2026-10-02)

* `incoming_files/dc98de100_rosieslime.zip`: the rosie slime texture.

* `incoming_files/eb39379e2_kuudra-hypixel.gif` and the Kuudra tentacle image: the user's reference for the FIRST boss (the Ember Guardian, the user forgot the name, the one I called Cinderfall). It is a magma-cube head with large spiked lava tentacles around it, in a dark fiery scene. Use it as the look of the kraken redesign.

## Progress log

### Village houses: DONE (2026-10-02)

* The four houses are now real vanilla 1.21 village buildings read straight from `server-1.21.11.jar` (`data/minecraft/structure/village/plains/houses`). Converter: `mapwork/vanilla_houses.py` (jigsaw becomes its final\_state, direction properties rotate, chest/lava/barrel dropped).

* house\_a armorer (239 blocks), house\_b mason (216), house\_c cottage = small\_house\_1 (153), house\_d apothecary = temple\_4 (302). `objects.HOUSE_KIND` maps house name to kind. `houseBlocks` in the map data is keyed by house NAME and `MapBuilder.java` reads `houseBlocks[name]`.

* Only buildings whose y=0 layer is cobblestone or planks were used. Buildings with a dirt/grass yard at y=0 (library\_2, fletcher, cartographer, small\_house\_8) would raise the ground by a block.

* PROVEN: `verify_map.py` ALL PASS, including a door-on-the-plaza-side check that I proved can fail by flipping a house. `bot/map_build_test.js` (fresh world) places every block exactly: 239/239, 216/216, 153/153, 302/302, trees 60/60, boulders 30/30, 0 exceptions. `mapwork/door_path_check.py` ALL PASS (a model walk, not the game pathfinder).

* NOT PROVEN: a live mob walking through a door (`bot/house_door_test.js` is unreliable, plain summoned zombies get swept or burned, ignore it); the real-client look.

* After every `gen_map.py`, copy `map_data.json` to `mod/src/main/resources/data/emberfall/map/expedition_map.json`. Backups: `/tmp/*bak_houses`.

### Next in order

Mob fixes (Bonecaller horseman, summoner damage audit, witch AI, Tiki, coloured names), weapon AOE and levels, pink slime (texture in `emberfall/art/rosie`), lava kraken boss (references received), creative tab. Armor and weapon shop last.

### Summon targeting fix (2026-10-02): DONE and measured

* Root cause: `AutoAttackSystem.isEmberfallHostile` accepted only the `emberfall` namespace. The Umbral Magus's Thrall and Colossus and the Bonecaller's horse and raised dead are vanilla types, so every weapon skipped them. `RunMobPurge` spares them only because they carry a custom name.

* Fix: `isEmberfallHostile` also accepts `RunMobTeam.isTeamMob(mob)` (now public): any mob in an active run that is not a player and not a tamed companion. Side effects checked: `ArenaBoundary.pullMobsBack` now also pulls strays back in (harmless) and `WaveDirector.currentHostileCount` now counts summons toward the mob cap (better).

* Measured with `bot/summon_damage_test.js` (one subject at a time, named, frozen, fire proof, midnight), same test on both jars. OLD jar: control hurt, all six vanilla types untouched at 200 for 15 s. FIXED jar: zombie, husk, skeleton, wither skeleton, zombie villager all hurt; the zombie horse was slain within 1 s (server log) so its first reading was missing; a tamed wolf stays untouched.

* Test lessons: `instant_health` HARMS undead and killed every target; undead burn in daylight (fake damage), so midnight plus fire\_resistance; a NAME lets a vanilla mob survive the purge; parse `/data` by the text `entity data: <n>f`; the `Attributes` NBT in a summon did not raise hp, use `/attribute ... base set` then `/data modify ... Health`.

* Backups of the fixed files: `/tmp/bak_summonfix/`. Jars: `/tmp/emberfall_OLD_summon.jar`, `/tmp/emberfall_FIXED_summon.jar`.

### Weapon system plan (user, 2026-10-02): ALL weapons need level by level treatment, a built in Ultimate, more AOE, be creative, no weapon lacking, fractional math and stacks

Findings: there is NO per-weapon level today. `WeaponPool` has 8 weapons on a flat "every 4th hit" rhythm; `Loadout.Slot` already holds per-weapon run state (streak, cadence) so a weapon level fits there. Tomes give all current growth. Movesets: MELEE\_SINGLE, MELEE\_DUAL, MELEE\_CLEAVE, MELEE\_HOOK, RANGED\_SINGLE, RANGED\_AOE, ORBIT, TOTEM.
Plan: (1) shared mechanism first: a run-scoped weapon level per `Loadout.Slot` rising with that weapon's kills, fractional math helper (1.5 = 1 plus a 50% chance of a second), an Ultimate meter filled by the weapon's own play that fires automatically when full (no key combo), HUD/feedback. (2) Then one weapon at a time, each with a non-vacuous test: Broadsword (longer wider cone, Sunbrand Sweep), Twin Daggers (two floating daggers, Thousand Cuts), War Halberd (War Slam radius to 5, Earthsplitter), Hunting Bow (fractional arrows, pierce, Storm of Arrows), Arcane Staff (purple fire bolts, fractional multishot, Starfall), Gravechain (fractional extra chains, Grave Vortex), Spectral Sickles (filled circle growing, paired sickle pile and slice, Reaping Cyclone), Ashen Beacon (growing fire, extra homing projectiles, life sap lantern, Pyre Ascension).

* END TO END (`bot/magus_summon_hurt_test.js`, real `spawnelite umbral_magus`, fixed jar): ALL PASS, the real Colossus (wither skeleton) lost hp 32 of 40 to the player's weapon; 0 server exceptions. The Thrall count stayed 0 in that run, so the Thrall type is proven by the stand-in test only. NOT packaged yet: it ships with the weapon work.

### Weapon growth foundation (2026-10-02): BUILT and measured

* `item/WeaponGrowth.java` (pure math: level from kills 0/4/10/18/28/40/54/70/88/108, max 10; `roll(avg)` fractional count; `scale`; meter 0..1000 with overflow kept) checked by `bot/growth_math_check.java`, 16 of 16 (roll(1.5) mean 1.4998 over 400k trials). `item/WeaponProgress.java` (onKill, onHit, levelOf, ultimateReady, consumeUltimate). `Loadout.Slot` now holds kills, level, meter. Kills are credited to the weapon that made them through `Loadout.acting(player, slotIndex, work)`; things that land on a LATER tick re-enter it with `Loadout.deferred` / `deferred2` (bow bolts in `fireBolt`, Spin Barrage sweep bolts); sickles, beacon pulse, Piercing Laser and Spin Barrage ticks are wrapped. Only PAYING kills count (KillRewards gate), so a Magus's summons cannot be farmed to level a weapon.

* REAL BUG FOUND BY THE TEST (pre-existing, affected every multi-weapon loadout): `fireSlot` read ENTITY\_INTERACTION\_RANGE right after `setItemSlot`, but a held item's modifiers apply on the entity's equipment tick, so slot 2+ used the PREVIOUS weapon's reach (measured `slot 1 hunting_bow attr=3.5 wanted=10.0`). Fixed by `AutoAttackSystem.weaponReach` (live attribute, minus the reach of the item already in hand, plus this weapon's own `range - 3.0`; keeps the Gravedigger +0.75). Bow probe at 7 blocks: hp 1000 flat before, 1000 to 956 steady after.

* `bot/weapon_growth_test.js` W0-W8 ALL PASS (far kill credited to the bow only, point blank to the halberd, 12 level boundaries exact, meter reads back, summon kill credits nothing), 0 exceptions. Debug: `/emberfall debugweapongrowth <player> [grant <slot> <kills> <meter>]`.

* NOT YET: no weapon reads its level, no ultimate fires, no HUD for level/meter. Backups `/tmp/bak_growth1/`, jar `/tmp/emberfall_growth1.jar`.

* TEST LESSONS: a stale `one_suite.sh` server from the previous run survives and makes a rerun show the OLD result (check PIDs and the output file mtime); a kill test needs a non-vacuous proof the target died; a `|| true` in an assertion makes it unfailable.

### Broadsword growth (2026-10-02): BUILT and measured

* `combat/BroadswordSystem.java` + new `world/DelayedTasks.java` (bounded run-later queue: 4096 pending, 30 s max delay, failing task dropped). Hook `AutoAttackSystem.broadswordGrowth` after the plain hit, gated on the ACTING slot being the broadsword, splash based on the damage the hit really dealt. Per level: arc 150 to 300 degrees, +0.15 reach, splash 50% to 95%, +0.2 extra sweeps (fractional, each repeats the arc 4 ticks later and widens 12%). Ultimate Sunbrand Sweep (meter full, fires itself): full circle radius 5.0 + 0.35/level, 3.0 + 0.25/level times the hit, knock back.

* `bot/broadsword_test.js` ALL PASS (vanguard, sword only): S0 primary hit, S1 level 1 foe 90 degrees off untouched, S2 level 10 the same foe hurt, U0/U0b ring foe at 4.6 exists and the sword cannot reach it, U1 ultimate hit it (1000 to 975.99), U2 meter 999 to 30. 0 exceptions. `bot/broad_dmg_probe.js`: one plain hit = 3.94, ultimate 24.01 = 20.68 + one 3.74 splash, so the model is consistent.

* BALANCE NOTE: the vanguard sword hits for only 3.94 on an armoured horde zombie, so multiples of it are small in absolute terms. Tune the base or the multiples in the balance pass, do not hide it.

* NOT YET: HUD for level and meter, the other 7 weapons, ultimate sound/visual review on a real client. Backups `/tmp/bak_broad1/`, jar `/tmp/emberfall_broadsword1.jar`.

* TEST LESSON: a phase that reuses foes from the previous phase fires the meter early; clear everything, prove the target exists and is out of plain reach, THEN fill the meter.

### War Halberd growth (2026-10-02): BUILT, partly measured

* `combat/HalberdSystem.java` + hooks in `AutoAttackSystem.meleeCleave` (level-aware radius 2.5 to 4.0, cleave count 2.0 + 0.5/level fractional, Sundering Wake bonus still stacks on top) + `levelRandom` helper. Ultimate War Slam: radius 5.0 + 0.33/level, 4.0 + 0.5/level times the hit, max Sunder, 2 s slowness 6, shock rings and sounds. `SUNDER_DURATION_TICKS` / `SUNDER_MAX_AMPLIFIER` made package-visible. Backup /tmp/AutoAttackSystem.java.bak\_halberd.

* `bot/halberd_test.js` (juggernaut, halberd only) PROVEN: H1 level 1 leaves a foe 3.6 from the primary untouched, H2 level 10 hurts it, U0 slam target 4.6 away unreachable alone, U1 slam hurt it (1024 to 974.6), U2 slowed and meter 999 to 30, 0 exceptions. Temporary probe showed level 10 cleaves 7 to 8 foes in one swing with cap 6 to 7.

* NOT PROVEN: a clean level 1 vs level 10 cleave COUNT (H3). Lessons: `debugweapongrowth grant` only ADDS kills, never lowers a level, so measure level 1 BEFORE any grant; Minecraft clamps max\_health at 1024; a frozen tick loop needs enough steps to span a slow weapon's swing (halberd about 5 s), a 200 step cap never saw a swing; freeze BEFORE placing a pack.

* HALBERD RESULT (2026-10-02, final): `bot/halberd_test.js` ALL PASS, 0 exceptions, probe lines stripped and jar checked. H3 measured by arithmetic (no freeze): ring foes hit per swing level 1 = 2.0 (2.1, 1.9), level 10 = 6.6 (6.3, 7.0), design 6.5. Radius 2.5 to 4.0 proven (H1/H2), War Slam hits at 4.6 blocks, slows, consumes meter (999 to 30). Swing cadence measured: 1.2 s (`bot/halberd_cadence.js`). TEST LESSONS: `/tick step` does NOT advance the auto-attack loop, so a frozen swing test never sees a swing; count per-swing by arithmetic over several swings instead. The auto attack targets the NEAREST foe, so the primary must be the nearest or the ring gets the swings. `grant` only adds kills. Backups /tmp/bak\_halberd1/, jar /tmp/emberfall\_halberd1.jar.

* WEAPON STATUS: Broadsword DONE, Halberd DONE. TODO: Twin Daggers, Hunting Bow, Arcane Staff, Gravechain, Spectral Sickles, Ashen Beacon, HUD for level and meter, full regression, package with patchnotes.

### Twin Daggers growth (2026-10-02): BUILT and measured

* `combat/DaggerSystem.java` + hooks in `AutoAttackSystem.meleeDual` (after the plain hit, half of the hit when the 4th-hit double is active) and `weaponReach` (dagger level adds reach 3.0 to 5.0 through `Loadout.levelOfWeapon`). Virtual blades, no entities. Flurry: 0.2 + 0.2/level strikes (fractional), each 60% of the hit on a foe near the target, 2 ticks apart through `DelayedTasks`, Rend stack each, kills credited to the daggers through `Loadout.acting(slotIndex)`. Ultimate Blade Dance: 4 + level foes in reach, 2.5 + 0.25/level times the hit, Rend each, streak particles.

* NEW permanent counter: `Loadout.Slot.ultimates()`, bumped in `WeaponProgress.consumeUltimate`, shown as `ults=N` by `/emberfall debugweapongrowth` (plain and grant replies). Use it to prove an ultimate fired, no stdout probe needed.

* `bot/dagger_test.js` (duelist) ALL PASS, 0 exceptions, jar has no debug text: D1 level 1 leaves a foe 4.3 away untouched, D2 level 10 hurts it (reach 5.0), D3 flurry ring damage ratio 0.21 at level 1 vs 1.02 at level 10, D4 full meter fires exactly once (ults 0 to 1 in 702 ms), D5 meter 11 right after. Server line proved the dance cuts 6 foes at level 2 (4 + level).

* LESSONS: the dagger swings about 5 times a second, so counting foes hurt cannot show an ultimate (a plain level 1 window already hurts all 9); prove it with the fire counter. `/tp` from the bot cannot move it (pin and boundary put it back at 0.5 65 0.5). `debugweapongrowth grant` ADDS to the meter. A wrong theory cost many runs: when a test contradicts the log, instrument the server before theorising. Backups /tmp/bak\_dagger1/, jar /tmp/emberfall\_dagger1.jar.

* WEAPON STATUS: Broadsword, Halberd, Daggers DONE. TODO: Hunting Bow, Arcane Staff, Gravechain, Spectral Sickles, Ashen Beacon, HUD for level and meter, full regression, package with patchnotes.

### Hunting Bow growth (2026-10-02): BUILT and measured

* `combat/BowSystem.java` + hooks in `AutoAttackSystem.rangedSingle` (level read while the bow is the acting slot, extras fired at launch, meter and storm at impact), `piercingVolley` (beam radius 1.2 to 2.0 by level) and `weaponReach` (range 10 to 13 through `Loadout.levelOfWeapon`). Multishot: 0.25 extra arrows per level above 1 (fractional, 0 at level 1, 2.25 at level 10), each for 70% of the arrow at OTHER foes nearest first, 2 ticks apart through `DelayedTasks`, kills credited to the bow through `Loadout.acting(slotIndex)`. Ultimate Storm of Arrows: 8 + level arrows on the densest cluster in reach (3.5 radius), 1.5 + 0.15/level times the arrow. No new entities.

* `bot/bow_test.js` (ranger) ALL PASS, 0 exceptions: B1 level 1 leaves a foe at 11.5 untouched, B2 level 10 hurts it (range 13), B3 ring damage relative to the primary 0.48 at level 1 vs 2.14 at level 10, B4 full meter fires exactly once (ults 0 to 1 in 1404 ms), B5 meter 5 right after.

* The 0.48 BASELINE is the existing 4th-shot Piercing Volley, not multishot: 98.4 primary damage = 24.6 shots, 6.2 pierces, 2 ring foes on the 1.2 beam line = 49.2 predicted vs 47.2 measured. The level 10 result (202 to 219) sits under the 253 ceiling of pierce 2.0 plus extras, as expected.

* Backups /tmp/bak\_bow1/, jar /tmp/emberfall\_bow1.jar.

* WEAPON STATUS: Broadsword, Halberd, Daggers, Bow DONE. TODO: Arcane Staff, Gravechain, Spectral Sickles, Ashen Beacon, HUD for level and meter, full regression, package with patchnotes.

### Arcane Staff growth (2026-10-02): BUILT and measured

* `combat/StaffSystem.java` + hooks in `AutoAttackSystem`: `fireBolt` trails WITCH purple magic for the staff (crit stars stay for the bow), `detonateNova` radius 3.5 to 5.5 by level (chained novas keep 70%), `rangedAoe` reads the level while the staff is acting, fires extra bolts at OTHER foes (1.0 + 0.2 per level above 1 bolts per shot, fractional, 2.8 at level 10; each extra bolt can roll its own mini detonation and feeds the meter), every landed ordinary bolt rolls a mini detonation (4% + 2% per level, radius 2.0, 0.8 of a bolt), `weaponReach` adds 0 to 2.0 range (9 to 11). Ultimate Starfall: 6 + level stars, one every 6 ticks, each on the densest cluster still alive (radius 2.5), 2.0 + 0.2/level times the bolt, purple rings. No new entities.

* `bot/staff_test.js` (battlemage) ALL PASS, 0 exceptions: S1 level 1 leaves a foe 10.5 away untouched, S2 level 10 hurts it (range 11), S3 ring damage relative to the primary 0.15 at level 1 vs 2.52 at level 10, S4 full meter fires exactly once (ults 0 to 1 in 701 ms), S5 meter 5 right after.

* ENGINE FACT: in 1.21.11 `ParticleTypes.DRAGON_BREATH` is a `ParticleType<PowerParticleOption>`, so `sendParticles` will not take it bare; use a plain particle (SOUL\_FIRE\_FLAME, WITCH, END\_ROD).

* TEST LESSON: a ring test must be CALCULATED, not eyeballed. A 4 point circle always has a near point at offset minus radius; the first ring put one foe at 2.7 (inside the level 1 nova) and one at 6.7 (outside level 10's). Use 3 points on the far side (4.65, 4.65, 5.3 from the primary) so the baseline is clean.

* Backups /tmp/bak\_staff1/, jar /tmp/emberfall\_staff1.jar.

* WEAPON STATUS: Broadsword, Halberd, Daggers, Bow, Staff DONE. TODO: Gravechain, Spectral Sickles, Ashen Beacon, HUD for level and meter, full regression, package with patchnotes.

### Gravechain growth (2026-10-02): BUILT and measured

* `combat/ChainSystem.java` + hooks in `AutoAttackSystem.meleeHook` (after the yank: meter, extra chains, vortex), `gatherNearby` (now takes the chain level as an argument: radius 6.0 to 9.0, cap 4 to 8 with a fractional roll) and `weaponReach` (chain reach 4.5 to 7.0 through `Loadout.levelOfWeapon`). `pullToward` is now package visible. Extra chains: 0.25 per level above 1 (fractional, 2.25 at level 10), each hooks ANOTHER foe within reach for 65% of the hit, yanks it, soul-fire line, 2 ticks apart. Ultimate Grave Vortex: 15 pulses, one every 4 ticks (3 s), radius 8.0 + 0.3/level, 1.0 + 0.15/level times the hit per pulse, drags foes to the centre, soul-fire spiral. The Grave Anchor Tome pile record is untouched. No new entities.

* `bot/chain_test.js` (gravedigger) ALL PASS: G1 level 1 leaves a foe 6.5 away untouched, G2 level 10 hurts it, G3 other-foe damage relative to the primary 0.00 at level 1 vs 1.39 at level 10, G4 full meter fires exactly once (ults 0 to 1 in 1400 ms), G5 meter 11 after.

* MEASURED, not assumed: the Gravedigger holding the chain has interaction range 5.25 (3 base + 1.5 chain + 0.75 character).

* TEST LESSON: the vortex is 15 queued pulses over 3 s, so they outlive `clear()` and hit the NEXT phase's foe (G1 read 1024 to 1012 for that reason, not a reach bug). After any timed ultimate, sleep past its whole duration before the next measurement.

* A BUG I CAUGHT IN MY OWN PATCH before the build: `gatherNearby` read the level from `target` (a mob, never a player), which would always be level 1. Its only caller has `player`, so the level is now an argument.

* Backups /tmp/bak\_chain1/, jar /tmp/emberfall\_chain1.jar.

* WEAPON STATUS: Broadsword, Halberd, Daggers, Bow, Staff, Chain DONE. TODO: Spectral Sickles, Ashen Beacon, HUD for level and meter, full regression, package with patchnotes.

### Spectral Sickles growth (2026-10-02): BUILT and measured

* `combat/SickleSystem.java` (maths and Harvest state) + a reshaped `OrbitWeaponSystem.tickOrbit`. Blades: 2.0 + 0.3 per level, so 4.7 at level 10; the whole part always orbits and the fractional blade is on for that share of each lap, decided by the ring's phase (steady, no flicker), on FIXED evenly spaced slots so it never moves the others. Orbit radius 2.2 to 3.6, contact radius 0.9 to 1.3, spin 8 to 14 degrees a tick, hard cap 12 blades. Ultimate Reaper's Harvest: 80 ticks (4 s), blades double, spin x2.5, ring breathes 1.4 to 2.0 times its radius, per target cooldown 8 to 3, soul particles. It starts on the first cut after the meter fills, never while one runs. Widening Gyre Tome logic kept exactly.

* PERFORMANCE: the old loop ran one entity query PER BLADE per tick; it is now ONE query per tick for the whole ring, each blade plain distance maths, so cost is flat however many blades (nine at the Harvest).

* `bot/sickle_test.js` (reaper) ALL PASS: K1 level 1 leaves a foe at 3.6 untouched, K2 level 10 cuts it (26.9), K3 one foe at 2.7 takes 17.3 at level 1 vs 40.3 at level 10 (2.33x), K4 full meter starts the Harvest once (ults 0 to 1), K5 meter 11 after, K6 a foe at 3.4 (outside the normal 3.1 reach) took 0 before and 15.4 during the Harvest.

* TEST LESSON: my first K3 put the foe at 2.2, ON the level 1 ring but INSIDE the level 10 ring (radius 3.6, contact 1.3, so it covers 2.3 to 4.9), and read 0.0 at level 10. The weapon was right, the geometry was wrong. When a weapon's reach GROWS, compute each level's covered band and put the probe where BOTH overlap (here 2.3 to 3.1, used 2.7).

* Backups /tmp/bak\_sickle1/, jar /tmp/emberfall\_sickle1.jar.

* WEAPON STATUS: Broadsword, Halberd, Daggers, Bow, Staff, Chain, Sickles DONE. TODO: Ashen Beacon, HUD for level and meter, full regression, package with patchnotes.

### Ashen Beacon growth (2026-10-02): BUILT and measured. ALL 8 WEAPONS NOW HAVE GROWTH.

* `combat/BeaconSystem.java` (maths, satellites, Pyre Nova) + hooks in `TotemWeaponSystem` (record carries `weaponLevel`, captured at deploy; embers and kill embers inherit it; interval, lifespan and radius read from it; growth only on a MAIN beacon's ordinary pulse, never the Tome's embers or final burst) and `AutoAttackSystem.weaponReach` (range 8 to 11). THE DESIGN RULE IS KEPT: one main beacon at a time, record list bound unchanged (1 main, 1 ember, 1 kill ember). Pulse radius 3.0 to 5.0, interval 20 to 12 ticks, lifespan 200 to 280. SATELLITES (fractional, 0.3 per level above 1, 0 at level 1, 2.7 at level 10): instant small fire bursts (radius 1.8, 60%) on foes the main pulse did NOT reach, so no records added. Foes caught are set alight (2 s). Ultimate Pyre Nova: 4 expanding bands, 6 ticks apart, 3 out to 9 + 0.3/level, 1.6 + 0.2/level times the pulse, starts from the pulse that filled the meter. No new entities.

* `bot/beacon_test.js` (emberwarden) ALL PASS, 0 exceptions: A1 a foe 4.0 from the beacon untouched at level 1 (1024 to 1024), A2 hurt at level 10 (lost 44), A3a a foe 6.5 away untouched at level 1 and A3 reached by satellites at level 10 (lost 26), A4 full meter fires once (ults 0 to 1 in 1402 ms), A5 meter 5 after, A6 the pulsed foe has Fire 24.

* A NAME CLASH I CAUGHT BEFORE BUILDING: `Beacon` already had a field `level` of type ServerLevel (the world). Adding `int level` would have been a duplicate and every `original.level` I wrote would have meant the world. Renamed to `weaponLevel`. Grep a record's fields before adding one.

* TEST NOTE: a beacon is a no-op while one is live (10 to 14 s), so every phase ends with clear plus a 16 s burn out, else the old beacon pulses the next phase's foes.

* Backups /tmp/bak\_beacon1/, jar /tmp/emberfall\_beacon1.jar.

* WEAPON STATUS: Broadsword, Halberd, Daggers, Bow, Staff, Chain, Sickles, Beacon ALL DONE. TODO: HUD for level and meter, full regression (42 suites), package with patchnotes.

### HUD: weapon level and ultimate meter (2026-10-02): BUILT, server and wire PROVEN, drawing NOT seen

* `HudStatePayload.WeaponEntry` is now (id, name, level, meterStep); `HudSync.build` fills it from `Loadout.slot(i)`. The meter is QUANTISED to 20 steps (5% each) so the once-a-second "send only on change" rule still holds in a fight: the payload changes only when the bar visibly moves. `describeLast` keeps the old leading text and appends `#id@level/step,` so older checks keep their meaning.

* Client `LoadoutHud`: a thin meter bar on the inside bottom edge of each weapon cell (dark track, orange fill, gold and breathing once a second when full) and a `Lv N` badge (gold at level 10). Two layouts chosen by a computed rule: ROOMY (gap between name and bar at least one text line, cells of about 28 px and up): name on top, badge beneath. TIGHT (smaller cells): one line, the name shortened so the badge fits. My first version overlapped the name below 30 px (even collided with the bar at 30); a layout calculation caught it before any run.

* PROVEN: `bot/hud_level_test.js` ALL PASS W1 to W7 (30 byte payload decodes EXACT, fresh level 1 step 0, half meter step 10 with exactly ONE new payload, 6 s idle sends nothing, level 10 read, 999 of 1000 reads step 19, 1000 reads step 20). `bot/hud_test.js` H0 to H5 still pass, `bot/hud_wire.js` updated and EXACT (30/30, 56/56, 4/4). 0 exceptions.

* NOT VERIFIED: how it LOOKS. No graphical client exists in the sandbox, so the fills, the text fit and the full-meter glow are unseen. The layout maths is a calculation, not a screenshot.

* TEST LESSONS: (1) `debugweapongrowth grant <slot> <kills> <meter>` ADDS kills but SETS the meter, so a grant of 1 after 999 gives 1, not 1000. (2) `WeaponGrowth.ready` needs meter >= 1000, so 999 is correctly NOT full and the bar must read 19: a bar that showed full at 999 would promise an ultimate that will not fire. (3) An old decoder reporting MISMATCH after a layout change is the check working; update it rather than deleting it.

* Backups /tmp/bak\_hud1/, jar /tmp/emberfall\_hud1.jar.

* STATUS: ALL 8 WEAPONS and the HUD DONE. TODO: full 42-suite regression (bot/regress4.sh), package with patchnotes (WHATS\_NEW\.txt).

### STAR BIT CRASH FOUND AND FIXED (2026-10-02): a lethal star hit threw ConcurrentModificationException

* FOUND BY ACCIDENT in a gold\_reroll\_test server log: `java.util.ConcurrentModificationException` at `StarBitLob.tickAll` (line 256) right after `Slot 0 abandoned`. The suite's own "log problems: 0" counter MISSED it (it only counts chat lines), so that counter is not evidence of a clean server.

* MECHANISM (read from the code, then REPRODUCED): `tickAll` iterates `STARS`; a star that hits a player calls `victim.hurtServer(...)`. A lethal hit on a run player ends the run (`endRunInsteadOfDying` then `finishRun`), the arena is torn down, the Magus or Horde Witch is discarded, and its `remove()` calls `StarBitLob.clearFor(this)`, which edits `STARS` and `SHARDS` while `tickAll` is still inside its loop. The next `it.remove()` throws. `tickShards` has the identical hazard (`hurtServer` on a player, then `it.remove()`).

* REPRODUCED ON DEMAND with `bot/star_lethal_test.js`: a survival player with NO protection, held at 1 hp under a real Umbral Magus. BEFORE the fix: 1 exception, same stack. AFTER: 0 exceptions, and the server log shows the star lob and the run ending one second later. Two fixed runs were checked in their server logs (0 exceptions, run ended by the lethal hit); four repeats reported exceptions 0 but only the last run's log survives, so 2 of 4 are log-verified.

* FIX (`StarBitLob.java`, backup /tmp/StarBitLob.java.bak\_cme): a static `ticking` flag set around the whole tick (try/finally). While it is set, `clearFor` only MARKS (`Star.end()`, new `Shard.dead`) and the tick removes them itself; outside a tick `clearFor` behaves exactly as before. `tickShards` skips `dead` shards. Build fresh, jar 34,354,153 bytes.

* TEST LESSONS: (1) `/data merge entity @s {Health:1.0f}` CANNOT edit a player ("Unable to modify player data"), so my first lethal test silently never killed anyone; lower hp with `/damage @s <n> minecraft:generic`. (2) The bot's `death` event did not fire even when the run ended by the lethal hit, so judge by the server log (`meta-currency` line plus the exception count). (3) A run that ends by a lethal hit does NOT kill the player: `endRunInsteadOfDying` heals and calls `removeAllEffects()`, which also strips a test's resistance.

* NOT PROVEN: the race is intermittent by nature. 1 broken vs 2 log-verified fixed runs is a small sample; the stack trace and the mechanism are the stronger argument.

* GOLD TEST (gold\_reroll\_test) ROOT CAUSES, all TEST issues, 0 mod bugs found: (1) G0 to G2 failed on a stale jar until the map was prebuilt (PREBUILD=1); (2) G3/G4 asserted an exact `gold === 0` but a live run pays stray kill coins (price step 30 to 60 is the exact proof); (3) G5/G6 read 0 because the unprotected bot was killed by the live wave after about 40 to 70 s and `endRunInsteadOfDying` ended the run (fix: resistance 4 plus regeneration 4 at start); (4) G6 once read `[ember_touch]` at 5 stacks: the 5 granted tomes level the bot up and a real level-up screen rolled before the 5th stack landed (the generator itself is proven by cap\_test: `ROLL []` at stack 5); fix: skip screens at levels 1 to 4 first. VERIFIED: 6 of 6 fresh-world runs of the fixed test passed 8/8 with 0 server exceptions (before the fixes 3 of 6 failed); the server log confirms Resistance and Regeneration applied and each run ended exactly once (69 to 73 s, the test's own leave).
