package com.solme.emberfall.network;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: server offers the player a weapon pick - either the pre-run
 * starting-weapon choice (isStartingPick=true, requestId=0) or a mid-run
 * weapon-swap offer on a milestone level-up (isStartingPick=false,
 * requestId=that level). Mirrors {@link OpenTomeChoicePayload}'s
 * flattened-to-strings shape for the same reason: the client just displays
 * what it's told, no shared registry lookup needed.
 */
public record OpenWeaponChoicePayload(boolean isStartingPick, int requestId, List<OfferInfo> offers)
        implements CustomPacketPayload {
    public static final Type<OpenWeaponChoicePayload> TYPE = new Type<>(EmberfallMod.id("open_weapon_choice"));

    public record OfferInfo(String id, String displayName, String description, String moveset) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenWeaponChoicePayload> STREAM_CODEC = CustomPacketPayload.codec(
            (payload, buf) -> {
                buf.writeBoolean(payload.isStartingPick);
                buf.writeVarInt(payload.requestId);
                buf.writeVarInt(payload.offers.size());
                for (OfferInfo offer : payload.offers) {
                    buf.writeUtf(offer.id());
                    buf.writeUtf(offer.displayName());
                    buf.writeUtf(offer.description());
                    buf.writeUtf(offer.moveset());
                }
            },
            buf -> {
                boolean isStartingPick = buf.readBoolean();
                int requestId = buf.readVarInt();
                int count = buf.readVarInt();
                List<OfferInfo> offers = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    offers.add(new OfferInfo(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf()));
                }
                return new OpenWeaponChoicePayload(isStartingPick, requestId, offers);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
