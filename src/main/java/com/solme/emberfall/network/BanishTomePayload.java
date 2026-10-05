package com.solme.emberfall.network;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: the player spent a banish charge to permanently remove one specific
 * offered Tome ({@code tomeId}) from their entire current run - see
 * {@link com.solme.emberfall.tome.PlayerTomeCharges}. {@code forLevel} is
 * the same staleness guard {@link ChooseTomePayload} already uses.
 */
public record BanishTomePayload(int forLevel, String tomeId) implements CustomPacketPayload {
    public static final Type<BanishTomePayload> TYPE = new Type<>(EmberfallMod.id("banish_tome"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BanishTomePayload> STREAM_CODEC = CustomPacketPayload.codec(
            (payload, buf) -> {
                buf.writeVarInt(payload.forLevel);
                buf.writeUtf(payload.tomeId);
            },
            buf -> new BanishTomePayload(buf.readVarInt(), buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
