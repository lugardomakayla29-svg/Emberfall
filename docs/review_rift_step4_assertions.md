# Review of Rift step 4 (#150): which assertion can pass while its rule is broken

Reviewer: Vesper. Date: 2026-10-08. Read: RiftManager, RiftSpot, RiftRules, RiftShardItem, rift_manager_test.js, rift_inrun_test.js, the changed lines of rift_show_test.js.
Labels: VERIFIED = I ran or read it just now. PROPOSAL = my suggestion. UNVERIFIED = not run.

## What is solid (VERIFIED)
I mutated the real rules in copies (tools/testbot/ref/v18_rift_rule_mutants.py, repo files untouched): 18 mutants, 18 red, 0 green, 0 not applied. They cover the refusal order and all three refusal texts, spacing (47 vs 48, > vs >=), the 10 minute idle timer, idle with a player waiting, the natural distance and cooldown, the 70% open share (50% and 90%), the empty sample, 3D versus flat distance, "first instead of nearest", and the +X/-X swap. So the numbers and the order of the rules are pinned by the pure checks. The live tests only need to prove the WIRING (that the shard and the command reach those rules).

## Assertions that can pass while their rule is broken
1. **C3 (spacing).** Asserts `rifts == 1 && shards == 2`. It never reads WHY. useShard swallows every error and a 2.5 s timeout (`catch (e) {}`), so a click that never reached the server gives the same two numbers. PROPOSAL: also require the refusal text `too close to another Rift` (the shard sends it with sendSystemMessage(..., true); mineflayer 4.39.0 routes that packet through its systemChat handler, which also emits `message`, so the existing `lines` capture should see it. Read from the handler, NOT seen arriving).
2. **D1 (open air).** Asserts `rifts == 0` and the shard kept. Spacing and the dimension guard are checked BEFORE open air, so a leftover Rift would make D1 pass without hasOpenAir running. D2 is a good control for an always-refuse bug, but nothing shows D1's refusal was for air. PROPOSAL: require `no open air`.
3. **I2 (inside a run).** Asserts only `/refused/`. Any refusal matches, including `no open air`. The text for the expedition guard is `the expedition map is a run arena`. PROPOSAL: require that exact text.
4. **E1 (creative).** Already labelled by Koda as not testing the instabuild guard. Agreed, nothing to add.

## Checked and NOT a gap
- G1 (idle Rift still open after 20 s) is weak alone, but the 10 minute number is pinned: IDLE_TICKS == 12000 and the 30 s mutant went red.
- D1 against a lost stack: D0 asserts 2 shards before, and `d1 === d0` would fail on 0, so a vanished stack is caught.
- The fill cap: D uses 23x15x23 = 7,935 blocks and asserts the server accepted it (D-setup).

## A rule corner with no test (UNVERIFIED in play)
RiftManager.hasOpenAir skips cells in unloaded chunks and RiftSpot.hasOpenAir then uses the smaller `total`. With ONE loaded cell, ONE open cell passes (70% of 1, rounded up, is 1). Any sample of 2 or 3 loaded cells needs all of them. A real Rift sits 16 to 32 blocks from a player, so its chunks are normally loaded; I think this is a corner, not a likely bug. PROPOSAL: require a minimum loaded count (for example 15 of 25) before the share is judged.

## Not testable today (agreed with Koda)
insideActiveRun: every run is in the expedition dimension, so the dimension guard always refuses first.

## One diagnosability note
The refusal-text check in RiftRulesCheck puts four exact-string comparisons in one boolean. It is correct (M1 to M4 all went red), but a failure will not say which of the four broke.

Tested headless, look unverified.
