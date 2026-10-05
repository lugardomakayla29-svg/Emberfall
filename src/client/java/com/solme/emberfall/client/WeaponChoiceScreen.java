package com.solme.emberfall.client;

import com.solme.emberfall.network.ChooseWeaponPayload;
import com.solme.emberfall.network.OpenWeaponChoicePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Weapon pick screen - reuses {@link TomeChoiceScreen}'s exact layout
 * approach (plain client-only Screen, one card per offer, description
 * drawn below) for the same reasons documented there: no server-
 * authoritative inventory sync needed, just "show these options, tell the
 * server which one got clicked."
 *
 * NOTE on testing: same honest gap as TomeChoiceScreen - no connected
 * graphical client in this sandbox to visually verify against.
 */
public class WeaponChoiceScreen extends Screen {
    private final boolean isStartingPick;
    private final int requestId;
    private final List<OpenWeaponChoicePayload.OfferInfo> offers;

    public WeaponChoiceScreen(boolean isStartingPick, int requestId, List<OpenWeaponChoicePayload.OfferInfo> offers) {
        super(Component.literal(isStartingPick ? "Choose Your Weapon" : "New Weapon Found"));
        this.isStartingPick = isStartingPick;
        this.requestId = requestId;
        this.offers = offers;
    }

    private static final int CARD_W = 180, GAP = 20, CARD_H = 78, PAD = 6;
    /** Index of the card the player has clicked (highlighted), or -1. Picking needs a second click on Confirm: no accidental picks. */
    private int selected = -1;
    private Button confirm;

    private int cardY() { return this.height / 2 - 30; }
    private int startX() { return (this.width - (offers.size() * CARD_W + (offers.size() - 1) * GAP)) / 2; }

    @Override
    protected void init() {
        int y = cardY();
        for (int i = 0; i < offers.size(); i++) {
            final int index = i;
            // Empty label: render() draws the name on the top row so the taller card can hold the wrapped summary under it.
            addRenderableWidget(Button.builder(Component.empty(), button -> { selected = index; confirm.active = true; })
                    .bounds(startX() + i * (CARD_W + GAP), y, CARD_W, CARD_H)
                    .build());
        }
        int rowY = y + CARD_H + 10;
        confirm = Button.builder(Component.literal("Confirm"), button -> {
                    if (selected >= 0 && selected < offers.size()) {
                        choose(offers.get(selected).id());
                    }
                })
                .bounds(this.width / 2 - 85, rowY, 80, 20).build();
        confirm.active = false;
        addRenderableWidget(confirm);
        if (!isStartingPick) {
            // A mid-run offer or replace step is optional: an empty id tells the server to keep everything.
            addRenderableWidget(Button.builder(Component.literal("Skip"), button -> choose(""))
                    .bounds(this.width / 2 + 5, rowY, 80, 20)
                    .build());
        }
    }

    private void choose(String weaponId) {
        ClientPlayNetworking.send(new ChooseWeaponPayload(isStartingPick, requestId, weaponId));
        this.minecraft.setScreen(null);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int y = cardY();
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, y - 18, 0xFFFFFFFF);
        for (int i = 0; i < offers.size(); i++) {
            OpenWeaponChoicePayload.OfferInfo offer = offers.get(i);
            int x = startX() + i * (CARD_W + GAP);
            if (i == selected) {
                // A gold frame around the chosen card, so it is obvious what Confirm will take.
                guiGraphics.renderOutline(x - 2, y - 2, CARD_W + 4, CARD_H + 4, 0xFFFFD84A);
            }
            guiGraphics.drawCenteredString(this.font, offer.displayName(), x + CARD_W / 2, y + 6, i == selected ? 0xFFFFD84A : 0xFFFFFFFF);
            CardText.draw(guiGraphics, this.font, CardText.summary(offer.description()), x + PAD, y + this.font.lineHeight + 14, CARD_W - 2 * PAD, 0xFFCCCCCC);
            CardText.tooltipIfOver(guiGraphics, offer.description(), mouseX, mouseY, x, y, CARD_W, CARD_H);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
