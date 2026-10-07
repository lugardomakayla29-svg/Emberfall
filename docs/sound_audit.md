# Sound audit (server side)

Task V4, batch 1. Written by Vesper. Every row below was found by a scan and then **confirmed by reading the code** (the method and
every helper it calls). Line numbers are from the base branch `koda/inbox-vesper-batch1` and will drift.

**Tested headless, look unverified.** This is a read of the code. Nobody has listened to any of these in a real client.

## How it was measured

1. `python3` scan of `src/main/java/com/solme/emberfall` (not `bot/`, `mixin/`, `network/`) for methods that tell the player
   something happened (`sendSystemMessage`, `displayClientMessage`, titles, `broadcastToSlot`) and never call `playSound` or
   `SoundEvents.` in that method. That gave **46 candidate methods**.
2. A scan cannot tell a silent event from one that calls a helper which plays a sound, so each row I use was read by hand, with
   the helpers it calls (`burst`, `SwarmPortal.open`, `PlayerBuild.grant`, `SynergyEffects`, `CombatStats`, `PlayerWeapon`, `Loadout`).
   Helper sound counts were grepped: all 0 for the Tome path.
3. The **client** has 15 source files and **none** plays a sound (no `SimpleSoundInstance`, `playLocalSound`, `forUI`), so a
   silent server event is not rescued by the client.

## Blind spots (what this audit does NOT cover)

- Vanilla screen click sounds. The Tome, shrine, shop and weapon screens are vanilla `Screen` classes, so button clicks may
  already click. I cannot hear a client from here.
- Vanilla sounds that fire without this mod's code: the XP level-up chime (vanilla `giveExperiencePoints` plays `PLAYER_LEVELUP`
  itself), item pickups, block breaking, damage hurt sounds.
- Sounds on mobs (`entity/` has 26 files with sound and was not audited event by event).
- **Coverage, counted by script:** of the 46 scan candidates, 5 are excluded as legacy, 14 were read and classified, and **27 were not
  reached** (listed at the bottom). So this is a partial audit: it ranks the events I read, not every silent event in the mod.
- Row 4 (free chest appears) did not come from the scan. `dropFree` places a block and messages nobody, so the scan cannot see it. I
  found it by reading `ChestManager`. Silent events of that kind (no message, no sound) are invisible to the scan and are **not**
  systematically covered.

## Deliberate exclusions

| Candidate | Why it is not in the audit |
| --- | --- |
| `shrine/ShrineManager` (`triggerGreed`, `triggerCurse`, `startChallenge`, `tickChallenge`, `grantChallengeReward`) | Legacy. Reachable only through the op-only `/emberfall paste` (`EmberfallCommands.java:697` -> `RunManager.pasteArena`). A player's `/expedition` always uses `RunManager.startOnMap`, which uses `MapShrines`. |
| Refusals: "not enough Silver", "already at max level", "Step closer to the gate", "no free tome slot" | An error does not need a reward sound. |
| `WaveDirector.onHydraDefeated` (boss 1 escalation) | Fixed by V2 (PR #66, `vesper/v2`). Filling it here would collide in a hotspot file. |

## Silent events, ranked (frequency in a run x size of the moment)

| # | Event | File:line (base branch) | Silent because | Proposed vanilla sound | Filled in this PR |
| --- | --- | --- | --- | --- | --- |
| 1 | Tome picked | `tome/TomeChoiceManager.java:194` `resolve` | grant + chat line only; `PlayerBuild`, `SynergyEffects`, `CombatStats` have 0 sound calls | `ENCHANTMENT_TABLE_USE` | yes |
| 2 | Final Swarm begins | `wave/WaveDirector.java:341` `beginSwarm` -> `wave/SwarmPortal.java:42` `open` | chat line only; the one sound in `SwarmPortal` (`:134`) is for *entering* the portal | `ENDER_DRAGON_GROWL` (pitch 0.6) | yes, in `SwarmPortal.open` (not a hotspot) |
| 3 | Shrine trial cleared | `shrine/MapShrines.java:410` `tick` (message at `:438`) | gold/XP pickups, chat line, particle `burst` (particles only) | `UI_TOAST_CHALLENGE_COMPLETE` | yes |
| 4 | Free chest appears | `relic/ChestManager.java:98` `dropFree` (placed at `:117`) | the chest block just exists; no cue that a reward dropped | `VAULT_OPEN_SHUTTER` | yes |
| 5 | Gate countdown tick | `hub/GateManager.java:126` `tickAll` (message at `:148`) | action-bar text and Darkness only | `NOTE_BLOCK_BELL`, once per second | yes |
| 6 | Boss 1 defeated | `wave/WaveDirector.java:236` `onHydraDefeated` | chat line only | (V2 adds one) | no, PR #66 |
| 7 | Curse / Greed shrine chosen | `shrine/MapShrines.java:316` `onChoice` | particles via `burst` (`:372`, no sound) + chat | `BEACON_ACTIVATE` | no |
| 8 | Shop purchase (stat, charge, slot, weapon) | `progression/ShopManager.java:59,106,124,145` | Silver spent, chat line only | `EXPERIENCE_ORB_PICKUP` or `UI_BUTTON_CLICK` | no |
| 9 | Weapon swapped in | `item/WeaponChoiceManager.java:182` `resolve` | chat line only | `ITEM_PICKUP` | no |
| 10 | Hearth ignites (hub built) | `hub/HearthBlock.java:68` `activate` | chat line only; once ever per hub | `BEACON_ACTIVATE` | no |
| 11 | Gate stirs / you join / you step away | `hub/GateManager.java:95` `click` and `:126` | action bar only | `BLOCK_BELL` family | no |

**Why 1 to 5 and not 6 to 11:** 1 happens at every level-up, 2 and 3 are the two biggest set pieces of a run, 4 is a reward the
player can miss, 5 is felt by every party at every start. 6 belongs to V2. 7, 8, 9, 10 and 11 are rarer or smaller.


## Why these five sounds (collision check, by grep of `SoundEvents.<NAME>` in `src/main/java`)

A cue only works if it means one thing. Counts of existing uses before choosing:

| Sound | Existing uses | Decision |
| --- | --- | --- |
| `ENCHANTMENT_TABLE_USE`, `UI_TOAST_CHALLENGE_COMPLETE`, `VAULT_OPEN_SHUTTER`, `NOTE_BLOCK_BELL` | 0 | free to use |
| `ENDER_DRAGON_GROWL` | 1 (a mob, `CorruptedSentinel`) | acceptable for the swarm start |
| `WITHER_SPAWN` | 4, including `SwarmWrath:33`, the recurring swarm scream | **rejected** for the swarm start: it would sound like the scream |
| `AMETHYST_BLOCK_CHIME` | `PickupSystem:196`, every gold pickup | **rejected** for the free chest: it would sound like gold |

Note: V3 (PR #70) reuses `AMETHYST_BLOCK_CHIME` for "a chest shimmers and fills again". That is the same collision with the gold
pickup. It is not changed here (different PR, already open); it is flagged to Koda in the INBOX.

## Not reached (27 flagged by the scan, not read)

`CharacterSelectManager.onChoiceReceived`, `CharacterCommand.trySelect`, `RunCommand.tryStartFrom/tryStartParty`,
`HubInteractions.runAction`, `HubSiteSearch.begin/tick`, `BossSummonerItem.use`, `WeaponChoiceManager.openMidRunChoice/openReplace/decline`,
`WeaponProgress.consumeUltimate`, `RelicHitEvents.scale`, `RelicUnlocks.announce`, `SynergyEffects.grantMomentumBonus`,
`TomeChoiceManager.openChoice/onRerollReceived/onBanishReceived`, `SwarmPortal.tickSite`, `WaveDirector.triggerBossNow/triggerDevourerNow/tickSwarm`,
`ArenaBoundary.notifyOnce`, `CircleBoundary.keepPlayerIn`, `RunManager.broadcastToSlot`, `MapShrines.open/startChallenge`.
They may already be covered or may be real silences. Each needs the same read before it is listed as a finding.

## How each filled sound is proven

Each fires through one test-visible log line, `SOUND_TEST <id> ...`, printed only when `-Demberfall.testMode=true`. The line is
printed in the same statement block as `playSound`, so a line without the sound is not possible by reading the code, and a test
that sees the line has run the real code path. A log line shows the call was made. It does **not** show a person can hear it.

## Results (fresh world per run, graded from the server log by `tools/testbot/cues_grade.py`)

| Part | Script | Checks | Result |
| --- | --- | --- | --- |
| main: Tome pick and skip, free chest and the cap, shrine trial | `cues_test.js` | T1 T2 F1 F2 F2b H1 X1 E1 | 8 pass, 0 fail |
| swarm: start and a repeated start | `CUES_MODE=swarm cues_test.js` | W1 W2 W2b X2 E1 + W0 in the script | 5 pass, 0 fail |
| gate: full 3 s hold, and walking away | `cues_gate_test.js` | G1 G2 G3 E1 | 4 pass, 0 fail |

**Controls that must stay quiet:** skipping a Tome (T2), free chests the run cap refuses (F2: 11 placed of 14 tries, 11 cues), a second
swarm start (W2), and stepping away from the gate (G3: 1 bell, then none).

**Mutations (each one breaks one cue in the real statement; the grader must fail):**

| Mutation | Caught by |
| --- | --- |
| M1 Tome cue also plays on skip | T2, X1 |
| M2 free chest cue played before the cap check | F1, F2, X1 |
| M3 shrine cue removed | H1, X1 |
| M4 swarm cue played twice | W1, W2b, X2 |
| M5 gate bell back on `elapsed % 20 == 0` (the first version) | G1, G2, G3 |

Source files were restored from a backup after each mutation and their SHA-256 sums matched the originals.

**Two corrections made while testing (kept here so nobody repeats them):**

- *Gate bell.* The first version rang on `elapsed % 20 == 0`. The first tick can land one tick after the click, so the 3 s bell was
  sometimes skipped (2 bells at pitch 0.95 and 1.0 were measured). It now rings once per distinct seconds-left value, tracked on the
  departure (`lastBell`), so a hold gives 3 bells at pitch 0.9, 0.95, 1.0. The action-bar text keeps its old `% 20` rule, so that text
  can still skip its first line; it was not changed here.
- *Mutation M2, first attempt.* Moving the chest cue to before `put(...)` changed nothing, because `put` succeeds on the first spot in
  the test arena. That was an equivalent mutant, not a test hole, so it was replaced by M2 above (cue before the cap check), which the
  grader does catch.

**Not established:** that anyone can hear these at a sensible volume, or that the sounds fit the game's feel. The log shows the call
was made. Pitch and volume choices are my picks from the vanilla list and need an ear. The 27 candidates under "Not reached" are
unread. Party play (more than one player at the gate) was not run; the bell is rung per member at their own position.
