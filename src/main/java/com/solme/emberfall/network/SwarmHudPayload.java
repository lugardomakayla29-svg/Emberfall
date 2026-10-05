package com.solme.emberfall.network;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: the Final Swarm silver multiplier. {@code tenths} is the multiplier times ten (1 = 0.1x, 50 = 5.0x); 0 hides the
 * indicator. {@code portal} is true while the escape portal is open. Sent only when a value changes (checked once a
 * second), so a quiet swarm costs no traffic. Kept apart from the run HUD packet so that one's layout never changes.
 */
public record SwarmHudPayload(int tenths, boolean portal) implements CustomPacketPayload {
    public static final Type<SwarmHudPayload> TYPE = new Type<>(EmberfallMod.id("swarm_hud"));

    public static final SwarmHudPayload HIDDEN = new SwarmHudPayload(0, false);

    public static final StreamCodec<RegistryFriendlyByteBuf, SwarmHudPayload> STREAM_CODEC = CustomPacketPayload.codec(
            (p, buf) -> {
                buf.writeVarInt(p.tenths);
                buf.writeBoolean(p.portal);
            },
            buf -> new SwarmHudPayload(buf.readVarInt(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
