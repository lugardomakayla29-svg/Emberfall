package com.solme.emberfall.client;

import com.solme.emberfall.relic.HudLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws the icon glyph of a menu row, the way the stats panel in {@link RunHud} does: measured with {@code font.width} (never assumed), centred in a
 * marker column from {@link HudLayout#markerColumn} / {@link HudLayout#markerOffset}, in the row's own colour. Which glyph a row gets is NOT decided here;
 * it comes from the one table {@link com.solme.emberfall.relic.MenuGlyphs}, which the check also reads.
 *
 * Tested headless, look unverified: how any of this LOOKS has not been seen.
 */
final class MenuGlyphDraw {
    private MenuGlyphDraw() {}

    /** Gap in pixels between a glyph and the text that follows it. */
    static final int GAP = 4;
    /** The least a marker column is, as in the stats panel (the old 5 px square). */
    static final int MIN_COLUMN = 5;
    /** Left inset of a glyph inside a vanilla Button. */
    static final int BUTTON_INSET = 6;

    /** The width of the glyph column for one glyph: wide enough for it, never narrower than the stats panel's minimum. */
    static int column(Font font, String glyph) {
        return HudLayout.markerColumn(MIN_COLUMN, font.width(glyph));
    }

    /**
     * Draws {@code glyph} then {@code text}, the pair centred on {@code cx} as one block: the TEXT is drawn here too so the pair cannot drift apart.
     * {@code y} is the text's top. Returns the x of the block's RIGHT edge, so something drawn after it (the "+N" Silver) is placed from the real edge and
     * not from the text width alone.
     */
    static int centredWithGlyph(GuiGraphics g, Font font, String glyph, String text, int cx, int y, int glyphColour, int textColour) {
        if (glyph.isEmpty()) {
            g.drawCenteredString(font, text, cx, y, textColour);
            return cx + font.width(text) / 2;
        }
        int col = column(font, glyph);
        int textW = font.width(text);
        int total = col + GAP + textW;
        int left = cx - total / 2;
        g.drawString(font, glyph, left + HudLayout.markerOffset(col, font.width(glyph)), y, glyphColour, true);
        g.drawString(font, text, left + col + GAP, y, textColour, true);
        return left + total;
    }

    /**
     * Draws a glyph at a fixed left inset on a vanilla Button, vertically centred on it, and leaves the button's own label alone. Call after
     * {@code super.render} so the glyph sits above the button. A disabled button's glyph is dimmed so it does not look pressable.
     */
    static void onButton(GuiGraphics g, Font font, String glyph, int bx, int by, int bh, boolean active, int colour) {
        if (glyph.isEmpty()) {
            return;
        }
        int col = column(font, glyph);
        int ty = by + (bh - font.lineHeight) / 2 + 1;
        int c = active ? colour : (0x60000000 | (colour & 0x00FFFFFF));
        g.drawString(font, glyph, bx + BUTTON_INSET + HudLayout.markerOffset(col, font.width(glyph)), ty, c, true);
    }

    /**
     * Draws a glyph just LEFT of a line of text that the caller draws centred on {@code cx} (the text may be a styled Component, so the caller draws it).
     * {@code textWidth} is the measured width of that text. The glyph's right edge sits {@link #GAP} px left of the text's left edge.
     */
    static void beforeCentred(GuiGraphics g, Font font, String glyph, int textWidth, int cx, int y, int colour) {
        if (glyph.isEmpty()) {
            return;
        }
        int col = column(font, glyph);
        int textLeft = cx - textWidth / 2;
        g.drawString(font, glyph, textLeft - GAP - col + HudLayout.markerOffset(col, font.width(glyph)), y, colour, true);
    }

    /** Draws only a glyph at {@code x, y} (a card's top-left marker), centred in its column. */
    static void at(GuiGraphics g, Font font, String glyph, int x, int y, int colour) {
        if (glyph.isEmpty()) {
            return;
        }
        int col = column(font, glyph);
        g.drawString(font, glyph, x + HudLayout.markerOffset(col, font.width(glyph)), y, colour, true);
    }
}
