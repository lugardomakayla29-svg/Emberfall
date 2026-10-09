package com.solme.emberfall.network;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: the player closed the Character Select screen the Rift opened (Close button or Escape) without picking. It carries ONLY the id the
 * server issued when it opened the screen, so a client cannot speak for another player or for a phase that is not open. The server honours it
 * only if that player is pending in the phase with that exact id ({@code RiftGate.onSelectClosed}); anything else is ignored. Closing means
 * "keep the character I already have": the run reads each player's stored choice when it places them.
 */
public record CloseCharacterSelectPayload(int selectId) implements CustomPacketPayload {
    public static final Type<CloseCharacterSelectPayload> TYPE = new Type<>(EmberfallMod.id("close_character_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CloseCharacterSelectPayload> STREAM_CODEC = CustomPacketPayload.codec(
            (payload, buf) -> buf.writeVarInt(payload.selectId),
            buf -> new CloseCharacterSelectPayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
