package com.solme.emberfall.network;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: the player spent a reroll charge on their currently-open Tome
 * Choice screen (design goal: avoid forcing a pick from a bad 3-offer
 * roll when the player has meta-progression charges banked - see
 * {@link com.solme.emberfall.tome.PlayerTomeCharges}). {@code forLevel} is
 * the same staleness guard {@link ChooseTomePayload} already uses.
 */
public record RerollTomePayload(int forLevel) implements CustomPacketPayload {
    public static final Type<RerollTomePayload> TYPE = new Type<>(EmberfallMod.id("reroll_tome"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RerollTomePayload> STREAM_CODEC = CustomPacketPayload.codec(
            (payload, buf) -> buf.writeVarInt(payload.forLevel),
            buf -> new RerollTomePayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
