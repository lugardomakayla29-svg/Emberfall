# PROMISES: everything the owner asked for, and its REAL status

Written 2026-10-09 after the owner's real-client playtest found that the "final" jar was missing most of the big items.
**Rule from now on:** nothing is "done" until the owner has SEEN it on a real client. Headless tests can only prove "built".
Statuses: NOT STARTED, BUILT (headless only), SEEN (owner confirmed on a real client). Nothing below is SEEN yet.
Every delivery note MUST list this table with the current statuses. A promised item that is NOT STARTED is named as such, never skipped.

| # | Promise (owner's words, short) | Status | Owner | Drop |
|---|---|---|---|---|
| P1 | Rift is visible after opening: animated, moving particles, jagged pink-lilac tear with warm rim | BUILT, UNSEEN (RiftIdle draws every tick after the opening; 15 pure checks, 7 mutants red; real look unknown) | Koda | 1 |
| P2 | Rift opening animation clearly visible (heavier, larger) | NOT STARTED (current is faint) | Koda | 1 |
| P3 | Chests use the Excalibur pack: iron=paid, copper=free, gold=gold; world, held, inventory; real lid animation | BUILT, UNSEEN (6 models rebuilt from the pack's own UVs and display transforms; open lid swings up; every face samples painted texture; not a game render) | Koda | 1 |
| P4 | Chest roll plays a sound while rolling for a relic | BUILT, UNSEEN (merged #213: note-block tick per reel step, chime when the tier lands, harp when the item lands; timing proven by a 23-check pure test; the sounds and pitches are a PROPOSAL nobody has heard) | Vesper | 1 |
| P5 | Expedition dimension: no day/night, permanent noon | BUILT, UNSEEN (emberfall:noon timeline; headless probes could not see the sky, fallback is fixed_time) | Koda | 1 |
| P6 | Every GUI uses the provided glyphs, no square missing-glyph box (docs/UI_GLYPHS.md) | CAUSE UNEXPLAINED (merged #212: the whole mod uses only 5 escaped glyphs, all in RunHud, plus the section sign; every one is in the verified set; GlyphGuardCheck blocks new unverified ones; the box the owner saw is NOT explained by a missing glyph) | Vesper | 1 |
| P7 | Rift Character Select looks nicer (not flashing) | BUILT, UNSEEN (framed panel, 8 cards with badge, name and weapon, detail pane, draining time bar, keyboard focus; layout fits down to 320x240; the cause of the 'flashing' you saw is NOT known, the old screen had no animation) |
| P8 | Broodtide replaces the Ember Guardian as boss 1 (slime kraken, Tide, Devour, Brood-Kin) | NOT STARTED (plan only) | Koda | 2 |
| P9 | Sickly slime + sickly mob skins from Emberfall-SicklySlimeSicklySkins.zip used on Broodtide, Brood-Kin, slimes | NOT STARTED | Koda | 2 |
| P10 | Tiki Magma removed; Tiki Slimes (slime version of Tiki, no ice) tied to Broodtide | NOT STARTED | Koda | 2 |
| P11 | Devourer overhaul: more epic and harder than Broodtide (docs/devourer_expansion_design.md, 5 levers) | NOT STARTED (owner approved 2026-10-09) | Koda | 3 |
| P12 | Rift entry, countdown, Character Select, Hearth removed, 4 new songs | BUILT (headless only) | Koda | done |
