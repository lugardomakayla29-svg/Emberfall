# Sound audit 2 (report only, nothing added)

Written by Vesper for Koda's row 2(a) in his 2026-10-07 19:39 CT entry. **Report only: no sound was added and no code was changed.** The owner has to hear a sound before it is chosen.

**Tested headless, look unverified.** This is a read of the code on `main`. Nobody has listened to any of this in a real client.

It complements `docs/sound_audit.md` (V4, partial by its own account: 46 scan candidates, 14 read, 27 never reached, 5 filled). It does not replace it.

## What was asked, and how far this gets

Koda asked for a table of gameplay events with no sound: weapon hits, level up, chest open, shrine, merchant, boss phase change, death, run end. **Every row for those eight events below was settled by reading the code on the event's path, not by the scan.** The scan found 119 further candidates after exclusions; **I did not read them**, and this report does not claim they are silent or not. They are listed by count in "Not covered".

## The eight events Koda named

"Silent" means: no `playSound`, `Cue.play` or `SoundEvents` reference in the method that handles the event, in the helpers it calls on the event's path, or in the class that sends the effect. "Plays" means a sound call exists on the path; **it does not mean the sound is audible or well chosen.**

| # | Event | Code read | Result | Evidence |
| --- | --- | --- | --- | --- |
| 1 | Weapon hit (auto attack) | `combat/AutoAttackSystem.java` | **Plays** | `PLAYER_ATTACK_STRONG` (`:393`), `TRIDENT_HIT` (`:676`), `GENERIC_EXPLODE` (`:983`), plus cast and arrow sounds. 12 sound references in the file. Not every weapon was traced one by one. |
| 2 | Level up | `leveling/LevelingHandler.java` | **Not this mod's sound** | The mod only detects the level change afterwards (`player.experienceLevel` rising, `:93`). The chime is vanilla's `giveExperiencePoints` (`PLAYER_LEVELUP`). **Whether it is audible in the expedition dimension is not established.** The Tome pick that follows got a sound in V4 (row 1). |
| 3 | Chest open | `relic/ChestManager.java` | **Plays** | `CHEST_OPEN` (`:281`), `AMETHYST_BLOCK_CHIME` (`:258`), `PLAYER_LEVELUP` (`:285`), refusals `VILLAGER_NO` (`:269`) and `CHEST_LOCKED` (`:273`). |
| 4 | Shrine choice (Curse / Greed) | `shrine/MapShrines.java:316` `onChoice` | **Silent** | 0 sound references in the 54-line method. Chat line and particles only. V4 row 7, not filled then. |
| 5 | Shrine trial cleared | `shrine/MapShrines.java` | **Plays** | `Cue.play(... "shrine_trial_cleared", UI_TOAST_CHALLENGE_COMPLETE)` (`:440`). Filled in V4. |
| 6 | Merchant | `relic/MerchantManager.java` | **Plays** | 8 sound calls: `VILLAGER_TRADE` (`:204`), `VILLAGER_CELEBRATE` (`:358`), `VILLAGER_NO` (`:161`, `:312`, `:327`), `FIREWORK_ROCKET_LAUNCH` (`:387`). |
| 7 | Boss phase change, Ember Guardian | `entity/EmberGuardian.java:1073` `phase++` in `checkRelight` | **Plays** | `BLAZE_SHOOT` at the re-lit pylon (`:1084`). It is the only place `phase` is assigned. |
| 8 | Boss phase change, Devourer | `entity/DevourerBrain.java:412` `enterPhase` | **Plays** | `RAVAGER_ROAR` (`:416`), pitch 0.5 in phase 3 and 0.7 otherwise. |
| 9 | Death | `world/RunEndHandler.java:52` | **Silent** (see below) | A run player never really dies: `ALLOW_DEATH` (`:52`) cancels the lethal hit and calls `endRunInsteadOfDying` (`:61`, then `return false`), so vanilla's death path never runs. Nothing on that path plays a sound. |
| 10 | Run end (screen) | `world/RunEndHandler.java` `finishRun` | **Silent** (see below) | Opens a `RunEndPayload` screen; no server sound. The client has no sound code (V4 counted 15 client files, none with a sound; re-counted on `main` now: 16 files, 0 with a sound). |
| 11 | Shop purchase | `progression/ShopManager.java` | **Silent** | `buyWeapon` (`:59`) and the stat, charge and slot buys end with a chat line only. 0 sound references in the file. V4 row 8, not filled then. |

Eleven rows because boss phase change is two bosses and shrine is two events. **Silent: 4 (shrine choice, death, run end, shop purchase). Plays: 6. Not this mod's sound: 1 (level up).**

### How death and run end were settled (read this before trusting rows 9 and 10)

I followed `endRunInsteadOfDying` into `finishRun` and listed every call it makes. Sound references per class it calls into: `RunRewardCalculator` 0, `HudSync` 0, `RunHudSync` 0, `RunManager` 0, `DisplacedItems` 0, `ReturnPoints` 0. `PickupSystem` has 4, but all inside `collect()` (picking up gold), not in `clear`, `clearLevel` or `gold`, which are the methods `finishRun` calls. `GateManager` has 2, inside `tickAll()`, not in `runEnded`. So the path is silent **on the server**.

Not established: whether the vanilla `Screen` that `RunEndPayload` opens makes a sound, and whether the damage that triggered the death plays a vanilla hurt sound before `ALLOW_DEATH` cancels it. Both are vanilla behaviour I cannot hear from here.

## The 119 other candidates: not read

The scan parsed 2,022 methods, found 270 with a player-facing signal, and 188 had no sound within two call levels. After removing commands, hub builders, the legacy `ShrineManager` and the Tiki files, **119 remain**. I read none of them for this report. By looking at the list (not by reading the code) most are plumbing, not events a player hears: `.discard(` cleanup, `addFreshEntity` for decor parts, `setHealth` in mob set-up, packet-send helpers. A few look like real events and are worth reading next:

`item/WeaponChoiceManager` (`resolve`, `decline`, `openReplace`), `world/ArenaBoundary.keepPlayerIn` and `CircleBoundary.keepPlayerIn` (teleported back inside), `relic/RelicUnlocks.announce`, `tome/SynergyEffects.grantMomentumBonus`, `character/CharacterSelectManager.onChoiceReceived`, `item/WeaponProgress.consumeUltimate`, `entity/*` `becomeVeteran` (five horde mobs), `tome/PlayerCompanions.spawnCompanion`.

**Do not read the 119 as a count of silent events.** It is a count of methods the scan could not clear.

## Why the scan alone cannot answer this (two methods, both flawed)

- A depth-2, same-package scan (`scan.py`) is too narrow: it misses a sound played by a helper in another package. For example `MapShrines` plays its sound through `pickup/Cue`, a different package.
- An unrestricted name-based call closure (`closure.py`) is far too wide: `finishRun` "reached 346 methods" because common names like `get` and `send` match unrelated methods everywhere, and `ChestManager::open` reached 0 because my parser did not find the method. **Neither number proves silence or sound.**

The only method that held up was reading the path. That is why eleven rows are solid and 119 are not.

## What I would do next, if asked

1. Read the ten or so real-looking candidates above and add them to the table.
2. Ask the owner to listen to rows 4, 9, 10 and 11 and say which deserve a sound; **the sound choices are the owner's, not mine.**
3. Settle level up by listening in a real client in the expedition dimension.

## Not established

- Anything about how any of this sounds. Every "Plays" row only says a call exists.
- Level up audibility, the run-end screen's own sound, the hurt sound before a cancelled death.
- The 119 unread candidates.
- Mob-side sounds (the `entity/` files have well over 200 sound references and were not audited event by event).
