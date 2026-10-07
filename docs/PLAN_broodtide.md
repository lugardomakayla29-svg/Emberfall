# PLAN: Broodtide (boss 1 replacement, slime kraken) and Devourer refinement
Status: PLAN ONLY. Nothing here is built, merged or approved. Owner decides when it enters the mod.
Authors: Vesper (original brief, `slime_kraken_prompt_for_koda.md`), Koda (review + the Devour idea). Written 2026-10-06 CT.
Tested headless, look unverified: nobody has seen any of this on a real screen. Every number is a starting point, not a measurement.
Related, already built: `boss1_replacement_design.md` (Ember Guardian), `devourer_expansion_design.md`, `PLAN_party_scaling.md`.

Labels: VERIFIED = read from source this session. PROPOSAL = a design choice, change freely. UNVERIFIED = needs a prototype.

## 0. Trigger: how to use this file
- The owner says "Broodtide is ready" -> Koda opens ONE issue "Broodtide: first boss redesign" linking this file and writes the
  Vesper prompt in section 7 into `docs/comms/INBOX.md`. Vesper then reads the inbox once and works its queue.
- Do not start any code from this file alone. Do not remake the boss a third time before the current release is packaged.
- Order: Emberfall is packaged FIRST. Broodtide starts after the owner says so.

## 1. The boss in one paragraph (PROPOSAL)
A giant slime kraken. A real Slime-model body (square face, glowing lime eyes, translucent shell with a darker core you can see into),
ringed by thick tapering spiked tentacles (Kuudra SHAPE only, none of Kuudra's mechanics). It sits in the arena and the FLOOR becomes its
limbs: goo spreads out from it, tentacles rise from the goo, and it EATS the horde and spits them back out as its followers.

## 2. Pillars
| Pillar | What it is | Why |
|---|---|---|
| The Tide | Ebb (about 14 s, body hittable) then Flood (about 9 s, body armoured x0.35, never immune). Size + opacity + sound show the state. | A readable rule instead of a pylon gate |
| The Spread | A data grid of goo cells (no blocks), slow-and-sticky, tentacles rise only from goo. | The space the player stands on changes |
| Devour | The boss reaches a tentacle to a nearby horde mob, pulls it into the body, spits it out as a Brood-Kin. | Ties the boss to the horde you already built |
| Rooted body | Does not walk. Fight centre = the `boss_spawn` marker. | Stuck-in-terrain risk, known-good beginner shape |

## 3. Devour and Brood-Kin (Koda's addition, PROPOSAL)
1. EBB: a tentacle reaches for a horde mob within reach (telegraph >= 0.9 s). One-shot pull (one `setDeltaMovement`, no sustained drag). The mob is
   hidden inside the body for about 1.5 s (translucent shell shows a silhouette). It is hidden, not deleted.
2. FLOOD: the body swells and spits every swallowed mob out in a telegraphed burst. Each returns as a Brood-Kin:
   slime on the HEAD (a helmet item, VERIFIED: the mod already dresses mobs with custom heads, `EliteHeads`), more HP, one twist per type.
3. ZERO extra entities: a Brood-Kin is the SAME mob that was eaten. No new entity type for v1. The slime cap is the existing hostile cap.
4. Cap: at most 6 Brood-Kin alive. Eating needs a free slot, so the boss cannot flood the arena. Counts toward the normal mob cap.
5. Reward: killing a Brood-Kin in its first ~3 s refunds a little (ult charge or a small heal). One still alive at Flood heals the boss a small, VISIBLE amount.
6. Phase 3: the boss may eat a Brood-Kin and spit a Brood-Champion (two slime blobs on the head). Visible risk and reward.
7. v1 twists, two types only: Zombie (sticky trail slows you), Spitter (spits goo). The rest (Skeleton, Bomber, Spider, Witch) ship after a playtest.
UNVERIFIED and prototype FIRST: hide a mob and bring it back with its AI, aggro and team tags intact (RunMobTeam / RunMobAggro).

## 4. Scope
v1 (build): Tide, Spread, rooted Slime body with a custom renderer, 3 phases, Grab (effects + one impulse), Devour with Zombie and Spitter Brood-Kin,
clean island, phase beats, death = puddle with no split, party scaling through the existing party-damage path.
CUT from v1 (after a playtest): arms as separately hittable targets, sever and regrow, Whirlpool, Cyst adds, Undertow, the other four Brood-Kin types.
KEEP the Ember Guardian, `CinderPylon` and their tests behind a flag until Broodtide is playtested; `/emberfall boss` and the summoner item must keep working.

## 5. Facts the build must respect (VERIFIED unless noted)
- `Slime.setSize` clamps 1..127 and overwrites max health, speed and damage: one `applySize` that re-applies boss stats and keeps the HP fraction.
- `Slime.remove` splits a dying size>1 slime: drop size to 1 before death. Once-per-death effects fire from `remove()`.
- Slime jumps by itself (`nextInt(20)+10` ticks): the body must zero horizontal motion and disable its own jump.
- Client renderer needs its OWN outer layer (vanilla `SlimeOuterLayer` hard-codes the green texture). Template: `PinkSlimeRenderer`.
- Party scaling is BUILT (PR #50, #51): hook the same `setPartyDamageFactor` path the Guardian uses (`EmberGuardian` lines 119-123, 466), not `applyCurse` alone.
- Contract that must keep working: `wasDefeated()` true only after a genuine `die()`, `teardownRig` idempotent, `markHydraDefeated(slot)` only on a genuine kill,
  `WaveDirector.onHydraDefeated`, free chest at `boss_spawn`, `setBossActive(false)` on EVERY exit path, `RewardFormula.HYDRA_BONUS`.
- Targeting picks the nearest Mob by `position()` (`AutoAttackSystem.findNearestHostile`). Hence v1 keeps the body the single hittable Mob.
- Telegraphs: wind-up >= 0.5 s everywhere (this plan uses >= 0.9 s), no faster than every 4 ticks. Terrain stays unbreakable: goo is data + particles.
- No silent immunity, no weapon disabling. Armour 0.35 is shown by size, opacity and sound.

## 6. Work split (who does what)
| # | Task | Owner | Done-when (a test or a log line) |
|---|---|---|---|
| 0 | Measure per-weapon DPS vs a boss-class target (also unblocks every tuning number) | Koda | table in `docs/audit/`, one row per weapon |
| 1 | Guardian baseline: ms/tick with 1 and 5 players (entity count is already known: 36 displays + brain + 4 pylon proxies) | Koda | numbers in the PR |
| 2 | Prototype A: swallow a mob and return it with AI/aggro/team intact | Koda | `devour_proto_test` PASS/FAIL, 0 leaks |
| 3 | Prototype B: custom Slime renderer with own outer layer | Koda | builds; look stays UNVERIFIED until a real client |
| 4 | `BroodtideBody` (size, HP fraction, no split, rooted, teardown) | Koda | tests 1-3 of section 8 |
| 5 | `GooGrid` data + cap + one-damage-tick-per-second rule | Vesper writes the pure class + unit checks, Koda wires it | `GooCheck` PASS, cap and rate asserted |
| 6 | Tide clock + armour in `hurtServer` | Koda | durations within 1 tick, armour exactly 0.35, void damage passes |
| 7 | Grab (effects + one impulse) | Koda | at most 1 impulse counted, never into body or below floor |
| 8 | Devour + Brood-Kin (Zombie, Spitter) | Koda | hidden mob returns with AI, cap 6 holds |
| 9 | Phases 2-3, clean island, beats, death puddle | Koda | party of 1, 2, 5 all reach a clean island; no split |
| 10 | Test review: write a suite for each section-8 item the pure classes cover, hunt false passes | Vesper | each suite prints PASS/FAIL + total, exits 1 on failure |
| 11 | Docs: WHATS_NEW, handoff, creative-tab entry, summoner item rename or keep | Vesper | files updated; owner decision recorded |
| 12 | Swap into the fight behind a flag, run the party tests | Koda | flag off = old Guardian, unchanged |
Vesper does NOT touch `bot/`, hotspots (`WaveDirector`, boss fight classes, `EmberfallCommands`, `RunManager`) or the renderer.

## 7. Prompt for Vesper (paste or write to the inbox when the owner says go)
> Read `docs/comms/HANDOFF.md`, then `docs/PLAN_broodtide.md`, then the newest INBOX entry and quote its time. Your tasks are rows 5, 10 and 11 of
> section 6 only, in that order. For each: one concern per PR, PASS/FAIL lines plus a total, exit 1 on failure, label every claim VERIFIED / PROPOSAL /
> UNVERIFIED, and re-check any VERIFIED line your work depends on against `main`. Do not touch `bot/` or the hotspots. Two failed attempts on a problem:
> write a BLOCKED entry and stop. Do not poll. Append your result to INBOX.md and stop.

## 8. Tests (each prints PASS/FAIL and a total, exits 1 on failure)
1 spawn + teardown: zero leaked entities after death AND after a forced run end. 2 size: HP fraction kept over 20 size changes, boss stats not vanilla.
3 no split on death at size>1. 4 Tide clock within 1 tick, state visible. 5 Grab: Slowness + Mining Fatigue only, at most one impulse, clears on arm/grab end.
6 Devour: eaten mob returns with AI and team tags, cap 6 holds, no leak. 7 Goo: cap, one damage tick per second from ALL goo. 8 bad terrain never soft-locks.
9 party of 1, 2, 5 scale through the party path and a clean island exists. 10 contract: kill promotes tier 2 once, run teardown does NOT.

## 9. Devourer refinement (AFTER Broodtide, PROPOSAL)
Same infection thread: the Devourer swallows a horde mob or a Brood-Kin and leaves a mini-worm where it surfaces (it already spits mini-worms, so the
mechanic exists). Boss 1 = slime that spits what it ate, boss 2 = hunger from below. Measure before adding: part count and packets (see
`devourer_expansion_design.md` lever 1). No work on this until Broodtide is playtested.

## 10. Final review (the end of the plan)
When rows 0-12 are done: (a) Vesper audits Koda's PRs against section 5 and section 8 and lists every false pass it can construct; (b) Koda audits Vesper's
suites by breaking the code on purpose and showing each suite goes red (mutation check, as done for party scaling); (c) one full regression, 0 exceptions;
(d) a written list of what is UNVERIFIED on a real client, for the owner's playtest; (e) the owner decides: keep the Guardian, retire it, or ship both.

## 11. Open owner questions
Name (Broodtide ok?), colour (green/teal, not pink), Ebb length (wait for DPS), retire the Guardian (suggest: keep behind a flag), whether the boss
may ever move (default: no), first-boss HP (600 vs the Devourer's 260 base: boss 1 should not be the harder one).

## 6a. PROTOTYPE A RESULT (2026-10-07 18:40 CT, Koda, measured live on the current jar)
Question: can a mob be hidden and brought back with its AI, aggro and team status intact? Answer: YES, with vanilla flags only (no new mod code).
Method: tools/testbot/devour_proto_zombie_test.js and devour_proto_spitter_test.js. HIDE = `Invulnerable:1b, Silent:1b` plus the invisibility effect, the mob stays in the world inside the arena (so the positional RunMobTeam check keeps it). SWALLOW = also `NoAI:1b`. REVEAL = clear all four. Every state was read back from the server.
MEASURED: a visible zombie hit the player 10 times in 9 s; the SAME zombie after hide, 4 s frozen, and reveal hit 9 times in 9 s. Spitter 9 hits before, 9 hits after. While hidden without NoAI the zombie kept walking (x 8.2 to 1.2 in 3 s); with NoAI it held still (1.009 to 1.009). Invisibility confirmed in the entity data, Invulnerable cleared on reveal.
WHAT THIS DOES NOT SHOW (state it, do not assume): (1) the RunMobPurge and wave-counter behaviour while hidden was not exercised, only that the entity stays in the world; (2) one run per mob type, no rate; (3) it was hidden for about 4 s, not a full Devour with the spit-out and the Brood-Kin conversion; (4) the look (virus slime on the head) is UNVERIFIED, headless only; (5) the wave director's alive-count was not read, so whether a hidden mob still counts toward the wave is still open.
TRAP FOUND: my first version measured 'it moves', which is wrong for a ranged Spitter (it stops at firing range and shoots). Aggro is tested by hits taken, not by distance.
DECISION: Prototype A is proven for the two v1 Brood-Kin types, so Devour (row 6 to 9) and the Tiki Slime Swallow Mask are NOT blocked. Open items (1) and (5) get their own check before Devour ships.
