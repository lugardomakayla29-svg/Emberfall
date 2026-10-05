# PLAN: designed expedition map + new shrines (started 2026-09-30)
Spec is in notes/emberfall/boss-concepts.md ("Expedition map rework" and "Shrine trigger + Challenge detail").

## Verified engine facts this plan rests on
- RunManager.pasteArena(server, structureId) already pastes a template into slot N of emberfall:expedition (void flat, SLOT_SPACING 256, BASE_Y 64) and scans/strips emberfall:marker blocks. A 200 wide circle fits inside a 256 slot.
- In-place runs (startInPlace + TerrainScanner, ARENA_RADIUS 28) are what the user wants to LEAVE. They must be kept working until the new path is proven, then switched.
- NO block protection exists anywhere in the mod. "Unbreakable by any means" is new work.
- Shrines today: ShrineManager, proximity trigger radius 2.5, pedestal + head display. Fabric API provides AttackBlockCallback, AttackEntityCallback, UseBlockCallback, PlayerBlockBreakEvents.
- Hub click pattern already proven: invisible Interaction entity + tag + UseEntityCallback (HubInteractions). Auto-attack only targets Mob, so an Interaction can never be auto-hit.
- Schematics: Sponge v3, WorldEdit 7.4.2, DataVersion 4671. Stray grass_block = copy markers (Challenge 2, Curse 1, Greed 2). Curse has a redstone_block core; Greed has a skull block entity.

## STEPS (in order, each proven before the next)
1. DONE (measured 2026-09-30, fresh world each, 188,568-block disc, 0 exceptions):
   blocks/tick 200->1.98 ms avg, 500->3.77-4.45, 1000->6.63, 2000->14.82, 3000->23.12.
   Worst tick was 92-359 ms at EVERY rate, and the slowtick log ties each one to chunk load/unload (598 chunks in one tick at start; only 5 of 375 ticks over 50 ms).
   DECISION: block writes are cheap (500/tick = about 4 ms). Chunk creation is the cost. So the map is built ONCE, ahead of time (server start or first use), at about 500 blocks/tick with the chunks force-loaded first, and NEVER when a run starts. Runs then only need slot reuse and a reset of what a run changed.
2. World shell: base (6 thick), 5-thick 20-high jagged non-climbable ring. Protection layer: block break, place, explosions, pistons, fluids, fire, mob griefing, creative/op break, /setblock is admin-only.
3. Landforms from mapwork/layout.py (5 hills, 2 ramps, max step 1, 70.7% flat, verified). Then trees, boulders, plains houses on a fixed layout.
4. Shrines: convert 3 .schem (strip grass markers, keep air from overwriting, keep skull block entity), place at fixed map spots, punch-to-trigger via Interaction hotspot.
5. New effects: Challenge horde + Silver/Gold/XP reward, Boss Curse multipliers, Statue of Greed +1..+5 difficulty scale.
6. Switch runs to the new map; keep in-place as fallback until 100% proven.
7. Hydra replacement + Devourer expansion (design docs already saved).
8. Full regression, new tests for each step, 0 exceptions, then package.

## Cannot be verified here (needs the user's real client)
- How the map, statues and houses LOOK. I check slopes, clearances, overlaps and reachability by number only.
- Whether fights feel fun.

## Open question
- Greed +5 selection UI. Default if unanswered: each punch adds +1, capped at +5.

## Storage model (decided 2026-09-30, from measured numbers + code reading)
- Design doc 4.1: a run is "a freshly-prepared instance". RunManager already has slots (SLOT_SPACING 256 fits the 200 circle) and joinPlayer.
- ONLY 4 features write blocks during a run, all via RunManager.setBlockJournaled: shrine props, Hydra AcidDais, Umbral Magus cauldron, marker strip. BlockJournal persists to disk and recovers after a crash.
- So: build each slot's map ONCE (about 25 s at 500 blocks/tick, chunks preloaded), keep it, and let the journal restore the few changed blocks at run end. NEVER rebuild per run.
- Build a slot lazily the first time it is needed, during a loading moment, not mid-run. Reuse freed slots (freeSlots already does this).
- The Hydra AcidDais and old shrine pedestals will be REPLACED (Hydra is being replaced anyway; shrines become the 3 schematics).
