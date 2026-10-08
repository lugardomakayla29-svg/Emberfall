package com.solme.emberfall.relic;

/**
 * Where the top-left run HUD boxes go, as plain integer rectangles, so a check can prove they never overlap without a Minecraft client.
 * The real HUD ({@code RunHud}) calls these same methods, so the arithmetic that is checked is the arithmetic that draws. Pure: no Minecraft types;
 * text widths come in as numbers because only the client knows the font.
 *
 * Three boxes share the top of the screen: the stats panel (top left), the weapon box (right of the stats panel) and the relic list (under the stats
 * panel). The timer plate is centred at the top, so the weapon box must also stay clear of it.
 */
public final class HudLayout {
    private HudLayout() {}

    /** Distance from the screen edge, and the gap between neighbouring boxes. */
    public static final int MARGIN = 6;
    public static final int GAP = 4;
    /** At most this many weapons are ever listed (the loadout has four cells), so the box can never grow past four rows. */
    public static final int MAX_WEAPON_ROWS = 4;

    /** A rectangle: x, y, width, height. */
    public record Box(int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        /** True when the two share at least one pixel. Touching edges do not count. */
        public boolean overlaps(Box o) {
            return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();
        }
    }

    /**
     * The weapon box: right of the stats panel, same top edge. {@code weapons} is the number of weapon rows (capped at {@link #MAX_WEAPON_ROWS}); the
     * "Weapons" title row is added here, so the cap limits weapons and never cuts the last one off. Same row height and trim as the relic list, so a
     * box for N weapons is exactly as tall as a relic list of N relics.
     */
    public static Box weaponBox(Box stats, int widestRow, int weapons, int rowH, int padding) {
        int n = Math.max(0, Math.min(weapons, MAX_WEAPON_ROWS));
        int w = padding * 2 + widestRow;
        int h = padding * 2 + (n + 1) * rowH - 2;
        return new Box(stats.right() + GAP, stats.y(), w, h);
    }

    /** The relic list: under the stats panel, left aligned with it. */
    public static Box relicBox(Box stats, int w, int h) {
        return new Box(stats.x(), stats.bottom() + GAP, w, h);
    }

    /** The timer plate, centred at the top. */
    public static Box timerBox(int screenW, int w, int h) {
        return new Box(screenW / 2 - w / 2, MARGIN, w, h);
    }

    /**
     * Whether the weapon box fits beside the stats panel before it reaches the timer plate. When it does not, the client hides the weapon box
     * rather than draw over the timer.
     */
    public static boolean weaponBoxFits(Box stats, Box weapon, Box timer) {
        return !weapon.overlaps(timer) && !weapon.overlaps(stats);
    }

    /** The scale vanilla picks for "Auto" GUI scale: the largest whole number that keeps the scaled screen at least 320 x 240. */
    public static int autoScale(int windowW, int windowH) {
        int s = 1;
        while (windowW / (s + 1) >= 320 && windowH / (s + 1) >= 240) {
            s++;
        }
        return s;
    }

    /** Rows of the relic list that are actually drawn, after the cap. The rest fold into "+N more". */
    public static int relicRowsShown(int owned, int cap) {
        return Math.max(0, Math.min(owned, cap));
    }

    /** Height of the relic box for this many rows: a padded title row, the shown rows, and one extra row when some are folded. */
    public static int relicBoxHeight(int owned, int cap, int rowH, int padding) {
        int rows = relicRowsShown(owned, cap);
        int hidden = owned - rows;
        return padding * 2 + rowH * (rows + 1) - 2 + (hidden > 0 ? rowH : 0);
    }

    /**
     * The top edge of the bottom-left loadout panel, which the relic list must stay above. Mirrors {@code LoadoutHud}: a cell is at most 30 wide and
     * shrinks to fit beside the hotbar; the panel is two cells and a 3 px gap tall, 4 px above the bottom. Returns the screen height when the loadout
     * panel hides itself (the cell would be under 16), because then nothing is down there.
     */
    public static int loadoutTop(int screenW, int screenH) {
        int free = screenW / 2 - 91 - 4 * 2;
        int cell = Math.min(30, (free - 2 * 3) / 4);
        if (cell < 16) {
            return screenH;
        }
        return screenH - 4 - (cell * 2 + 3);
    }

    /**
     * How many relic rows fit between {@code top} and {@code limit} (a y the box must end above), never more than {@code cap}. At least one row is
     * always allowed so a player with relics sees something; {@link #relicBoxHeight} adds the "+N more" row when some fold away.
     */
    public static int relicRowsFit(int owned, int cap, int top, int limit, int rowH, int padding) {
        int rows = Math.min(cap, owned);
        while (rows > 1 && top + relicBoxHeight(owned, rows, rowH, padding) > limit) {
            rows--;
        }
        return Math.max(0, Math.min(rows, owned));
    }
}
