# V13: does the pink party crash probe ever put the party where pool damage kills them?

Vesper, 2026-10-07. Newest Koda entry read: 2026-10-07 13:27 CT. Tested headless, look unverified. Report only: nothing under src/ changed.
Files read: tools/testbot/pink_party_crash_probe.js, pinkcrash_driver.sh, pink_party_crash_grade.sh, entity/PinkPools.java, entity/PinkSlime.java (pool callers), bot/BotPilot.java (target choice, read only).
All runs: current main jar (sha256 cdf5f3b85343cc07), fresh world each, 4 bots PA..PD in one run.

## Answer
No, not by design. Over 4 runs of the probe's own setup, pool damage happened in 2 (10 and 2 burns) and killed nobody on its own. And on current main the probe's pinned slimes can only make a pool when they die.

## Why (code, then measured)
1. Pools come from four places in PinkSlime.java: a trail (line 233, needs the slime on the ground AND moving), spit splats (286, 293), a slam splat (393), and the death burst (409). A slime with NoAI (probe line 23) moves, spits and slams never, so its only pool source is dying.
2. Measured: one pinned slime alone for 12 s made no spit, slam, leap or pool. After `/kill` it logged `PINK_TEST burst size=2` and 3 `pool burn` lines (v13_pinned_slime_alone.js).
3. In 4 probe-setup runs no pinned slime ever died (`PINK_TEST burst` = 0 in all four). Pool burns in the four runs were 0, 10, 2 and 0. In run 1 they came from SPIT: 21 `PINK_TEST spit` lines and all ten burns damage=2.0 (a splat). Run 2 had 22 spit lines but only 2 burns and run 3 had 15 spit lines and none, so a spit does not always leave a pool on the party; I did not trace run 2's two burns to a source. Those slimes were not the pinned ones. The wave director starts after the probe's `NoAI` merge and spawns its own pink slimes with AI (11 to 18 `grow` lines against the 4 the probe spawns).
4. So "pool damage in 1 of 5" is decided by whether a wave pink slime happens to spit before the party dies of something else. That fits what you saw.
5. Pool damage is small by construction. One hit per player per second from all pools together (PinkPools.java line 28, tickAll), the worst pool decides: trail 1.0 for at most 2 hits, splat 2.0 for at most 4, burst 2.0 for at most 6. One pool kind therefore costs a player at most 2, 8 or 12 HP over its whole life, and a 20 HP player cannot die to one of them standing still.
6. In the probe runs the party died within 21 to 70 s of the wave start with burns of 0 to 10 in total (each 1.0 or 2.0), i.e. at most 20 HP of burns against 80 HP of party. Something else killed them (wave mobs, the shrine challenge that started in every run); I did not isolate what.

## What would make pool damage the killer every run (measured, one run)
Remove everything else and make the slimes act: `/emberfall wavestop 0` right after the party joins, then spawn the four slimes UNPINNED (v13_slimes_only_no_wave.js). Result, one run: bots in the run at t=7 had HP 10, 18, 20, 20; all four were out of the run by t=27; 11 pool burns (3 x 1.0, 8 x 2.0 = 19.0 HP), 8 spit hits (4.0 each = 32.0), 9 slam lines. Pools reached the party in that run, but were a minority of the damage. A probe that must reach the CME path needs the LAST player to die to a pool while the earlier ones are still being iterated, so it also needs the survivors to be low and standing in a pool at once.

## What I did not establish
- One run of the design, not a count. 11 burns is an existence result, not a rate.
- Whether the CME can occur at all from a pool kill: no CME appeared in any of my 6 runs (4 probe-setup, 1 pinned slime alone, 1 slimes-only; 0 `ConcurrentModification` lines in the 5 logs I kept and the last server log).
- Who got burned: `pool burn` lines carry no player name (PinkPools.java line 184), so burns cannot be matched to a bot or a death from the log. Adding the player name to that TEST_MODE line would make this answerable (a src change, so not mine to make).
- `fallen` counts: run with waves stopped showed 3 fallen at read time, not 4 (the fourth was still dying when I stopped reading).
- Damage figures are the constants before armor, resistance or statMultiplier.

## Own slips
- Early on I read `run=0` as "dead". It is the run SLOT, so `run=0` means in the run. My scratch scripts had it backwards in one test; I redid that test with the right label and HP.
- A scratch script was left on a jar that disabled the purge (V11 mutant) after an interrupted call; I found it by hash before measuring and restored the baseline. No result here was taken on that jar.
- A cancelled call left a game server running; I stopped it (PID found by its first word being java) and reran.
