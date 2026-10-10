package com.solme.emberfall.client;

import com.solme.emberfall.network.BuyShopItemPayload;
import com.solme.emberfall.network.OpenShopPayload;
import com.solme.emberfall.relic.MenuGlyphs;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The currency shop's browse-and-buy screen (design decision: weapons and
 * permanent stat upgrades, reachable from the hub or mid-run - see
 * {@link com.solme.emberfall.progression.ShopManager}).
 *
 * Unlike {@link WeaponChoiceScreen}/{@link TomeChoiceScreen} (one forced
 * pick, screen closes the instant you choose), this screen stays open
 * across any number of purchases: every row's button IS the buy action,
 * and the server always answers a buy with a fresh {@link OpenShopPayload}
 * that {@link EmberfallModClient} feeds straight back into a brand new
 * ShopScreen instance, replacing this one in place - so a successful buy
 * (or a rejected one, e.g. insufficient funds) simply redraws the same
 * screen with up-to-date numbers instead of closing anything.
 *
 * NOTE on testing: same honest gap as TomeChoiceScreen/WeaponChoiceScreen -
 * no connected graphical client in this sandbox to visually verify against.
 */
public class ShopScreen extends Screen {
    private static final int ROW_WIDTH = 280;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_SPACING = 4;

    private final long balance;
    /** Where each row button is and which kind it is, so render() can draw its glyph after the buttons. */
    private final java.util.List<Button> rowButtons = new java.util.ArrayList<>();
    private final java.util.List<String> rowKinds = new java.util.ArrayList<>();
    private final java.util.List<OpenShopPayload.WeaponEntry> weapons;
    private final java.util.List<OpenShopPayload.UpgradeEntry> upgrades;

    public ShopScreen(long balance, java.util.List<OpenShopPayload.WeaponEntry> weapons,
                       java.util.List<OpenShopPayload.UpgradeEntry> upgrades) {
        super(Component.literal("Emberfall Shop"));
        this.balance = balance;
        this.weapons = weapons;
        this.upgrades = upgrades;
    }

    @Override
    protected void init() {
        int rowCount = weapons.size() + upgrades.size();
        int blockHeight = rowCount * (ROW_HEIGHT + ROW_SPACING) + 30; // +30 for the trailing Close button
        int x = (this.width - ROW_WIDTH) / 2;
        int y = Math.max(45, (this.height - blockHeight) / 2 + 20);

        for (OpenShopPayload.WeaponEntry weapon : weapons) {
            String label = weapon.owned()
                    ? weapon.displayName() + " §7(owned)"
                    : weapon.displayName() + " §7- " + weapon.cost() + " currency";
            Button button = Button.builder(Component.literal(label), b -> buy("weapon", weapon.id()))
                    .bounds(x, y, ROW_WIDTH, ROW_HEIGHT)
                    .build();
            button.active = !weapon.owned() && balance >= weapon.cost();
            addRenderableWidget(button);
            rowButtons.add(button);
            rowKinds.add("weapon");
            y += ROW_HEIGHT + ROW_SPACING;
        }

        for (OpenShopPayload.UpgradeEntry upgrade : upgrades) {
            boolean maxed = upgrade.level() >= upgrade.maxLevel();
            String label = maxed
                    ? upgrade.displayName() + " §7(" + upgrade.level() + "/" + upgrade.maxLevel() + " - maxed)"
                    : upgrade.displayName() + " §7(" + upgrade.level() + "/" + upgrade.maxLevel() + ") - "
                            + upgrade.nextCost() + " currency";
            Button button = Button.builder(Component.literal(label), b -> buy("upgrade", upgrade.id()))
                    .bounds(x, y, ROW_WIDTH, ROW_HEIGHT)
                    .build();
            button.active = !maxed && balance >= upgrade.nextCost();
            addRenderableWidget(button);
            rowButtons.add(button);
            rowKinds.add("upgrade");
            y += ROW_HEIGHT + ROW_SPACING;
        }

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.minecraft.setScreen(null))
                .bounds(x + ROW_WIDTH / 2 - 40, y + 6, 80, ROW_HEIGHT)
                .build());
    }

    private void buy(String kind, String id) {
        ClientPlayNetworking.send(new BuyShopItemPayload(kind, id));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        for (int i = 0; i < rowButtons.size(); i++) {
            Button b = rowButtons.get(i);
            MenuGlyphDraw.onButton(guiGraphics, this.font, MenuGlyphs.glyph(MenuGlyphs.SHOP, rowKinds.get(i)), b.getX(), b.getY(), b.getHeight(), b.active, 0xFFD5DCE4);
        }
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        MenuGlyphDraw.centredWithGlyph(guiGraphics, this.font, MenuGlyphs.glyph(MenuGlyphs.SHOP, "silver"), "Balance: " + balance + " currency", this.width / 2, 32, 0xFFD5DCE4, 0xFFFFD700);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
