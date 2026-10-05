package com.solme.emberfall.client;

import com.solme.emberfall.network.ChooseCharacterPayload;
import com.solme.emberfall.network.OpenCharacterSelectPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Character selection: one button per character in a grid, lore and stats in a hover tooltip. The current pick is
 * marked with a green arrow and cannot be re-picked. Clicking sends only an id; the server validates it (unknown,
 * locked, mid-expedition) exactly like /character select. Same shape as {@link WeaponChoiceScreen}.
 */
public class CharacterSelectScreen extends Screen {
    private static final int COLUMNS = 4;
    private static final int BUTTON_W = 110;
    private static final int BUTTON_H = 24;
    private static final int GAP = 8;

    private final String currentId;
    private final List<OpenCharacterSelectPayload.Entry> characters;

    public CharacterSelectScreen(String currentId, List<OpenCharacterSelectPayload.Entry> characters) {
        super(Component.literal("Choose Your Character"));
        this.currentId = currentId;
        this.characters = characters;
    }

    @Override
    protected void init() {
        int rows = (characters.size() + COLUMNS - 1) / COLUMNS;
        int totalW = COLUMNS * BUTTON_W + (COLUMNS - 1) * GAP;
        int startX = (this.width - totalW) / 2;
        int startY = this.height / 2 - (rows * (BUTTON_H + GAP)) / 2 + 6;
        for (int i = 0; i < characters.size(); i++) {
            OpenCharacterSelectPayload.Entry e = characters.get(i);
            boolean current = e.id().equals(currentId);
            int x = startX + (i % COLUMNS) * (BUTTON_W + GAP);
            int y = startY + (i / COLUMNS) * (BUTTON_H + GAP);
            Component label = Component.literal((current ? "\u25B6 " : "") + e.name());
            Button button = Button.builder(label, b -> choose(e.id())).bounds(x, y, BUTTON_W, BUTTON_H).build();
            button.active = !current;
            button.setTooltip(Tooltip.create(Component.literal(
                    e.name() + "\n" + e.lore() + "\n\nWeapon: " + e.weapon() + "\n" + e.stats()
                            + (current ? "\n\nCurrently selected" : ""))));
            addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(this.width / 2 - 40, startY + rows * (BUTTON_H + GAP) + 6, 80, 20).build());
    }

    private void choose(String id) {
        ClientPlayNetworking.send(new ChooseCharacterPayload(id));
        this.minecraft.setScreen(null);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 62, 0xFFFFFFFF);
        guiGraphics.drawCenteredString(this.font, "Hover a character to read their story", this.width / 2,
                this.height / 2 - 50, 0xFFAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
