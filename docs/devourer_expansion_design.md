# Devourer expansion: DESIGN PROPOSAL (not built, awaiting your approval)

Goal (your words): even more scary and menacing than boss 1. Order: Hydra replacement first, Devourer after.
Every mechanic below is tied to a source read first-hand (research_synthesis_verified.md) or to a measured fact in this repo.

## What is wrong now (measured from DevourerBrain.java and WormBody.java)
- The worm is 6 displays (head + 5 segments, spacing 1.5), about 9 blocks long. The references are 82 to 102 segments. It reads as a snake, not a monster.
- Only the head is dangerous. Three attacks (dash, leap, burrow burst), all one at a time, no rotation, three hp steps only.
- Mini-worms are capped at 4 and there is no body-wide threat.

## The five levers (each one from a real reference)

### 1. LENGTH (Devourer of Gods 82 segments, Destroyer 82)
- Grow from 6 to a target of N parts. N is NOT chosen yet: it must be MEASURED. Plan: run cost_test-style packet counting with 6, 14, 24 and 32 parts while the worm moves, then pick the largest N that keeps the extra packets/s inside an agreed budget.
- WormBody is already display-only, one entity per part and a cheap 2-pass constraint loop, so entity cost is exactly N. Body parts shrink toward the tail (existing SEGMENT_SCALES idea).

### 2. A MOOD ROTATION with a colour tell (Devourer of Gods: passive blue 15 s, aggressive magenta 15 s, laser purple 5 s)
- Replace the fixed surface > dive > burrow loop with three moods:
  - STALK (slow, precise, close): circles and snaps at the player. Reads as "it is hunting you".
  - RUSH (fast, sloppy tracking): the existing dash and leap live here, with the current telegraphs kept.
  - COIL (short): the body wraps a ring around the player and closes in, leaving ONE gap that moves. This is the Destroyer's "surround the player with its segments".
- Tell: head glow colour (WormBody.setThreat already sets glow colour and full-bright, so this is nearly free) plus a distinct sound per mood. Timings and the exact rotation are set in the build, then measured.

### 3. BODY-WIDE PRESSURE (Destroyer: every segment fires; Calamity: laser grid)
- In COIL, segments fire short telegraphed lines inward at the ring centre. The player survives by standing in the moving gap or by leaving the ring early.
- Rule from Megabonk's page: every attack needs a wind-up of at least 0.5 s. Each segment line is drawn with the existing Fx line telegraph for a wind-up only (telegraph cost rule from memory: wind-up only, every 4 ticks or slower).

### 4. PROBES / SHEDDING SEGMENTS (Destroyer: 1 in 25 per damaged segment, once each, red light goes off)
- Replace "mini-worms capped at 4" with shed segments: when the boss takes damage a segment can break off as a small independent hunter (reuse DevourerSpawn). The lit part goes dark on the body so the player can SEE what happened.
- Hard cap on live probes (from the measured entity budget), and each part can shed once so the fight cannot snowball.

### 5. RIFT AMBUSH + FINAL-PHASE ESCALATION (Devourer of Gods: rift with arrow, chained lunges at 25%)
- Phase 3 adds a burst that is ANNOUNCED: an on-screen marker points at the surface point (your current crack telegraph already exists, we extend it to a ring of ground cracks).
- Below 25%: the fight opens with a COIL, and the leap is chained twice with a shorter gap. Speed ramps in small steps (Calamity ramps at 92, 84, 76, 68%) instead of only at 66/33.

## Things I will NOT copy
- Level-scaled hp or an unbeatable fail-state boss (Vampire Survivors Reaper).
- Disabling the player's weapons (Megabonk players called it bad design).
- Any number I cannot source. Timings above are TARGETS to be tuned by measurement, not facts.

## Verification plan (same standard as the rest of the mod)
- Entity count: exactly N displays plus probes, checked against a baseline read BEFORE the spawn (memory lesson).
- Packets/s at each length (cost_test.js), reported as numbers.
- Chain spacing (worm_chain_test.js), leap height (leap_profile.js), zero exceptions in the server log.
- NOT verifiable here: how it looks on a real client, so you must look at it.

## Decisions I need from you (short)
1. Target length: pick after I measure, or a range you prefer?
2. COIL ring: the play area is 57 x 57 blocks (TerrainScanner.ARENA_RADIUS = 28), so a ring around the player fits. Still your call whether you want it.


## COIL: MEASURED CORRECTIONS (2026-10-01, from bot/coil_sim.py, an exact port of WormBody.update with 20 parts)
1. DRIVING THE HEAD ROUND A CIRCLE DOES NOT MAKE A RING. The chain takes the inside chord of a tight turn: at R 5, 6 and 7, speeds 0.5 and 0.7 blocks/tick, 120 to 400 ticks, the body radius minimum stayed under 1 block (the tail crosses the player's position) and the tail-to-head gap wandered 5 to 15 blocks. It never converges.
2. THEREFORE the ring is made by DIRECT PLACEMENT: a new WormBody mode puts part i at angle a0 - i*spacing/R on the circle. Neighbour chord 1.49 to 1.50 (spacing 1.5), so it reads as one body.
3. THE RING CANNOT CLOSE TO 3. With 20 parts at spacing 1.5 the body covers 327 deg at R 5 (gap 1.4 blocks, NOT walkable), 272 deg at R 6 (gap 7.7 blocks), 251 deg at R 6.5 (10.8 blocks). Below R 5 the parts overlap (20 parts evenly round R 4 need spacing 1.26).
4. SPEC NOW: encircle at R 6 with a 7.7 block gap, the gap ROTATES around the player, closing only to R 5.5 (gap 4.6 blocks, still walkable). The old 'close 6 to 3' is dropped.
