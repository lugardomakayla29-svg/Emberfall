package com.solme.emberfall.network;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: server offers the player 3 Tome choices on level-up (design doc
 * 7.4). Each offer is flattened to plain strings so the client needs no
 * shared Tome registry lookup to render the cards - it just displays what
 * it's told.
 *
 * {@code rerollsRemaining}/{@code banishesRemaining} are this player's
 * current per-run charge counts (see
 * {@link com.solme.emberfall.tome.PlayerTomeCharges}) - sent fresh every
 * time this payload goes out (initial open, and again after every reroll
 * or banish) so the client screen always renders accurate, live counts
 * without a separate round-trip.
 */
public record OpenTomeChoicePayload(int newLevel, List<OfferInfo> offers, int rerollsRemaining, int banishesRemaining,
                                    int goldPrice, int gold)
        implements CustomPacketPayload {
    public static final Type<OpenTomeChoicePayload> TYPE = new Type<>(EmberfallMod.id("open_tome_choice"));

    public record OfferInfo(String id, String displayName, String description, String category, String tags) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTomeChoicePayload> STREAM_CODEC = CustomPacketPayload.codec(
            (payload, buf) -> {
                buf.writeVarInt(payload.newLevel);
                buf.writeVarInt(payload.offers.size());
                for (OfferInfo offer : payload.offers) {
                    buf.writeUtf(offer.id());
                    buf.writeUtf(offer.displayName());
                    buf.writeUtf(offer.description());
                    buf.writeUtf(offer.category());
                    buf.writeUtf(offer.tags());
                }
                buf.writeVarInt(payload.rerollsRemaining);
                buf.writeVarInt(payload.banishesRemaining);
                buf.writeVarInt(payload.goldPrice);
                buf.writeVarInt(payload.gold);
            },
            buf -> {
                int newLevel = buf.readVarInt();
                int count = buf.readVarInt();
                List<OfferInfo> offers = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    offers.add(new OfferInfo(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf()));
                }
                int rerollsRemaining = buf.readVarInt();
                int banishesRemaining = buf.readVarInt();
                int goldPrice = buf.readVarInt();
                int gold = buf.readVarInt();
                return new OpenTomeChoicePayload(newLevel, offers, rerollsRemaining, banishesRemaining, goldPrice, gold);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
