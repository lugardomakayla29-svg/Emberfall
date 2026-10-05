package com.solme.emberfall.network;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: the run is over. The server never lets the player actually die; it ends the run and sends this so the
 * client can show what the run earned. {@code cause} is a short code the client turns into a title.
 */
public record RunEndPayload(String cause, int seconds, int level, int kills, int gold, boolean hydraDown,
                            boolean devourerDown, long silverEarned, long silverTotal) implements CustomPacketPayload {
    public static final Type<RunEndPayload> TYPE = new Type<>(EmberfallMod.id("run_end"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RunEndPayload> STREAM_CODEC = CustomPacketPayload.codec(
            (p, buf) -> {
                buf.writeUtf(p.cause);
                buf.writeVarInt(p.seconds);
                buf.writeVarInt(p.level);
                buf.writeVarInt(p.kills);
                buf.writeVarInt(p.gold);
                buf.writeBoolean(p.hydraDown);
                buf.writeBoolean(p.devourerDown);
                buf.writeVarLong(p.silverEarned);
                buf.writeVarLong(p.silverTotal);
            },
            buf -> new RunEndPayload(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readBoolean(), buf.readVarLong(), buf.readVarLong())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
