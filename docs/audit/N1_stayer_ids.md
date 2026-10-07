# N1: what removes a planted husk before the run ends

Vesper, 2026-10-07. Newest Koda entry read: 2026-10-07 12:58 CT. Tested headless, look unverified. Docs plus one reference script.

## Answer
`RunMobPurge` (src/main/java/com/solme/emberfall/world/RunMobPurge.java). While a run is active, it discards every `Mob` in the play area (box plus 8 blocks) that `shouldPurge` (line 63) does not spare: once on load (`ENTITY_LOAD`, line 38) and again in a sweep every 20 ticks (line 46). A vanilla husk from `/summon` is a `Mob` with no name, so it is gone before the first poll.

Spared by `shouldPurge`: any entity type in the `emberfall` namespace, players, tamed pets, mobs carrying the scout tag (`emberfall_bot_scout`), and any mob with a custom name or a leash.

## Evidence (four live runs, three hypotheses: run 2 redid run 1 on the right jar; fresh world each, bot TrailBot in an active run, entities planted 2 to 5 blocks away, polled over 15 s)
| run | Jar | plain husk | named husk | armor_stand | scout-tagged husk | emberfall:horde_zombie |
|---|---|---|---|---|---|---|
| 1 | older build (see below) | gone at t~0 | alive | alive | alive | not planted |
| 2 | current main, sha256 cdf5f3b85343cc07 | gone at t~0 | alive | alive | alive | not planted |
| 3 | same jar, `shouldPurge` returns false on its first line, sha256 4ee677a0180c050b | **alive 15 s** | alive | alive | alive | not planted |
| 4 | current main, cdf5f3b85343cc07 | n/a | alive | alive | alive | **alive 15 s** |

Row 3 is the control: one statement changed, one outcome flipped, so the purge is the remover and not a coincidence of timing. Rows 1, 2 and 4 are the same planting test (`tools/testbot/ref/v11_purge_plant_test.js`; row 4 replaces the first summon with `emberfall:horde_zombie`).

## What this means for N1
- A stayer test that plants vanilla mobs during a run cannot work: they are purged. That is why my planted husks vanished, and it was never a failure of N1's filter.
- The purge cannot hide a real trailer made of an emberfall type, because those types are spared (row 4). N1 watches the same entities a real trailer would be.
- Not covered: a vanilla mob that is named or leashed is also spared, so N1 would see it. That is not a trailer the game creates.
- N1 can still only be shown red by a planted emberfall mob or a named/tagged one. I did not run N1 itself against such a plant; its red path is still shown only on its filter logic (see V8 review).

## Not established
- Entity 564, the first-run failure, is still unidentified. This finding makes an unspared vanilla mob an unlikely candidate while a run is active; it does not say what it was.
- Row 1 used the jar that was on disk (3852-byte RunMobPurge class, same size as the no-purge build, without the scout-tag line). I rebuilt current main for rows 2 to 4. The scout-tag row on row 1 therefore does not test line 74; rows 2 and 4 do.
- Planted entities were 2 to 5 blocks from a standing bot, not a walking one; the polling was 15 s, not N1's 40-sample walk.
- Whether the sweep, the load hook, or both removed the husk on row 2: it was gone before my first poll (about 1 s), which fits the load hook, but I did not separate the two.

## Own slips on the way
- I ran row 1 on a jar older than main's source and only noticed when its class size matched the no-purge build. I redid it on a rebuilt main.
- My leftover-server check matched its own command line (it contained the jar name) and reported a "server still up" three times. The rule `redeploy.sh` uses (first word is `java`) shows zero game servers.
