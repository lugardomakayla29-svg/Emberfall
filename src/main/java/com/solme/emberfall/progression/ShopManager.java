package com.solme.emberfall.progression;

import com.solme.emberfall.item.WeaponPool;
import com.solme.emberfall.item.WeaponType;
import com.solme.emberfall.network.BuyShopItemPayload;
import com.solme.emberfall.network.OpenShopPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Server side of the currency shop (design doc: weapons and permanent
 * stat upgrades, both "buy them with the meta-currency shop"). Unlike
 * {@link com.solme.emberfall.item.WeaponChoiceManager} / {@link com.solme.emberfall.tome.TomeChoiceManager}'s
 * one-shot "offer N choices, first pick wins, grace-window auto-resolves"
 * flow, browsing a shop isn't a single forced decision - a player should
 * be able to open it, buy zero or several things in any order, and close
 * whenever, so there's deliberately no pending-choice/grace-window
 * bookkeeping here at all: every buy is a plain stateless request/response
 * against the player's persistent currency and unlock/upgrade records.
 *
 * Reachable both from the overworld hub and mid-run (design decision:
 * unlike the mid-run weapon-swap offer, opening the shop does NOT pause
 * the Wave Director - it's a currency-spending screen, not a mandatory
 * decision, and not pausing sidesteps an entire class of "forgot to
 * resume on disconnect" bugs the same way this session's other pause
 * points already had to be hardened against).
 *
 * "upgrade" purchases cover both {@link UpgradePool} (live attribute
 * bonuses) and {@link ChargeUpgradePool} (per-run Tome reroll/banish
 * charges - see {@link com.solme.emberfall.tome.PlayerTomeCharges}) - both
 * share the exact same {id -> level} storage and shop-card shape, so they
 * render as one combined list and only the buy-side effect differs.
 */
public final class ShopManager {
    private ShopManager() {}

    public static void open(ServerPlayer player) {
        send(player);
    }

    public static void onBuyReceived(ServerPlayer player, BuyShopItemPayload payload) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        switch (payload.kind()) {
            case "weapon" -> buyWeapon(server, player, payload.itemId());
            case "upgrade" -> buyUpgrade(server, player, payload.itemId());
            default -> { /* unknown kind - ignore */ }
        }
        send(player); // always refresh the open screen, success or failure, so balance/state stays truthful
    }

    private static void buyWeapon(MinecraftServer server, ServerPlayer player, String weaponId) {
        WeaponType weapon = WeaponPool.byId(weaponId);
        if (weapon == null) {
            return;
        }
        WeaponUnlocks unlocks = WeaponUnlocks.get(server);
        if (unlocks.isUnlocked(player.getUUID(), weaponId)) {
            player.sendSystemMessage(Component.literal("§7You already own " + weapon.displayName() + "."));
            return;
        }
        MetaProgressionData currency = MetaProgressionData.get(server);
        if (currency.getBalance(player.getUUID()) < weapon.unlockCost()) {
            player.sendSystemMessage(Component.literal("§cNot enough Silver for " + weapon.displayName() + " §7("
                    + currency.getBalance(player.getUUID()) + " / " + weapon.unlockCost() + ")."));
            return;
        }
        currency.addCurrency(player.getUUID(), -weapon.unlockCost());
        unlocks.unlock(player.getUUID(), weaponId);
        player.sendSystemMessage(Component.literal("§a+ Unlocked " + weapon.displayName() + " §7(-" + weapon.unlockCost() + " Silver)"));
    }

    /** Reserved upgrade ids for the two slot purchases. They ride the existing upgrade cards. */
    public static final String SLOT_WEAPON_ID = "slot_weapon";
    public static final String SLOT_TOME_ID = "slot_tome";

    private static void buyUpgrade(MinecraftServer server, ServerPlayer player, String upgradeId) {
        if (SLOT_WEAPON_ID.equals(upgradeId)) {
            buySlot(server, player, SlotUnlocks.Kind.WEAPON, "Weapon Slot");
            return;
        }
        if (SLOT_TOME_ID.equals(upgradeId)) {
            buySlot(server, player, SlotUnlocks.Kind.TOME, "Tome Slot");
            return;
        }
        UpgradeType upgrade = UpgradePool.byId(upgradeId);
        if (upgrade != null) {
            buyStatUpgrade(server, player, upgrade);
            return;
        }
        ChargeUpgradeType chargeUpgrade = ChargeUpgradePool.byId(upgradeId);
        if (chargeUpgrade != null) {
            buyChargeUpgrade(server, player, chargeUpgrade);
            return;
        }
        player.sendSystemMessage(Component.literal("§cThat item is not for sale."));
    }

    private static void buySlot(MinecraftServer server, ServerPlayer player, SlotUnlocks.Kind kind, String name) {
        SlotUnlocks slots = SlotUnlocks.get(server);
        long paid = slots.buyNext(server, player.getUUID(), kind);
        if (paid == -1) {
            player.sendSystemMessage(Component.literal("§7All " + name + "s are already unlocked."));
            return;
        }
        if (paid == -2) {
            player.sendSystemMessage(Component.literal("§cNot enough Silver for another " + name + " §7("
                    + MetaProgressionData.get(server).getBalance(player.getUUID()) + " / "
                    + slots.nextPrice(player.getUUID(), kind) + ")."));
            return;
        }
        int now = slots.slots(player.getUUID(), kind);
        player.sendSystemMessage(Component.literal("§a+ " + name + " §7now " + now + "/" + SlotUnlocks.MAX_SLOTS
                + " (-" + paid + " Silver)."));
    }

    private static void buyStatUpgrade(MinecraftServer server, ServerPlayer player, UpgradeType upgrade) {
        PlayerUpgrades owned = PlayerUpgrades.get(server);
        int currentLevel = owned.getLevel(player.getUUID(), upgrade.id());
        if (currentLevel >= upgrade.maxLevel()) {
            player.sendSystemMessage(Component.literal("§7" + upgrade.displayName() + " is already at max level."));
            return;
        }
        long cost = upgrade.costForNextLevel(currentLevel);
        MetaProgressionData currency = MetaProgressionData.get(server);
        if (currency.getBalance(player.getUUID()) < cost) {
            player.sendSystemMessage(Component.literal("§cNot enough Silver for " + upgrade.displayName() + " §7("
                    + currency.getBalance(player.getUUID()) + " / " + cost + ")."));
            return;
        }
        currency.addCurrency(player.getUUID(), -cost);
        int newLevel = owned.levelUp(player.getUUID(), upgrade.id());
        UpgradeEffects.apply(player); // take effect immediately, mid-run or not
        player.sendSystemMessage(Component.literal(
                "§a+ " + upgrade.displayName() + " §7now level " + newLevel + "/" + upgrade.maxLevel() + " (-" + cost + " Silver)"));
    }

    private static void buyChargeUpgrade(MinecraftServer server, ServerPlayer player, ChargeUpgradeType upgrade) {
        PlayerUpgrades owned = PlayerUpgrades.get(server);
        int currentLevel = owned.getLevel(player.getUUID(), upgrade.id());
        if (currentLevel >= upgrade.maxLevel()) {
            player.sendSystemMessage(Component.literal("§7" + upgrade.displayName() + " is already at max level."));
            return;
        }
        long cost = upgrade.costForNextLevel(currentLevel);
        MetaProgressionData currency = MetaProgressionData.get(server);
        if (currency.getBalance(player.getUUID()) < cost) {
            player.sendSystemMessage(Component.literal("§cNot enough Silver for " + upgrade.displayName() + " §7("
                    + currency.getBalance(player.getUUID()) + " / " + cost + ")."));
            return;
        }
        currency.addCurrency(player.getUUID(), -cost);
        int newLevel = owned.levelUp(player.getUUID(), upgrade.id());
        // No live effect to apply - this only changes how many charges PlayerTomeCharges.reset()
        // seeds at the START of the player's NEXT run, same as any other permanent meta-progression buy.
        player.sendSystemMessage(Component.literal(
                "§a+ " + upgrade.displayName() + " §7now level " + newLevel + "/" + upgrade.maxLevel() + " (-" + cost + " Silver)"));
    }

    /** A slot purchase shown as an upgrade card: level is slots owned beyond the free first one. */
    private static OpenShopPayload.UpgradeEntry slotCard(SlotUnlocks slots, ServerPlayer player, SlotUnlocks.Kind kind,
                                                         String id, String name, String description) {
        int owned = slots.slots(player.getUUID(), kind);
        return new OpenShopPayload.UpgradeEntry(id, name, description,
                owned - SlotUnlocks.BASE_SLOTS, SlotUnlocks.MAX_SLOTS - SlotUnlocks.BASE_SLOTS,
                slots.nextPrice(player.getUUID(), kind));
    }

    private static void send(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        WeaponUnlocks unlocks = WeaponUnlocks.get(server);
        MetaProgressionData currency = MetaProgressionData.get(server);
        PlayerUpgrades upgrades = PlayerUpgrades.get(server);

        List<OpenShopPayload.WeaponEntry> weaponEntries = new ArrayList<>();
        for (WeaponType weapon : WeaponPool.ALL) {
            boolean owned = unlocks.isUnlocked(player.getUUID(), weapon.id());
            weaponEntries.add(new OpenShopPayload.WeaponEntry(
                    weapon.id(), weapon.displayName(), weapon.description(), owned, weapon.unlockCost()));
        }

        List<OpenShopPayload.UpgradeEntry> upgradeEntries = new ArrayList<>();
        SlotUnlocks slotUnlocks = SlotUnlocks.get(server);
        upgradeEntries.add(slotCard(slotUnlocks, player, SlotUnlocks.Kind.WEAPON, SLOT_WEAPON_ID, "Weapon Slot",
                "Carry another weapon. Each one fires on its own timing."));
        upgradeEntries.add(slotCard(slotUnlocks, player, SlotUnlocks.Kind.TOME, SLOT_TOME_ID, "Tome Slot",
                "Hold another Tome at once."));
        for (UpgradeType upgrade : UpgradePool.ALL) {
            int level = upgrades.getLevel(player.getUUID(), upgrade.id());
            long nextCost = level >= upgrade.maxLevel() ? -1 : upgrade.costForNextLevel(level);
            upgradeEntries.add(new OpenShopPayload.UpgradeEntry(
                    upgrade.id(), upgrade.displayName(), upgrade.description(), level, upgrade.maxLevel(), nextCost));
        }
        for (ChargeUpgradeType upgrade : ChargeUpgradePool.ALL) {
            int level = upgrades.getLevel(player.getUUID(), upgrade.id());
            long nextCost = level >= upgrade.maxLevel() ? -1 : upgrade.costForNextLevel(level);
            upgradeEntries.add(new OpenShopPayload.UpgradeEntry(
                    upgrade.id(), upgrade.displayName(), upgrade.description(), level, upgrade.maxLevel(), nextCost));
        }

        ServerPlayNetworking.send(player, new OpenShopPayload(
                currency.getBalance(player.getUUID()), weaponEntries, upgradeEntries));
    }
}
