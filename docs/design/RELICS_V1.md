# EMBERFALL RELICS v1 (24 relics, data-driven, expandable)

Status: DESIGN ONLY (2026-10-04). Nothing below is built yet.
Inspiration: user's Megabonk research (Key, Microwave, Credit Card, rarity by Luck). All names/effects are
Emberfall/Minecraft originals, not copies.

## Foundation (built first, every later system depends on it)
- `relic/RelicRarity`  COMMON, UNCOMMON, RARE, LEGENDARY (colour + base weight 60/28/10/2 at Luck 0).
- `relic/Relic`        record(id, name, rarity, description, maxStacks, unlock condition id or null).
- `relic/RelicPool`    the 24 definitions + roll(rarityWeights, owned, unlocked).
- `relic/PlayerRelics` per-run stacks, reset/clear in RunManager next to PlayerBuild (lines ~376 / ~407),
                       cleanup registry for attribute modifiers (same crash lesson as tomes).
- `relic/RelicEffects` pure stat derivation, recomputed from stacks (like CombatStats): never mutated
                       incrementally, so two code paths cannot disagree.
- `relic/RelicUnlocks` SavedData, template = WeaponUnlocks (UUID -> string set) + counter map (UUID -> id -> long)
                       for unlock conditions. Relics with no condition are unlocked for everyone.
- Luck: a derived stat. Each Luck point shifts weight from COMMON toward RARE/LEGENDARY, never to zero
  for any rarity ("legendary possible at any luck", per the research).

## Economy
- Paid chest: base 30 Gold, price = round(30 * 1.25^opened) per PLAYER (counter in PlayerRelics), shown on HUD.
  (Growth rate 1.25 is MY placeholder, to be tuned from a simulation, not from Megabonk.)
- Free chests (elite/boss/challenge shrine) never raise the counter.
- Key relic: +10% per stack, capped 50%, chest opens free and counter does NOT rise.
- Ember Ledger (price-cap relic): stops the counter rising while held.
- Microwave: interactable, duplicates one chosen relic for Gold, destroys another of the SAME rarity.

## The 24 (6 per tier)  [effects must each be a REAL working effect with a test; no filler]
COMMON
 1 Four-Leaf Clover   +Luck
 2 Ember Key          Key behaviour above (unlock: open 25 paid chests across runs)
 3 Oat Loaf           +max health
 4 Golden Nugget      +Gold from kills
 5 Clockwork Charm    +XP gain
 6 Iron Boots         +move speed (unlock: reach a level-N tome)
UNCOMMON
 7 Quiver of Plenty   +1 projectile/strike to multi-hit weapons
 8 Ender Pearl Shard  chance to dodge a hit
 9 Campfire Core      regen while standing still
10 Ember Ledger       price cap (above)
11 Thorn Vest         reflects melee damage
12 Magnet Stone       larger pickup radius
RARE
13 Big Bonk Hammer    2% hit for x20
14 Spiked Censer      on-hit area burst
15 Frostbound Ring    on-hit chill/freeze
16 Wizard's Cowl      shorter weapon cooldowns
17 Blood Chalice      lifesteal
18 Anvil of Dawn      every weapon upgrade gains a bonus (needs 3 challenges)
LEGENDARY
19 Totem of Returning survive one fatal hit per run
20 Mirror Shard       reflect + brief invulnerability when hit
21 Soul Lantern       kills feed a permanent stacking damage bonus
22 Hourglass          slow time at low health
23 Dragon's Heart     huge health + regen, damage aura
24 Wither Crown       big damage, raises run difficulty (risk/reward)

## Still to decide while building (will report, not assume)
- exact numbers per relic (needs the DPS meter + a balance pass)
- unlock conditions per gated relic and where the counters are incremented
