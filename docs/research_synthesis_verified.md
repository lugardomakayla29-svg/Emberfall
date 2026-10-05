# Boss replacement research: VERIFIED facts only (read first-hand, 2026-09-30)

Sources read through the browser tool (wiki hosts block the fetch tool). Anything not listed here is UNVERIFIED.

## Empress of Light (terraria.wiki.gg/wiki/Empress_of_Light)
- Phase 1 is a FIXED 10-step loop, not random: Prismatic Bolts 1, Dash, Sun Dance, Dash, Everlasting Rainbow, Prismatic Bolts 1, Dash, Ethereal Lance 1, Dash, Everlasting Rainbow. 0.75 s gap between attacks. Repeats until phase 2.
- Phase 2 at below half hp: she vanishes and reappears above the player's next estimated position (3 s transition); wait times shrink by 0.25 s. TEMPO rises, not just hp.
- Prismatic Bolts: 20/30 bolts in 1 s in random directions, slow down, then accelerate and HOME with imperfect tracking (a sharp turn dodges them).
- Ethereal Lances do NO damage until launched and are telegraphed by coloured beams. Lance 3 aims at the player's exact spot and always spawns behind the player.
- Dash: she positions left or right of the player, afterimages and a sound play first, briefly invincible, then charges over 1.5 s.
- Everlasting Rainbow: ring of 13 that spirals out then back in, lingers 11 s and leaves a damaging trail (area denial).
- Daytime enrage: much higher damage.

## Supreme Calamitas (calamitymod.wiki.gg/wiki/Supreme_Calamitas)
- Always shielded by a circular force field that enlarges her hitbox.
- 15 s invulnerable, immobile BULLET HELL interludes at fixed thresholds (opening, 75%, 50%, 30%, 10%); the direction order of each is fixed and learnable.
- GATES: invulnerable until the Sepulcher's 10 Brimstone Hearts die; later invulnerable while 10 rotating Soul Seekers live. While adds live she fires half as many projectiles.
- Brothers phase: two side bosses swap sides after each barrage; when one dies the other gets faster.
- Brimstone Fireblast homes, PAUSES near the player, then bursts into a ring (8/12/14/16 darts by difficulty). Gigablast ring is 20 to 36.
- Whispering Maelstrom (30%): a persistent hazard that chases the player; damage grows toward its centre; slows to 80% during gate phases.
- Final: at 1 life point she drops every attack and becomes invulnerable, clean ending.

## Moon Lord (terraria.wiki.gg/wiki/Moon_Lord)
- 145,000 total: head 45,000, each of two hands 25,000, core 50,000.
- Core vulnerable only when all three eyes are dead. Eyes damageable only while open (eyelids close in 15 to 21 ticks).
- Attack cycles: hands 600 ticks, head 1200; each True Eye offset by 1/3 of a cycle so attacks never stack.
- Phantasmal Bolt: aim line shown, sound at 0.92 s, aim LOCKS at 1.18 s, so a sudden direction change dodges it.
- Deathray: 3 s charge, then a 120 degree sweep over 3 s; no damage for its first 0.33 s; staying near its origin is safer (rotation is slower there).
- Moon Leeches heal the boss 1000 unless killed or outrun (anti-turtle).
- Spawn: 12 s countdown with screen dimming and a 1 s flash.

## Absolute Radiance (hollowknight.wiki/w/Absolute_Radiance)
- 6 phases, each changes the ARENA RULE: hover; spike floor claims half the ground (golden glow warns first); ground splits to the edges; small platforms over a void; a rising void forces upward movement; final phase a single attack (Orb Barrage) and she stays put.
- Stagger points at fixed hp; last phase ends on one clear hit.

## Ender Dragon (minecraft.wiki/w/Ender_Dragon)
- Alternates FLYING (hard to hit) and PERCHED (vulnerable). Perch chance 1 in (3 + crystals alive).
- End crystals heal it (1 hp per 10 ticks) with a visible white beam; destroying a healing crystal does 10 hp and forces a state change.
- Perch escape valve: 50 cumulative damage forces takeoff. Anti-camping: if no player near the portal within 5 s of landing it CHARGES.
- Breath: 3 s purple cloud, collectable.

## Wither (minecraft.wiki/w/Wither)
- Spawn: 11 s (220 tick) invulnerable immobile charge, then a huge explosion.
- Below half hp: gains armor (immune to arrows, tridents, wind charges), flies at the target's height.
- Three heads fire independently, main head every 2 s, side heads every 2 to 3 s, each with its own target and cooldown.
- Blue skull is slow and destructive; damage-rate and idle counters force it.

## Megabonk bosses (screenrant.com/megabonk-boss-guide-locations-how-to-beat/, Oct 15 2025)
- 8 bosses, 2 regions, 3 tiers each reusing the boss stronger.
- Tier 1 boss is STATIONARY and you kite it in circles (that is the beginner tier).
- Tier 3 bosses disable secondary weapons and use healing pylons; desert bosses use rotating lasers (stay near the boss's feet) and freezing ice projectiles.
- Boss Curse shrines: each activated shrine spawns an extra CLONE of the next boss.
- Player complaints (steamcommunity.com thread 600791249529605888): disabling weapons is "bad game design"; invulnerability had no clear cue; knockback off small cliff platforms; a 1 s vulnerability window after altars die; boss so big it gets stacked under enemies and is hard to hit; a wave timer made tank builds unwinnable.

## Shadow of the Colossus (ign.com/wikis/shadow-of-the-colossus-and-ico/Colossus_1)
- Weak points are REVEALED (sword beam), staged (leg patch to make it kneel, then climb to the head), and exposed in rhythm (head shakes, attack when it calms).

## Engine facts (this repo and minecraft.wiki/w/Display)
- Displays have no hitbox, collision or damage: every hittable part must be a real Mob.
- Block display pivot is the bottom-north-west corner; item display pivots at its centre.
- Auto-weapon targets the NEAREST live Mob in the emberfall namespace within reach (AutoAttackSystem.isEmberfallHostile), so objectives must be where the player naturally stands.
- Reusable now: Fx telegraph shapes (ring, fill, cone, line, square), TrackedProjectiles (virtual bolts: straight, homing, sweep), MobRig, WormBody, hurtServer override for gates.
- No numeric display-entity cap is documented; the "15 to 30 safe" figure from Research B is UNVERIFIED and must be measured in this mod.

## ADDITIONS after cross-checking the sub-agent files (2026-09-30)

### Megabonk, second source (megabonkwiki.net/articles/how-to-beat-every-boss-in-megabonk, published 03/17/2026)
- Confirms research_C's Megabonk numbers: Lil Bark root slam + leaf toss; Chadbark charge (lowers branches, glows green ~1.5 s), vine grab, root minions; Bark Vader dark nova after a 3 s purple charge-up, hide behind STONE PILLARS, 3-hit melee combo; Lil Anubis dash (crouch tell) + sand wave; Anubruh quicksand -40% speed + Scorpion adds; Judge Anubis 180 degree beam, 2 s charge, 25% damage-reduction curse for 8 s, get BEHIND him; Big Bob slam ~1/3 of arena + instant grab under 30% hp; Spooky Steve phases in/out, homing ghosts; Calcium's Dad BONE CAGE = shrinking arena.
- Its general tips: every boss attack has a wind-up of at least 0.5 s; movement speed is the best defensive stat; AoE weapons are weaker vs bosses than focused damage.
- Screen Rant says Lil Bark is stationary and you kite it; this wiki lists a root slam and leaf toss. Compatible: it stays put and attacks.

### Hades (ign.com/wikis/hades/Hades, updated Oct 21 2020)
- VERIFIED: two full health bars; quick axe swings over a big area with a visual indicator; spear dash; flying skulls (Blood Boil if hit, otherwise stand still then explode in a wide shockwave); at least twice in phase 1 he shields (invulnerable) and summons armored adds; phase 2: many more skulls, JARS that stun when broken near you, lasers (first three, then many) answered by hiding behind one of TWO CIRCULAR STRUCTURES when he stands still spinning his spear.
- DROPPED (not on the page): red forcewave, 66/33% thresholds, "5 second" skull timer, green jars, snow arena.

### Enter the Gungeon, Gatling Gull (ign.com/wikis/enter-the-gungeon/Gatling_Gull)
- VERIFIED: fought in a variety of rooms; regurgitates missiles around you and is very vulnerable while doing it; shoots a large ball that shatters into a bullet ring on a wall.
- DROPPED: red reticles, advancing cone spray, hp-scaled speed.

### Warden (minecraft.wiki/w/Warden)
- 500 hp, 100% knockback resistance. Darkness pulse: 13 s of Darkness within a 20 block ovoid every 6 s. Sonic boom: 14 horizontal / 20 vertical range, 1.7 s charge, teal particles from the chest, ignores armor and shields. Melee cooldown 36 ticks. If it cannot reach its target it switches to the sonic boom.

### Wither (minecraft.wiki/w/Wither)
- Spawn: 220 tick invulnerable immobile charge, then a large explosion. Below half hp: armor (immune to arrows, tridents, wind charges). Three heads fire independently: main 2 s, sides 2 to 3 s, each with its own cooldown and target.

### Vampire Survivors (steam thread 3194745319514477721, 2022 player posts, illustration only)
- The 30 minute Reaper is a fail-state boss: faster than the player, immune to knockback, hp scales with player level. The opposite of a fair fight.

### Reliability ledger
- research_B.md: leads only. Its "which server built what" claims rest on one generic wiki (elkonia.com); "15 to 30 displays safe" is UNSOURCED and must be measured in this mod.
- research_C.md: Megabonk section checked and correct; the rest not yet checked.
- research_A1.md: Hades and Gungeon skeletons match IGN; padded specifics dropped as above. Isaac section not yet checked.

### Binding of Isaac, Mega Satan (ign.com/wikis/the-binding-of-isaac-rebirth/Mega_Satan, Mar 2016)
- VERIFIED: hands shield the head and respawn if "killed" (only the head must die); at damage thresholds the face and hands become inaccessible while he summons fights in the room: the four Horsemen in pairs, then seven Super Sins, then a black Angel boss followed by a stronger one; then he sinks and rises as a skull for the final phase.
- DROPPED (not on the page): hand-slam blood rings, brimstone column, bullet curtains, flame orbs, the 75/50/25% numbers.
- research_A1.md reliability: skeletons real, specifics padded in all three bosses. Use only what is listed in this file.

## DEVOURER EXPANSION RESEARCH (read first-hand 2026-09-30, no sub-agents)

### Devourer of Gods (calamitymod.wiki.gg/wiki/The_Devourer_of_Gods)
- 82 segments in phase 1, 102 in phase 2. Head and tail take full damage; body segments take 92.5% less.
- Three-mood ROTATION with a colour tell: passive (background blue, slow precise biting, 15 s), aggressive (magenta, fast and sloppy tracking, 15 s), laser (dark purple, coils around the player, 5 s, fires a grid or rows of instant beams aimed at the player). Cycle passive > aggressive > laser.
- Speed rises at 92%, 84%, 76%, 68% hp. Laser phase unlocks below 90% hp. Aimed fireballs during the passive mood below 75%.
- At 65% it goes intangible into a portal (segments 82 to 102). Final phase adds the RIFT: a portal opens, an on-screen arrow points to it when off-screen, it lunges out of it and leaves rings of fireballs. Body segment lights change colour with the mood (pink aggressive, blue passive, both in laser).
- At 25%: starts with the laser phase, every laser set gains one extra larger beam aimed at the player, the rift lunge is chained 3 times, and mood cycles every 10 s.
- Head contact is the biggest hit. Spawned body segments are invulnerable for 6 s and the tail for 12 s.

### The Destroyer (terraria.wiki.gg/wiki/The_Destroyer)
- 82 segments (239 tiles long), all sharing ONE health pool; no segment can die on its own.
- EVERY segment fires red lasers at the player; dim segments still fire.
- Each body segment has a 1 in 25 chance, whenever damaged, to release a Probe (independent flying laser enemy, killable), ONCE per segment, signalled by that segment's red light turning off. Probes keep their distance and occasionally close in. They run out eventually.
- It leaps from the ground to ram, and tries to surround the player with its segments. Tip: the head has no defense, so lining up with it and firing down the body is the risk/reward play.

### What this says about OUR worm (from DevourerBrain.java constants)
- Body: 5 segments at 1.5 spacing, about 9 blocks total; the references are 80 to 100 segments. Length is the first menace lever.
- Attacks: dash, high leap, burrow burst; three phases at 66/33; minis capped at 4. No mood rotation, no body-wide attack, no coil, no rift.
