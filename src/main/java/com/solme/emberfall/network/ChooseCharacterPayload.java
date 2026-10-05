package com.solme.emberfall.network;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: the character id the player clicked. Validated on the server by CharacterCommand.trySelect, never trusted. */
public record ChooseCharacterPayload(String characterId) implements CustomPacketPayload {
    public static final Type<ChooseCharacterPayload> TYPE = new Type<>(EmberfallMod.id("choose_character"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChooseCharacterPayload> STREAM_CODEC = CustomPacketPayload.codec(
            (payload, buf) -> buf.writeUtf(payload.characterId, 64),
            buf -> new ChooseCharacterPayload(buf.readUtf(64))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
