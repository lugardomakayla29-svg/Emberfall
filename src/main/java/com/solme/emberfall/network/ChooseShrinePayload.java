package com.solme.emberfall.network;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: the shrine type and option index the player clicked. Never trusted: the server re-checks range, use and index. */
public record ChooseShrinePayload(String shrineType, int option) implements CustomPacketPayload {
    public static final Type<ChooseShrinePayload> TYPE = new Type<>(EmberfallMod.id("choose_shrine"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChooseShrinePayload> STREAM_CODEC = CustomPacketPayload.codec(
            (payload, buf) -> {
                buf.writeUtf(payload.shrineType, 32);
                buf.writeVarInt(payload.option);
            },
            buf -> new ChooseShrinePayload(buf.readUtf(32), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
