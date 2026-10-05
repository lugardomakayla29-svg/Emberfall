# Boss Design Research: EMBERFALL Roguelike

## Boss 1: Hades (Lord of the Underworld) - *Hades (2018)*
- **Source URL**: `https://www.ign.com/wikis/hades/Hades`

### Phases and Triggers
- **Total Phases**: 2 primary phases (standard encounter).
- **Phase 1 Trigger**: Starts at 100% HP.
- **Phase 2 Trigger**: Triggered when Phase 1 health bar reaches 0%. Hades kneels, emits a massive red forcewave clearing the arena, completely refills his health bar to 100%, and enters Phase 2.
- **Phase 1 Invulnerability Triggers**: At ~66% HP and ~33% HP in Phase 1, Hades summons an invincible grey shield around himself and spawns armor-clad minion waves.

### Attacks, Telegraphs, Patterns, and Dodging
1. **Spin Attack / 360 Axe-Spear Sweep**
   - **Telegraph**: Hades pauses movement, grips his weapon with both hands, and a white/red circular charge indicator expands around him.
   - **Pattern Shape**: 360-degree wide circular AoE sweep centered on Hades (can follow up with a 2nd or 3rd sweep in rapid succession).
   - **How it is Dodged**: Dash through Hades or dash rapidly out of the circular indicator boundary before the attack lands.
2. **Spear Dash / Lunge**
   - **Telegraph**: Hades leans back, points his spear forward toward Zagreus, leaving a brief directional visual trail.
   - **Pattern Shape**: Fast linear thrust across the arena floor.
   - **How it is Dodged**: Dash sideways perpendicular to his dash trajectory.
3. **Boiling Blood Skull Fire**
   - **Telegraph**: Hades raises his left hand, glows red, and hurls a skull projectile.
   - **Pattern Shape**: Fast linear projectile. If it hits the player, it inflicts the "Blood Boil" debuff (increased damage taken). If it misses, it lodges in the ground as a red skull node for ~5 seconds.
   - **How it is Dodged**: Dodge sideways or block line-of-sight behind arena pillars. If a skull lands on the ground, destroy it within 5 seconds before it detonates; otherwise it releases a wide expanding circular shockwave across the arena floor.
4. **Minion Summons (Invulnerability State)**
   - **Telegraph**: Hades gains a translucent shield dome and disappears/stands immune while spawning Tartarus/Elysium foes.
   - **Pattern Shape**: Multiple armored adds spawn around the map while Hades throws skulls from safety.
   - **How it is Dodged**: Defeat the summoned adds quickly while avoiding incoming skull shockwaves; boss remains invincible until adds are cleared.
5. **Boiling Death Lasers (Phase 2 Only)**
   - **Telegraph**: Hades stops moving, stands in place, and rapidly spins his spear above/in front of him while charging red energy.
   - **Pattern Shape**: Fires 3 beam lasers (upgrading to a full 360-degree rotating laser barrage covering the entire arena).
   - **How it is Dodged**: Immediately sprint and position behind one of the two indestructible stone pillars in the arena to break line of sight.
6. **Exploding Green Urns / Jars (Phase 2 Only)**
   - **Telegraph**: Green pottery jars sprout from the ground across the arena floor.
   - **Pattern Shape**: Stationary point hazards. Striking a jar (via player attack or boss spear) detonates it in a green AoE cloud.
   - **How it is Dodged**: Keep distance from jars; if caught in the explosion, the player is temporarily stunned and trapped in entangling vines.

### One Memorable Signature Mechanic
- **Boiling Skulls & Delayed Shockwave Mines**: Fired skulls force a dual decision: if hit, the player suffers bonus damage from all subsequent hits (Blood Boil); if dodged, the skull sits on the arena floor as a countdown mine. Players must destroy the skull in 5 seconds or prepare to jump/dash over a screen-wide expanding circular shockwave ring.

### Arena Usage
- **Circular Snow Arena with Cover Pillars**: Features two large, indestructible stone statues/pillars. They provide critical line-of-sight cover against Phase 2 laser barrages and skull throws, but constrain movement during linear spear rushes. Green jars add hazardous landmines that choke safe movement lanes.

---

## Boss 2: Gatling Gull - *Enter the Gungeon*
- **Source URL**: `https://www.ign.com/wikis/enter-the-gungeon/Gatling_Gull`

### Phases and Triggers
- **Total Phases**: 1 continuous phase (HP 100% to 0%). Attack frequency and movement speed increase slightly as HP decreases.

### Attacks, Telegraphs, Patterns, and Dodging
1. **Rapid-Fire Vulcan Spray**
   - **Telegraph**: Gatling Gull rears up, raises his heavy Vulcan cannon, and aims directly at the player.
   - **Pattern Shape**: Dense, continuous cone/stream of high-velocity bullets sprayed while slowly advancing toward the player.
   - **How it is Dodged**: Strafe laterally around Gatling Gull, use room pillars as hard physical cover, or execute timed dodge-rolls through bullet lanes.
2. **Missile Mortar / Regurgitation Barrage**
   - **Telegraph**: Gatling Gull stops moving, tilts his head back, and fires a stream of missiles directly upward into the air.
   - **Pattern Shape**: Multiple red circular targeting reticles appear on the floor around the player's position, followed by delayed explosive missile impacts.
   - **How it is Dodged**: Continuously run out of the red reticles before missiles land; this attack leaves Gatling Gull completely stationary and vulnerable to damage.
3. **Bouncing Shatter Orb**
   - **Telegraph**: Gatling Gull charges and fires a larger, slower bullet sphere forward.
   - **Pattern Shape**: Linear projectile that travels until hitting a wall, upon which it shatters into a 360-degree ring of smaller bouncing bullets.
   - **How it is Dodged**: Dodge-roll through the initial expanding ring or weave through gaps between shattered bullet reflections.

### One Memorable Signature Mechanic
- **Regurgitated Mortar Reticles**: Gatling Gull stops moving to bombard the arena with air-dropped missiles. The visible red ground reticles force constant movement, creating a risk/reward window where the boss is immobilized but the floor becomes a hazardous minefield.

### Arena Usage
- **Variable Room Layouts & Pillar Dancing**: Gatling Gull is unique among bosses for spawning in several distinct arena variants (e.g., open hall, 4 large stone pillars, library with destructible bookshelves, river channels). In layout variants with solid stone pillars, players can kite the Gull around pillars, using line of sight to completely neutralize his Vulcan spray.

---

## Boss 3: Mega Satan - *The Binding of Isaac: Rebirth*
- **Source URL**: `https://www.ign.com/wikis/the-binding-of-isaac-rebirth/Mega_Satan`

### Phases and Triggers
- **Total Phases**: 2 primary phases.
- **Phase 1 Trigger**: Starts at 100% HP (giant red demon head flanked by two massive hands).
- **Phase 1 Sub-Phase Wave Triggers**: At HP thresholds (~75%, ~50%, ~25%), Mega Satan retracts his hands and head into darkness, becoming invincible, while spawning wave gauntlets:
  1. *Harbingers Wave*: 4 Horsemen of the Apocalypse (Famine & Pestilence, followed by War & Death).
  2. *Super Sins Wave*: 7 Super Sins (Super Envy, Super Lust + Super Wrath, then Super Gluttony + Super Sloth + Super Greed + Super Pride).
  3. *Angels Wave*: Fallen Angels (black versions of Uriel and Gabriel).
- **Phase 2 Trigger**: Triggered when Phase 1 HP reaches 0%. Mega Satan's flesh burns away, leaving a giant skeletal skull rising from the dark abyss.

### Attacks, Telegraphs, Patterns, and Dodging
1. **Giant Hand Slam & Radial Blood Rings (Phase 1)**
   - **Telegraph**: One of Mega Satan's giant hands lifts high off the arena floor.
   - **Pattern Shape**: Hand slams down onto the floor, emitting expanding radial arcs of red blood projectiles.
   - **How it is Dodged**: Move away from the hand impact zone and weave through the spreading gaps between radial projectile rings.
2. **Brimstone Laser Column (Phase 1 & 2)**
   - **Telegraph**: Mega Satan's eyes glow bright red/blue and his jaw opens wide with charging beam particles.
   - **Pattern Shape**: Massive, thick vertical beam of laser energy blasting straight down the center line of the arena.
   - **How it is Dodged**: Move immediately to the far left or right edges of the screen outside the central vertical channel.
3. **Dense Waving Bullet Curtains (Phase 1)**
   - **Telegraph**: Mega Satan leans forward and shakes his head rapidly.
   - **Pattern Shape**: Dense, sweeping S-shaped or wave-patterned walls of red projectiles moving downward across the arena.
   - **How it is Dodged**: Stand at medium distance and navigate horizontally/vertically through the curved gaps between bullet curtains.
4. **Phase 2 Floating Flame Orbs & Spiral Bullet Barrage**
   - **Telegraph**: Skeletal skull jaw opens wide; eyes flare with fiery aura.
   - **Pattern Shape**: Spawns floating flame orbs that drift across the screen emitting 360-degree radial rings while the skull fires high-density helical bullet streams.
   - **How it is Dodged**: Continuously kite around the perimeter of the arena floor, keeping maximum distance from the floating orbs to navigate bullet ring gaps.

### One Memorable Signature Mechanic
- **Phase-Gated Minion Wave Gauntlet**: At major health thresholds, Mega Satan retreats into invulnerability and summons iconic mini-bosses (the 4 Horsemen, 7 Super Sins, and Angels) directly into the boss room, shifting the encounter from a stationary head bullet-hell into a mobile add-clearing fight.

### Arena Usage
- **Fixed Top-Wall Anchor & Open Floor Arena**: Mega Satan is permanently anchored to the upper wall over a dark pit. The player cannot move behind or around the boss, restricting all movement to the lower open floor. This geometry emphasizes lateral positioning across horizontal laser beams and vertical depth management against bullet walls.

---

## 6 Transferable Rules for a Dodge-Only Auto-Attack Boss in EMBERFALL

1. **Unambiguous Visual Telegraphs**: Every major attack must provide clear, 1.0–1.5 second visual pre-indicators (such as expanding floor reticles, charge particles, or directional wind-ups) so dodging remains 100% skill-based without block/parry abilities.
2. **Dodge Lanes and Bullet Gaps**: All attack patterns (lasers, bullet spreads, sweeps) must leave readable safe lanes or expanding gaps, enabling movement-only players to dodge smoothly.
3. **Indestructible Line-of-Sight Cover**: Incorporate physical arena structures (e.g., stone pillars or obstacles) that players can use to break continuous channeled attacks (such as laser beams or heavy gatling sprays).
4. **Dynamic Area Denial (Anti-Circle-Strafe)**: Combine direct boss attacks with floor hazards (e.g., mortar reticles, delayed explosion skulls, or toxic urns) to break up mindless circle-kiting and force intentional movement choices.
5. **Vulnerability Windows Post-Telegraph**: Follow high-potency telegraph attacks with brief boss recovery windows where auto-attacks can land safely without immediate retaliation.
6. **Clearing Pacing Resets via Phase Triggers**: Utilize health-threshold phase triggers or add-spawns to clear active projectile clutter, give players breathing room, and transition the boss into new attack rhythms.
