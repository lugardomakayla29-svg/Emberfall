# Vesper, DROP 2 brief (written by Koda, 2026-10-09 CT)

Read this whole file before you touch anything. It exists so there is no guessing. If a line here conflicts with an older INBOX entry, **this file wins**.

## 0. Do this first, in this order
1. `git fetch`, then read `docs/comms/INBOX.md` on `origin/main`. Quote the **newest entry's timestamp and sender** in your first line. (Newest at time of writing: 2026-10-09 13:20 CT, FROM Koda.)
2. Read `docs/PROMISES.md`, `docs/UI_GLYPHS.md`, `docs/ui_glyphs.json`.
3. Check `ps` for a Java server on port 25565. One server at a time. None should be needed for this drop (all pure checks and client text), so do not start one.
4. Base every branch on `origin/main`. **One PR per task.** Check `p['base']['ref'] == 'main'` before you call anything merged, and confirm the change is on `origin/main` afterwards.

## 1. What the owner actually asked for (this corrects a misunderstanding)
The owner's "square boxes" are **NOT a missing-glyph bug**. They are the small icon glyphs next to each stat in the **stats panel** (`RunHud`, the five in `STAT_ICONS`). He liked them. He wants **the same glyph style, consistent, on every menu in the expedition**, and maybe elsewhere later. Stop hunting for a missing glyph. Your P6 sweep result (0 unverified glyphs) was correct; the job now is to **add** verified glyphs.

## 2. TASK A (the big one): glyphs on every expedition menu. Files: `src/client/` only.
Menus (client screens): `ShopScreen`, `TomeChoiceScreen`, `WeaponChoiceScreen`, `MerchantScreen`, `ShrineScreen`, `RunEndScreen`, `ChestRevealScreen`. Also `LoadoutHud` and `SwarmHud` if they have label rows.
**Copy the stats panel pattern exactly** (`RunHud.java` lines ~100-135): one glyph per row, in the row's own colour, drawn with `relic/HudLayout.markerColumn(...)` (`src/main/java/com/solme/emberfall/relic/HudLayout.java:117`) and `relic/HudLayout.markerOffset(...)` (`:129`) so every label starts at the same x. Measure the glyph with `font.width(...)`, never assume a width.
**Rules, each one is a past mistake:**
- Use **only** glyphs from the **bitmap** table of `docs/UI_GLYPHS.md` (64 of them). Never the `unifont_unverified` section. `GlyphGuardCheck` must stay green; run it.
- A glyph must **fit the row**: rows 0..6 or shorter, width in the table. `U+2611` does not fit a 9 px row. Check the Rows column.
- Pick glyphs that **mean something** for the row (a heart for health, a skull for kills, a snowflake only for cold things). Put your choice table (screen, row, glyph, code point, why) in the PR body and in `docs/UI_GLYPHS.md`.
- **Do not touch** `CharacterSelectScreen.java` or `SelectLayout.java` (Koda's, just rebuilt, it uses cards). Do not touch `rift/`, chest models, `bot/`, `WaveDirector`, boss fight classes, `EmberfallCommands`, `RunManager`, the renderer.
- Strings sent from the **server** (chat lines, titles) are out of scope this drop. Client screens only.
- You cannot see the screen. Say **UNSEEN** for how it looks. What you CAN prove: every glyph is in the verified set, every glyph fits its row, no label column overlaps, and the build passes.
- Add a pure check (`MenuGlyphCheck`) that lists each menu's rows and glyphs and fails if a glyph is missing from the verified set, is too tall for its row, or two rows in one menu use the same glyph for different meanings. **Prove it can fail:** inject a bad glyph in a scratch copy and show the check goes red, then revert. (Past mistake: my own injection used a glyph that WAS in the set, so it passed. Pick one that is not, e.g. U+2728.)

## 3. TASK B: Tiki Slime pure rules
Read `docs/design/TIKI_REPLACEMENT.md` (owner decision 2026-10-07 18:15 CT: a **slime** version of Tiki Magma, **no ice, no frost, no burrowing**, pole of 1/3/4 player-head masks facing the player, 3 tiers, spawn weight 0.15, split capped at 2).
Write `entity/TikiSlimeRules` (pure, no Minecraft types) and `tools/testbot/relic_math/TikiSlimeCheck.java`: the tier table (same wave counts as the old Tiki Magma so wave tables do not change: **read them from the existing wave code and quote file and line**. The old Tiki Magma is spawned in `wave/WaveDirector.java` at lines 443 and 582 (`TikiMagma.spawnElite`), 677 (`TikiMagma.spawn`), with the spawn weight `TIKI_SPAWN_WEIGHT = 0.15` at line 608. The tier table you must mirror is in `docs/design/TIKI_REPLACEMENT.md` under "Tiers" (Fodder 1 mask, Elite 3, Corrupted 4)), spawn weight, split cap, and "every move has a wind-up of at least 0.5 s and shares a constant with its damage". A mutant for each rule, and report which check caught which.

## 4. TASK C: `TideClock` review (it is NOT yours to write)
Koda wrote `boss/TideClock.java` and `TideCheck.java` (15 checks) plus `boss/BossTuning.java` and `BossTuningCheck.java` (10 checks), on branch `koda/drop1` (they reach `main` with #215). **Do not rewrite them.** Review them as a hostile reader: list every wrong implementation that would still pass `TideCheck` or `BossTuningCheck` (a false pass), and add the check that closes each. Then break the code on purpose (Ebb and Flood swapped, armour 0.35 in Ebb, off by one at tick 280, curse swallowed by the boost) and show which check goes red. Do this after #215 is merged; if it is not merged when you get here, write BLOCKED and do Tasks A and B.

## 5. Things that are NOT yours
`GooGrid` and `GooCheck` are already on `main` (your #141, 49 checks). Do not redo them. Broodtide's body, Grab, Devour, phases and renderer are Koda's. The Devourer and Broodtide stat boost is Koda's (`BossTuning`).

## 6. How to report (copy this shape into `docs/comms/INBOX.md`, FROM Vesper)
- First line: `READ. Newest entry above mine on main: <timestamp> (FROM <sender>).`
- One section per task: what you changed, the exact command you ran, the real output line (`ALL PASS (N checks)`), and which mutants went red.
- A **PROMISES** line and a **NOT ESTABLISHED** line. Label every claim VERIFIED / PROPOSAL / UNSEEN. Never write "done" for how a menu looks.
- If you are unsure what a line means, write the question in the INBOX and stop that task. **Do not guess.** Two failed attempts on one problem: write BLOCKED and stop.
- Do not poll. Write the entry and stop.
