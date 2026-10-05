package com.solme.emberfall.client;

import com.solme.emberfall.network.RunEndPayload;
import com.solme.emberfall.progression.RewardFormula;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shown when a run ends because the player fell. The player is NOT dead (the server cancelled the lethal hit),
 * so this is a plain info screen: it closes on the button or Escape and never blocks the game.
 *
 * NOTE on testing: like every other screen here, the layout cannot be checked visually in the sandbox (no
 * graphical client); only the payload that feeds it is verified.
 */
public class RunEndScreen extends Screen {
    private final RunEndPayload run;

    public RunEndScreen(RunEndPayload run) {
        super(Component.literal(titleFor(run)));
        this.run = run;
    }

    private static String titleFor(RunEndPayload run) {
        return switch (run.cause()) {
            case "fallen" -> "Expedition Over";
            default -> "Expedition Ended";
        };
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Continue"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height / 2 + 62, 100, 20)
                .build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int y = this.height / 2 - 70;
        g.drawCenteredString(this.font, this.title, cx, y, 0xFFFF5555);
        y += 22;
        long m = run.seconds() / 60, s = run.seconds() % 60;
        y = line(g, cx, y, "Time survived", String.format("%d:%02d", m, s), RewardFormula.forTime(run.seconds()));
        y = line(g, cx, y, "Level reached", Integer.toString(run.level()), RewardFormula.forLevels(run.level()));
        y = line(g, cx, y, "Enemies defeated", Integer.toString(run.kills()), RewardFormula.forKills(run.kills()));
        y = line(g, cx, y, "Gold collected", Integer.toString(run.gold()), RewardFormula.forGold(run.gold()));
        if (run.hydraDown() || run.devourerDown()) {
            String bosses = run.hydraDown() && run.devourerDown() ? "Ember Guardian, Devourer"
                    : run.hydraDown() ? "Ember Guardian" : "Devourer";
            y = line(g, cx, y, "Bosses defeated", bosses, RewardFormula.forBosses(run.hydraDown(), run.devourerDown()));
        }
        y += 4;
        g.drawCenteredString(this.font, "+" + run.silverEarned() + " Silver", cx, y, 0xFFFFD700);
        g.drawCenteredString(this.font, "Silver total: " + run.silverTotal(), cx, y + 12, 0xFFAAAAAA);
        if (run.silverEarned() == 0) {
            // A bare "+0" reads as a bug. Say why, and what pays.
            g.drawCenteredString(this.font, "Survive 10s, defeat 5 enemies or gain a level to earn Silver.", cx, y + 26, 0xFF999999);
        }
    }

    /** One stat row; the Silver it paid is shown in gold beside it, or left off when it paid nothing. */
    private int line(GuiGraphics g, int cx, int y, String label, String value, long silver) {
        String text = label + ": " + value;
        g.drawCenteredString(this.font, text, cx - (silver > 0 ? 18 : 0), y, 0xFFFFFFFF);
        if (silver > 0) {
            g.drawString(this.font, "+" + silver, cx + this.font.width(text) / 2 - 12, y, 0xFFFFD700);
        }
        return y + 12;
    }
}
