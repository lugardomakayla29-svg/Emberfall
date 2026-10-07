# V6: assertions that cannot fail (PRs #81, #85, #66)

Reviewer pass by Vesper, 2026-10-07. Docs only, no code changed. Method: read every `check(` in the tests of the three PRs, then
**ran the two graders on synthetic logs** (each edited to break exactly one fact) to see which checks go red. Newest inbox entry
read before writing: 2026-10-07 08:40 CT (FROM Koda). Koda already named three for V2/V3 (a, b, c); they are not repeated here.

## A. Can NEVER fail (the condition is a constant or a copy of itself)
| # | File : check | Why it cannot fail |
|---|---|---|
| 1 | `tools/testbot/cues_test.js:67` `S0 the test reached the end` | The condition is the literal `true`. It prints PASS whenever the script reaches that line, so it proves only that the script did not crash earlier. |
| 2 | `relic_math/AfterBossOneCheck.java` "the log tag is AFTERBOSS1" | `LOG_TAG.equals("AFTERBOSS1")` compares the constant to the literal that defines it. It fails only if someone edits the constant. A change detector, not a behavior check. |
| 3 | same file, "the factor is 0.7" | `INTERVAL_FACTOR == 0.7`, same reason. The behavior is proven separately by the 58 to 41 tick check, so this one adds nothing. |

## B. Can fail, but ONLY when a different check has already failed (redundant, no new information)
| # | File : check | Evidence |
|---|---|---|
| 4 | `cues_grade.py` `W2b the control is not hollow` | Its condition (`first is not None and len(first) == 1`) restates `W1`. On a synthetic log with no first cue, W1 and W2b both went red; with a correct first cue both pass. It never fails alone. |

## C. Can fail, but a narrow gap lets a wrong run pass (weak, not empty)
| # | File : check | Gap |
|---|---|---|
| 5 | `map_mob_stray_test.js` `S0 METHOD` | Accepts x from 100 to 108. The play radius is not read in the test, so if the radius were raised to about 106, S0 would still pass while the foe was no longer outside. |
| 6 | `map_mob_stray_test.js` `S1 CONTROL` | The expression repeats `in1` (`in1 && in0 && in1`). Harmless, but it reads as two guards and is one. |
| 7 | `map_mob_stray_test.js` ending | `process.exit(0)` runs even when `fails > 0`. Only the printed `FAILED n` shows a failure, never the exit code. `regress3.sh` greps for `FAILED`, so the suite is covered; anyone running the file alone and reading the exit code is not. |
| 8 | `afterboss1_test.js` `A1b` and `A6` | Negative checks (`no warning yet`, `no repeat`). They are real only while the `WARN` regex matches the shipped text. The shipped text has colour codes (`§4§lWARNING: §cThe horde...`); the test relies on the bot stripping them. `A5` (exactly 1 match) guards that, so A1b and A6 are sound only as long as A5 passes. |

## D. Checked and CAN fail (evidence, so they need no change)
- `afterboss1_grade.py` G3, G4, G6, G7: each went red on a synthetic log edited to break only that fact (warned=0, heard=0, after=before, after=9).
- `cues_grade.py` W1, W2, X2: W2 and X2 went red when a second `swarm_begins` was injected (control broken); W1 went red when the first was removed.
- `map_mob_stray_test.js` S2 returns `null` when the foe cannot be read, and `out1 &&` then fails it, so a dead or despawned foe cannot pass S2.

## NOT established
- I did not re-run any live suite for V6 (docs only). The G, W and X results are from synthetic logs, not from a server.
- I did not mutate the Java math checks (AfterBossOneCheck); section D covers the Python graders only. Koda's inbox entry says the V2 mutations are credible; I did not redo them.
- The `§` stripping in item 8 is how mineflayer prints chat, which I did not re-measure here. A5 passing in the earlier live run is the only evidence.
- I did not review `cues_test.js` T/F/H checks beyond reading `cues_grade.py`'s windows. Nobody has heard the V4 sounds in a real client.

Tested headless, look unverified.
