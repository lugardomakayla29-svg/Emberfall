# Boss 1 replacement (Hydra): DESIGN PROPOSAL, not built, awaiting approval

Problem (measured from HydraBrain.java / HydraBossFight.java / AcidDais.java):
- A Ravager brain (300 hp) that mostly stands on a 7x7 blackstone dais inside an 11x11 water moat.
- Its only real attack is one slam every 11 to 16 s (radius 4.5, telegraph 16 ticks). The four heads each lob a SmallFireball every 3 s and lose their hitbox at 75/50/25% hp.
- Result: a stationary damage sponge. The heads die on a script, not by player skill, and nothing in the fight changes the ground the player stands on except the fixed moat.

## Concept: THE CINDER WARDEN (working name, change freely)
A towering ember-forged guardian that fights in a ring of four Brazier Pylons. Not a serpent, so the moat/dais theme is replaced by an arena the boss itself reshapes.
Every mechanic below is tied to a verified source (research_synthesis_verified.md) or to code that exists.

### Structure (uses existing tools)
- BRAIN: real Mob, holds the hp pool, gate logic in hurtServer (same hook Hydra uses at HydraBrain.hurtServer).
- BODY: MobRig display parts (existing class) for the visible giant: torso, two arms, head, crown of flame. Displays cannot be hit, so they are skin only.
- HITTABLE PARTS: the four pylons and two "core" hands are REAL emberfall Mobs marked with CompositeParts.markAsPart so their damage redirects to the brain, or is refused by the gate. The auto-weapon picks the nearest emberfall Mob (AutoAttackSystem.isEmberfallHostile), so pylons must be where the player naturally stands.

### The fight loop (fixed order, learnable: Empress of Light uses a fixed 10 step loop)
PHASE 1  100% to 66%  "The Kindling"
  - Boss is INVULNERABLE while any pylon is lit (Supreme Calamitas gate: invulnerable until its hearts die; Ender Dragon crystals heal it, visible beam).
  - Player must break the 4 pylons. Each pylon has a visible beam to the boss (tell) and its own hp.
  - While pylons live the boss uses: EMBER FAN (cone, 0.75 s wind-up) and FALLING SPARKS (ground circles that fill in, Fx.telegraphFill). Attacks never overlap (Moon Lord offsets each eye by a third of a cycle).
  - When the last pylon dies the boss is stunned, exposed for a fixed 6 s window (Gatling Gull is vulnerable while it does its big attack; Colossus exposes weak points on a rhythm).
PHASE 2  66% to 33%  "The Forge"
  - Pylons re-light one at a time (not all four), so the player never faces a full reset (Megabonk players disliked a 1 s vulnerability window after altars; ours is 6 s).
  - New attack: SWEEPING BEAM from the boss, 2 s wind-up, hide behind a NON-DESTRUCTIBLE stone pillar (Hades and Bark Vader both use pillar cover). Pillars are placed by the fight and restored by the block journal.
  - Arena rule change (Absolute Radiance changes the arena every phase): a ring of the floor becomes magma, the safe area shrinks toward the middle in two steps with a golden warning glow first.
PHASE 3  33% to 0%  "The Cinderfall"
  - Tempo up, not just damage (Empress phase 2 shortens waits by 0.25 s): gaps between attacks shrink.
  - Boss opens with the beam, chains two leaps, then a Cinderfall: a rain of sparks in a moving lane pattern that always leaves a lane (Isaac/Hades style, readable gaps).
  - At 5% it drops every attack and gives a clean finishing window (Supreme Calamitas ends on a calm invulnerable beat).

### Anti-frustration rules (from real player complaints in the Megabonk Steam thread)
- Invulnerability ALWAYS has a visible cause: lit pylon beams. No silent immunity.
- No weapon disabling.
- Wave timers are paused for the fight (HydraBossFight already pauses the WaveDirector).
- Boss hitbox is where players stand; no tiny platforms, no knockback into the void.
- Every attack wind-up is at least 0.5 s (megabonkwiki tip) and drawn only during the wind-up, every 4 ticks or slower (telegraph cost rule).

## Cost and safety plan
- Entity budget: brain + 4 pylons + 2 hands + about 12 display parts. Exact count to be MEASURED against a baseline read BEFORE spawn.
- Packets/s to be measured with cost_test.js; no unmeasured claim goes in the notes.
- Terrain: reuse RunManager.setBlockJournaled so the arena restores exactly (AcidDais already proves this).
- Tests: extend the Hydra tests, gate test (boss takes 0 damage while a pylon lives, exact hp math after), pylon respawn cadence, zero leaked entities after death, zero exceptions.

## Not verifiable here
- How the giant looks on a real client. You must look at it.
- The fight feel (difficulty, length). I can make it beatable by a scripted bot, not judge fun.

## Questions for you
1. Keep the name and theme (ember guardian), or do you want something else? A serpent or sea theme would keep the moat.
2. OK to remove the moat and dais entirely, or should the acid moat stay as one of the arena rules?

## Risk found while checking (2026-09-30)
- The fight position comes from markers("boss_spawn"), falling back to the last "spawn_point", then the arena origin (HydraBossFight.pickSpawnPos). It is shared with the Devourer.
- Arenas are natural terrain, so the ground around that point is NOT guaranteed flat. AcidDais only carves an 11x11 footprint.
- So pylons and pillars must be placed by a terrain check (respect the hub rule: never level or clear terrain), and the fight needs a fallback: if fewer than 4 good pylon spots exist, place what fits and scale the phase-1 gate to the pylon count. This must be built and tested with a bad-terrain case.
