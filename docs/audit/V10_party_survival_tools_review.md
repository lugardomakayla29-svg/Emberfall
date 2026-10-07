# V10: review of bot_party_survival_probe.js and party_survival_grade.sh

Vesper, 2026-10-07. Newest Koda entry read: 2026-10-07 12:58 CT. Docs only. Tested headless, look unverified.

Question: can the grader report "N deaths" when the log has none, or report 0 when bots died? Name the line, prove with a constructed log. Also: can the 180 s window ever matter?

Reproduce everything below from the repo root: `bash docs/audit/party_survival_grade_cases/run_cases.sh`. It runs the real grader on each constructed log in `party_survival_grade_cases/`.

## 1. "N deaths" when nobody died: YES
Line: `tools/testbot/party_survival_grade.sh:6`, `grep -vE 'cause=(null|leave|abandon)'`.

The game can log exactly three causes (RunEndHandler.java:125 `null`, :130 `"escaped"`, :142 `"fallen"`). The grader treats everything that is not null/leave/abandon as a death, so `escaped` (leaving through the Final Swarm portal, a win) counts as a death. `leave` and `abandon` are never logged by the game, so those two exclusions do nothing.

| Constructed log | Truth | Grader prints |
|---|---|---|
| C_two_escaped.txt (SA, SB escaped) | 0 deaths | `deaths: 2` |
| D_one_fallen_one_escaped.txt | 1 death | `deaths: 2` |
| E_same_bot_twice.txt (SA fallen twice) | 1 bot | `deaths: 2` (it counts lines, not bots) |

Fix, not made here (it is yours to ask for): count `cause=fallen` only.

## 2. 0 when bots died: YES, in two ways, both silent
| Constructed log | Truth | Grader prints |
|---|---|---|
| H_testmode_off_bots_died.txt (no RUNEND_TEST lines, SA slain) | deaths happened | `deaths: 0` |
| does_not_exist.txt (wrong log path) | unknown | `deaths: 0` and `exceptions: ` (empty), exit code 0 (measured; it is also 0 with four deaths, so the exit code says nothing) |

- RUNEND_TEST is written only when `-Demberfall.testMode=true` (RunEndHandler.java:45, :189). A server started without it logs nothing, and the grader prints the same `0` as "nobody died".
- A wrong path prints `grep: ... No such file` to stderr but the result lines still say 0.
- From the source only, NOT reproduced live: `finishRun` returns before logging when the player is null, the server is null, or `RunManager.slotOf(player)` is null (RunEndHandler.java:150-155). A bot that dies while not registered in a run would leave no line.

## 3. Other lines
- `party_survival_grade.sh:7` counts any line with "Exception" or "ERROR" anywhere, including chat. G_chat_with_ERROR.txt (a chat line with the word ERROR) prints `exceptions: 1`.
- The grader does not check that four bots were in the run, so 4 lines for 4 bots and 2 lines for 2 bots look alike except by eye.
- Bot names outside SA..SD (F_other_names.txt) are correctly ignored.

## 4. The 180 s window
- The grade does not depend on it. The grader reads the server log, not the probe's samples, and the probe never stops early when bots die (no break in the sampling loop, lines 21-26), so with every bot dead inside 50 s it still samples to 180 s.
- It matters only for a bot that lives past the window: a death after the window ends is still in the log if the server keeps running, but the run is stopped at `WINDOW_END` (line 28, `process.exit`), after which the driver copies the log.
- Time budget, computed from the constants in the code and not observed: the wait for the run to start can take up to 70 x 2.7 s = 189 s, so the probe can need about 400 s in the worst case, while the driver gives the suite wrapper 330 s (party_survival_driver.sh:11). In the normal case (run starts quickly) it is about 215 s.

## Not established
- Whether any bot in a survival run ever reaches `escaped`. If none can in 180 s, finding 1 cannot change a real result.
- Your four runs: their logs are not in my sandbox, so I did not check "every bot died inside 50 s".
- The slot-null case, live.
- `guarded_suite_stage.sh` and `guarded_suite_old.sh` (called by the driver) are not in the repo; I did not review them.
