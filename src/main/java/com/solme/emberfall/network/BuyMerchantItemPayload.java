package com.solme.emberfall.network;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: the player picked shelf slot {@code index} (0 to 2) at the Testificate they are browsing. Only the index travels: the
 * server owns the stall, re-checks distance, gold and that the relic can still be received, and ignores anything out of range.
 * {@code index = -1} means "I am leaving without buying" (closing the screen), so the Testificate can look disappointed.
 */
public record BuyMerchantItemPayload(int index) implements CustomPacketPayload {
    public static final Type<BuyMerchantItemPayload> TYPE = new Type<>(EmberfallMod.id("buy_merchant_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuyMerchantItemPayload> STREAM_CODEC = CustomPacketPayload.codec(
            (p, buf) -> buf.writeVarInt(p.index),
            buf -> new BuyMerchantItemPayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
