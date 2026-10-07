# Obsolete suites

Kept for history, not run by any regress script.

- `boundary_test.js` (retired 2026-10-07): tested the old square 57-wide in-place arena box (edge planes, a particle curtain on x=x1, a push over the east edge,
  a corner escape on both axes). `/expedition` now only calls `RunManager.startOnMap`, the static map, whose guard is a CIRCLE, so B0 reads `none` and B2, B3, B4, B6
  describe geometry that no longer exists. The behaviour it protected ("you cannot leave the arena") is covered by `circle_test.js` (soft push, hard put-back,
  height clamp, no-push control; 11/11 on the 03:03 CT jar) and `map_boundary_test.js` (r=90 and 92.5 left alone, soft band never passes 96, put back from r=99 and r=110,
  solid ground at the edge; 6/6). Mob containment (old B8 to B10: a stray mob outside is pulled back, a mob inside is left alone) was NOT covered by either until `map_mob_stray_test.js`, which also found and drove the fix for a real gap: `CircleBoundary` never touched mobs (the box guard skips map arenas). A held foe placed at x=107 stayed at 107.6 on the old jar and ends at r=91.0 now.
Not re-tested: the old B10 (a PLAYER who left is not pulled back again).
