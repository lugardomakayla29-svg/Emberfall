---
title: Hydra replacement boss design (Ember Guardian) and Devourer expansion plan
summary: Design draft for replacing the Hydra with a multi-phase boss on the new 200-block circle map, plus a menace and mechanics pass for the Devourer. Facts verified from source are separated from proposals. Nothing here is built yet.
---

# Hydra replacement and Devourer expansion (design draft, 2026-09-30)

Status of this page: the Ember Guardian part was approved and BUILT (2026-09-30 to 2026-10-01; final numbers are in main.md, several differ from this draft, e.g. Cinderfall is 4 rows over one shared strip). The Devourer expansion part is still a proposal. Original note: a proposal for the user to approve. Every number marked (source) was read from the mod; every number marked (proposal) is a starting value to be tuned by measurement, not a measured result. Related notes: [boss-concepts.md](./boss-concepts.md) (Devourer as built, Reflection reserved, map rework) and [main.md](./main.md).

## Why replace the Hydra

Verified from `entity/HydraBrain.java` (source):
* 300 max health, one attack: a telegraphed radial slam, radius 4.5, every 11 to 16 s (220 to 320 ticks), 16 tick wind-up.
* The only phase mechanic is losing a head at 75, 50 and 25 percent. It changes looks, not what the player must do.
* It sits on a 7x7 dais inside an acid moat, built with `AcidDais`, which writes blocks during the run. The new map is meant to be unbreakable and pre-built, so this pattern no longer fits.

So the fight is one ability on a timer against a stationary target. The player never has to move differently in phase 3 than in phase 1.

## Engine facts the design relies on (source)

* Telegraph tools already exist in `combat/Fx.java`: ring, zone, fill (progress), cone, line, square, square fill, target marker. No new telegraph code is needed.
* Play radius is 88 on the 200 block circle (`CircleBoundary.PLAY_RADIUS`), with a soft push then a hard return past it.
* Cost rule (measured earlier): telegraphs are drawn only during the wind-up and no faster than every 4 ticks, because a cone redrawn through a 3 s attack cost 23 packets per second.
* Boss parts can be hittable or not; the Devourer already gates behaviour by phase and guards against logic running after death (`defeated` flag).

## Proposal: the Ember Guardian

The user's own spec from the map rework asked for an Ember Guardian with a foot-stomp rupture: a cone shaped ground crack from its foot, 6 blocks long, with particles and camera shake. The design below builds on that.

Core idea: the fight changes the player's job in each phase, instead of only raising numbers.

### Structure (proposal)
* One large boss entity (the hitbox and health) plus display parts for a stone body, two fists and a chest core made of display entities. Target: 6 or fewer entities, since entity count is a standing priority.
* Health: start at 600 for a solo run (proposal, the Hydra's 300 was too short-lived). Tune by measuring time to kill with the eight starter weapons.

### Phase 1, "Awakening" (100 to 70 percent)
* Foot stomp: 6 block cone from the foot, 0.8 s telegraph using `telegraphCone`, ground crack particles, camera shake. This is the user's requested attack.
* Ground pound ring: radius 5, every 9 to 12 s, punishes standing next to it.
* The core is armoured: damage is heavily reduced until a fist is destroyed. This is the sub-part gating pattern decided earlier.

### Phase 2, "Rupture" (70 to 35 percent)
* Arena rule change: lava cracks open in a pattern from the boss outward. Because the map must be unbreakable, these are particles and damage zones, not block edits.
* Cycling attack offsets: stomp cone angle rotates 30 degrees each use so the safe spot moves.
* Fists become separate targets that must be broken to open a damage window on the core (about 6 s), then they regrow.

### Phase 3, "Meltdown" (35 to 0 percent)
* Faster stomp cadence, and a slow expanding ring the player must jump or outrun.
* Core is permanently exposed but the boss now also fires a slow lava lob at the player position, so standing still to hit it costs health.
* Enrage timer of 45 s so it cannot be stalled.

## Devourer expansion (proposal)

Facts (source): 785 lines in `DevourerBrain.java`; three phases at 66 and 33 percent; up to 4 minis; burrow then dash; leap attack added. The user has said yes to a coil ring, about 12 blocks long, and wants it scarier and more menacing.

Proposed additions:
* Coil ring: the worm circles the player at a fixed radius, then tightens over about 4 s. The player escapes by jumping over the body or leaving through the shrinking gap. Length 12 blocks unless the existing body is already longer.
* Menace pass: head glow that ramps per phase, low rumble with a rising pitch during the burrow telegraph, small dust trail along the burrow path.
* Arena rule: in phase 3 the surface cracks along the dash lane after each dash and stays hazardous for about 5 s (particles and a damage zone, no block changes).

## Open questions for the user
1. Is 600 health right for the guardian, or should boss health scale with the Boss Curse shrine (1.2 to 1.5x) as already specified?
2. Should the guardian replace the Hydra entirely, or become boss 3 so the Hydra stays for now?
3. Lava zones as particles plus damage areas, or do you want a real fluid effect on a removable layer? (Real blocks conflict with the unbreakable map.)

## Not verified
Nothing here was run. Damage numbers, cone angles and timings are starting points. The visual look on a graphical client is unverified, as with all earlier boss visuals.

## Update 2026-09-30 (after the user said "yes and replace the hydra")

The user approved replacing the Hydra. Stage 1 is built and tested (entity, registration, fight swap, lifecycle; 6 of 6 checks, 0 exceptions).

CORRECTION to the phase text above: it treats the fists as display-only parts tracked by player proximity. That is wrong. Displays have no hitbox, and the auto-weapon only targets nearby emberfall Mobs, so a display fist cannot be targeted. The fuller design in the project folder (`boss1_replacement_design.md`, the "Cinder Warden") is the one being built: four pylons that are real Mobs marked with the composite-parts system, the boss invulnerable while any pylon is lit (with a visible beam as the cause), three phases, and a fallback when the terrain offers fewer than four good spots. The health figure of 600 is kept for now and is unmeasured.
