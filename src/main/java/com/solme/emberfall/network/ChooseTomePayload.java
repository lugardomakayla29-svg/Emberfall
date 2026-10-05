package com.solme.emberfall.network;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: the player's pick from an open Tome Choice screen (offer index
 * 0-2, for the given level). {@code choiceIndex == -1} is a deliberate
 * "Skip" sentinel: the player declined all 3 offers outright (free,
 * unlike Reroll/Banish - see {@link com.solme.emberfall.tome.PlayerTomeCharges} -
 * closing the screen without picking costs nothing but also grants
 * nothing).
 */
public record ChooseTomePayload(int forLevel, int choiceIndex) implements CustomPacketPayload {
    public static final Type<ChooseTomePayload> TYPE = new Type<>(EmberfallMod.id("choose_tome"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChooseTomePayload> STREAM_CODEC = CustomPacketPayload.codec(
            (payload, buf) -> {
                buf.writeVarInt(payload.forLevel);
                buf.writeVarInt(payload.choiceIndex);
            },
            buf -> new ChooseTomePayload(buf.readVarInt(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
