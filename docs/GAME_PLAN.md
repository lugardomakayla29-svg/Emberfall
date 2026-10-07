# Emberfall: the plan from now to shipping

Written 2026-10-07 17:55 CT by Koda at the owner's request: "plan the game from start to finish, Vesper's doings and yours, in the end both are reviewed, ship when ALL content is done, I playtest."
Every row was checked against `main` this session (a class either exists or it does not). Labels: DONE (on main, proven), TODO, UNVERIFIED (headless-proven, never seen on a real client).

## The rule of this plan
1. Nothing ships until EVERY content row below is DONE. The owner playtests the finished jar once.
2. Every build step has a check that can FAIL, shown red on a mutant before it is trusted.
3. Each side reviews the other's work before the final regression (see Phase 6).
4. Look and sound cannot be judged without a graphical client: they are listed as UNVERIFIED for the owner, never claimed as good.

## Where it stands (verified on main)
DONE: 8 weapons with levels and ultimates, party scaling (max 10), Expedition Gate, Final Swarm (ramps to cap in 5 min), relic/chest/merchant systems, chest textures, EmberTester bots (hidden scout, smooth movement, personalities), Tiki facing, FrostbloomRules (pure, 16/16 mutation-proven), V15 and V16 audits merged.
NOT BUILT (grep finds no class): RiftShape, Broodtide body, GooGrid, chest slot-machine reveal screen, any terrain-throw code, the Frostbloom entities.

## Phase 1: foundations (in parallel, no file overlap)
| # | Task | Owner | Done when |
|---|---|---|---|
| 1.1 | Rift steps 1-3: RiftShape (pure), RiftRules extend, RiftFx timeline | Vesper | pure checks green + each red on a mutant; particle budget and zero-entity proven live |
| 1.2 | Tiki V4 test fix (test sequencing, NOT a mod bug: run setup tears down the arena and discards the Tiki) | Koda | tiki_voice tests V4 passes and goes red when the Tiki is removed |
| 1.3 | Broodtide prototype A (hide then reveal a mob, AI and target intact) and B (slime renderer outer layer) | Koda | devour_proto_test PASS/FAIL, 0 leaks |
| 1.4 | GooGrid pure class + checks (Broodtide row 5) | Vesper | cap and damage rate asserted, mutants red |

## Phase 2: the big builds (Koda; Vesper joins where marked)
| # | Task | Owner | Done when |
|---|---|---|---|
| 2.1 | Broodtide boss (rows 4, 6-9, 12): body, Tide clock, Grab, Devour + Brood-Kin, phases, island, flag-swap | Koda | the 10 section-8 tests pass, flag off = old Guardian unchanged |
| 2.2 | Frostbloom Tiki replacement: Step 0 burrow prototype, then Frostbud, Frostbloom, Rimeheart, then delete the 5 Tiki files | Koda | burrow facts proven live, spawn tables and wave tests re-run |
| 2.3 | Devourer overhaul (design below), measured packets first | Koda | owner approves the design, then tests per mechanic |
| 2.4 | Rift wiring: shard item + recipe, creative tab, /emberfall rift, natural event | Koda | opens on uneven ground, ground byte-identical after |
| 2.5 | Character Select screen + packet, shop tab; retarget the gate to the Rift | Koda | gate_test, party tests, boundary_test green |
| 2.6 | Chest slot-machine reveal (client screen: tier first, then item) | Vesper (isolated screen), Koda wires the packet | screen opens, tier-then-item order proven by packet |
| 2.7 | Remove the Hearth and hub code (last, only after 2.4 and 2.5 are proven) | Koda | full regression, 0 exceptions |

## Phase 3: Vesper's small items (any time, no hotspots)
Sound audit: list the silent things, then fill gaps. Patch notes (`docs/WHATS_NEW.md`) and credits. Chest respawn tuning check. Anything the owner reports.

## Phase 4: Devourer design (proposal, owner decides)
Throw the terrain, never destroy it. The Devourer burrows, COPIES a real patch of arena terrain (2 to 12 blocks wide) into a display, tears it out with an animation, and hurls it. Terrain is never edited. Entity cost is the hard limit: a chunk is a SHELL (visible blocks only) or one scaled display, never one entity per block, and the cap is measured first. Extra moves: Wall of Earth (a slab that rises then sinks), Tremor Lanes (cracks run at you), Tail Breach (a body-wide sweep), Pit Snare (a visual pull, no terrain change). HP rises from 260 toward about 700 (Broodtide's planned boss is 600, and boss 2 should be the harder one). Every telegraph is at least 0.9 s and shares its constant with the damage. Open owner questions: real terrain or stylized rock, and rubble where it lands.

## Phase 5: integration
One full regression of every suite, 0 exceptions, red tests listed by name. Rebuild from `main`, confirm the jar md5 equals build/libs and 0 debug markers. WHATS_NEW complete.

## Phase 6: the two-way review (the owner's requirement)
- Vesper audits every Koda PR against the plan's tests and lists each false pass he can construct.
- Koda audits every Vesper suite by breaking the code on purpose and showing each goes red.
- A written list of everything UNVERIFIED on a real client goes to the owner with the jar.

## Phase 7: ship
Only when every row above is DONE and Phase 6 is clean. Package the zip, upload, hand it to the owner to playtest. Fixes from his playtest are a new round, not part of this plan.

## Risks I can already see
1. The look and sound of the Rift, Broodtide and the thrown terrain are all UNVERIFIED headless. A graphical client would remove most of this risk.
2. Burrowing (Frostbloom) and hiding a mob (Broodtide) are new mechanics: both start with a prototype, and either may need a redesign.
3. Entity count: the thrown chunks and Brood-Kin must stay inside the cap. Measure before choosing sizes.
