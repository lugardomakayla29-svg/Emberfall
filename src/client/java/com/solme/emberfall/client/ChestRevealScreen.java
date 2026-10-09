package com.solme.emberfall.client;

import com.solme.emberfall.network.CloseChestRevealPayload;
import com.solme.emberfall.network.OpenChestRevealPayload;
import com.solme.emberfall.relic.ChestReveal;
import com.solme.emberfall.relic.ChestRevealClock;
import com.solme.emberfall.relic.ChestRevealView;
import com.solme.emberfall.relic.ChestRollSound;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

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
    /** The screen tick the roll sounds were last worked out for, so a skip is told apart from a normal step. */
    private int soundedTick;

    public ChestRevealScreen(OpenChestRevealPayload payload) {
        super(Component.literal("Chest"));
        this.data = payload;
        this.reveal = ChestRevealView.safeBuild(payload.tier(), payload.item(), payload.seed());
        if (this.reveal == null) {
            this.screenTicks = ChestReveal.TOTAL_TICKS; // no spin to show: straight to the held answer
        }
        this.soundedTick = this.screenTicks; // nothing to sound for the ticks before the first one
    }

    @Override
    public void tick() {
        this.screenTicks++;
        this.playRollSounds();
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

    /**
     * Plays what {@link ChestRollSound#cues} says for the move from the last sounded tick to now. Vanilla sounds only, played as UI sounds (no position).
     * PROPOSAL: nothing here was heard. Tick = NOTE_BLOCK_HAT, tier lands = NOTE_BLOCK_CHIME, item lands = NOTE_BLOCK_HARP (none is used elsewhere in the mod).
     */
    private void playRollSounds() {
        ChestRollSound.Cues cues = ChestRollSound.cues(this.soundedTick, this.screenTicks);
        this.soundedTick = Math.max(this.soundedTick, this.screenTicks);
        if (this.reveal == null) {
            return;
        }
        for (int i = 0; i < cues.ticks(); i++) {
            ui(SoundEvents.NOTE_BLOCK_HAT.value(), 1.4F, 0.6F);
        }
        if (cues.tierLands()) {
            ui(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0F, 0.9F);
        }
        if (cues.itemLands()) {
            ui(SoundEvents.NOTE_BLOCK_HARP.value(), 1.3F, 1.0F);
        }
    }

    /** One UI sound at (pitch, volume), the argument order of SimpleSoundInstance.forUI(event, pitch, volume) read from its bytecode. */
    private static void ui(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private void press() {
        switch (ChestRevealClock.onPress(this.screenTicks)) {
            case SKIP -> {
                this.screenTicks = ChestRevealClock.skipTarget();
                this.playRollSounds(); // the jump plays only the landings it crossed, no ticks (ChestRollSound)
            }
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
