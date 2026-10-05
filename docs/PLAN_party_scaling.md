# Plan: party scaling (issue #13, V3). DESIGN ONLY, no code until Koda approves.

Author: Vesper. Reviewer: Koda. Base: `import/full-mod` @ `460ead2`. Tested headless, look unverified (nothing here has been seen on a real screen, and nothing here has been run as code: this is a reading of the source plus arithmetic on its constants).

## 0. Read this first: the premise is wrong today

The issue asks how to scale for 1/2/5/10 players. **No player-facing path puts more than one player in a run today.** The mechanism exists (an operator command, see the last row), but nothing a player can click or type forms a party. Scaling numbers is the second problem; the first is that flow.

Evidence (all read from source, not run):

| Fact | Where |
|---|---|
| Every run is created for exactly one player: reserve a map slot, build/reuse the map, `startOnMap`, `joinPlayer(p)`, `WaveDirector.start`. | `RunCommand.tryStartFrom` |
| `tryStartFrom` refuses if the player is already in a run, and nothing lets a second player attach to an existing slot. | `RunCommand.tryStartFrom` first lines |
| The gate countdown is per player (`PENDING: UUID -> Pending`) and ends in `start(p, gate)` for that one player. | `GateManager` |
| The data structure allows a party: `playerSlots: UUID -> slot`, `hasAnyPlayers(slot)`, `broadcastToSlot`. There is no `partySize(slot)`; it would be a count of entries with that slot. | `RunManager` |
| `joinPlayer` resets the joining player's build, relics, weapon and stats. A late joiner starts from zero while others are mid-run. | `RunManager.joinPlayer` |
| **`/emberfall join <slot> <player>` (op only) already attaches any player to any live slot** by calling `RunManager.joinPlayer(level, instance, player)` (`EmberfallCommands` `join()`, line 122). So the attach step exists as a debug action. I first wrote that it did not exist: my probe typed it without arguments, which Brigadier reports as `Unknown or incomplete command`. | `EmberfallCommands:122` |

So the plan has two parts: **A. forming a party** (prerequisite: the attach call exists, the player-facing flow, limits and no-late-join rule do not; touches `GateManager`, `RunCommand`, `RunManager`) and **B. scaling the numbers** (pure maths plus a few call sites). B is useless without A. I propose they ship as separate PRs, A first.

## 1. What exists today (the numbers to scale)

All constants are quoted from the source; interpretations are marked.

**Spawning (`WaveDirector`)**
- `HOSTILE_CAP = 40` alive at once (line 101), enforced at lines 490 and 567; packs are sized to "the room left under the cap" (line 660).
- Threat ramps `+1 / 60 s`, capped at 20 (lines 104-105). Spawn interval is linear: `round(100 - t * 85)` ticks with `t = threat / 20` (lines 470-471). So **5.0 s between spawn events at threat 0, 0.75 s at threat 20** (12 to 80 events/min).
- Mob/elite health is one number, `statMultiplier = 1 + threat / 20` (1.0 to 2.0), passed to each `X.spawn(level, pos, statMultiplier)` (lines 509, 410). Elites add `TIER2_ELITE_STAT_BONUS = 0.5` after tier 2.
- Boss at `BOSS_SPAWN_AT_TICK = 12000` (10 min). Second boss (Devourer) after the first dies.

**Boss health.** `EmberGuardian` base `MAX_HEALTH 600` (line 264); `applyCurse(mods.bossStatMultiplier())` already multiplies it (x1.0 then 1.2 to 1.5, `RunModifiers`). That is an existing hook; party HP should go through the same door.

**Rewards**
- Silver: `RewardFormula.total(seconds, level, kills, gold, hydra, devourer)`: 1 per 10 s, 1 per 5 kills, 3 per level, 1 per 20 gold, +50 / +150 per boss (`RewardFormula`).
- `awardRunReward(server, player, slot)` is already **per player**. `level`, `kills`, `gold` are that player's. `seconds` and the two boss flags come from the slot (`RunTelemetry`).
- XP and Gold are not credited on kill. The kill spawns **orbs with no owner** at the mob (`KillRewards:52` XP, `:59` Gold); any run player within `MAGNET_RANGE = 6` pulls them and the nearest collects (`PickupSystem`).
- The kill count goes to the player who landed the last hit (`KillRewards:44`, `source.getEntity()`).

**Modifiers (`RunModifiers`).** Per-slot, pure numbers "so the balance can be checked without a server". This is the pattern to copy.

## 2. The design question that decides everything: what does a second player mean?

Three options; I recommend **(b)** and want Koda's call before any code.

- (a) **Solo only.** Close #13 with "not supported". Zero risk, zero multiplayer.
- (b) **Host-and-join before the countdown.** One player starts a run at the gate; others can join during the countdown window only (or before the first wave). Late join is refused. This avoids the mid-run reset problem (`joinPlayer` wipes build/relics).
- (c) **Drop-in/drop-out at any time.** Needs catch-up rules for level, relics and weapon. Large and risky; I would not start here.

Everything below assumes (b). If Koda picks (a), sections 3 to 6 become a shelved appendix.

## 3. Part A: forming a party (prerequisite)

Proposed behaviour, to be approved:
1. A player clicks the gate. Today that starts a private countdown. New: it opens a **loadout screen** (section 7) and, on confirm, starts the countdown as **host**.
2. During the countdown, another player who clicks the **same gate** joins that pending run (max 4; see section 4 for why not 10). Moving away cancels only their own participation; the host moving away cancels the run (today's rule, unchanged).
3. When the countdown ends, every participant goes through the existing `joinPlayer` for the **same** slot. `tryStartFrom` is called once for the host and the others attach to that instance.
4. After the run starts, `tryStartFrom` for a non-member of an active slot stays refused. No late join in v1.

Exact changes (signatures proposed, not written):
```java
// GateManager
private record Pending(BlockPos gate, double x, double z, long startedTick, UUID host, java.util.LinkedHashSet<UUID> party)
public static String click(ServerPlayer player, BlockPos gate, long nowTick)   // unchanged signature, new behaviour: join an existing Pending at this gate
// RunCommand
public static String tryStartParty(ServerPlayer host, java.util.List<ServerPlayer> party, double backX, double backY, double backZ)
// RunManager
public static int partySize(int slot)                                          // count of playerSlots values == slot
public static java.util.List<java.util.UUID> membersOf(int slot)
```
`tryStartFrom` stays as a thin wrapper that calls `tryStartParty(p, List.of(p), ...)`, so the solo path and every existing test keep the same entry point.

## 4. Part B: scaling the numbers

Let `n` = `RunManager.partySize(slot)`, fixed at run start in option (b) (so it is a constant per run, not a moving target).

**Proposal, not measured.** A single pure class `PartyScaling` (no game types), same style as `RunModifiers`:

| Quantity | Formula (proposal) | 1 | 2 | 4 | Note |
|---|---|---|---|---|---|
| Mob health multiplier | `1 + 0.5 * (n - 1)` | 1.00 | 1.50 | 2.50 | multiplies the existing `statMultiplier` |
| Boss health multiplier | `1 + 0.75 * (n - 1)` | 1.00 | 1.75 | 3.25 | goes through `applyCurse`; stacks with the curse (multiply) |
| Spawn interval divisor | `1 + 0.6 * (n - 1)` | 1.00 | 1.60 | 2.80 | more spawn events per minute |
| Hostile cap | `40 + 10 * (n - 1)` | 40 | 50 | 70 | see risk R1 |
| Pack size | unchanged | | | | packs are already sized to room under the cap |
| Silver | per player, unchanged formula | | | | see fairness rule |

The coefficients (0.5, 0.75, 0.6, 10) are **guesses**. They are placeholders to make the structure concrete; they must be tuned against the measured targets in section 5 before they mean anything.

Why not 5 or 10 players: at 10 the cap would be 130 live hostile entities. Nobody has measured the server cost of 40 (the current cap) with several players, let alone 130. I propose a hard party cap of **4 for v1** and leave 5/10 as a later question that needs a load measurement first. **Koda: did you intend 10 as a real target?**

**Fairness rule for rewards (proposal).**
1. Silver stays per player and uses the player's own kills, level and gold, exactly as today.
2. The two boss bonuses (+50 / +150) are **currently given to every member of the slot, including a player who did nothing**. In a party that is a free ride. Proposal: a member gets the boss bonus only if they dealt at least **10% of the damage the boss took** (needs a per-player damage tally on the boss; a new small structure, see R3). Below that they get half.
3. XP and Gold orbs: keep ownerless, but do not multiply the orb value by party size (more mobs already means more orbs). Known weak spot: the nearest player takes a drop (R4).
4. No reward is divided by `n`. Dividing would make a party strictly worse than four solos.

## 5. Measurable targets (to be filled by measurement, not promised)

The issue asks for time-to-kill and damage-taken targets per party size. **I have no measurements and will not invent them.** What I can state:
- Baseline unit: time for one player to kill one mob of each type at `statMultiplier = 1.0` with the starting weapon. I have not measured this. The bot (`bot/`) is Koda's.
- Target shape (proposal): the time to kill a mob per player stays within +/-20% of solo, and damage taken per player per minute stays within +/-25% of solo, for `n = 1, 2, 4`.

**Koda: can your bot produce (1) per-weapon DPS against a mob at `statMultiplier` 1.0 and 2.0, and (2) damage taken per minute at threat 0, 10, 20 for one player?** I need those two tables to turn the coefficients above from guesses into numbers. I will not touch `bot/`.

## 6. Tests

**Pure maths.** The repo has no `src/test` and no JUnit in `build.gradle` (checked). So "unit tests" need a decision:
- (i) add JUnit and a `test` task (new dependency, touches `build.gradle`), or
- (ii) keep the project's pattern: `PartyScaling` is pure Java and is exercised from a `tools/testbot` script that calls it through a tiny `/emberfall partyscale <n>` debug command printing the numbers.

I recommend (ii): no build change, and it tests the shipped jar. **Koda: (i) or (ii)?**

Assertions (pure, any choice): `n=1` returns exactly the current values (`1.0` everywhere, cap 40, interval unchanged), so solo is provably unchanged; each multiplier is monotonic in `n`; the cap never falls below 40; nothing is NaN or negative for `n` in 1..4; `n` out of range is clamped, not thrown.

**Live (bot, headless).** `party_test.js` with 2 bots: host clicks gate, second joins during countdown, both are in the same slot (`slotOf` equal), `partySize == 2`, the first wave's `statMultiplier` logged equals the formula, a third bot clicking after start is refused. Failing first: run it before Part A to show it fails for the right reason.

## 7. The gate loadout screen (the issue's last question)

Today the click only runs the countdown (`GateManager.click`). A screen is needed for a party, so the host and joiners can see who is in. Proposal, smallest thing that works: a **chest-style menu** opened by the click, server-driven (no client code, so it can be tested headless), with: the party list, each member's character, and a Start/Leave button. Confirming starts the countdown. **Caveat: the look is unverifiable for me** (no real client), so the layout is a guess until the owner or Koda sees it.
Alternative: skip the screen and show the party in the action bar during the countdown. Cheaper, less to go wrong. **Koda's call.**

## 8. What could break (ranked)

- **R1. Entity cost.** Cap 70 at `n=4` (section 4) is unmeasured. Mitigation: measure MSPT with the bot at caps 40/50/70 before choosing; the cap formula is the most likely number to change.
- **R2. `joinPlayer` resets build/relics/weapon.** Fine at the countdown (nobody has anything yet); catastrophic if a player joins mid-run. The "no late join" rule must be enforced in `tryStartFrom`, with a test.
- **R3. Per-player boss damage tally** does not exist. It needs a hook on boss `hurt`; the bosses are big classes (`EmberGuardian`, `DevourerBrain`) and conflict hotspots. Fallback with no new hook: keep the boss bonus for everyone (today's behaviour) and accept the free ride in v1.
- **R4. Ownerless orbs.** First player to the orb takes it; a player standing back gets less XP/gold. Not fixed in v1; measure first.
- **R5. Shrines** (`RunModifiers`) are per slot and "used once per run". With a party, one player's choice changes everyone's run. v1: only the host can use a shrine. Needs a test.
- **R6. Disconnect.** `n` is fixed at start, so a leaver does not change scaling (the rest keep the harder run). Simple, slightly unfair; the alternative (recompute live) changes mob health mid-fight, which I would avoid.
- **R7. Death/respawn.** What happens when one member dies but others live is not covered here. I have not read `RunEndHandler` for the multi-player case. **Not analysed: needs its own read before code.**
- **R8. The run-end HUD and `RunEndPayload`** are built for one player. Unread.
- **R9. Hub `GateRules` tests** exist in `tools/testbot`; changing `Pending` may break them. Not checked.

## 9. Order of work (each a separate PR, each needs Koda's approval)

0. **Measure before designing further (optional, no mod code):** with two bots and the existing `/emberfall join <slot> <player>`, record what really happens to a second player in a live run: does `joinPlayer` wipe their build, do mobs target both, what does `WaveDirector` do, what does `awardRunReward` pay each. This replaces several guesses in sections 4 and 8 with observations. Only a new `tools/testbot` script, nothing shared.
1. Decision on section 2 (a/b/c) and the open questions below. **No code.**
2. `PartyScaling` + its test (pure maths, solo unchanged). Touches no hotspot.
3. Part A: `GateManager` + `RunCommand.tryStartParty` + `RunManager.partySize`. Hotspots.
4. Wire `PartyScaling` into `WaveDirector` (`statMultiplier`, interval, cap) and the boss `applyCurse`. Hotspots.
5. Rewards fairness (boss bonus share), only if R3's hook is approved.
6. Loadout screen.

## 10. Questions for Koda (what I need, what I do not understand)

1. Section 2: **(a), (b) or (c)?** I recommend (b).
2. Was **10 players** a real target? I propose capping v1 at 4 until the entity cost is measured.
3. Can the bot give me the two measurement tables in section 5? Without them every coefficient in section 4 is a guess.
4. Tests: JUnit (i) or testbot (ii)?
5. Loadout: real menu, or action-bar party list?
6. I do not understand how **you** want the boss-bonus free ride handled (R3). Accept it in v1, or build the damage tally?
7. Should I do step 0 (the two-bot measurement with `/emberfall join`) first? It would turn R2, R4, R7 and R8 from guesses into observations. Related: R7/R8 (death of one member, run-end screen) I have not read. Should I read them before you review, or after you choose (a/b/c)?

## 11. Not verified

Everything here is from reading source at `460ead2`. Nothing was run. The formulas in section 4 are proposals with guessed coefficients. No multiplayer path has been tested because none exists. The look of any screen is unverified.
