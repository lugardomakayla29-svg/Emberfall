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

**The cap row is unmeasured.** `40 + 10 * (n - 1)` would be 130 live hostile entities at n = 10, and nobody has measured the server cost of even 40 with several players. So the first measurement is **server MSPT with the bot at caps 40 / 60 / 80**, not a formula. Until that exists, treat the cap row as a placeholder.

**10 players is a real target** (the owner's issue #6: "join near the gate (10+)", acceptance "1/2/5/10 players"). It is not dropped. Staging: the maximum party size is **one constant** (`PartyScaling.MAX_PARTY`), **v1 ships it at 4**, then it is measured and raised stage by stage toward 10. Every formula above must stay valid for `n` up to that constant, and the pure check asserts the maths for `n = 1..10` even while the shipped limit is 4, so raising it is a one-line change with the maths already proven. The owner is asked to confirm the staging.

**Fairness rule for rewards (proposal).**
1. Silver stays per player and uses the player's own kills, level and gold, exactly as today.
2. The two boss bonuses (+50 / +150) are currently given to every member of the slot, including a player who did nothing. **Decision (Koda, on PR #19): accept this in v1.** No boss `hurt` hook (the bosses are large hotspot classes). It is recorded here and in `STATUS.md` as a **known limitation**: in a party, a member who does no damage still receives the full boss bonus. Revisit only after a per-player damage tally has its own approved plan.
3. XP and Gold orbs: keep ownerless, but do not multiply the orb value by party size (more mobs already means more orbs). Known weak spot: the nearest player takes a drop (R4).
4. No reward is divided by `n`. Dividing would make a party strictly worse than four solos.

## 5. Measurable targets (to be filled by measurement, not promised)

The issue asks for time-to-kill and damage-taken targets per party size. **I have no measurements and will not invent them.** What I can state:
- Baseline unit: time for one player to kill one mob of each type at `statMultiplier = 1.0` with the starting weapon. I have not measured this. The bot (`bot/`) is Koda's.
- Target shape (proposal): the time to kill a mob per player stays within +/-20% of solo, and damage taken per player per minute stays within +/-25% of solo, for `n = 1, 2, 4`.

**Koda: can your bot produce (1) per-weapon DPS against a mob at `statMultiplier` 1.0 and 2.0, and (2) damage taken per minute at threat 0, 10, 20 for one player?** I need those two tables to turn the coefficients above from guesses into numbers. I will not touch `bot/`.

## 6. Tests

**Pure maths. Decision (Koda, PR #19): a pure class driven from `tools/testbot`, no JUnit, no build change.** The repo has no `src/test` and no JUnit in `build.gradle` (checked). So `PartyScaling` is pure Java with no game types, and its check is a plain `tools/testbot/relic_math/PartyScalingCheck.java` in the same style as the 26 existing `*Check.java` files (prints `PASS` / `FAIL` and `ALL PASS`). The CI loop picks up every `*Check.java` automatically, so it also runs in the CI maths step; the loop's `n_ok -ge 20` floor is unaffected. No debug command is needed unless the class ends up needing game types, which it should not.

Assertions (pure, any choice): `n=1` returns exactly the current values (`1.0` everywhere, cap 40, interval unchanged), so solo is provably unchanged; each multiplier is monotonic in `n`; the cap never falls below 40; nothing is NaN or negative for `n` in 1..4; `n` out of range is clamped, not thrown.

**Live (bot, headless).** `party_test.js` with 2 bots: host clicks gate, second joins during countdown, both are in the same slot (`slotOf` equal), `partySize == 2`, the first wave's `statMultiplier` logged equals the formula, a third bot clicking after start is refused. Failing first: run it before Part A to show it fails for the right reason.

## 7. The gate loadout screen (the issue's last question)

**Decision (Koda, PR #19): an action-bar party list first.** During the countdown the action bar shows who is in the party ("Party 2/4: NameA, NameB"), next to the existing "Departing in N" line. It is smaller, testable headless, and has less to go wrong. A real menu is a later, separate PR, only after someone with a real client can see it. My caveat still holds: I cannot see any screen, so the wording and placement of the action-bar text are unverified until a person looks.

## 8. What could break (ranked)

- **R1. Entity cost.** Cap 70 at `n=4` (section 4) is unmeasured. Mitigation: measure MSPT with the bot at caps 40/50/70 before choosing; the cap formula is the most likely number to change.
- **R2. `joinPlayer` resets build/relics/weapon.** Fine at the countdown (nobody has anything yet); catastrophic if a player joins mid-run. The "no late join" rule must be enforced in `tryStartFrom`, with a test.
- **R3. Per-player boss damage tally** does not exist and is **not built in v1** (decision on PR #19). The free ride on the boss bonus is accepted and recorded as a known limitation.
- **R4. Ownerless orbs.** First player to the orb takes it; a player standing back gets less XP/gold. Not fixed in v1; measure first.
- **R5. Shrines** (`RunModifiers`) are per slot and "used once per run". With a party, one player's choice changes everyone's run. v1: only the host can use a shrine. Needs a test.
- **R6. Disconnect.** `n` is fixed at start, so a leaver does not change scaling (the rest keep the harder run). Simple, slightly unfair; the alternative (recompute live) changes mob health mid-fight, which I would avoid.
- **R7. Death and leaving (read `RunEndHandler`, `finishRun`; NOT yet observed, step 0 will confirm).**
  - `finishRun` is **per player**: a member who falls (`endRunInsteadOfDying`), disconnects or runs `/expedition leave` is paid, sent home (`ReturnPoints.sendBack`) and removed with `RunManager.leavePlayer`. The arena and `WaveDirector` are stopped **only when `!hasAnyPlayers(slot)`**, so survivors keep fighting. Good: the run does not end for everyone.
  - **Consequence 1 (trade-off):** `n` is fixed at start (R6), so when a member falls the survivors keep the `n`-scaled run with fewer people. The party gets harder exactly as it gets weaker. Accepted for v1 because recomputing live would change mob health mid-fight; the alternative is listed, not chosen.
  - **Consequence 2 (reward):** the boss flags (`RunTelemetry.wasHydraDefeated(slot)`) are read **at the moment the player leaves**. A member who falls at minute 2 is paid without any boss bonus even if the party kills the boss at minute 10. This partly offsets the free ride (a member who did nothing but stayed alive is paid both). Not a bug; a property to state on the run-end screen later.
  - **Consequence 3:** Totem of Returning cancels a lethal hit **per player** (`RelicDefenceEvents.tryTotem`), so one member can be saved while another falls.
  - Still to observe in step 0: that the arena really survives when one of two leaves, that the leaver lands at their own return point, and that the gate lockout (`GateManager.runEnded`) is per player.
- **R8. Run-end screen: resolved by reading, no change needed.** `RunEndPayload` (`cause, seconds, level, kills, gold, hydraDown, devourerDown, silverEarned, silverTotal`) carries **that one player's** numbers and is sent only to them from `finishRun`. A party member's screen already shows their own run. The look is unverified (no real client).
- **R9. Existing gate tests that a `Pending` / `GateManager` change could break (counted, not felt):**
  - **Pure, runs in CI:** `tools/testbot/relic_math/GateCheck.java` asserts 7 `GateRules` members: `LOCKOUT_TICKS`, `countdownDone`, `lockedOut`, `moved`, `returnSpot`, `returnSpots`, `secondsLeft`. Part A keeps `GateRules` unchanged, so this should stay green; it must be re-run.
  - **Live, the only suite that exercises `GateManager.click` / `Pending`:** `tools/testbot/gate_test.js` (11 checks; verdicts from the server log via `gate_grade.py`). It drives the gate with the op debug command `/emberfall hubclick hubact_gate`, not a block click. Most at risk: **T2** (click, countdown, run starts), **T4** (gate works again after the lockout), **T4b** (a click right after a run ends is refused as "settling"). It is listed in `regress4.sh` and `regress_guardian.sh`.
  - **Named "gate" but with no gate reference found by grep:** `gate_noterrain_test.js`, `gate_weapon_test.js`, `attack_gate_test.js` (also in both regress lists). I have not shown they are independent of the gate; treat them as "re-run, not known safe".
  - Not checked: whether `hubclick` itself calls `GateManager.click` unchanged (it must keep its signature; section 3 does).

## 9. Order of work (each a separate PR, each needs Koda's approval)

0. **Measure before designing further (approved by Koda, do this first):** with two bots and the existing `/emberfall join <slot> <player>`, record what really happens to a second player in a live run: does `joinPlayer` wipe their build, do mobs target both, what does `WaveDirector` do, what does `awardRunReward` pay each. This replaces several guesses in sections 4 and 8 with observations. Only a new `tools/testbot` script, nothing shared.
1. Decision on section 2 (a/b/c) and the open questions below. **No code.**
2. `PartyScaling` + its test (pure maths, solo unchanged). Touches no hotspot.
3. Part A: `GateManager` + `RunCommand.tryStartParty` + `RunManager.partySize`. Hotspots.
4. Wire `PartyScaling` into `WaveDirector` (`statMultiplier`, interval, cap) and the boss `applyCurse`. Hotspots.
5. Rewards fairness (boss bonus share), only if R3's hook is approved.
6. Loadout screen.

## 10. Questions for Koda: answered on PR #19

1. Party model: **(b)** host-and-join during the countdown, no late join.
2. 10 players: **a real target** (owner's issue #6). v1 ships `MAX_PARTY = 4`, one constant, raised after measurement. The owner is asked to confirm the staging.
3. Bot tables (DPS vs mob at `statMultiplier` 1.0/2.0; damage taken per minute at threat 0/10/20): **not available yet**; they depend on Koda's issue #2 (the walk fix). Koda will post them on #13. Not blocking: the pure class is written first.
4. Tests: **(ii)**, `tools/testbot/relic_math/PartyScalingCheck.java`.
5. Loadout: **action-bar party list** first.
6. Boss-bonus free ride: **accepted in v1**, recorded as a known limitation.
7. Step 0 (two-bot measurement with `/emberfall join`): **yes, first**, after reading `RunEndHandler` (R7) and `RunEndPayload` (R8). New file under `tools/testbot` only; two bots that are not EmberTester; record, do not fix.

## 11. Not verified

Everything here is from reading source at `460ead2`. Nothing was run. The formulas in section 4 are proposals with guessed coefficients. No multiplayer path has been tested because none exists. The look of any screen is unverified.
