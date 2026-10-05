package com.solme.emberfall.network;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: a Testificate's stall for THIS player. The client only displays it: relic names, colours and descriptions come from the
 * shared RelicPool by id (same jar), so the packet stays tiny. {@code open=false} closes the screen (the merchant left, or the
 * purchase went through). {@code tier} is the merchant's own rarity ordinal, shown as his banner.
 */
public record OpenMerchantPayload(boolean open, int tier, int secondsLeft, int gold, List<Item> items) implements CustomPacketPayload {
    public static final Type<OpenMerchantPayload> TYPE = new Type<>(EmberfallMod.id("open_merchant"));

    /** One shelf slot: the relic id, its price, and whether this player can afford it right now. */
    public record Item(String relicId, int price, boolean affordable) {}

    public static final OpenMerchantPayload CLOSE = new OpenMerchantPayload(false, 0, 0, 0, List.of());

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMerchantPayload> STREAM_CODEC = CustomPacketPayload.codec(
            (p, buf) -> {
                buf.writeBoolean(p.open);
                buf.writeVarInt(p.tier);
                buf.writeVarInt(p.secondsLeft);
                buf.writeVarInt(p.gold);
                buf.writeVarInt(p.items.size());
                for (Item it : p.items) {
                    buf.writeUtf(it.relicId());
                    buf.writeVarInt(it.price());
                    buf.writeBoolean(it.affordable());
                }
            },
            buf -> {
                boolean open = buf.readBoolean();
                int tier = buf.readVarInt();
                int secs = buf.readVarInt();
                int gold = buf.readVarInt();
                int n = Math.min(buf.readVarInt(), 3);
                List<Item> items = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    items.add(new Item(buf.readUtf(64), buf.readVarInt(), buf.readBoolean()));
                }
                return new OpenMerchantPayload(open, tier, secs, gold, items);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
