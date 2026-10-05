package com.solme.emberfall.network;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: opens the small shrine window. The client only displays what it is told: a title, one line of lore, and up to
 * {@value #MAX_OPTIONS} buttons, each with a label and a tooltip that spells out its exact cost and reward. The shrine
 * is named by {@code shrineType} ("challenge", "curse", "greed"); the player's choice comes back as a
 * {@link ChooseShrinePayload} and is fully re-validated on the server.
 */
public record OpenShrinePayload(String shrineType, String title, String lore, List<Option> options) implements CustomPacketPayload {
    public static final Type<OpenShrinePayload> TYPE = new Type<>(EmberfallMod.id("open_shrine"));

    /** One button. {@code enabled} false draws it greyed out (already used, or nothing to pick). */
    public record Option(int index, String label, String tooltip, boolean enabled) {}

    public static final int MAX_OPTIONS = 8;

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenShrinePayload> STREAM_CODEC = CustomPacketPayload.codec(
            (payload, buf) -> {
                buf.writeUtf(payload.shrineType, 32);
                buf.writeUtf(payload.title, 64);
                buf.writeUtf(payload.lore, 512);
                buf.writeVarInt(payload.options.size());
                for (Option o : payload.options) {
                    buf.writeVarInt(o.index());
                    buf.writeUtf(o.label(), 64);
                    buf.writeUtf(o.tooltip(), 512);
                    buf.writeBoolean(o.enabled());
                }
            },
            buf -> {
                String type = buf.readUtf(32);
                String title = buf.readUtf(64);
                String lore = buf.readUtf(512);
                int count = Math.min(buf.readVarInt(), MAX_OPTIONS);
                List<Option> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    list.add(new Option(buf.readVarInt(), buf.readUtf(64), buf.readUtf(512), buf.readBoolean()));
                }
                return new OpenShrinePayload(type, title, lore, list);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
