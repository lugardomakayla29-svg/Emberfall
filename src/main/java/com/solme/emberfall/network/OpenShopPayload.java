package com.solme.emberfall.network;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: the player's current shop state - their live meta-currency balance
 * plus every weapon and upgrade entry with enough info to render and price
 * a "buy" button for each. Sent both when the shop is first opened
 * ({@code /shop}) and again after every successful purchase so the
 * already-open screen can refresh in place (updated balance, updated
 * owned/tier state) without the player having to reopen it - see
 * {@link com.solme.emberfall.progression.ShopManager}.
 *
 * Same flattened-to-primitives shape as {@link OpenWeaponChoicePayload}
 * for the same reason: the client just displays what it's told, no shared
 * registry lookup needed.
 */
public record OpenShopPayload(long balance, List<WeaponEntry> weapons, List<UpgradeEntry> upgrades)
        implements CustomPacketPayload {
    public static final Type<OpenShopPayload> TYPE = new Type<>(EmberfallMod.id("open_shop"));

    public record WeaponEntry(String id, String displayName, String description, boolean owned, long cost) {}

    public record UpgradeEntry(String id, String displayName, String description,
                                int level, int maxLevel, long nextCost) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenShopPayload> STREAM_CODEC = CustomPacketPayload.codec(
            (payload, buf) -> {
                buf.writeVarLong(payload.balance);
                buf.writeVarInt(payload.weapons.size());
                for (WeaponEntry w : payload.weapons) {
                    buf.writeUtf(w.id());
                    buf.writeUtf(w.displayName());
                    buf.writeUtf(w.description());
                    buf.writeBoolean(w.owned());
                    buf.writeVarLong(w.cost());
                }
                buf.writeVarInt(payload.upgrades.size());
                for (UpgradeEntry u : payload.upgrades) {
                    buf.writeUtf(u.id());
                    buf.writeUtf(u.displayName());
                    buf.writeUtf(u.description());
                    buf.writeVarInt(u.level());
                    buf.writeVarInt(u.maxLevel());
                    buf.writeVarLong(u.nextCost());
                }
            },
            buf -> {
                long balance = buf.readVarLong();
                int weaponCount = buf.readVarInt();
                List<WeaponEntry> weapons = new ArrayList<>(weaponCount);
                for (int i = 0; i < weaponCount; i++) {
                    weapons.add(new WeaponEntry(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readVarLong()));
                }
                int upgradeCount = buf.readVarInt();
                List<UpgradeEntry> upgrades = new ArrayList<>(upgradeCount);
                for (int i = 0; i < upgradeCount; i++) {
                    upgrades.add(new UpgradeEntry(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong()));
                }
                return new OpenShopPayload(balance, weapons, upgrades);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
