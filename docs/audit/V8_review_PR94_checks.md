# V8 review: the checks added by #94 (and N1 from #98)

Vesper, 2026-10-07. Newest Koda entry read: 2026-10-07 14:00 CT. Docs only. Tested headless, look unverified.

Method: for each check, change ONE statement of the real game code in a copy of `src/main/java` (the harness refuses unless the text matches exactly once), compile both checks against the copy, run, and read the FAIL lines. A mutant that leaves 0 FAIL means the checks cannot see that change. Clean code: BotMindCheck 28 PASS, BotMotionCheck 26 PASS, 0 FAIL (main at 69fc03e).

The fixes are in PR #100 (tools/testbot only). Nothing here touches game code.

## 1. Assertions that cannot fail

| Where (main) | What | Evidence |
|---|---|---|
| BotMindCheck.java:26 and :32 | `rushStrafe` and `cowLeash` are declared and summed, and no `check(` reads either. Dead. | grep: 2 lines each, none inside a check |
| (no check) | `strafeShare()` and `jumpEveryTicks()` feed the game (BotPilot.java:286, :306) and no check asserts their value or direction | mutant: strafeShare slope 0.70 -> 0 gave 0 FAIL; jump slope reversed gave 0 FAIL |
| BotMindCheck "allyLeash 4..18" | range check only, so the span can be anything inside it | mutant: span 14 -> 0 and 14 -> 40 both gave 0 FAIL |
| BotMindCheck "push never exceeds the cap" | an upper bound, so a push of any size below the cap passes | mutants: magnitude x0.3 and strength halved both gave 0 FAIL |
| BotMotionCheck:34 "gentler near the target" | an ordering, so any monotone ramp passes | mutant: ramp 90 -> 30 gave 0 FAIL |
| BotMotionCheck strafe checks | only statistics (about 60%, both ways, 8 distinct lengths) | mutants: hold range 22 -> 8, reversal 50% -> 10%, ease-in 3 -> 9 ticks each gave 0 FAIL |
| BotMindCheck "slot bearings cover the circle" | does not see the wobble term | mutant: wobble x5 gave 0 FAIL |
| bot_scout_hidden_test.js T2 | passes on any digit in any chat line (`\|\| /Count\|\d/`) | constructed replies: "[12:04] <Kira> hi" passes; silent, error text and "No entity was found" fail, so T2 is weak, not constant |
| bot_scout_hidden_test.js T4 | if `pos=` is not parsed, `m` is null and the filter measures from the world origin | live, regex broken, visible husk beside the human: bot at the origin -> T4 FAIL (origin happened to equal the bot); bot sent to 150,80,150 -> `PASS T4`, exit 0, with `0 spawn packets near the bot, 30 total`. The bot's own position at T1 still read -0.1,0.5, so I did not establish that it stood 150 blocks away; I established that the filter reads the wrong point |

## 2. Checks that are strong (so the list above is not the whole suite)
Gross breakage is caught, 1 to 3 checks each: slotPoint ignoring the stand-off, slotBearing ignoring index or party size, pickFoe always returning foe 0, easeSpeed snapping, turnRate always max, angleDelta without the 360 wrap, angleDelta wrap 360 -> 720, turnToward never snapping, turnToward turning double. The JITTER baseline (BotMotionCheck:32) and CLUMP baseline compare against an old rule rebuilt inside the check; they cannot fail from game code, which is what they are for, and they are not counted as holes.

## 3. N1 in bot_nothing_trailing_test.js (#98)
Established by running N1's own filter (copied from the file) on constructed inputs, and live on a fresh world:
- Goes RED for one id that stays within 3 blocks for 5 or more samples, for husk, zombie, armor_stand, marker, villager, bat, including when the id has left the client table.
- Too loose by threshold: a follower seen for 4 samples passes.
- Too loose by type: `experience_orb`, `item`, `arrow`, `spectral_arrow`, `interaction`, `item_display`, `block_display`, `text_display` pass at any duration. Excluding loot and arrows is deliberate, so I left it.
- Too loose by prefix: the type regex had no `$`, so `item_frame`, `items_display`, `interaction_marker` were excluded too. Fixed in #100.
- A follower re-created with a new id each time never accumulates samples.
- Live after the fix: N0, N1, N2 PASS, exit 0, 40 samples; the long-stayers were experience orbs.

## 4. Not established
- What entity 564 (the unidentified failing N1 run) was.
- A live N1 mutant. Planted invulnerable husks were gone by the end of the run, although they survived 40 s with no bot present. I did not find what removes them, so N1's red path is shown on its filter logic only.
- Why the harness wrapper wrote empty results twice. I ran the live tests by hand and read the PASS/FAIL lines.
- Still surviving after #100: fleeBelow slope 0.45 -> 0.40 and jump slope 90 -> 60. The new checks are orderings with margin.
- The longest strafe hold was 101 ticks in a 3000 tick sample, against a design range of 8 to 29. Not an assertion; I did not chase it.
