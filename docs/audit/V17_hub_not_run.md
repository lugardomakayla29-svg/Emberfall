# V17: suites that may stand in the hub, not in a run

**Report only. Tested headless, look unverified.** Nothing was run. Every statement below comes from reading the files named, or from a search of `src/` I ran in this checkout. Koda's own measurements (the 31.8 / 31.9 / 32.6 s map build, `activeInstances()=0`) are quoted as his, not re-measured by me.

Asked by Koda (INBOX 2026-10-08 17:45 CT). I do not edit the tests.

## What I could and could not read

- **Could read:** the 32 suites below as `tools/testbot/<name>_test.js` on `main`, plus `prebuild.js`, `one_suite.sh`, `regress.sh`, `regress3.sh`, `regress4.sh`, `regress_guardian.sh`, `regress_cinder.sh` and the other runners.
- **Could NOT read:** your sandbox folder `emberfall/bot/`. I only have the repo copies. If a sandbox copy differs from the repo copy, my lines do not describe it.
- **`boundary_test.js` is not in `tools/testbot/` on main.** It was moved to `tools/testbot/obsolete/boundary_test.js` by commit `0a08856` (Koda, 2026-10-07 13:04 UTC), and I read that copy. `regress4.sh` still names `boundary_test` in its suite list, so the runner would look for a file that is not there. That is a stale entry, not something I fixed.
- **Count mismatch, not corrected:** you counted 40 suites with `ask('/expedition', 1500..3500)`. In `tools/testbot/` I count **109**. I cannot see your folder, so I report both and do not claim either is wrong.

## The finding that changes the question: the harness already pre-builds the map

`tools/testbot/prebuild.js` (11 lines) runs `/emberfall mapbuild 0`, waits for `MAPDONE` (up to 100 polls of 2 s), and its own comment says it exists "so a suite's /expedition only has to START a run". Whether a suite stands in the hub therefore depends on **how it is launched**:

| Launcher | Builds the map first? | Evidence |
|---|---|---|
| `regress4.sh` | **Yes**, for every suite except `map_build_test` and `map_run_test` | line `case $t in map_build_test|map_run_test) ;; *) node prebuild.js ...` |
| `regress_guardian.sh` | **Yes** | its `one_suite.sh` call is `PREBUILD=1 bash one_suite.sh $s 300` |
| `one_suite.sh` with `PREBUILD=1` | **Yes** | `if [ "$PREBUILD" = "1" ]; then node prebuild.js` |
| `one_suite.sh` without it (e.g. `regress_cinder.sh`, `cover_rep.sh`) | **No** | the script only starts the server, waits 5 s and runs the suite |
| `regress3.sh` | **No** | after `sleep 5` it runs `node $t.js` directly |
| Bare `node <suite>.js` on a fresh server | **No** | nothing builds the map |

Koda's two proofs (the Tiki stall, and `boundary_test` giving up after 3 s) were both bare runs on a fresh server, which matches this table.

## The facts every row below relies on (each checked in `src/`)

1. **A run only exists after the build.** `RunCommand.tryStartFrom` calls `MapManager.ensureBuilt(expedition, slot, ..., built -> { ... RunManager.startOnMap(...); RunManager.joinPlayer(...); WaveDirector.start(instance); })`. The reply "Expedition started" is printed before that callback (RunCommand.java lines ~118 to 138).
2. **`slotOf(player)` is null until the join.** `playerSlots.put` is at `RunManager.java:376`, inside `joinPlayer`. `BUILDING` is not consulted by `CharacterCommand`.
3. **Run commands refuse without a run.** `/emberfall boss <slot>` and `/emberfall wavestop <slot>` both reply "No active Wave Director for slot N" and do nothing when `WaveDirector.get(slot)` is null (`EmberfallCommands.java` `bossTest` line 908, `waveStop` line 816). They do not spawn a boss in the hub.
4. **Run music starts at the join.** `RunMusic.start(player)` is at `RunManager.java:379`, inside `joinPlayer`.
5. **The purge/sweep systems skip non-run arenas.** `RunMobPurge.java:50`: `if (!arena.inPlace()) { continue; }`.

## Rectangular in-place run: does any start path still make one?

**No.** In all of `src/` there are exactly two `new ArenaInstance(` calls:

| Where | Flags | Shape |
|---|---|---|
| `RunManager.java:106` (`pasteArena`) | `inPlace=false`, `map=false` | pasted arena, not in place. Only caller: the admin paste command, `EmberfallCommands.java:728` |
| `RunManager.java:157` (`startOnMap`) | `inPlace=true`, `map=true` | circular map run |

Every player-facing start funnels into `startOnMap`: typed `/expedition` (`RunCommand.java:136`), the hub plate (`GateManager` to `RunCommand.tryStartParty`), and the Rift gate (`RiftGate` to `RunCommand.tryStartParty`, which is the `startOnMap` call at line 207). `EmberfallCommands` `join` (line 849), `waveStart` (line 810) and `BotEggItem` (line 63) take an arena that is **already active** (`RunManager.getActive(slot)`), so they cannot change its shape.
`inPlace=true` with `map=false`, which is what `boundary_test` B0 asserts (a 57-wide box), is never constructed. **`boundary_test` cannot pass on any current start path; it is correctly retired. Delete or keep it in `obsolete/`; do not rewrite it.** The retire note in `obsolete/README.md` says the same and names the replacements (`circle_test`, `map_boundary_test`, `map_mob_stray_test`). I checked the code rather than the note.

## Classification of the 33

Wait = time the file spends between the `/expedition` ask and its first line that needs a run, counting `ask(..., ms)` and `sleep(ms)`, read by hand. The build is 31.8 to 32.6 s (Koda's logs). No file contains any poll for a run (`Dimension`, `activeInstances`, `wavestatus`, `hudstate`, or similar); I searched all 33 and read the three regex hits, all false positives (a comment, a regex literal, an array of labels). `rift_inrun_test.js` line 19 is the only file with the working pattern.

Classes: **NEEDS-FIX** = on a bare launch it stands in the hub at its first run-dependent step. **PROBABLY-FINE** = its first step does not need a run, or it is only run through a launcher that pre-builds. **CANNOT-TELL** = depends on something I could not read.

Where a launcher pre-builds, the class is PROBABLY-FINE **for that launcher only**; the file itself still has no wait, so it fails if someone runs it bare.

### Group A: boss suites (`/emberfall boss 0` right after a 3 s wait)

Quoted line for all: `await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);` then `await ask('/emberfall boss 0', 900); await ask('/emberfall wavestop 0', 300);`. Wait: 3.0 s, no poll. In the hub both commands reply "No active Wave Director" (fact 3), so the boss never exists.

| Suite | /expedition line | First assertion needs a run? | Launched by | Own verdict? | Class |
|---|---|---|---|---|---|
| attack | 17 | Yes, the boss (`/emberfall boss 0`, line 18) | regress4 (pre-built), regress_guardian (pre-built) | 1 print line; main result comes from the log grader | PROBABLY-FINE (pre-built launchers only) / bare: NEEDS-FIX |
| attack_gate | 12 | Yes, `hold()` tps to `${G}` (line 16) | regress4, regress_guardian (both pre-built) | none; no assertion | PROBABLY-FINE (pre-built launchers only) / bare: NEEDS-FIX. In the hub it prints nothing a grader can fail on |
| beam | 17 | Yes, `kill pylon`, `Health set` on `${G}` (lines 20 to 21) | regress4, regress_guardian | none | PROBABLY-FINE (pre-built launchers only) / bare: NEEDS-FIX. Same as attack_gate: no verdict of its own |
| calm | 18 | Yes (line 19 boss, 21 `Health set`) | regress4, regress_guardian (pre-built); **regress_cinder (NOT pre-built)** | none; judged by `attacks begun` count from the log | PRE-BUILT launcher: PROBABLY-FINE / regress_cinder: NEEDS-FIX |
| cinder | 13 | Yes (line 14) | **no runner names it** | none | NEEDS-FIX (no launcher) |
| cinder_dodge | 13 | Yes (line 14) | **no runner names it** | none | NEEDS-FIX (no launcher) |
| cinder_walk | 14 | Yes (line 15) | **no runner names it** | none | NEEDS-FIX (no launcher) |
| cinder_still | 14 | Yes (line 15) | regress4, regress_guardian (pre-built); **regress_cinder (NOT pre-built)** | none | PRE-BUILT launcher: PROBABLY-FINE / regress_cinder: NEEDS-FIX |
| cover | 14 | Yes, `NoAI set` on `${G}` then `num(G,'Pos[1]')` (lines 16 to 17) | `cover_rep.sh` via `one_suite.sh`, **no PREBUILD** | yes: 12 `check` lines | NEEDS-FIX (only launcher, cover_rep.sh, does not pre-build) |
| ledge | 16 | Yes, `P(G)` then `fill` (line 18) | regress4, regress_guardian (pre-built) | 3 verdict lines | PROBABLY-FINE (pre-built launchers only) / bare: NEEDS-FIX |
| ring_guard | 14 | Yes (line 15 boss, 17 `Health set`) | **no runner names it** | none | NEEDS-FIX (no launcher) |
| ring_high | 14 | Yes (same) | **no runner names it** | none | NEEDS-FIX (no launcher) |
| ring_phase3 | 14 | Yes (same) | regress4, regress_guardian (pre-built) | none | PROBABLY-FINE (pre-built launchers only) / bare: NEEDS-FIX |
| ring_tower | 16 | Yes, line 20 `const c = centre(); c.x + 15` | regress4, regress_guardian (pre-built) | none | PROBABLY-FINE (pre-built launchers only) / bare: NEEDS-FIX. In the hub `centre()` returns `null` (no "boss fight started" line), so line 21 throws a TypeError: a loud crash |
| gate_weapon | 20 | Yes, `countR(PYL)` then `W0 pylons present` (line 25) | regress4, regress_guardian (pre-built) | has `check` lines | PROBABLY-FINE (pre-built launchers only) / bare: NEEDS-FIX. In the hub W0 fails (`pylons=0`): loud |
| gate_noterrain | 20 | Yes, `N0 the boss spawned` (line 33) | regress4, regress_guardian (pre-built) | has `check` lines | PROBABLY-FINE (pre-built launchers only) / bare: NEEDS-FIX. In the hub N0 fails (`guardians=0`): loud. Before that it fills about 36 volumes of stone around the player in whatever world the player stands in, which in the hub is the hub |
| tentacle | 22 | Yes (line 23) | **no runner names it** | has verdict lines | NEEDS-FIX (no launcher) |
| tentacle_attack | 16 | Yes (line 17 boss, then 25 s of waiting for the loop) | **no runner names it** | 8 verdict lines | NEEDS-FIX (no launcher) |
| tentacle_floor | 18 | Yes, `F0 boss found` (line 22) | **no runner names it** | has `check` lines | NEEDS-FIX (no launcher). In the hub F0 fails loudly |

### Group B: mob and wave suites

| Suite | /expedition line | First assertion needs a run? | Launched by | Class |
|---|---|---|---|---|
| mini_wave | 19 | Yes, `bossdevourer 0` (line 22) then `M0 brain alive` | regress4 (pre-built); **regress3 (NOT pre-built)** | PRE-BUILT launcher: PROBABLY-FINE / regress3: NEEDS-FIX. Hub: M0 fails, loud |
| purge | 25 | Yes: T1 expects 5 foreign mobs removed in 2.6 s, and removal only happens for `inPlace()` arenas (fact 5) | regress4 (pre-built); **regress3 (NOT pre-built)** | PRE-BUILT launcher: PROBABLY-FINE / regress3: NEEDS-FIX. **Hub is the dangerous case:** with no run nothing purges, so T1 fails, but T2 ("named zombie and named cow are spared") would PASS for the wrong reason, because nothing was removing anything. A half-green report |
| sweeper | 23 | Yes: T2 expects hostiles removed within 3.5 s (same purge system) | **no runner names it** | NEEDS-FIX (no launcher). Same half-green risk as purge: T3 (keepers survive) passes in the hub trivially |
| blood | 23 | Yes, `emberfall wavestop 0` (line 25), then summons mobs in whatever world it stands in | regress3 (NOT pre-built), regress4 (pre-built), tail4 (not pre-built) | PRE-BUILT launcher: PROBABLY-FINE / regress3, tail4: NEEDS-FIX |
| blood_cost | 20 | The first reading needs a live wave: it sleeps 8 s ("let the wave build up", line 22) then records 30 s of particles | **no runner names it** | NEEDS-FIX (no launcher). Total wait before recording is 3.5 + 8 = 11.5 s, still under 32 s. In the hub it **measures the hub's particles** and prints a percentage that looks like a result: a measurement-style suite with no verdict, so it cannot fail |
| pink_particle | 20 | Yes, `spawnelite pink_slime` after `wavestop 0` (lines 22, 24) | **no runner names it** | NEEDS-FIX (no launcher). Same "measures the hub" risk as blood_cost (20 s recording, no verdict) |
| sickle_inner | 12 | `wavestop 0` (line 14) then summons its own mobs with `NoAI`, so the mob part works anywhere | **no runner names it** | CANNOT-TELL. It summons its own targets and records for `SECS`, so it may measure the weapon honestly in the hub. Whether sickle damage code depends on being in a run I did not read |
| star_lethal | 14 | `wavestop 0` (line 15), then `spawnelite umbral_magus` (line 16) | **no runner names it** | CANNOT-TELL. Its only verdict is `PASS L0` if a death event arrives, else `INFO L0`; its proof of a run end is read from the server log, which I do not have |

### Group C: run-lifecycle and message suites

| Suite | /expedition line | First assertion needs a run? | Launched by | Class |
|---|---|---|---|---|
| kill_cmd | 12 | `/kill @s` after 2.5 s (line 13). It then prints inventory and counts `run_end` packets (`ends`), and has no assertion | **no runner names it** | NEEDS-FIX (no launcher). In the hub `/kill @s` is an ordinary death: `ends` stays 0 and the printed inventory is the hub death's. It prints, it cannot fail |
| music | 21 | Yes: M1 "a track starts on entry", read 2.5 s after the ask | regress4 (pre-built) | PROBABLY-FINE (pre-built launchers only) / bare: NEEDS-FIX. Bare: M1 fails (music starts at the join, fact 4), loud |
| music_dc | 11 | Yes: counts music packets | **no runner names it** | NEEDS-FIX (no launcher). Its two branches print `PASS` only when `music === 0` after a rejoin, so in the hub (no music ever) it **passes for the wrong reason** |
| music_exit | 13 and 22 | Yes: X0 "music on in the run" | **no runner names it** | NEEDS-FIX (no launcher). X0 fails in the hub; loud |
| table | 55 | B10 "picking mid-expedition is refused", 3 s after `/expedition` (line 55 to 57) | **no runner names it** | NEEDS-FIX (no launcher), for B10 only. The refusal is `RunManager.slotOf(player) != null` (`CharacterCommand.java:92`), true only after the join (fact 2). During the build the pick succeeds, so B10 fails, loud. B0 to B9 do not use a run |

### Group D: retired

| Suite | Note |
|---|---|
| boundary | RETIRED. B0 can never pass: no current path builds a rectangular in-place run. Still listed in `regress4.sh`, file absent from `tools/testbot/` |

## Counts (recounted by script from the tables above; 14 + 17 + 1 + 1 = 33)

| Situation | Suites | Count |
|---|---|---|
| Named by `regress4.sh` and/or `regress_guardian.sh` (both pre-build) | attack, attack_gate, beam, blood, calm, cinder_still, gate_noterrain, gate_weapon, ledge, mini_wave, music, purge, ring_phase3, ring_tower | 14 |
| Of those 14, ALSO named by a launcher that does NOT pre-build | blood (regress3, tail4), calm (regress_cinder), cinder_still (regress_cinder), mini_wave (regress3), purge (regress3) | 5 |
| Of those 14, pre-built path only | attack, attack_gate, beam, gate_noterrain, gate_weapon, ledge, music, ring_phase3, ring_tower | 9 |
| No shell runner names it at all | blood_cost, cinder, cinder_dodge, cinder_walk, kill_cmd, music_dc, music_exit, pink_particle, ring_guard, ring_high, sickle_inner, star_lethal, sweeper, table, tentacle, tentacle_attack, tentacle_floor | 17 |
| Only a non-pre-building launcher | cover (`cover_rep.sh`) | 1 |
| Retired | boundary | 1 |

Class totals: **NEEDS-FIX: 16** (15 of the 17 launcherless suites, plus `cover`), **CANNOT-TELL: 2** (`sickle_inner`, `star_lethal`, both launcherless), **retired: 1**, and **14 PROBABLY-FINE under the pre-building launchers**, of which 5 are NEEDS-FIX again if run through the other launcher listed above.
"NEEDS-FIX" means the file itself has no wait and nothing launches it with a pre-built map. It does not mean it has been seen failing. A person who runs one by hand after `prebuild.js` gets a real run.

## Silent passes (worse than loud failures)

These are the ones that can report green while standing in the hub:
1. **`music_dc`**: passes when `music === 0` (line 16), which is exactly what the hub produces.
2. **`purge` T2 and `sweeper` T3**: "the named mob and the tame wolf survive". In the hub nothing removes anything, so they pass. T1/T2 of `sweeper` fail, so the suite total is red, but a reader scanning the keep-checks sees green.
3. **`blood_cost`, `pink_particle`, `kill_cmd`, `attack_gate`, `beam`, `cinder*`, `ring_guard`, `ring_high`, `calm`**: no verdict of their own. They print numbers or nothing; a grader that reads the server log has to supply the verdict, and in the hub the log contains nothing to fail on (`NO CHECKS RAN` in `regress4.sh` is the guard, and only for suites run through it).

## What would fix it (suggestion only, the tests are Koda's)

The pattern already exists: `rift_inrun_test.js` line 19, `for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) {...} }`. A shared helper for the 17 launcherless suites would remove the dependence on how someone launches them. Alternatively the shell side: `regress3.sh`, `regress_cinder.sh`, `cover_rep.sh` and `tail4.sh` could call `prebuild.js` the way `regress4.sh` does.
Also: wait for `Dimension` is a necessary but maybe not sufficient signal. `joinPlayer` teleports the player in; `WaveDirector.start` runs on the next line of the same callback. I did not verify whether the wave director is registered by the time `Dimension` flips, so a boss suite may still want a short extra wait or a `wavestatus` poll after `Dimension` says `expedition`.

## Not established

- Whether your sandbox copies of the 33 files match the repo copies.
- Whether the pre-building launchers really protect each suite: I read `regress4.sh` and `regress_guardian.sh` and the `PREBUILD=1` line, I did not run them.
- That the 3.0 s waits are all the time there is. A few suites run other commands first (`/character select`, `/effect give`), which I counted as part of the wait.
- Anything about how the suites behave on a real client.
