# Review of the chest reveal wiring (#155): what can break unseen

Reviewer: Vesper. Date: 2026-10-08. Read: ChestManager (reveal block, tryOpen), ChestRevealSessions, RunManager.leavePlayer, RunEndHandler (all exits), EmberfallMod:81, EmberfallNetworking:53, chest_reveal_test.js, reveal_grade.py, build.yml.
Labels: VERIFIED = I ran it or read the source just now. READ = from source, not run. UNVERIFIED = not run.

## 1. The relic pool is 24, not 21 (VERIFIED, and it breaks two checks in #155)
`RelicPool.all()` run directly: **24 relics, 6 per tier** (Common 6, Uncommon 6, Rare 6, Legendary 6), 3 gated: `ember_key`, `iron_boots` (Common), `anvil_of_dawn` (Rare). The "21 (4/6/5/6)" in the 08:02 CT entry is exactly the number of `add(...)` lines: it leaves out the `gated(...)` lines. Three more sources say 24: PoolCheck (ran it: PASS "24 relics"), relic_test.js:33 (`RELIC pool 24:`), and the RelicPool header.
- **R6b** (chest_reveal_test.js) and **G2c** (reveal_grade.py) build their id-to-name table with `add\("id", "name"`. That finds 21 of 24. For a gated relic the table has no entry, so both FAIL (a false failure, not a false pass). A fresh player cannot own a gated relic, so a normal run never hits it and the gap stays hidden.
- Fix (proposal, not done): build the table from `add|gated`, or read it from the class. A pure source of truth now exists: ChestRevealPools (#162), which reads RelicPool.all().
- **Docs grep** ("24 relics", "6 per tier", "21"): nothing to fix. Every hit in code and docs says 24 (RelicPool.java:11, RELICS_V1.md:1 and :28, VESPER_HANDBOOK.md:48, STATUS.md:5, PoolCheck.java:9, relic_test.js:33). The only wrong statement is INBOX.md:570 and :572 (append-only), corrected by a new entry.

## 2. Does every exit from a run forget the reveal? (READ, agrees with the 08:02 entry)
A reveal can only be opened by a player with a slot, an active arena and a run record (`ChestManager.tryOpen`, lines 197-203, returns early otherwise). Every way out reaches `RunManager.leavePlayer`, which calls `forgetReveal` (RunManager:414):
- death: ALLOW_DEATH cancels it, `endRunInsteadOfDying` -> `finishRun(..., "fallen")` -> `leavePlayer` (RunEndHandler:168)
- disconnect: DISCONNECT -> `server.execute(handlePlayerLeftRun)` -> `finishRun(..., null)` (hops to the server thread first)
- `/expedition leave` and the Final Swarm escape: `handlePlayerLeftRun` and the escape path -> `finishRun`
- `/emberfall leave` (op): EmberfallCommands:861 calls `leavePlayer` directly
- server stop: RunManager:311 calls `leavePlayer` per player
- `clear()` is correctly NOT called on exit: the store is one global map, and `forget(uuid)` per player is the right unit.
**One window (hypothesis, no evidence it happens):** `finishRun` runs reward and HUD calls between its early return (line 152) and `leavePlayer` (line 168). An exception there would skip `forgetReveal`. The 600 tick sweep still drops the session, so it is bounded, not leaked.

## 3. What the live test CANNOT see (VERIFIED by grep of the two files)
`chest_reveal_test.js` connects ONE bot and never mentions expiry, sweep, forget, clear, tickReveals or a disconnect. `reveal_grade.py` has no check for them either. So these wiring lines can be deleted with every live assertion still green:
1. `EmberfallMod:81` (the registration of `tickReveals`): without it nothing ever sweeps.
2. `RunManager:414` (`forgetReveal`).
3. The `% 20` gate in `tickReveals`.
4. The player key: `REVEALS.open(player.getUUID(), ...)`. With one bot, a wrong key cannot show.
**CI cannot see them either (VERIFIED):** build.yml:31 compiles each pure check with `-sourcepath` and no Minecraft jar, and none of the 40 pure checks mentions ChestManager, RunManager, EmberfallMod or RunEndHandler. Only a live run can catch a broken wiring line.
The pure store IS covered for the three cases the 08:02 entry lists as not covered by the 5 live mutants: expiry (checks at 1599 and 1600), two players (P and Q cannot close each other's), and forget (only that player's). I re-ran v17_reveal_sessions_mutants.py: 35 run, 33 red, 2 green, both the equivalent pair proven in #147. So the gap is the WIRING, not the store.

## 4. Assertions that can pass while the rule is broken
- **R1** (a refused open sends no reveal) compares `seen.length === n0`. If the bot's packet listener were dead it would pass; R2 (needs exactly +1) is its control, so a dead listener fails R2. Fine as a pair.
- **R6** is true for any non-empty item name. R6b replaced it; R6 is redundant.
- **G1 + G2** read the SERVER log line `REVEAL_TEST sent`, which the code writes BEFORE the payload is built (the wrong-item mutant that first survived). R6b now reads the wire. G2/G2b/G2c still read the log, so they prove what the server decided, not what it sent. They need R6b to be correct, and R6b is the one that skips gated relics (section 1).
- **G5** (`real == [True, False]`) depends on the closes arriving in order. One TCP connection keeps order, so it holds; I did not run it.

## 5. Proposed live additions (NOT done: I have no test server)
Two bots (A and B): A opens, B forges A's id, A's real close still honoured (the player key). A opens and disconnects, then a new login of A: no reveal is open. A opens and waits 30 s: a close is refused and the log shows the sweep. Each needs a live server.

Tested headless, look unverified.
