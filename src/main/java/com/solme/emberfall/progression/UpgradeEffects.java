package com.solme.emberfall.progression;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Applies every permanent shop upgrade a player owns as a real attribute
 * modifier on their entity. Unlike {@link com.solme.emberfall.tome.SynergyEffects}'s
 * one-shot Momentum grant (applied once, never revisited), this always
 * fully re-derives each upgrade's modifier from {@link PlayerUpgrades}'
 * current level and replaces whatever was there before - safe to call any
 * number of times (login, every run join, right after a purchase) and
 * always ends up correct even if a level changed since the last call.
 *
 * Each upgrade gets one fixed per-player {@link Identifier} (not
 * per-level like Momentum's threshold-specific id) so re-applying after a
 * level-up genuinely replaces the old value instead of stacking a second
 * copy alongside it.
 */
public final class UpgradeEffects {
    private UpgradeEffects() {}

    /** Re-applies every owned upgrade's current level to this player's live attributes. */
    public static void apply(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        PlayerUpgrades upgrades = PlayerUpgrades.get(server);
        for (UpgradeType upgrade : UpgradePool.ALL) {
            int level = upgrades.getLevel(player.getUUID(), upgrade.id());
            applyOne(player, upgrade, level);
        }
    }

    private static void applyOne(ServerPlayer player, UpgradeType upgrade, int level) {
        AttributeInstance instance = player.getAttribute(upgrade.attribute());
        if (instance == null) {
            return;
        }
        Identifier modifierId = Identifier.fromNamespaceAndPath(
                EmberfallMod.MOD_ID, "shop_upgrade_" + upgrade.id() + "_" + player.getUUID());
        instance.removeModifier(modifierId);
        if (level <= 0) {
            return; // nothing bought yet - modifier stays removed
        }
        instance.addPermanentModifier(new AttributeModifier(
                modifierId, upgrade.totalValueAtLevel(level), upgrade.operation()));
    }
}
