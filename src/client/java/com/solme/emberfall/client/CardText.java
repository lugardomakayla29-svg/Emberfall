package com.solme.emberfall.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Card text that can never run across the card next to it. The old choice screens drew a whole description as ONE centred line,
 * which overlapped its neighbours for anything longer than about 35 letters (a weapon tome's text is 250+ letters, 10 lines at card width).
 *
 * Every card now shows a SHORT SUMMARY (the first sentence, wrapped inside the card) and the FULL text in a tooltip while the mouse is over it.
 * Only methods verified in the 1.21.11 client jar are used: Font.wordWrapHeight, GuiGraphics.drawWordWrap, GuiGraphics.setTooltipForNextFrame.
 */
final class CardText {
    private CardText() {}

    /** The text up to and including its first sentence end (". "), or all of it when there is no second sentence. Pure string work. */
    static String summary(String text) {
        int cut = text.indexOf(". ");
        return cut < 0 ? text : text.substring(0, cut + 1);
    }

    /** Pixel height the wrapped text needs at this width. */
    static int height(Font font, String text, int width) {
        return font.wordWrapHeight(Component.literal(text), width);
    }

    /** Draws the text wrapped inside {@code width}, left aligned at (x, y); returns the y just under it. */
    static int draw(GuiGraphics g, Font font, String text, int x, int y, int width, int colour) {
        g.drawWordWrap(font, Component.literal(text), x, y, width, colour);
        return y + height(font, text, width);
    }

    /** Shows the full text as a tooltip at the mouse when it is over the card. */
    static void tooltipIfOver(GuiGraphics g, String fullText, int mouseX, int mouseY, int x, int y, int w, int h) {
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
            g.setTooltipForNextFrame(Component.literal(fullText), mouseX, mouseY);
        }
    }
}
