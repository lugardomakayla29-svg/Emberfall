package com.solme.emberfall.character;

/**
 * The pure layout of the Character Select panel: where the panel, every card, the detail pane and the Skip button sit. The client screen
 * draws from these numbers and the click areas are the same rectangles, so what you see is what you click. No Minecraft types, so a check
 * can run it without the game jar and prove the panel fits the window and nothing overlaps.
 */
public final class SelectLayout {
    /** The widest grid. Narrow windows use fewer columns (see {@link #columnsFor}), so the panel never runs off the sides. */
    public static final int COLUMNS = 4;
    public static final int CARD_W = 104;
    public static final int CARD_H = 34;
    /** The card height on a very short window: the badge and name only, no weapon line. */
    public static final int CARD_H_COMPACT = 22;
    public static final int GAP = 6;
    public static final int DETAIL_H = 78;
    /** On a short window the detail pane shrinks to this (two lines of lore and one of stats) before anything else gives. */
    public static final int DETAIL_H_MIN = 44;
    public static final int TITLE_H = 30;
    public static final int FOOT_H = 34;

    public final int panelX;
    public final int panelY;
    public final int panelW;
    public final int panelH;
    public final int gridX;
    public final int gridY;
    public final int detailY;
    public final int detailH;
    public final int count;
    public final int columns;
    public final int cardH;
    public final int rows;

    public SelectLayout(int windowW, int windowH, int count) {
        this.count = count;
        this.columns = columnsFor(windowW);
        int rows = Math.max(1, (count + columns - 1) / columns);
        this.rows = rows;
        int gridW = columns * CARD_W + (columns - 1) * GAP;
        int ch = CARD_H;
        int gridH = rows * ch + (rows - 1) * GAP;
        this.panelW = gridW + 24;
        int fixed = TITLE_H + gridH + 10 + FOOT_H;
        if (fixed + DETAIL_H_MIN > windowH - 8) {
            ch = CARD_H_COMPACT;
            gridH = rows * ch + (rows - 1) * GAP;
            fixed = TITLE_H + gridH + 10 + FOOT_H;
        }
        this.cardH = ch;
        // The detail pane takes what is left, between its minimum and its full height.
        this.detailH = Math.max(DETAIL_H_MIN, Math.min(DETAIL_H, windowH - 8 - fixed));
        this.panelH = fixed + detailH;
        this.panelX = (windowW - panelW) / 2;
        this.panelY = Math.max(4, (windowH - panelH) / 2);
        this.gridX = panelX + 12;
        this.gridY = panelY + TITLE_H;
        this.detailY = gridY + rows * (cardH + GAP) + 4;
    }

    /** How many cards fit across: the panel is columns * 104 + gaps + 24 wide, and must leave 8 px each side. */
    public static int columnsFor(int windowW) {
        for (int c = COLUMNS; c >= 1; c--) {
            if (c * CARD_W + (c - 1) * GAP + 24 + 16 <= windowW) {
                return c;
            }
        }
        return 1;
    }

    public int cardX(int i) {
        return gridX + (i % columns) * (CARD_W + GAP);
    }

    public int cardY(int i) {
        return gridY + (i / columns) * (cardH + GAP);
    }

    /** The card under the point, or -1. This is the one rule both the drawing and the click use. */
    public int cardAt(int x, int y) {
        for (int i = 0; i < count; i++) {
            if (x >= cardX(i) && x < cardX(i) + CARD_W && y >= cardY(i) && y < cardY(i) + cardH) {
                return i;
            }
        }
        return -1;
    }

    public int skipX() {
        return panelX + panelW / 2 - 40;
    }

    public int skipY() {
        return panelY + panelH - 26;
    }

    /** True when the whole panel is inside the window. */
    public boolean fits(int windowW, int windowH) {
        return panelX >= 0 && panelY >= 0 && panelX + panelW <= windowW && panelY + panelH <= windowH;
    }
}
