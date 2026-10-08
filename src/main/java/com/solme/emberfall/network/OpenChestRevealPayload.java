package com.solme.emberfall.network;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: opens the chest slot-machine reveal. The server has ALREADY decided the result ({@code ChestOpening.open} rolls the tier, then picks a relic
 * within it); this only carries that answer so the client can SHOW it: the tier label, the relic name and the seed that orders the decoys
 * ({@code ChestReveal.build(tier, item, tierPool, itemPool, seed)} is deterministic in those). {@code revealId} is what the client must quote when it
 * closes the screen. The client never decides anything, and the answer is not a secret from the player it is sent to.
 *
 * No screen consumes this yet. Tested headless, look unverified.
 */
public record OpenChestRevealPayload(int revealId, String tier, String item, long seed) implements CustomPacketPayload {
    public static final Type<OpenChestRevealPayload> TYPE = new Type<>(EmberfallMod.id("open_chest_reveal"));

    /** Longest tier label or relic name on the wire, in characters. The longest real name is far shorter; this bounds what a peer can make us read. */
    public static final int MAX_TEXT = 64;

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenChestRevealPayload> STREAM_CODEC = CustomPacketPayload.codec(
            (payload, buf) -> {
                buf.writeVarInt(payload.revealId);
                buf.writeUtf(payload.tier, MAX_TEXT);
                buf.writeUtf(payload.item, MAX_TEXT);
                buf.writeLong(payload.seed);
            },
            buf -> new OpenChestRevealPayload(buf.readVarInt(), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readLong())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
