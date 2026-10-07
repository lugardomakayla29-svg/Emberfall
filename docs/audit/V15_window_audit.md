# V15: window audit of tools/testbot/*_test.js

Vesper, 2026-10-07. Newest Koda entry read: 2026-10-07 15:49 CT. Tested headless, look unverified. Report plus reference scripts only: no test and nothing under src/ or bot/ was edited.
Jar for every live run: sha256 cdf5f3b85343cc07 (main as of #109; main has moved since, none of the checks below touch code that #112, #117 or #118 changed, but I did not rerun on newer main). Fresh world per run, real test or a one-line mutant copy (tools/testbot/ref/v15_mutant_*.js).

## Scope, honestly
220 *_test.js files. I did NOT read all of them. I scanned all 220 with four regex scans (tools/testbot/ref/v15_scan_*.py) and read every file a scan flagged, plus the 8 files whose check is a single negative. A test written in a shape my regexes do not match is not covered. Treat the lists below as a floor.

## Pattern (a): window opened AFTER a pull-back or hold loop ended
One instance: pink_spit_test.js S5 (loop ends L34, wall window L51). That is the one you fixed in #118. Three independent scans (loop end then a mark within 6 lines; any mark after a pull-back `tp` inside `while(Date.now()-x<N)`; counter-reset after an action) found no second file. Flagged and read, NOT instances: charger_test.js (each scenario resets `base` immediately before its own spawn, L37/L49/L63 region), pink_moves_test.js (`t0` L26 is taken before the spawn; the loop is a sampler, not a pull-back).

## Pattern (b): window start taken AFTER the action it claims to test
No pure instance found. spitter_test.js phase B takes `b0` (L62) after the wall fill, the tp and a 1.5 s sleep, which is the right order (pre-wall balls excluded), and S4a/S4b/S4c have positive floors (`bTele >= 3`, `bLanded >= 3`). unlock_chest_test.js L22 marks right before `open()`; the scan flagged it only because a setup command precedes the mark.

## Related finding, same family: "the thing counted already exists before the window"
**F1. spitter_test.js S5, L81 (`burned >= 1`), window opens at `burn0` L78.** The player is pinned at one spot for the whole test (hold, L27) and phase A already put globs on that spot (S3 needs hits >= 3, L51). Phase B logs `0 new pools, 5 refreshed`, i.e. the puddle was already there. So S5 can pass from a leftover puddle with no phase C glob landing.
PROOF: scratch copy that kills the spitter just before phase C (ref/v15_mutant_spitter_s5.js, one added line). Result: `MUTANT: spitter killed before phase C`, then `PASS S5 ... 4 burn ticks in 4 s`, `ALL PASS`, exit 0. The real test on the same jar: ALL PASS, S5 also `4 burn ticks in 4 s`. One run each.
WHAT WOULD MAKE IT SOUND: count burns only if a `spit landed` or `pool add` occurs inside the same phase-C window, or clear the pools before `burn0`.

## Other checks that cannot fail, outside the two patterns (I think you want these too)
**F2. unlock_chest_test.js K3, L31.** `!lines.slice(n).join(' ').includes('Relic unlocked')` has no positive half, and `open()` (L8 to L13) returns false when no chest is found while the call at L30 ignores the return value.
PROOF: scratch copy with the 26th `open()` removed (ref/v15_mutant_chest_k3.js). Result: `MUTANT: 26th open() skipped`, `PASS K3`, `ALL PASS`, exit 0. K1 and K2 are NOT affected: they read `open_25_chests=24` and `=25`, which a skipped open cannot produce.
**F3. tome_fix_test.js T2, L31 (reply read at L30).** A negative regex over the reply text of `/emberfall weaponpending`; no positive control that the command can report a pending offer.
PROOF: scratch copy where the reply is replaced by empty text (ref/v15_mutant_tome_t2.js). Result: `PASS T2 ... ::` (empty detail), `ALL PASS`, exit 0. T1 and T1b are unaffected. I did not read what the server prints in the real passing case, so I cannot say what a positive half should match.
**F4. death_screen_test.js D0, L22.** `!/nothing|not in|null|hidden/i.test(inRun) || true` is true for every input. By reading only; no mutant is needed for a constant. D1 (L29, requires the run_end payload) is what actually proves the run started.

## Negative checks I read and found sound (they have a control in the same window)
tiki_voice_test.js, tiki_voice_vet_test.js, tiki_voice_elite_test.js V2 (hold through the window, V1 shriek and V4 alive in the same window). witch_test.js W5 (W2 shows 4 stars first, kill then 2.5 s). summon_friendly_test.js S2 (S1 `maxSummons > 0`, same counters). death_screen_test.js D6 (listener at file top, D1 needs the payload). pink_moves_test.js M1 to M6 (floors on every count).
NOT CHECKED: that the `veteran` branch of the three Tiki V2 lines runs in each file (I did not read which tier arg each passes). sickle_test.js K1 (`lost === 0` over 6 s, L48) has its control in K3 in a different window and on a different foe, so it is controlled elsewhere, not in its own window; I did not mutant it.

## Not established
- Any rate: every mutant is one run.
- That nothing else exists. The scans are shape-based; roughly 200 files were never read.
- F1 to F3 prove the check cannot see the thing it names; they do not show the product is broken.
- I did not rerun anything on newer main.

## Own slips
- Last turn I restored the V11 NO-PURGE jar (4ee677a0, identical to /tmp/v11_nopurge.jar) and called it "the original build". It was not. I found it by hashing /tmp/*.jar before any measurement and redeployed cdf5f3b8 (/tmp/v11_base.jar). No result here used the wrong jar.
- A tool call was cut off at its limit before my cleanup check ran; I redid the check next turn.
- My first scan flagged charger_test, pink_moves_test and unlock_chest_test; all three were false positives after reading. Two of my own line offsets were one off until I re-grepped them.
