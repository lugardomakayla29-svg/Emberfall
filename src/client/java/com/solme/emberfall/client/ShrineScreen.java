package com.solme.emberfall.client;

import com.solme.emberfall.network.ChooseShrinePayload;
import com.solme.emberfall.network.OpenShrinePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The small window a shrine opens on a left click. A title, one line of lore and a vertical list of buttons; hovering a
 * button spells out its exact cost and reward. Clicking sends only (shrine type, option index) and closes the window; the
 * server re-validates everything, so a stale or forged click does nothing. Same shape as {@link CharacterSelectScreen}.
 */
public class ShrineScreen extends Screen {
    private static final int BUTTON_W = 170;
    private static final int BUTTON_H = 22;
    private static final int GAP = 6;

    private final String shrineType;
    private final String lore;
    private final List<OpenShrinePayload.Option> options;

    public ShrineScreen(OpenShrinePayload payload) {
        super(Component.literal(payload.title()));
        this.shrineType = payload.shrineType();
        this.lore = payload.lore();
        this.options = payload.options();
    }

    @Override
    protected void init() {
        int total = options.size() * (BUTTON_H + GAP);
        int startY = this.height / 2 - total / 2 + 10;
        int x = this.width / 2 - BUTTON_W / 2;
        for (int i = 0; i < options.size(); i++) {
            OpenShrinePayload.Option o = options.get(i);
            Button button = Button.builder(Component.literal(o.label()), b -> choose(o.index()))
                    .bounds(x, startY + i * (BUTTON_H + GAP), BUTTON_W, BUTTON_H).build();
            button.active = o.enabled();
            button.setTooltip(Tooltip.create(Component.literal(o.tooltip())));
            addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(this.width / 2 - 40, startY + total + 4, 80, 20).build());
    }

    private void choose(int option) {
        ClientPlayNetworking.send(new ChooseShrinePayload(shrineType, option));
        this.minecraft.setScreen(null);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int top = this.height / 2 - (options.size() * (BUTTON_H + GAP)) / 2 - 24;
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, top, 0xFFFFFFFF);
        guiGraphics.drawCenteredString(this.font, lore, this.width / 2, top + 12, 0xFFAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
