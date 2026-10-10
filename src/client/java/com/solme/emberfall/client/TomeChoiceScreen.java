package com.solme.emberfall.client;

import com.solme.emberfall.network.BanishTomePayload;
import com.solme.emberfall.network.ChooseTomePayload;
import com.solme.emberfall.network.OpenTomeChoicePayload;
import com.solme.emberfall.network.RerollTomePayload;
import com.solme.emberfall.relic.MenuGlyphs;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Design doc 7.4: the 3-card level-up reward pick, plus Reroll/Banish/Skip
 * (design goal: avoid ever being forced into a Tome nobody wants -
 * Reroll/Banish spend a per-run charge from {@link com.solme.emberfall.tome.PlayerTomeCharges},
 * bought permanently via the currency shop; Skip is always free). Every one
 * of those three actions round-trips through the server (it owns the real
 * offer list/RNG/charge counts) and gets answered with either a fresh
 * {@link OpenTomeChoicePayload} (Reroll/Banish - {@link com.solme.emberfall.client.EmberfallModClient}
 * replaces this screen instance with a new one built from the updated
 * payload) or simply closes the screen (Skip/pick).
 *
 * Deliberately a plain client-only {@code Screen} (not a menu/ScreenHandler) -
 * there's no server-authoritative inventory state to synchronize, just
 * "show these options, tell the server what got clicked", so a full Menu/
 * container round-trip would be pure overhead for what's really a client
 * display decision confirmed by a few small C2S packets.
 *
 * NOTE on testing: this file cannot be visually verified in this sandbox -
 * there's no connected graphical client to render it against (same
 * constraint flagged for AutoAttackSystem.tickPlayer and LevelingHandler).
 * The networking plumbing it depends on (payload encode/decode, receiver
 * registration) was checked directly against the mapped API; the button
 * layout/rendering itself is standard vanilla Screen/Button usage but is an
 * honest gap versus an in-game screenshot.
 */
public class TomeChoiceScreen extends Screen {
    private final int newLevel;
    /** The per-card Banish buttons and the Reroll button, so render() can draw their glyphs AFTER the buttons paint. */
    private final java.util.List<Button> banishButtons = new java.util.ArrayList<>();
    private Button rerollBtn;
    private final List<OpenTomeChoicePayload.OfferInfo> offers;
    private final int rerollsRemaining;
    private final int banishesRemaining;
    /** Gold the next reroll costs (0 while a free charge remains) and the gold the player holds. */
    private final int goldPrice;
    private final int gold;

    public TomeChoiceScreen(int newLevel, List<OpenTomeChoicePayload.OfferInfo> offers,
                             int rerollsRemaining, int banishesRemaining, int goldPrice, int gold) {
        super(Component.literal("Level " + newLevel + " - Choose a Tome"));
        this.newLevel = newLevel;
        this.offers = offers;
        this.rerollsRemaining = rerollsRemaining;
        this.banishesRemaining = banishesRemaining;
        this.goldPrice = goldPrice;
        this.gold = gold;
    }

    /** A reroll is possible with a free charge, or when the player can pay the gold price. */
    private boolean canReroll() {
        return rerollsRemaining > 0 || gold >= goldPrice;
    }

    private String rerollLabel() {
        if (rerollsRemaining > 0) {
            return "Reroll All (" + rerollsRemaining + ")";
        }
        return "Reroll All (" + goldPrice + " gold)";
    }

    // Vertical layout, all relative to CARD_Y so render()'s description row lines up exactly:
    // title -> description row -> [cards row, 40 tall] -> [banish row, 16 tall] -> [reroll/skip row, 20 tall]
    private static final int CARD_HEIGHT = 78;      // name row + up to 6 wrapped summary lines (was 40, with the text OUTSIDE the card)
    private static final int CARD_PAD = 6;
    private static final int CARD_TO_DESC_GAP = 20;   // title above the cards only now; the description lives inside each card
    private static final int CARD_TO_BANISH_GAP = 8;
    private static final int BANISH_HEIGHT = 16;
    private static final int BANISH_TO_BOTTOM_GAP = 14;

    @Override
    protected void init() {
        int cardWidth = 180;
        int spacing = 20;
        int totalWidth = offers.size() * cardWidth + (offers.size() - 1) * spacing;
        int startX = (this.width - totalWidth) / 2;
        int cardY = this.height / 2 - 20;

        for (int i = 0; i < offers.size(); i++) {
            final int index = i;
            OpenTomeChoicePayload.OfferInfo offer = offers.get(i);
            int x = startX + i * (cardWidth + spacing);
            // Empty label on purpose: a vanilla Button centres its label vertically, which would land the name in the middle of the
            // taller card on top of the summary. render() draws the name on the top row itself instead.
            addRenderableWidget(Button.builder(Component.empty(), button -> choose(index))
                    .bounds(x, cardY, cardWidth, CARD_HEIGHT)
                    .build());

            // Per-card Banish - permanently removes just this one Tome for the rest of the run.
            Button banishButton = Button.builder(Component.literal("§cBanish"), button -> banish(offer.id()))
                    .bounds(x + cardWidth / 2 - 35, cardY + CARD_HEIGHT + CARD_TO_BANISH_GAP, 70, BANISH_HEIGHT)
                    .build();
            banishButton.active = banishesRemaining > 0;
            addRenderableWidget(banishButton);
            banishButtons.add(banishButton);
        }

        int bottomY = cardY + CARD_HEIGHT + CARD_TO_BANISH_GAP + BANISH_HEIGHT + BANISH_TO_BOTTOM_GAP;
        Button rerollButton = Button.builder(
                        Component.literal(rerollLabel()), button -> reroll())
                .bounds(this.width / 2 - 165, bottomY, 150, 20)
                .build();
        rerollButton.active = canReroll();
        addRenderableWidget(rerollButton);
        this.rerollBtn = rerollButton;

        addRenderableWidget(Button.builder(Component.literal("Skip"), button -> skip())
                .bounds(this.width / 2 + 15, bottomY, 150, 20)
                .build());
    }

    private void choose(int index) {
        ClientPlayNetworking.send(new ChooseTomePayload(newLevel, index));
        this.minecraft.setScreen(null);
    }

    private void skip() {
        ClientPlayNetworking.send(new ChooseTomePayload(newLevel, -1));
        this.minecraft.setScreen(null);
    }

    private void reroll() {
        if (!canReroll()) {
            return;
        }
        ClientPlayNetworking.send(new RerollTomePayload(newLevel));
        // Leave the screen open - the server answers with a fresh OpenTomeChoicePayload
        // that EmberfallModClient replaces this screen instance with.
    }

    private void banish(String tomeId) {
        if (banishesRemaining <= 0) {
            return;
        }
        ClientPlayNetworking.send(new BanishTomePayload(newLevel, tomeId));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int cardY = this.height / 2 - 20;
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, cardY - CARD_TO_DESC_GAP - 15, 0xFFFFFFFF);

        int cardWidth = 180;
        int spacing = 20;
        int totalWidth = offers.size() * cardWidth + (offers.size() - 1) * spacing;
        int startX = (this.width - totalWidth) / 2;
        for (int i = 0; i < offers.size(); i++) {
            OpenTomeChoicePayload.OfferInfo offer = offers.get(i);
            int cardX = startX + i * (cardWidth + spacing);
            int textW = cardWidth - 2 * CARD_PAD;
            String label = offer.displayName() + (offer.tags().isEmpty() ? "" : " [" + offer.tags() + "]");
            MenuGlyphDraw.centredWithGlyph(guiGraphics, this.font, MenuGlyphs.glyph(MenuGlyphs.TOME, "tome"), label, cardX + cardWidth / 2, cardY + 6, 0xFFD5DCE4, 0xFFFFFFFF);
            CardText.draw(guiGraphics, this.font, CardText.summary(offer.description()),
                    cardX + CARD_PAD, cardY + this.font.lineHeight + 14, textW, 0xFFCCCCCC);
            CardText.tooltipIfOver(guiGraphics, offer.description(), mouseX, mouseY, cardX, cardY, cardWidth, CARD_HEIGHT);
        }
        // The buttons were painted by super.render above, so these glyphs go on top of them.
        String banishGlyph = MenuGlyphs.glyph(MenuGlyphs.TOME, "banish");
        for (Button banish : banishButtons) {
            MenuGlyphDraw.onButton(guiGraphics, this.font, banishGlyph, banish.getX(), banish.getY(), banish.getHeight(), banish.active, 0xFFD5DCE4);
        }
        if (rerollBtn != null) {
            MenuGlyphDraw.onButton(guiGraphics, this.font, MenuGlyphs.glyph(MenuGlyphs.TOME, "reroll"), rerollBtn.getX(), rerollBtn.getY(), rerollBtn.getHeight(), rerollBtn.active, 0xFFD5DCE4);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false; // the server, not the client pause menu, is what freezes the wave director
    }
}
