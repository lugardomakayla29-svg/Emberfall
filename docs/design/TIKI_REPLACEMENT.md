# Tiki Slime (replaces Tiki Magma)

Status: DESIGN PROPOSAL, 2026-10-07 18:10 CT. NOT built. NO ICE, NO FROST, NO BURROWING: an earlier version of this file (Frostbloom) was wrong and is gone.
Owner's words: "a slime version of tiki magmas", "same look/rotations, new slime abilities", all three tiers, and the pole keeps its look (player-head masks, no roof,
facing the player). It ties to the new slime boss, Broodtide (docs/PLAN_broodtide.md), whose "virus slime" look the Tiki Slime shares.

## What stays (the Tiki identity)
The tall pole of 1 / 3 / 4 player-head masks that sways and faces the player, the three tiers (Fodder, Elite, Corrupted), the spawn weight 0.15 and the wave slots,
the levitate-toward-you hunt, the shriek and the lane attack (now a goo jet, see Decisions). Roof stays removed. Displays only for the pole (the standing entity-count rule).

## What changes: the base is a real SLIME, not a MagmaCube
Engine facts (VERIFIED in this repo, see PinkSlime): `Slime.setSize` clamps 1..127 and OVERWRITES max health, speed and damage, so every size change must re-apply the
Tiki's own stats (PinkSlime.applySize is the pattern). A dying slime of size > 1 SPLITS into copies, so any Tiki Slime above size 1 must override `remove` (or stay size 1
and use Attributes.SCALE, as Tiki Magma does today). The vanilla slime outer layer hard-codes the green texture, so a custom colour needs its own renderer layer
(PinkSlimeRenderer is the template). Colour: the Broodtide green-teal, NOT pink (pink is the Pink Slime) and NOT white or blue.

## Ideas (every move gets a wind-up of at least 0.5 s and shares a constant with its damage, as Sentinel and Reaver do)
1. **Gloop Totem (all tiers, the identity).** The slime body is a translucent shell and the masks sit INSIDE a glob that creeps up the pole as it takes damage. Visual only
   (a scale on existing displays), zero new entities. Reads as "the totem is being eaten".
2. **Bounce Shriek (replaces the fire shriek).** It hops, and the LANDING is the shriek: a telegraphed ring on the ground, then a goo burst that slows (no fire). Reuses the
   Tiki's landing shove and `Fx.telegraphRing`.
3. **Mask Spit (Elite and Corrupted).** A mask lobs a goo ball at you (PinkSlime spit pattern: wind-up, speed, hit radius, lifetime all constants). Fodder never spits.
4. **Goo Trail (all tiers).** Leaves a short, fading, capped trail that slows you. Reuse the `PinkPools` idea with its own cap, so Tiki Slimes and Pink Slimes share one
   pool budget instead of stacking.
5. **Split Totem (Corrupted only).** At 50% it sheds ONE small Tiki Slime (a fodder-sized mob wearing one mask) and at 25% one more. Hard cap of 2 per Corrupted so a wave
   cannot snowball, and each shed mask goes dark on the pole so the player SEES why.
6. **Swallow Mask (Corrupted, ties to Broodtide).** It reaches for a nearby fodder mob, absorbs it into the shell for 1.5 s and spits it back as a Brood-Kin (the exact
   mechanic Broodtide prototypes). This is the link to the new boss, so build it only after Broodtide's Prototype A is proven.
7. **Tier look and size.** Fodder = one small slime, 1 mask, a small gloop. Elite = larger slime, 3 masks, drips. Corrupted = largest, 4 masks, a dark core visible inside.

## Tiers (same counts as today so the wave tables do not change)
| Tier | Body | Masks | Moves | Notes |
|---|---|---|---|---|
| Fodder | small slime | 1 | hop, Bounce Shriek, Goo Trail | no spit, no laser |
| Elite | medium slime | 3 | the above + Mask Spit + Goo Jet | the jet keeps the old laser's wind-up, lane width and range |
| Corrupted | large slime | 4 | the above + Split Totem + Swallow Mask | the only tier that sheds or swallows |

## Decisions (owner, 2026-10-07 18:15 CT)
1. **The laser becomes a GOO JET.** Same lane, same wind-up and the same shared constant for the telegraph and the damage as the old laser (so the existing lane maths and the
   tier recharge difference carry over); only the look and sound change: a green-teal stream of goo that leaves a short fading slow-patch at the far end (counts against
   the shared goo pool cap). No fire, no beam.
2. **Swallow Mask / Brood-Kin link to Broodtide: YES.** Corrupted Tiki Slimes swallow a nearby fodder mob and spit it back as a Brood-Kin. It shares Broodtide's
   rules: the Brood-Kin cap of 6 counts Tiki-made ones too, the swallowed mob is hidden (not deleted) and returns with its AI and team intact. It is built AFTER Broodtide's
   Prototype A proves hide-and-reveal; if the prototype fails, this move is cut and the rest of the Tiki Slime ships unchanged.
3. **Colour: Broodtide green-teal (not pink, not vanilla green).**

## Build order (each step proven before the next; every check has a mutant that goes red)
1. Pure `TikiSlimeRules`: tier stats, shriek ring, spit timing, shed thresholds and caps. Pure check first.
2. Prove a slime base: size, stats re-applied, no split on death, hitbox, entity count at or below today's Tiki. Live.
3. Fodder, then Elite, then Corrupted. Live test each.
4. Swap Tiki Magma out of WaveDirector / MobSpawner / ModEntities, delete the 5 Tiki files, re-run the wave and spawn-table tests.
