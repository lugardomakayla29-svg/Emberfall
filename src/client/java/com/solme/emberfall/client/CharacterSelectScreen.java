package com.solme.emberfall.client;

import com.solme.emberfall.network.ChooseCharacterPayload;
import com.solme.emberfall.network.OpenCharacterSelectPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.solme.emberfall.character.SelectLayout;
import java.util.List;

/**
 * Character selection: one button per character in a grid, lore and stats in a hover tooltip. The current pick is
 * marked with a green arrow and cannot be re-picked. Clicking sends only an id; the server validates it (unknown,
 * locked, mid-expedition) exactly like /character select. Same shape as {@link WeaponChoiceScreen}.
 */
public class CharacterSelectScreen extends Screen {
    private final String currentId;
    private final List<OpenCharacterSelectPayload.Entry> characters;
    /** 0 = the Character Table (no Rift party behind it). Otherwise the phase id the server issued; echoed back if the screen is closed unpicked. */
    private final int selectId;
    private final long openedAtMs = System.currentTimeMillis();
    private final int secondsAtOpen;
    /** True once a pick was sent, so closing the screen afterwards does not also send a "closed without picking". */
    private boolean answered;
    /** The card the detail pane shows: the hovered one, else the current character, else the first. */
    private int shown;
    /** Every rectangle comes from here, and the click areas are the same rectangles, so what is drawn is what is clicked. Rebuilt in init(). */
    private SelectLayout lay;

    public CharacterSelectScreen(String currentId, List<OpenCharacterSelectPayload.Entry> characters) {
        this(currentId, characters, OpenCharacterSelectPayload.NO_PHASE, 0);
    }

    public CharacterSelectScreen(String currentId, List<OpenCharacterSelectPayload.Entry> characters, int selectId, int secondsLeft) {
        super(Component.literal("Choose Your Character"));
        this.currentId = currentId;
        this.characters = characters;
        this.selectId = selectId;
        this.secondsAtOpen = secondsLeft;
        for (int i = 0; i < characters.size(); i++) {
            if (characters.get(i).id().equals(currentId)) {
                this.shown = i;
            }
        }
    }

    private boolean riftParty() {
        return selectId != OpenCharacterSelectPayload.NO_PHASE;
    }

    private int secondsLeft() {
        return Math.max(0, secondsAtOpen - (int) ((System.currentTimeMillis() - openedAtMs) / 1000L));
    }

    /** Fraction of the party's time still left, 1.0 at the start and 0.0 at the end; smooth because it uses milliseconds. */
    private float timeFraction() {
        if (secondsAtOpen <= 0) {
            return 0f;
        }
        float left = secondsAtOpen * 1000f - (System.currentTimeMillis() - openedAtMs);
        return Math.max(0f, Math.min(1f, left / (secondsAtOpen * 1000f)));
    }

    @Override
    protected void init() {
        lay = new SelectLayout(this.width, this.height, characters.size());
        for (int i = 0; i < characters.size(); i++) {
            OpenCharacterSelectPayload.Entry e = characters.get(i);
            boolean current = e.id().equals(currentId);
            // An invisible button gives the card its click, focus and keyboard use; the card itself is drawn in render().
            Button button = Button.builder(Component.empty(), b -> choose(e.id())).bounds(lay.cardX(i), lay.cardY(i), SelectLayout.CARD_W, lay.cardH).build();
            button.active = riftParty() || !current;
            button.setAlpha(0f);
            addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(Component.literal(riftParty() ? "Skip" : "Close"), b -> onClose())
                .bounds(lay.skipX(), lay.skipY(), 80, 20).build());
    }

    private void choose(String id) {
        answered = true;
        ClientPlayNetworking.send(new ChooseCharacterPayload(id));
        this.minecraft.setScreen(null);
    }

    /** Closing (Close button or Escape) without a pick tells the server so the party is not held up; "keep what I have". */
    @Override
    public void removed() {
        if (riftParty() && !answered) {
            answered = true;
            ClientPlayNetworking.send(new com.solme.emberfall.network.CloseCharacterSelectPayload(selectId));
        }
        super.removed();
    }

    /** True when Tab or the arrow keys have put keyboard focus on this card's (invisible) button, so the card can show its own focus border. */
    private boolean isCardFocused(int index) {
        return index >= 0 && index < characters.size() && index < this.children().size() && this.children().get(index).isFocused();
    }

    /** A steady colour per character, taken from the id so the same character always looks the same. */
    private static int accent(String id) {
        int h = id.hashCode();
        float hue = (Math.floorMod(h, 360)) / 360f;
        return 0xFF000000 | java.awt.Color.HSBtoRGB(hue, 0.55f, 0.95f);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        // Panel: dark body, thin border, title bar.
        g.fill(lay.panelX - 1, lay.panelY - 1, lay.panelX + lay.panelW + 1, lay.panelY + lay.panelH + 1, 0xFF6E5A3C);
        g.fill(lay.panelX, lay.panelY, lay.panelX + lay.panelW, lay.panelY + lay.panelH, 0xF0181418);
        g.fill(lay.panelX, lay.panelY, lay.panelX + lay.panelW, lay.panelY + 22, 0xFF2A2024);
        g.drawCenteredString(this.font, this.title, this.width / 2, lay.panelY + 7, 0xFFFFE9B8);

        // Time bar (Rift party only): drains smoothly, turns red in the last five seconds.
        if (riftParty()) {
            int barX = lay.panelX + 12;
            int barW = lay.panelW - 24;
            g.fill(barX, lay.panelY + 24, barX + barW, lay.panelY + 27, 0xFF3A3036);
            int fill = (int) (barW * timeFraction());
            g.fill(barX, lay.panelY + 24, barX + fill, lay.panelY + 27, secondsLeft() <= 5 ? 0xFFE0503C : 0xFFE8A83C);
        }

        // Cards.
        int hovered = -1;
        for (int i = 0; i < characters.size(); i++) {
            int x = lay.cardX(i);
            int y = lay.cardY(i);
            OpenCharacterSelectPayload.Entry e = characters.get(i);
            boolean current = e.id().equals(currentId);
            boolean over = mouseX >= x && mouseX < x + SelectLayout.CARD_W && mouseY >= y && mouseY < y + lay.cardH;
            if (over) {
                hovered = i;
            }
            int accent = accent(e.id());
            g.fill(x, y, x + SelectLayout.CARD_W, y + lay.cardH, over ? 0xFF3A3038 : 0xFF2A2228);
            // The current pick gets a border that breathes slowly (about 2 s per cycle); every other border is still.
            int edge = 0xFF4A3E44;
            if (current) {
                double wave = 0.5 + 0.5 * Math.sin((System.currentTimeMillis() % 2000L) / 2000.0 * Math.PI * 2);
                int a = 150 + (int) (105 * wave);
                edge = (a << 24) | (accent & 0xFFFFFF);
            } else if (over || isCardFocused(i)) {
                edge = 0xFFB89868;
            }
            g.fill(x, y, x + SelectLayout.CARD_W, y + 1, edge);
            g.fill(x, y + lay.cardH - 1, x + SelectLayout.CARD_W, y + lay.cardH, edge);
            g.fill(x, y, x + 1, y + lay.cardH, edge);
            g.fill(x + SelectLayout.CARD_W - 1, y, x + SelectLayout.CARD_W, y + lay.cardH, edge);
            // Badge with the initial, then the name and the weapon.
            int badge = Math.min(26, lay.cardH - 8);
            g.fill(x + 4, y + 4, x + 4 + badge, y + 4 + badge, accent);
            String shown = SelectLayout.cardName(e.name());
            String initial = shown.isEmpty() ? "?" : shown.substring(0, 1).toUpperCase();
            g.drawCenteredString(this.font, initial, x + 4 + badge / 2, y + 4 + (badge - 8) / 2, 0xFF181418);
            g.drawString(this.font, this.font.plainSubstrByWidth(shown, SelectLayout.CARD_W - 38), x + 34, y + (lay.cardH < SelectLayout.CARD_H ? 7 : 8), current ? 0xFFFFE9B8 : 0xFFFFFFFF, false);
            if (lay.cardH >= SelectLayout.CARD_H) {
                g.drawString(this.font, this.font.plainSubstrByWidth(e.weapon(), SelectLayout.CARD_W - 38), x + 34, y + 20, 0xFF9A9098, false);
            }
        }
        if (hovered >= 0) {
            shown = hovered;
        } else {
            for (int i = 0; i < characters.size(); i++) {
                if (isCardFocused(i)) {
                    shown = i;
                }
            }
        }

        // Detail pane for the shown character.
        int dx = lay.panelX + 12;
        int dy = lay.detailY;
        int dw = lay.panelW - 24;
        g.fill(dx, dy, dx + dw, dy + lay.detailH, 0xFF221C20);
        if (shown >= 0 && shown < characters.size()) {
            OpenCharacterSelectPayload.Entry e = characters.get(shown);
            g.drawString(this.font, e.name() + (e.id().equals(currentId) ? "  (current)" : ""), dx + 6, dy + 5, accent(e.id()), false);
            int line = dy + 18;
            for (net.minecraft.util.FormattedCharSequence part : this.font.split(Component.literal(e.lore()), dw - 12)) {
                if (line > dy + 42) {
                    break;
                }
                g.drawString(this.font, part, dx + 6, line, 0xFFD8D0D4, false);
                line += 10;
            }
            int sl = dy + 46;
            for (net.minecraft.util.FormattedCharSequence part : this.font.split(Component.literal("Weapon: " + e.weapon() + "   " + e.stats()), dw - 12)) {
                if (sl > dy + lay.detailH - 10) {
                    break;
                }
                g.drawString(this.font, part, dx + 6, sl, 0xFF9AB8A0, false);
                sl += 10;
            }
        }
        // Hint line.
        g.drawCenteredString(this.font, riftParty()
                        ? "Click a character. The run starts in " + secondsLeft() + " s, or when everyone has chosen."
                        : "Click a character to switch to them.", this.width / 2, lay.panelY + lay.panelH - 38, 0xFF8A8088);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
