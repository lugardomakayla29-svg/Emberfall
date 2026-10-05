package com.solme.emberfall.network;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: the player's pick from an open Weapon Choice screen (weapon id, echoing back requestId/isStartingPick). */
public record ChooseWeaponPayload(boolean isStartingPick, int requestId, String weaponId) implements CustomPacketPayload {
    public static final Type<ChooseWeaponPayload> TYPE = new Type<>(EmberfallMod.id("choose_weapon"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChooseWeaponPayload> STREAM_CODEC = CustomPacketPayload.codec(
            (payload, buf) -> {
                buf.writeBoolean(payload.isStartingPick);
                buf.writeVarInt(payload.requestId);
                buf.writeUtf(payload.weaponId);
            },
            buf -> new ChooseWeaponPayload(buf.readBoolean(), buf.readVarInt(), buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
