package com.solme.emberfall.client;

import com.solme.emberfall.network.BuyMerchantItemPayload;
import com.solme.emberfall.network.OpenMerchantPayload;
import com.solme.emberfall.relic.Relic;
import com.solme.emberfall.relic.RelicPool;
import com.solme.emberfall.relic.RelicRarity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A Testificate's stall: up to three cards side by side, the merchant's own rarity as a banner, and a Buy button per card.
 * The screen only DISPLAYS what the server sent (relic names, colours and text come from the shared RelicPool by id); a click
 * sends just the slot number and the server re-checks gold, distance and the stall. Closing the window without buying sends
 * -1, so the Testificate can look disappointed. Card text goes through {@link CardText}, so it can never overlap a neighbour.
 */
public class MerchantScreen extends Screen {
    private static final int CARD_W = 118;
    private static final int CARD_H = 132;
    private static final int GAP = 10;

    private OpenMerchantPayload data;
    private long openedAtMs;
    private boolean bought;

    public MerchantScreen(OpenMerchantPayload payload) {
        super(Component.literal("Testificate"));
        this.data = payload;
        this.openedAtMs = System.currentTimeMillis();
    }

    /** The server refreshed the stall (new gold, new countdown) while this window is open. */
    public void refresh(OpenMerchantPayload payload) {
        this.data = payload;
        this.openedAtMs = System.currentTimeMillis();
        this.rebuildWidgets();
    }

    /** The server closed the stall (bought, or he left): no "closed without buying" message is sent back. */
    public void closeFromServer() {
        this.bought = true;
        this.onClose();
    }

    private int left() {
        return this.width / 2 - (data.items().size() * CARD_W + (data.items().size() - 1) * GAP) / 2;
    }

    private int top() {
        return this.height / 2 - CARD_H / 2;
    }

    @Override
    protected void init() {
        for (int i = 0; i < data.items().size(); i++) {
            OpenMerchantPayload.Item it = data.items().get(i);
            int x = left() + i * (CARD_W + GAP);
            final int slot = i;
            Button buy = Button.builder(Component.literal("Buy " + it.price()), b -> {
                        bought = true;
                        ClientPlayNetworking.send(new BuyMerchantItemPayload(slot));
                    })
                    .bounds(x + 10, top() + CARD_H - 26, CARD_W - 20, 20).build();
            buy.active = it.affordable();
            addRenderableWidget(buy);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        RelicRarity tier = RelicRarity.values()[Math.max(0, Math.min(RelicRarity.values().length - 1, data.tier()))];
        int secs = Math.max(0, data.secondsLeft() - (int) ((System.currentTimeMillis() - openedAtMs) / 1000L));
        g.drawCenteredString(this.font, Component.literal(tier.label() + " Testificate").withStyle(s -> s.withColor(tier.rgb()).withBold(true)),
                this.width / 2, top() - 30, 0xFFFFFFFF);
        g.drawCenteredString(this.font, Component.literal("Pick one.  Leaves in " + secs + "s   |   Your gold: " + data.gold()),
                this.width / 2, top() - 17, 0xFFAAAAAA);
        for (int i = 0; i < data.items().size(); i++) {
            drawCard(g, i, mouseX, mouseY);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawCard(GuiGraphics g, int i, int mouseX, int mouseY) {
        OpenMerchantPayload.Item it = data.items().get(i);
        Relic relic = RelicPool.byId(it.relicId());
        int x = left() + i * (CARD_W + GAP), y = top();
        g.fill(x, y, x + CARD_W, y + CARD_H, 0xCC101010);
        if (relic == null) {
            g.drawCenteredString(this.font, "?", x + CARD_W / 2, y + 40, 0xFFFF5555);
            return;
        }
        int rgb = 0xFF000000 | relic.rarity().rgb();
        g.fill(x, y, x + CARD_W, y + 4, rgb); // rarity bar across the top
        g.fill(x, y + CARD_H - 1, x + CARD_W, y + CARD_H, rgb);
        g.drawString(this.font, Component.literal(relic.name()).withStyle(s -> s.withColor(relic.rarity().rgb()).withBold(true)), x + 6, y + 9, 0xFFFFFFFF);
        g.drawString(this.font, relic.rarity().label(), x + 6, y + 21, 0xFF888888);
        CardText.draw(g, this.font, CardText.summary(relic.description()), x + 6, y + 36, CARD_W - 12, 0xFFCCCCCC);
        CardText.tooltipIfOver(g, relic.description(), mouseX, mouseY, x, y, CARD_W, CARD_H - 28);
        if (!it.affordable()) {
            g.drawCenteredString(this.font, "Need " + (it.price() - data.gold()) + " more", x + CARD_W / 2, y + CARD_H - 38, 0xFFFF6655);
        }
    }

    @Override
    public void onClose() {
        if (!bought) {
            ClientPlayNetworking.send(new BuyMerchantItemPayload(-1)); // left without buying
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
