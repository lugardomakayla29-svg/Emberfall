package com.solme.emberfall.client;

import com.solme.emberfall.network.SwarmHudPayload;
import com.solme.emberfall.wave.FinalSwarm;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The Final Swarm's silver multiplier, drawn under the timer: the multiplier big, its tier beneath, and a hint while the
 * escape portal is open. Silver-grey like the silver it multiplies; the tier name takes a colour so danger reads at a glance.
 * Shows only what the server last sent; 0 means nothing is drawn.
 */
public final class SwarmHud implements HudElement {
    private static volatile SwarmHudPayload state = SwarmHudPayload.HIDDEN;

    public static void set(SwarmHudPayload payload) {
        state = payload;
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        SwarmHudPayload s = state;
        Minecraft mc = Minecraft.getInstance();
        if (s.tenths() <= 0 || mc.options.hideGui || mc.player == null) {
            return;
        }
        int cx = g.guiWidth() / 2;
        String mult = String.format("%.1fx", s.tenths() / 10.0);
        FinalSwarm.Tier tier = FinalSwarm.tierOf(s.tenths());
        int colour = switch (tier) {
            case EASY -> 0xFF9CD6A1;
            case MEDIUM -> 0xFFF2D56B;
            case HARD -> 0xFFEE8A3A;
            case NIGHTMARE -> 0xFFE5443C;
        };
        g.pose().pushMatrix();
        g.pose().translate(cx, 34);
        g.pose().scale(1.6F, 1.6F);
        g.drawCenteredString(mc.font, mult, 0, 0, 0xFFD8DCE2);
        g.pose().popMatrix();
        g.drawCenteredString(mc.font, "SILVER  " + tier.label().toUpperCase(), cx, 52, colour);
        if (s.portal()) {
            g.drawCenteredString(mc.font, "Escape portal open: stand in it to cash out", cx, 63, 0xFF8FB8FF);
        }
    }
}
