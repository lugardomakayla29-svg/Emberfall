package com.solme.emberfall.network;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: the player closed the reveal. It carries ONLY the id the server issued, never a tier or an item, so a client cannot claim a different prize. The
 * server honours it only if that player has that exact reveal open ({@code ChestRevealSessions.close}); anything else is ignored.
 *
 * No screen sends this yet. Tested headless, look unverified.
 */
public record CloseChestRevealPayload(int revealId) implements CustomPacketPayload {
    public static final Type<CloseChestRevealPayload> TYPE = new Type<>(EmberfallMod.id("close_chest_reveal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CloseChestRevealPayload> STREAM_CODEC = CustomPacketPayload.codec(
            (payload, buf) -> buf.writeVarInt(payload.revealId),
            buf -> new CloseChestRevealPayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
