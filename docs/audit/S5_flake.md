# S5 flake: why pink_spit_test S5 gives 0, 1 or 2 spits

Vesper, 2026-10-07. Newest Koda entry read: 2026-10-07 13:44 CT. Tested headless, look unverified. Report only: the test and src/ are unchanged.
Test: tools/testbot/pink_spit_test.js, S5 (line 54). Jar: current main, sha256 cdf5f3b85343cc07, fresh world each run, the real unmodified test for runs 1 and 2, a copy with two extra console.log lines (no logic change) for run 3.

## Result of three live runs
| run | S5 | window content | what the slime did after its last pre-wall spit |
|---|---|---|---|
| 1 | FAIL, 0 spits 0 landed | empty | leap telegraph dist=14.0, slam at 2.0 blocks, leap dist=1.6, burst (18:58:13) |
| 2 | FAIL, 0 spits 0 landed | empty | leap telegraph dist=13.9, slam at 2.1 blocks, leap dist=1.6, burst (19:00:44) |
| 3 | PASS, 0 spits 0 hits 1 landed | `pool add kind=splat ; spit landed` | no further spit; burst at 19:03:02 |

S1 to S4 passed in run 1 (the only one where I read them all).

## Named cause (two parts, both from log lines)
1. **The wall window is mostly empty of spits because the slime has stopped spitting.** The test keeps the slime at range only inside its 30 s pull-back loop (pink_spit_test.js lines 29 to 34). That loop ends before S5 starts (line 49 onward) and nothing replaces it. After it ends the slime leaps at the player (`leap telegraph dist=14.0` / `13.9`) and slams next to them (`slam ... at=2.0` / `2.1`), after which it is closer than 5 blocks and `tickSpit` returns early (`dist < 5.0`, PinkSlime.java line 256). It also dies on its own: `LIFESPAN_TICKS = 1200` (PinkSlime.java line 69, checked at 179), and in all three runs the burst came 47 s after the first `grow` line (18:57:26 to 18:58:13, 18:59:57 to 19:00:44, 19:02:15 to 19:03:02).
2. **When S5 passes, the glass did not stop anything.** Run 3's window has `spit landed` but NO `spit tick=` line, so the ball was fired before the window. Its log lines: `19:02:44 spit telegraph tick=1607`, `19:02:45 spit tick=1621`, `19:02:46 spit landed`; the test printed the end of its `/fill` at 19:02:46. The test takes `w0` right after the fill (line 51). In run 3 `w0` was 69 PINK_TEST lines; the `spit tick=1621` line is number 69 (the last one before the window) and `spit landed` is number 71, inside it. So a ball launched before the wall and landing after `w0` is counted as "landed on the wall". (The sandbox clock is UTC for both node and the server log, checked.) Whether it landed on glass or on the ground I cannot tell from `spit landed`.
So S5 is decided by whether a pre-wall ball happens to land on the window side of `w0`, not by the wall. The reading of 0, 1 or 2 spits fits that.

## Not established
- Three runs, 1 pass and 2 fails here; Koda saw 2 of 3 and 1 of 3. That the pass rate is about this is not shown.
- Whether `hasLineOfSight` is false behind the glass. I did not test it: the slime was never behind the wall at range long enough for a telegraph in my runs, so the question "does glass stop a NEW spit" is untested. It is the thing S5 is supposed to test.
- A "2 spits" reading (Koda's) did not occur in my runs; I do not explain it. Two `spit tick=` lines in a 16 s window would need the slime still at range at the start of the window, which is possible when the wall goes up earlier in its life than in my runs.
- The ~47 s from first `grow` to burst equals 60 s of age minus growth time only if growth is not counted in ageTicks; I read line 176 that way but did not measure it.
- The fill is `minecraft:glass` in the code and the comment above it says "stone wall" (line 48); I did not check whether that mismatch matters.

## Not changed
I did not change the test to make it pass. What would make S5 test the wall: keep the slime at range during the window (the pull-back loop needs to run through it), take `w0` before the fill and count only balls whose `spit tick=` lies after the wall, and report spits fired as well as landed.

## Own slips
- My first run crashed on a path mismatch (the test reads run/server_run.log, my wrapper writes server_run.log). I added a symlink in the ignored run/ folder and raised the wrapper's node timeout from 150 to 200 s; the test file is untouched.
- I first suspected line of sight (glass) as the cause of the empty window; the log showed the slime was next to the player instead, so I dropped it as the explanation, but did not test it.
