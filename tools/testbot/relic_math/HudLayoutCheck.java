import com.solme.emberfall.relic.HudLayout;
import com.solme.emberfall.relic.HudLayout.Box;

/**
 * Pure check for the top-left HUD layout: the stats panel, the weapon box and the relic list never share a pixel, the weapon box never reaches the
 * timer plate (it is hidden instead), and the relic list is capped so 64 relics cannot cover the screen.
 *
 * Font widths come from the real client, so they are INPUTS here: every check runs over a range of widths wider than anything the game draws, not one
 * guessed number. The window sizes in the work order (854x480, 1920x1080) are NOT what the HUD sees at the default GUI scale, so each is also checked at
 * the scaled size vanilla gives it (427x240, 480x270) and at every fixed scale a player can choose.
 */
public class HudLayoutCheck {
    static int pass = 0, fail = 0;

    static void check(String name, boolean ok) {
        if (ok) {
            pass++;
            System.out.println("PASS " + name);
        } else {
            fail++;
            System.out.println("FAIL " + name);
        }
    }

    // The client's real constants: line height 9, so a stats row is 13 and a relic row is 11. Padding 5, dot 5.
    static final int LINE = 9, STATS_ROW = LINE + 4, RELIC_ROW = LINE + 2, PAD = 5, DOT = 5, CAP = 12;

    /** The stats panel as RunHud builds it: x=6,y=6, five rows. Its width depends on the text, so it is an input. */
    static Box stats(int labelW, int valueW) {
        int w = PAD + DOT + 4 + labelW + 8 + valueW + PAD;
        int h = STATS_ROW * 5 + PAD * 2 - 4 + 2;
        return new Box(HudLayout.MARGIN, HudLayout.MARGIN, w, h);
    }

    public static void main(String[] a) {
        // The sizes: [windowW, windowH, guiScaleOption(0 = auto)]. Scaled = window / scale.
        int[][] windows = {{854, 480}, {1920, 1080}, {320, 240}};
        int[] options = {0, 1, 2, 3, 4};

        // S: the scale rule matches the one read from the 1.21.11 jar (calculateScale), so the sizes below are what a player sees.
        check("S1 auto scale at 854x480 is 2", HudLayout.autoScale(854, 480) == 2);
        check("S2 auto scale at 1920x1080 is 4", HudLayout.autoScale(1920, 1080) == 4);
        check("S3 auto scale never below 1", HudLayout.autoScale(100, 100) == 1);
        check("S4 scaled size at 854x480 auto is 427x240", 854 / HudLayout.autoScale(854, 480) == 427 && 480 / HudLayout.autoScale(854, 480) == 240);
        check("S5 scaled size at 1920x1080 auto is 480x270", 1920 / HudLayout.autoScale(1920, 1080) == 480 && 1080 / HudLayout.autoScale(1920, 1080) == 270);

        // O: no two boxes overlap, over every screen size, every font width and 0..64 relics, 0..4 weapons.
        int cases = 0, overlaps = 0, offscreen = 0, hidden = 0, shown = 0, timerHits = 0;
        for (int[] win : windows) {
            for (int opt : options) {
                int scale = opt == 0 ? HudLayout.autoScale(win[0], win[1]) : Math.min(opt, HudLayout.autoScale(win[0], win[1]));
                int sw = win[0] / scale, sh = win[1] / scale;
                for (int labelW = 20; labelW <= 60; labelW += 10) {
                    for (int valueW = 10; valueW <= 60; valueW += 10) {
                        Box st = stats(labelW, valueW);
                        for (int timerW = 40; timerW <= 80; timerW += 20) {
                            Box timer = HudLayout.timerBox(sw, timerW, 21);
                            for (int rows = 1; rows <= 5; rows++) {
                                for (int widest = 40; widest <= 120; widest += 20) {
                                    Box wb = HudLayout.weaponBox(st, widest, rows, RELIC_ROW, PAD);
                                    boolean fits = HudLayout.weaponBoxFits(st, wb, timer) && wb.right() <= sw - HudLayout.MARGIN;
                                    cases++;
                                    if (fits) {
                                        shown++;
                                        if (wb.overlaps(st) || wb.overlaps(timer)) overlaps++;
                                        if (wb.right() > sw || wb.bottom() > sh) offscreen++;
                                    } else {
                                        hidden++;
                                    }
                                }
                            }
                            for (int relics = 0; relics <= 64; relics++) {
                                int h = HudLayout.relicBoxHeight(relics, CAP, RELIC_ROW, PAD);
                                if (relics == 0) continue;
                                Box rb = HudLayout.relicBox(st, 100, h);
                                cases++;
                                if (rb.overlaps(st)) overlaps++;
                                // The relic list sits under the stats panel and may be wider than it, but the weapon box starts at the stats panel's
                                // top and ends at most 4 rows + title down. Any drawn weapon box must not reach the relic list either.
                                Box wb = HudLayout.weaponBox(st, 80, HudLayout.MAX_WEAPON_ROWS, RELIC_ROW, PAD);
                                if (HudLayout.weaponBoxFits(st, wb, timer) && wb.overlaps(rb)) overlaps++;
                                if (rb.bottom() > sh && relics <= CAP && sh >= 240) offscreen++;
                            }
                        }
                    }
                }
            }
        }
        System.out.println("  (" + cases + " layouts, " + shown + " weapon boxes drawn, " + hidden + " hidden for lack of room)");
        check("O1 no two drawn boxes ever share a pixel", overlaps == 0);
        check("O2 no drawn weapon box leaves the screen", offscreen == 0);
        check("O3 the weapon box is drawn in some layouts and hidden in others (the test exercises both)", shown > 0 && hidden > 0);

        // Q: the smallest GUI the game allows, 320 x 240 at scale 1 (vanilla never goes under it). The sweep repeats the O loop's ranges but counts this one
        // size on its own, so a broken fit rule cannot hide inside the totals of the bigger windows. Hiding the weapon box here is correct; drawing it over
        // the stats panel, over the timer plate or off screen is not. Koda measured 55 shown, 2195 hidden at this size, so both outcomes must occur.
        int qCases = 0, qShown = 0, qHidden = 0, qBad = 0;
        int qSw = 320, qSh = 240;
        check("Q0 a 320x240 window is scale 1, a 320x240 GUI", HudLayout.autoScale(320, 240) == 1);
        for (int labelW = 20; labelW <= 60; labelW += 10) {
            for (int valueW = 10; valueW <= 60; valueW += 10) {
                Box st = stats(labelW, valueW);
                for (int timerW = 40; timerW <= 80; timerW += 20) {
                    Box timer = HudLayout.timerBox(qSw, timerW, 21);
                    for (int rows = 1; rows <= 5; rows++) {
                        for (int widest = 40; widest <= 120; widest += 20) {
                            Box wb = HudLayout.weaponBox(st, widest, rows, RELIC_ROW, PAD);
                            qCases++;
                            // The same "drawn" rule RunHud.drawWeaponBox applies: fits beside the stats panel and timer, and inside the right margin.
                            boolean drawn = HudLayout.weaponBoxFits(st, wb, timer) && wb.right() <= qSw - HudLayout.MARGIN;
                            if (drawn) {
                                qShown++;
                                if (wb.overlaps(st) || wb.overlaps(timer) || wb.right() > qSw || wb.bottom() > qSh) qBad++;
                            } else {
                                qHidden++;
                            }
                        }
                    }
                }
            }
        }
        System.out.println("  (320x240: " + qCases + " layouts, " + qShown + " weapon boxes drawn, " + qHidden + " hidden)");
        check("Q1 at 320x240 a drawn weapon box never overlaps the stats panel or the timer and never leaves the screen (" + qBad + " bad)", qBad == 0);
        check("Q2 at 320x240 the weapon box is hidden in most layouts (the panel is too wide for the room)", qHidden > qShown);
        check("Q3 at 320x240 both outcomes occur, so Q1 is not vacuous (" + qShown + " drawn, " + qHidden + " hidden)", qShown > 0 && qHidden > 0);

        // The two named window sizes as written, at the scale a player gets by default, with typical widths.
        for (int[] win : windows) {
            int scale = HudLayout.autoScale(win[0], win[1]);
            int sw = win[0] / scale;
            Box st = stats(36, 30);
            Box timer = HudLayout.timerBox(sw, 56, 21);
            Box wb = HudLayout.weaponBox(st, 70, 5, RELIC_ROW, PAD);
            check("N" + win[0] + " weapon box at " + win[0] + "x" + win[1] + " (gui " + sw + " wide) clears stats and timer, or is hidden",
                    !HudLayout.weaponBoxFits(st, wb, timer) || (!wb.overlaps(st) && !wb.overlaps(timer)));
        }

        // R: the relic cap.
        check("R1 64 relics show exactly 12 rows", HudLayout.relicRowsShown(64, CAP) == 12);
        check("R2 3 relics show 3 rows", HudLayout.relicRowsShown(3, CAP) == 3);
        check("R3 0 relics show 0 rows", HudLayout.relicRowsShown(0, CAP) == 0);
        check("R4 exactly 12 relics fold nothing (no +N more row)", HudLayout.relicBoxHeight(12, CAP, RELIC_ROW, PAD) == PAD * 2 + RELIC_ROW * 13 - 2);
        check("R5 13 relics add the +N more row", HudLayout.relicBoxHeight(13, CAP, RELIC_ROW, PAD) == HudLayout.relicBoxHeight(12, CAP, RELIC_ROW, PAD) + RELIC_ROW);
        check("R6 64 relics are no taller than 13 (the cap holds)", HudLayout.relicBoxHeight(64, CAP, RELIC_ROW, PAD) == HudLayout.relicBoxHeight(13, CAP, RELIC_ROW, PAD));
        boolean monotone = true;
        for (int n = 1; n <= 64; n++) {
            if (HudLayout.relicBoxHeight(n, CAP, RELIC_ROW, PAD) < HudLayout.relicBoxHeight(n - 1 < 1 ? 1 : n - 1, CAP, RELIC_ROW, PAD)) monotone = false;
        }
        check("R7 the relic box never shrinks as relics are added", monotone);
        // The relic list must end above the bottom-left loadout panel at every GUI size a player can reach, with 64 relics (the worst case).
        Box st = stats(36, 30);
        int top = st.bottom() + HudLayout.GAP;
        int[][] guis = {{427, 240}, {480, 270}, {640, 360}, {960, 540}, {854, 480}, {1920, 1080}, {320, 240}};
        boolean allClear = true, atLeastOne = true;
        StringBuilder note = new StringBuilder();
        for (int[] gui : guis) {
            int limit = HudLayout.loadoutTop(gui[0], gui[1]);
            int rowsShown = HudLayout.relicRowsFit(64, CAP, top, limit, RELIC_ROW, PAD);
            int bottom = top + HudLayout.relicBoxHeight(64, rowsShown, RELIC_ROW, PAD);
            note.append(" ").append(gui[0]).append("x").append(gui[1]).append(":").append(rowsShown).append("rows/end").append(bottom).append("<=").append(limit);
            if (rowsShown > 1 && bottom > limit) allClear = false;
            if (rowsShown < 1) atLeastOne = false;
        }
        System.out.println("  relic rows with 64 relics:" + note);
        check("R8 with 64 relics the list ends above the loadout panel at every GUI size", allClear);
        check("R9 at least one relic row is always shown", atLeastOne);
        // The bug this replaces: the old fixed cap of 12 rows ran past the loadout panel on the default scale at 854x480 (a 427x240 GUI).
        int oldBottom = top + HudLayout.relicBoxHeight(64, CAP, RELIC_ROW, PAD);
        check("R10 the OLD fixed cap of 12 did overlap the loadout panel at 427x240 (end " + oldBottom + " > " + HudLayout.loadoutTop(427, 240) + ")",
                oldBottom > HudLayout.loadoutTop(427, 240));
        check("R11 on a tall GUI (1080 window at scale 1) the full 12 rows still show", HudLayout.relicRowsFit(64, CAP, top, HudLayout.loadoutTop(1920, 1080), RELIC_ROW, PAD) == 12);
        check("R12 few relics are never cut: 3 relics show 3 rows even on 427x240", HudLayout.relicRowsFit(3, CAP, top, HudLayout.loadoutTop(427, 240), RELIC_ROW, PAD) == 3);
        check("R13 relicBoxHeight with the shown rows as the cap matches the folded height (RunHud relies on this)",
                HudLayout.relicBoxHeight(64, 5, RELIC_ROW, PAD) == PAD * 2 + RELIC_ROW * 6 - 2 + RELIC_ROW);
        // F: relicRowsFit at its edges.
        int h1 = HudLayout.relicBoxHeight(64, 1, RELIC_ROW, PAD);
        check("F1 a limit too small for even one row still shows one row (the documented floor)", HudLayout.relicRowsFit(64, CAP, top, top + 1, RELIC_ROW, PAD) == 1);
        check("F2 a limit of exactly the one-row height shows one row", HudLayout.relicRowsFit(64, CAP, top, top + h1, RELIC_ROW, PAD) == 1);
        int h5 = HudLayout.relicBoxHeight(64, 5, RELIC_ROW, PAD);
        check("F3 exact fit: a limit equal to the 5-row bottom keeps 5 rows (touching the limit is allowed)", HudLayout.relicRowsFit(64, CAP, top, top + h5, RELIC_ROW, PAD) == 5);
        check("F4 one pixel short of the 5-row bottom drops to 4 rows", HudLayout.relicRowsFit(64, CAP, top, top + h5 - 1, RELIC_ROW, PAD) == 4);
        check("F5 no relics owned gives 0 rows", HudLayout.relicRowsFit(0, CAP, top, 1000, RELIC_ROW, PAD) == 0);
        check("F6 the cap is honoured even with unlimited room", HudLayout.relicRowsFit(64, 7, top, 100000, RELIC_ROW, PAD) == 7);
        check("F7 fewer owned than the cap shows them all", HudLayout.relicRowsFit(4, CAP, top, 100000, RELIC_ROW, PAD) == 4);

        // T: the timer plate is centred.
        check("T1 timer box centred on an even width screen", HudLayout.timerBox(400, 60, 20).x() == 170 && HudLayout.timerBox(400, 60, 20).right() == 230);
        check("T2 timer box sits at the top margin", HudLayout.timerBox(400, 60, 20).y() == HudLayout.MARGIN);
        check("T3 timer box on an odd width screen is within 1 px of centre", Math.abs((HudLayout.timerBox(427, 56, 20).x() * 2 + 56) - 427) <= 1);

        // A: the auto scale rule at its thresholds (both dimensions matter).
        check("A1 640x480 is scale 2 (320x240 exactly)", HudLayout.autoScale(640, 480) == 2);
        check("A2 639x480 is scale 1 (one short in width)", HudLayout.autoScale(639, 480) == 1);
        check("A3 640x479 is scale 1 (one short in height)", HudLayout.autoScale(640, 479) == 1);
        check("A4 630x480 is scale 1 (width between 300 and 320 per step)", HudLayout.autoScale(630, 480) == 1);
        check("A5 960x720 is scale 3", HudLayout.autoScale(960, 720) == 3);
        check("A6 a very wide but short window is limited by height", HudLayout.autoScale(4000, 300) == 1);

        // M: the loadout panel width threshold (cell 16 is the smallest that is drawn).
        check("M1 the loadout panel is drawn at the narrowest width that gives a 16 cell", HudLayout.loadoutTop(2 * (91 + 8 + 3 * 2 + 4 * 16 + 0 + 1) - 1 + 2, 240) < 240);
        check("M2 one pixel narrower and it is hidden (returns the screen height)", HudLayout.loadoutTop(2 * (91 + 8 + 3 * 2 + 4 * 16) - 2, 240) == 240);
        check("M3 a cell of 12 (between the thresholds 8 and 16) is hidden, not drawn", HudLayout.loadoutTop(2 * (91 + 8 + 3 * 2 + 4 * 12), 240) == 240);

        // L: loadoutTop mirrors LoadoutHud (cells 30 wide, 4 px margin, hidden when under 16).
        check("L1 loadout top at 427x240 is 179 (cell 27)", HudLayout.loadoutTop(427, 240) == 179);
        check("L2 loadout top at 480x270 is 203 (cell 30)", HudLayout.loadoutTop(480, 270) == 203);
        check("L3 loadout top at 1920x1080 scale 1 is 1080-4-63", HudLayout.loadoutTop(1920, 1080) == 1013);
        check("L4 a GUI too narrow for the loadout panel returns the full height", HudLayout.loadoutTop(200, 240) == 240);

        // W: the weapon box.
        check("W1 weapons are capped at 4 (plus the title row)", HudLayout.weaponBox(st, 50, 99, RELIC_ROW, PAD).h() == PAD * 2 + 5 * RELIC_ROW - 2);
        check("W1b the fourth weapon is not cut off: 4 weapons are taller than 3", HudLayout.weaponBox(st, 50, 4, RELIC_ROW, PAD).h() > HudLayout.weaponBox(st, 50, 3, RELIC_ROW, PAD).h());
        check("W2 0 weapons give just the title row", HudLayout.weaponBox(st, 50, 0, RELIC_ROW, PAD).h() == PAD * 2 + RELIC_ROW - 2);
        check("W3 a negative weapon count is treated as 0", HudLayout.weaponBox(st, 50, -3, RELIC_ROW, PAD).h() == PAD * 2 + RELIC_ROW - 2);
        Box wbox = HudLayout.weaponBox(st, 50, 3, RELIC_ROW, PAD);
        check("W4 the weapon box starts right of the stats panel with the gap", wbox.x() == st.right() + HudLayout.GAP);
        check("W5 the weapon box shares the stats panel's top edge", wbox.y() == st.y());
        check("W6 the weapon box width is the widest row plus padding both sides", wbox.w() == 50 + PAD * 2);

        // G: the real worst case, as RunHud draws it: four weapons plus the "Weapons" title row, and the relic list under the stats panel.
        Box worstW = HudLayout.weaponBox(st, 80, HudLayout.MAX_WEAPON_ROWS, RELIC_ROW, PAD);
        Box relicTop = HudLayout.relicBox(st, 100, 30);
        check("G1 the tallest weapon box (4 weapons + title) ends above the relic list top (" + worstW.bottom() + " < " + relicTop.y() + ")", worstW.bottom() < relicTop.y());
        check("G2 the weapon box and the relic list do not overlap even when the relic list is as wide as the screen",
                !worstW.overlaps(HudLayout.relicBox(st, 10000, 30)));
        check("G3 the weapon box and the stats panel have the same top edge", worstW.y() == st.y());
        // A relic list with N relics and nothing folded has N rows plus a title; a weapon box asked for N + 1 rows is built the same way.
        boolean pair = true;
        for (int n = 1; n <= 4; n++) {
            if (HudLayout.weaponBox(st, 50, n, RELIC_ROW, PAD).h() != HudLayout.relicBoxHeight(n, CAP, RELIC_ROW, PAD)) pair = false;
        }
        check("G4 a weapon box for N weapons is exactly as tall as a relic list of N relics (they read as a pair)", pair);

        // B: Box.overlaps itself.
        Box b1 = new Box(0, 0, 10, 10);
        check("B1 touching edges do not overlap", !b1.overlaps(new Box(10, 0, 5, 5)) && !b1.overlaps(new Box(0, 10, 5, 5)));
        check("B2 one shared pixel overlaps", b1.overlaps(new Box(9, 9, 5, 5)));
        check("B3 overlap is symmetric", new Box(9, 9, 5, 5).overlaps(b1));
        check("B4 a box inside another overlaps", b1.overlaps(new Box(2, 2, 3, 3)));
        check("B5 far apart boxes do not overlap", !b1.overlaps(new Box(50, 50, 5, 5)));

        // I: the stats panel's icon column. Expected values are written by hand from the design: column = max(5, widest glyph advance),
        // offset = floor((column - glyph) / 2), never negative. The glyph advances are the measured ones: star 8, diamond 6, envelope 8, hollow 6, skull 8.
        check("I1 the real five glyphs (8, 6, 8, 6, 8) give an 8 wide column", HudLayout.markerColumn(5, 8, 6, 8, 6, 8) == 8);
        check("I2 no glyphs at all keeps the old 5 px square", HudLayout.markerColumn(5) == 5);
        check("I3 glyphs narrower than the square never shrink the column", HudLayout.markerColumn(5, 3, 4, 2) == 5);
        check("I4 a glyph exactly as wide as the square leaves it at 5", HudLayout.markerColumn(5, 5) == 5);
        check("I5 one pixel wider than the square grows it to 6", HudLayout.markerColumn(5, 6) == 6);
        check("I6 a negative width counts as nothing", HudLayout.markerColumn(5, -4, 6) == 6);
        check("I7 a negative minimum counts as 0", HudLayout.markerColumn(-3, 4) == 4 && HudLayout.markerColumn(-3) == 0);
        check("I6b a negative width counts as nothing even when its size is larger than every real one (-9 beside 6)", HudLayout.markerColumn(5, -9, 6) == 6 && HudLayout.markerColumn(5, 6, -9) == 6);
        check("I8 the column is the widest of many, in any order", HudLayout.markerColumn(5, 6, 8, 6) == 8 && HudLayout.markerColumn(5, 8, 6, 6) == 8);
        check("I9 offset: a 6 wide glyph in an 8 column starts 1 in", HudLayout.markerOffset(8, 6) == 1);
        check("I10 offset: an 8 wide glyph in an 8 column starts at 0", HudLayout.markerOffset(8, 8) == 0);
        check("I11 offset: an odd leftover leans left (5 in 8 gives 1, not 2)", HudLayout.markerOffset(8, 5) == 1);
        check("I11b offset depends on the column: 7 in 11 starts 2 in, 3 in 5 starts 1 in, 4 in 4 starts 0", HudLayout.markerOffset(11, 7) == 2 && HudLayout.markerOffset(5, 3) == 1 && HudLayout.markerOffset(4, 4) == 0);
        check("I12 offset: a glyph wider than the column starts at 0, never negative", HudLayout.markerOffset(5, 9) == 0);
        check("I13 offset: a negative glyph width is treated as 0 (centres nothing)", HudLayout.markerOffset(8, -2) == 4);
        check("I14 every real glyph fits inside the 8 column: offset + width <= 8",
                HudLayout.markerOffset(8, 8) + 8 <= 8 && HudLayout.markerOffset(8, 6) + 6 <= 8);
        // The panel width the client builds: padding + column + 4 + labelW + 8 + valueW + padding. With the real glyphs the column is 8, so the
        // panel is 3 px wider than the old 5 px square gave, and the weapon box moves right by exactly that much and still clears the timer or hides.
        Box oldSt = stats(36, 30);
        int iconCol = HudLayout.markerColumn(5, 8, 6, 8, 6, 8);
        int newW = PAD + iconCol + 4 + 36 + 8 + 30 + PAD;
        check("I15 the panel with the 8 column is exactly 3 px wider than with the old 5 px square", newW - oldSt.w() == 3);
        boolean iconClear = true;
        for (int[] win : new int[][] {{427, 240}, {480, 270}, {512, 282}, {640, 360}, {854, 480}, {1920, 1080}}) {
            Box stI = new Box(HudLayout.MARGIN, HudLayout.MARGIN, newW, oldSt.h());
            Box tm = HudLayout.timerBox(win[0], 56, 21);
            Box wbI = HudLayout.weaponBox(stI, 80, HudLayout.MAX_WEAPON_ROWS, RELIC_ROW, PAD);
            Box rbI = HudLayout.relicBox(stI, 100, 30);
            if (HudLayout.weaponBoxFits(stI, wbI, tm) && (wbI.overlaps(stI) || wbI.overlaps(tm) || wbI.overlaps(rbI))) iconClear = false;
            if (stI.overlaps(rbI)) iconClear = false;
        }
        check("I16 with the wider icon column the stats panel, weapon box, timer and relic list still share no pixel at six window sizes", iconClear);

        System.out.println();
        if (fail == 0) {
            System.out.println("ALL PASS (" + pass + " checks)");
        } else {
            System.out.println("FAILED " + fail + " of " + (pass + fail));
            System.exit(1);
        }
    }
}
