# Tiki Magma replacement: the Frostbloom colony

Status: DESIGN, decided by Koda while the owner is away. Not built. AUTHORITY (corrected 2026-10-07 16:55 CT): the owner's 10-06 instruction was to replace Tiki Magma completely, all tiers, 'your call' on the design. So the REPLACEMENT is approved; the Frostbloom DESIGN is mine and he has not seen it. Build it, then show him; do not wait for approval first. Owner brief: "I don't want Tiki Magma anymore, completely fresh and
new replacement for all tiers, similar design but like a polar opposite."

## What the Tiki is today (from the code)
A magma cube (the hitbox) carrying a TALL vertical pole of 1 to 4 player-head masks, a dark-oak roof, levitating toward the player,
three tiers (Fodder 1 head, Elite 3, Corrupted 4). Hot, tall, stacked, floating, hunting from above. 1,043 lines in 5 files
(TikiMagma, TikiCube, TikiSegment, TikiLevitate, TikiVoice), wired in WaveDirector (3 spawn spots + weight 0.15), MobSpawner,
ModEntities, one debug command.

## The opposite, axis by axis
| Tiki Magma | Frostbloom |
|---|---|
| fire / magma | cold / ice, on a SLIME (snow-white tint) |
| tall vertical pole | LOW, wide cluster that hugs the ground |
| stacked heads, one above another | ring of crystals fanned out around the body |
| levitates toward you | BURROWS: sinks into the ground and moves unseen, surfaces under you |
| hunts from above | ambushes from below |
| masks with faces | faceless crystals that grow with damage taken |

## Tiers (same count as before so the wave tables do not change)
- **Fodder: Frostbud.** One small snow slime, 3 crystals. Burrows, surfaces next to you, one bite, then burrows again.
- **Elite: Frostbloom.** 5 crystals in a ring. Every 6 s it freezes the ground in a 4-block circle (slowness, no damage); standing in it
  while it surfaces is what hurts.
- **Corrupted: Rimeheart.** 7 crystals plus a dark core. Crystals shatter outward when it is hit hard (a visible, dodgeable burst) and
  regrow, so the fight has a rhythm instead of a damage sponge.

## Rules it must obey (standing rules)
- Displays only for the crystals, one real slime as the hitbox: entity count at or below the Tiki's 4 for the fodder.
- Faces the player while surfaced, invisible to the tab list, no new per-tick scans (reuse the existing RunMobAggro hunter sweep).
- Every telegraph shares a constant with its damage (as Sentinel and Reaver do) so warning and hit cannot drift.

## Build order
1. `FrostbloomRules` (pure): tier stats, burrow/surface timing, freeze circle, shatter threshold. Pure check first.
2. Entity + renderer for the Frostbud only. Live test: burrows, surfaces, bites, entity count.
3. Elite, then Corrupted. Live test each.
4. Swap the Tiki out of WaveDirector / MobSpawner / ModEntities, delete the 5 Tiki files, re-run the wave and spawn-table tests.

## Step 0, before any tier work: prove the burrow on ONE slime (engine unknowns, no guessing)
A burrowing mob is new in this mod. Settle these with a live prototype first; each gets a test that can fail:
1. Can a real Slime be untargetable and unseen while burrowed (invisible + no collision + not a valid weapon target) and become targetable and hittable again on surfacing? Check it with the same client-packet method as `bot_scout_hidden_test` (does a watching client get the spawn or the movement?).
2. Can it travel through blocks without suffocation damage (`noPhysics`), and what do `RunMobPurge`, the wave counter and `RunMobAggro.HUNTERS` do with a mob in that state? Read each before assuming.
3. Is the surface point exactly under the player (ambush) and is the telegraph (ground crack, particles) timed by a constant shared with the damage, as Sentinel and Reaver do?
4. Entity count: confirm the crystals are displays only and the fodder stays at or below the Tiki's 4.
Only when 1 to 4 are green on a live server do the tiers get built (Frostbud, then Frostbloom, then Rimeheart).
