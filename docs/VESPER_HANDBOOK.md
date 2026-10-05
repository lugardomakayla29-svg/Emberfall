# VESPER'S HANDBOOK: everything about EMBERFALL, how we work, and what you own

You are **Vesper**, the collaborator's AI agent. I am **Koda**, the repo owner's AI agent. Treat me as your team lead for the
first weeks: I coach, I review your work, and I build my own part in parallel. We are two engineers on one codebase who can
**never talk directly**. Every message between us is a GitHub issue comment, a PR comment, a commit message or a line in
`STATUS.md`. If it is not written there, it does not exist for the other one of us. Read this whole file before you touch code.

---------------------------------------------------------------------------------------------------------------------------

## PART 1. THE PROJECT

### 1.1 What EMBERFALL is
A 3D action roguelike built as a **Fabric mod for Minecraft Java 1.21.11**, inspired by the game *Megabonk* (inspiration only,
never a copy: every relic, mob and mechanic is a Minecraft-flavoured original). Modid `emberfall`, package
`com.solme.emberfall`, mod version `0.1.2`. Authors in `fabric.mod.json`: solme.

The player loop:
1. In the **hub** (built by placing an Ember Hearth block) the player picks one of 8 **characters** (busts you right-click).
2. They click the **Expedition Gate** (3 s countdown, cancels if you walk away) and a **run** starts on a generated static
   **map** in its own dimension (an "arena slot").
3. A **Wave Director** spawns hordes that scale with time and a "threat" number. Fodder, veterans, elites, special mobs.
4. The player has **weapons** that fire **automatically** (there is no attack key). They gain XP, level up, and pick **tomes**
   (stackable stat upgrades) and sometimes new weapons on a popup. Gold drops from kills.
5. **Chests** on the map cost gold (price rises each opening); **free chests** drop from elites, bosses and shrines. Chests
   give **relics** (run items with a rarity). **Testificate merchants** (invulnerable Nitwit-villager model) visit every 3 min
   (first at 2:00) and sell 3 items.
6. Three **map shrines** (Challenge, Boss Curse, Statue of Greed) give risk/reward choices.
7. **Bosses**: Ember Guardian (first, at 10:00; magma head, lava tentacles, 4 pylons) and the Devourer (segmented worm).
8. After the Devourer dies the **Final Swarm** starts: endless powerful mobs, a silver **multiplier** that climbs 0.1x per 20 s
   up to 5.0x, and an **optional escape portal**. Leave = cash out the multiplier. Stay = it keeps rising, and the mobs too.
9. The run ends (the player never really dies: `RunEndHandler` heals them and ends the run). Silver (meta currency) is paid.

### 1.2 The 8 weapons (levels 1 to 10, each has an auto-firing Ultimate)
Broadsword, Twin Daggers (Phantom Blades), War Halberd (Quake shock wave), Gravechain (Grave Legion raises foes), Hunting Bow,
Arcane Staff, Spectral Sickles (Reaper's Rite), Ashen Beacon (Pyre Lantern). Each has a capstone tome. Standing design rule:
every weapon must have more strikes/projectiles per level, a jaw-dropping ultimate, synergy, and feel different from the others.

### 1.3 Systems you will meet (where the code lives)
`src/main/java/com/solme/emberfall/`:
| Package | What it does |
|---|---|
| `EmberfallMod.java` | Registration and tick hooks (a conflict hotspot). |
| `world/` | `RunManager` (run lifecycle, slots), `MapManager`/`MapBuilder`/`StaticMap` (the map), `RunEndHandler`, `BlockJournal` (restores blocks), `DelayedTasks`, `RunMobTeam` (who may hurt whom), `RunMobAggro`. |
| `wave/` | `WaveDirector` (spawns, threat, bosses), `FinalSwarm` (pure ladder), `SwarmPortal`, `SwarmWrath`, `WrathCadence`. |
| `combat/` | `AutoAttackSystem` (the engine of auto fire), one `*System` per weapon, `Fx` (telegraph visuals), `QuakeWave`, `PhantomBlades`, `ReapersRite`, `GraveLegion` (pure maths beside systems). |
| `entity/` | ~50 mobs and bosses. Multi-part bosses = one brain entity + display segments. `MobRig`, `TentacleRig`. |
| `relic/` | 24 relics (`RelicPool`), `ChestManager`, `ChestOpening` (pure rule), `MerchantManager`, `RelicStats`, `RelicEffects`, `RelicUnlocks`. |
| `tome/`, `progression/`, `leveling/`, `character/`, `pickup/` | Tomes and offers, meta progress/shop, XP curve, the 8 characters, gold/XP pickups. |
| `hub/` | Hearth, `GateManager`/`GateRules`, hub builder, `HubInteractions`. |
| `shrine/` | `MapShrines`, `RunModifiers`. (`ShrineManager` is the old proximity shrine, unused on map runs.) |
| `network/` | ~22 custom packet payloads (HUD sync, choices, swarm HUD...). Shared files. |
| `mixin/` | 6 mixins (hostility, no vanilla orbs, tab-list filter...). |
| `bot/` | **EmberTester**, the test-bot player (see PART 4). Koda's area. |
| `src/client/java/.../client/` | Client only: HUD, screens, renderers. |
Data lives in `src/main/resources/`: `assets/emberfall` (textures, models, lang, sounds), `data/emberfall` (map, structures, recipes, loot).

### 1.4 The toolchain (what you need installed)
- **JDK 25 to BUILD.** Loom 1.18.2 itself needs a JVM 25 (`Dependency requires at least JVM runtime version 25`; JDK 21 fails,
  found by Vesper in V0). The bytecode TARGET stays Java 21 (`options.release = 21`, `fabric.mod.json` says `java >= 21`), so
  players only need Java 21. Do NOT raise the target.
- **Gradle** via the wrapper `./gradlew` (Loom `1.18.2`, Fabric loader `0.19.5`, Fabric API `0.141.6+1.21.11`,
  **official Mojang mappings**, split client/main source sets). First build downloads Minecraft and takes several minutes.
- **Node.js 20+** and `npm install` in `tools/testbot/` (needs `mineflayer`). Test clients are headless mineflayer bots.
- **Python 3** (map scripts, log graders). **git** and the **GitHub CLI** `gh` or an API token.
- A **Fabric server launcher for 1.21.11** in `run/server/` and `eula=true`. Start it with `-Demberfall.testMode=true` (otherwise
  a non-Fabric test client is kicked by registry sync).
- Optional: a graphical Minecraft client with the built jar. **Nobody has done this yet (see 2.2).**

---------------------------------------------------------------------------------------------------------------------------

## PART 2. HONEST STATE OF THE PROJECT

### 2.1 Done and proven by live tests
Weapons 1-10 and all 8 ultimates; tomes (29) with stacking and class gating; relic pool (24) with unlock meta-progress;
paid/free/gold chests; Testificate merchants; relic HUD; Final Swarm with portal and multiplier; Expedition Gate (plate loop
fixed); creative tab with mob eggs, bosses, and summoner items; EmberTester joins as a real player and chooses its own
character, weapons and tomes (bot_brain_test 5/5). Mobs: hordes, Tiki, Witch, Pink Slime, Kraken/Guardian, Devourer.

### 2.2 The big caveat (say it every time)
**Everything was tested headless.** No graphical client has ever rendered the mod in this project. So HUD layout, particle
looks, ultimates, the merchant GUI, chest animation, boss looks, screens and audio are all **LOOK UNVERIFIED**. A passing bot
test proves logic, never appearance. Never write "looks good". Write "tested headless, look unverified".

### 2.3 Open and unresolved
- **Issue #2 (Koda owns): the bot walk test fails.** Pure maths passes (BotWalk 21/21, BotPlan 17/17), but live the summoned
  foe and the path scout vanish and the bot never moves. Cause unknown. Do not touch `bot/`.
- Live suites were made portable (paths rewritten) but **not re-run from the new layout**. Expect path fixes.
- `./gradlew build` was not re-run from the cleaned repo tree. Vesper's first job proves it (see PART 7).
- Known obsolete/red: `boundary_test` (old box arena). Several suites print no verdict (attack, attack_gate, beam,
  ring_phase3, ring_tower): they were stripped of debug lines and not re-proven.
- CI is parked in `docs/ci/build.yml.txt` (token has no `workflow` scope).

---------------------------------------------------------------------------------------------------------------------------

## PART 3. THE RULES (binding, from the owner; they override your defaults)

1. **No half-baked features.** Ship it complete and production-ready, or do not open the PR.
2. **Reproduce before fixing.** Never guess at a bug. A bug PR shows the failing output first (test line, log line, bytecode).
3. **Measure, do not assume.** Performance and entity count matter in every mob mechanic. Prefer **zero or minimal entities**:
   virtual state plus particles, display entities only when needed. Give ms/tick and entity counts as numbers.
4. **Deep-dive the engine first.** Read the decompiled 1.21.11 method (bytecode via `javap`, or the Loom-remapped sources)
   before building on an API. Do not code from memory of older versions: 1.21.x changed a lot (item models, data components,
   `snapTo` instead of `moveTo`, `Avatar` not `Player` for some things).
5. **Anti-tamper on every custom item** carrying PersistentData/data-component tags.
6. **Custom mobs mirror player behaviour and stay off the tab list.** Player-head textures from minecraft-heads.com or similar.
7. **Do NOT use sub-agents** for research or design. Do the reading and design yourself.
8. **A test that prints no verdict proves nothing.** Every suite prints `PASS`/`FAIL`. Never loosen an assertion unless measured
   numbers show the test was wrong, and say so in the PR.
9. **No rushing. Check twice.** Say plainly what is unverified.
10. **Armour sets and the weapons shop are END GAME and LAST** (issue #9). Do not start them.
11. Held off on purpose: Smoke Veil, and the gold-cost shrine content beyond what exists.

### 3.1 Engine conventions that have burned us (read every line)
- Custom hostiles must pass `AutoAttackSystem.isEmberfallHostile()` (namespace `emberfall:`) to be targetable by weapons.
- **Never use `NoAI` for a test dummy** (it blocks knockback/movement). Use `movement_speed` base 0 via `/attribute ... base set`
  right after summon (inline `Attributes` NBT on `/summon` does not reliably stick for max_health).
- Any once-per-death effect (splits, drops) must fire from `remove(Entity.RemovalReason)`, not from a tick-time
  `isDeadOrDying()` check: an instant kill can remove the entity before its next tick.
- Constant-contact damage must snapshot/restore `deltaMovement` around `hurtServer` (automatic knockback halves velocity).
- A plain `/summon` runs in the **operator's** dimension. Use `/execute as <Bot> at @s run summon ...` to act in the bot's world.
- Registering any new registry (sounds, entity types...) breaks registry sync for vanilla test bots: add it to the OPTIONAL list
  in `EmberfallMod.enableHeadlessTestModeIfRequested`.
- `PlayerBuild`/`CombatStats` are static maps keyed by player UUID and survive a bot reconnect: restart the server or leave the
  run properly or old tomes contaminate the next test.
- Vanilla gives 0.5 s of hurt immunity: probe hits for relic windows must be 0.75 s apart.
- A creative-mode player is never a valid mob target. Ability tests need survival plus resistance and regeneration.
- Build without `-q`, confirm `compileJava` ran, and confirm the jar is newer than the sources. A quiet build once hid a stale jar.
- Never copy the jar while a JVM runs. Wait for `Done (` in the log and confirm exactly one java PID.
- Permission tests need a player that is **not** in `ops.json`. (`PlainPlayer` turned out to be op 4; use `NoPermGuest`.)
- Chat-parsed `/data get` replies: read one raw reply before writing a regex. Chat also carries action-bar and XP lines.
- `/data get entity @a[name=X] Pos` is rejected (more than one entity); use `/data get entity <Name> Pos`.
- A grader counting only lines that START with `FAIL` misses lines that do not; make graders strict.
- Kill a test server in its own command and start the next in another. Long node runs outlive tool timeouts: launch detached, poll.

### 3.2 Things that are NOT allowed
Commit jars, worlds, logs, tokens, `run/`. Force-push or rewrite shared history. Merge your own PR if it touches a hotspot.
Claim something works without showing the test output. Overwrite the other agent's open branch. Edit a file an open PR touches.

---------------------------------------------------------------------------------------------------------------------------

## PART 4. HOW WE TEST

### 4.1 Three layers
1. **Pure maths checks** (`tools/testbot/relic_math/*Check.java`): plain Java, no server, prove numbers and rules. 24 exist, 21
   run in CI. Compile with `javac -sourcepath src/main/java`. They print `PASS`/`FAIL` and `ALL PASS`.
2. **Live suites** (`tools/testbot/*_test.js`, ~197 scripts): a mineflayer bot joins a real server, runs commands and asserts.
   Run with `tools/testbot/one_suite.sh <suite> <seconds>`, read `/tmp/one_<suite>.txt` (ends `ONE_DONE`), and check
   `grep -c Exception run/server_run.log` is 0.
3. **Regression bundles** (`tools/testbot/regress*.sh`, `reg_*.sh`): run many suites. `regress4.sh` is the big one (61 runs).
   Before any PR touching shared systems, run the bundle that covers them.

### 4.2 The method that keeps us honest
- Write the probe before the fix. Measure the engine, write the number in the PR.
- Always run a **no-change control** next to a diff test (it separated real effects from world drift more than once).
- A permanent `TEST_MODE` trace log (for example `OPEN_TEST`, `PHANTOM_TEST`) is better than guessing from chat output.
- Server-side yes/no queries (`/execute if entity ...`) beat mineflayer entity tracking, which gave wrong results before.

### 4.3 EmberTester (the test bot) in one paragraph
`bot/EmberBot` builds a client-less `ServerPlayer` (a fake connection whose channel swallows writes). It appears to the game as a
real player (`@a` matches it), hidden from the tab list by `EmberfallTabListMixin`. `BotBrain` answers tome/weapon popups through
the real managers. **Measured:** a client-less player does not move from input or velocity, only from `teleportTo`/`snapTo`
(`snapTo` is visible to other clients). So `BotPilot` places it one step per tick along a vanilla path computed by `BotScout`
(an invisible NoAI zombie per level). Walking speed = movement attribute x 2.0 blocks/tick (measured median 0.1994 at 0.1).
It is **Koda's** area and currently broken live (issue #2).

---------------------------------------------------------------------------------------------------------------------------

## PART 5. HOW WE COLLABORATE

### 5.1 Communication protocol
| Need | Where |
|---|---|
| Start work | Comment `CLAIM Vesper <files>` on the issue, assign your human, label `status:claimed`. |
| Ask Koda something | Comment on the issue starting `@Koda QUESTION:` with what you tried and what you saw. |
| Hand off / stop | Hand-off comment (template below). |
| Show progress | Push a branch early and open a **draft PR**. |
| Report a bug you found | New issue with the FAILING output pasted and the command that produced it. |
| Daily state | Update `STATUS.md` in your PR. |
Koda checks the repo at the start of each session. Expect a reply on your next sync, not instantly. **Never wait idle:** if you are
blocked, write the question, then take the next unblocked issue.

**Hand-off template:**
```
STATE: done | blocked | needs-review
DONE: <what works, with the test that proves it>
NOT DONE: <what is missing>
UNVERIFIED: <what was not checked>
NEXT: <the one next step>
FILES: <paths touched>
```

### 5.2 Git rules
- Branch `vesper/<topic>`. Koda uses `koda/<topic>`. One topic per branch. Never commit to `main`.
- Small commits with messages that say WHY. Rebase on `main` before opening the PR. Squash-merge.
- Before pushing: `git diff --stat`. If it touches more files than your issue listed, stop and explain in the PR.
- Secrets: never paste a token into a file, log or comment.

### 5.3 Review protocol (how we prevent mistakes)
- **Koda reviews every Vesper PR; Vesper reviews every Koda PR.** Reviewing means: read the diff, pull the branch, run the suites
  the PR claims, run the regression bundle, and comment with the evidence (paste the PASS lines). "LGTM" without output is not a review.
- A reviewer may block for: no failing-first evidence, a loosened assertion, a new entity without a count, an unmeasured
  performance claim, a touched hotspot without an announcement, or an unstated "unverified".
- Approve only when the PR template's checklist is fully ticked by evidence, not by assertion.

### 5.4 Conflict hotspots (announce in the issue BEFORE editing)
`world/RunManager`, `wave/WaveDirector`, `combat/AutoAttackSystem`, `EmberfallMod`, `lang/en_us.json`, any `network/*Payload`,
`relic/RelicPool`. Add a NEW line or NEW file. Never reformat or reorder an existing block. If you must change a shared method,
say so in the claim comment and wait for Koda's "OK" on the issue.

### 5.5 Mistakes we are designing out
| Risk | Guard |
|---|---|
| Testing stale code | Build without `-q`; check jar newer than sources; `cmp` the jar you deploy. |
| Test that proves nothing | Verdict lines required; `grep -c PASS` must equal the expected count. |
| Fixing the wrong thing | Reproduce first; paste the failing output in the PR. |
| Two of us editing one file | Claim comment lists files; one open PR per file. |
| "Works" that was never seen | Every PR states what is look-unverified. |
| Performance regressions | Entity count and ms/tick numbers in the PR. |
| Memory lost between sessions | STATUS.md, issue comments, hand-off template. |
| Junk in the repo | `.gitignore`; no jars, logs, worlds. |

---------------------------------------------------------------------------------------------------------------------------

## PART 6. WHO DOES WHAT

### 6.1 Koda (lead) owns
- **#2 EmberTester walk fix**, **#3 bot answers shrines/merchant/shop/run-end**, **#4 bot cost inside a run and party fill**.
  (Files: `bot/*`, `BotBrain`, `BotPilot`, `BotScout`, `EmberBot`.) Vesper does not edit these.
- **Review** of every Vesper PR, and the regression bundle runs.
- **#5 Endgame polish** follow-ups and **#6 Party scaling design** (because it touches `RunManager`/`WaveDirector`).
- Packaging and delivery to the owner (zip + WHATS_NEW), after both of us are merged.

### 6.2 Vesper owns (in this order)
**V0. Onboarding proof (do this first, no coding).** Clone, build, run the pure checks, run ONE live suite, and report in a
comment on PR #1: JDK used, build time, result of `./gradlew build`, the 21-check output, the suite verdict lines, and every path
or script that broke. *This also verifies Koda's unverified portability work.* Fix broken paths in `tools/` only (branch
`vesper/portable-paths`). Acceptance: `relic_test` prints `RESULT: ALL PASSED` (it had 27 assertions when Koda last ran it) and `chest_test` has no FAIL lines (19 PASS when Koda last ran it). If a count differs, report the exact difference instead of adjusting the test.

**V1. Issue #7 Creative tab overhaul + non-op `/emberfall` test.**
Files: `item/`, `ModCreativeTab`, `lang`, new `tools/testbot/nonop_test.js`. A pre-existing creative tab has eggs for 19 mobs,
boss summoners and more; audit it against `entity/ModEntities.java`, list every missing mob/boss/test item, add them, and
write the non-op test: a client NOT in `ops.json` must get `Unknown or incomplete command` for `/emberfall`, an op must succeed.
Acceptance: `nonop_test` prints 2+ PASS with a control; every registered mob has an egg or a documented reason it cannot.
Risks: `lang/en_us.json` is a hotspot (append only); do not use `PlainPlayer` (it is op 4).

**V2. Issue #8 Real-client visual check (the biggest unknown).** Install a graphical Minecraft 1.21.11 with Fabric and the built
jar; start a run; capture a screenshot or clip of each: HUD, relic list, chest open, merchant GUI, swarm portal and swarm HUD,
each weapon ultimate, Guardian and Devourer, gate countdown. File one bug issue per defect with the screenshot.
Acceptance: one checklist comment with a screenshot per feature. If you have no graphical client, say so immediately and take
V3 instead. **Do not fabricate screenshots or claim a look you did not see.**

**V3. Issue #6 Party join (10+), fair scaling, fair rewards: DESIGN ONLY first.** Read `RunManager`, `GateManager`,
`WaveDirector`, `RunModifiers`. Write `docs/PLAN_party_scaling.md` with the measured per-player time-to-kill and incoming
damage targets for 1/2/5/10 players, the scaling formula, and the exact method signatures you will change. Koda reviews the
plan BEFORE you code (these are hotspots). Acceptance: plan approved on the issue.

**V4. Content polish from the playtest report (`docs/playtest-report-1.md`, sections B and F).** Pick ONE per PR: witch fodder
feel, coloured mob names, fodder line-up expansion, village houses. Each needs a live test with a control and an entity-count
measurement. Files are mob classes in `entity/` (not hotspots).

**V5. Cross-review Koda's PRs** (start with #1, the import). Run the suites, paste output.

### 6.3 Not yet assigned (ask before starting)
Issue #9 armour/shop (LAST, owner undecided). Anything touching `bot/`. Anything on `main` directly.

### 6.4 Suggested rhythm
Sync (pull, read STATUS.md, read issue comments, read open PRs) -> claim -> branch -> test first -> code -> suites -> draft PR ->
hand-off comment -> Koda reviews -> fix -> squash-merge -> update STATUS.md. One issue at a time until V0 and V1 are merged;
after that you may run two in parallel if their file lists do not overlap.

---------------------------------------------------------------------------------------------------------------------------

## PART 7. YOUR FIRST SESSION, STEP BY STEP
1. `git clone` the repo, `git checkout import/full-mod` (until #1 is merged).
2. Read in order: this file, `AGENTS.md`, `WORKFLOW.md`, `STATUS.md`, `docs/main.md`, `docs/playtest-report-1.md`, open issues.
3. Install JDK 21+, Node 20+, run `./gradlew build` (no `-q`). Record the result.
4. Run the pure checks (the loop in `docs/ci/build.yml.txt`, 21 should pass, 3 are skipped on purpose).
5. Follow `README.md` to set up `run/server`, then run `relic_test` with `one_suite.sh`.
6. Post your V0 report as a comment on PR #1 using the hand-off template.
7. Claim V1. Reply to this handbook with any statement in it that contradicts the code you found: **challenge it**. I want to
   know where I am wrong. I was wrong about the Java version earlier and fixed it.

## PART 8. WHAT KODA PROMISES YOU
Answers to your `@Koda QUESTION:` comments at the start of my next session; a review on every PR within one of my sessions;
no edits to your branch without a comment; honest status (including "I do not know why"); and credit for your work.
If you are unsure, stop and ask in writing. A question costs minutes. A wrong merge costs the owner's trust.
