package com.solme.emberfall.client;

import com.solme.emberfall.network.CloseChestRevealPayload;
import com.solme.emberfall.network.OpenChestRevealPayload;
import com.solme.emberfall.relic.ChestReveal;
import com.solme.emberfall.relic.ChestRevealClock;
import com.solme.emberfall.relic.ChestRevealView;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The chest slot-machine: a tier reel stops first, then a relic-name reel, then the answer holds. It only DISPLAYS what the server sent. The relic was
 * granted before this packet was sent, so nothing here can lose or change a prize; closing sends just the id the server issued, once.
 * If this client cannot build the spin (a relic name its pool does not know), it shows the answer without the spin instead of throwing.
 * Esc or any click after the tier has landed skips to the end; the screen also closes itself after the hold. Tested headless, look unverified.
 */
public class ChestRevealScreen extends Screen {
    private static final int PANEL_W = 220;
    private static final int PANEL_H = 96;

    private final OpenChestRevealPayload data;
    private final ChestReveal.Reveal reveal; // null: show the answer without the spin
    private int screenTicks;
    private boolean closeSent;

    public ChestRevealScreen(OpenChestRevealPayload payload) {
        super(Component.literal("Chest"));
        this.data = payload;
        this.reveal = ChestRevealView.safeBuild(payload.tier(), payload.item(), payload.seed());
        if (this.reveal == null) {
            this.screenTicks = ChestReveal.TOTAL_TICKS; // no spin to show: straight to the held answer
        }
    }

    @Override
    public void tick() {
        this.screenTicks++;
        if (ChestRevealClock.autoClose(this.screenTicks)) {
            this.onClose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = this.height / 2 - PANEL_H / 2;
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xCC101010);

        String tierText;
        String itemText;
        boolean tierDone;
        boolean itemDone;
        if (this.reveal == null) {
            tierText = data.tier();
            itemText = data.item();
            tierDone = true;
            itemDone = true;
        } else {
            ChestReveal.Frame f = this.reveal.frame(ChestRevealClock.animationTick(this.screenTicks));
            tierText = f.tier().symbol();
            itemText = f.item().symbol();
            tierDone = f.tier().state() == ChestReveal.State.STOPPED;
            itemDone = f.item().state() == ChestReveal.State.STOPPED;
        }

        int tierRgb = tierDone ? ChestRevealView.tierRgb(data.tier(), 0xFFFFFF) : 0x888888;
        int itemRgb = itemDone ? ChestRevealView.itemRgb(data.item(), 0xFFFFFF) : 0x888888;
        g.fill(left, top, left + PANEL_W, top + 3, 0xFF000000 | tierRgb); // tier colour bar across the top

        g.drawCenteredString(this.font, "Ember Chest", cx, top + 10, 0xFFAAAAAA);
        if (tierText != null) {
            g.drawCenteredString(this.font, Component.literal(tierText).withStyle(s -> s.withColor(tierRgb).withBold(tierDone)), cx, top + 30, 0xFFFFFFFF);
        }
        if (itemText != null) {
            g.drawCenteredString(this.font, Component.literal(itemText).withStyle(s -> s.withColor(itemRgb).withBold(itemDone)), cx, top + 50, 0xFFFFFFFF);
        }
        String hint = ChestRevealClock.finished(this.screenTicks) ? "Click to close" : (this.screenTicks >= ChestRevealClock.MIN_WATCH_TICKS ? "Click to skip" : "");
        if (!hint.isEmpty()) {
            g.drawCenteredString(this.font, hint, cx, top + PANEL_H - 14, 0xFF777777);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void press() {
        switch (ChestRevealClock.onPress(this.screenTicks)) {
            case SKIP -> this.screenTicks = ChestRevealClock.skipTarget();
            case CLOSE -> this.onClose();
            case NONE -> { }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.press();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Esc must always be able to leave. Before the tier lands it closes straight away; any other key behaves like a click.
        if (event.key() == InputConstants.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        this.press();
        return true;
    }

    @Override
    public void onClose() {
        if (!this.closeSent) {
            this.closeSent = true;
            ClientPlayNetworking.send(new CloseChestRevealPayload(data.revealId()));
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
