package com.solme.emberfall.network;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: the player clicked "buy" on one shop row. {@code kind} is
 * {@code "weapon"} or {@code "upgrade"} (plain string rather than a second
 * enum-shaped payload type - the two purchase kinds share every other
 * field and the server-side handling only really differs in which catalog
 * and persistence store it touches - see
 * {@link com.solme.emberfall.progression.ShopManager#onBuyReceived}).
 */
public record BuyShopItemPayload(String kind, String itemId) implements CustomPacketPayload {
    public static final Type<BuyShopItemPayload> TYPE = new Type<>(EmberfallMod.id("buy_shop_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuyShopItemPayload> STREAM_CODEC = CustomPacketPayload.codec(
            (payload, buf) -> {
                buf.writeUtf(payload.kind);
                buf.writeUtf(payload.itemId);
            },
            buf -> new BuyShopItemPayload(buf.readUtf(), buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
