package com.solme.emberfall.network;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: opens the character-selection screen. Everything is flattened to strings and numbers, same as
 * {@link OpenWeaponChoicePayload}: the client only displays what it is told. Carries the id of the character the
 * player has selected right now so the screen can mark it.
 */
public record OpenCharacterSelectPayload(String currentId, List<Entry> characters, int selectId, int secondsLeft) implements CustomPacketPayload {
    /** {@code selectId} of the old Character Table flow: no phase, no countdown, the screen behaves exactly as before. */
    public static final int NO_PHASE = 0;

    /** The table flow: no Rift phase behind it. */
    public OpenCharacterSelectPayload(String currentId, List<Entry> characters) {
        this(currentId, characters, NO_PHASE, 0);
    }

    public static final Type<OpenCharacterSelectPayload> TYPE = new Type<>(EmberfallMod.id("open_character_select"));

    /** One selectable character. {@code stats} is one ready-to-print line (health, speed, passive). */
    public record Entry(String id, String name, String lore, String weapon, String stats) {}

    /** Hard cap on how many entries a client will accept, so a bad packet cannot allocate without limit. */
    private static final int MAX_ENTRIES = 32;

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCharacterSelectPayload> STREAM_CODEC = CustomPacketPayload.codec(
            (payload, buf) -> {
                buf.writeUtf(payload.currentId);
                buf.writeVarInt(payload.selectId);
                buf.writeVarInt(payload.secondsLeft);
                buf.writeVarInt(payload.characters.size());
                for (Entry e : payload.characters) {
                    buf.writeUtf(e.id());
                    buf.writeUtf(e.name());
                    buf.writeUtf(e.lore(), 512);
                    buf.writeUtf(e.weapon());
                    buf.writeUtf(e.stats(), 512);
                }
            },
            buf -> {
                String current = buf.readUtf();
                int selectId = buf.readVarInt();
                int secondsLeft = buf.readVarInt();
                int count = Math.min(buf.readVarInt(), MAX_ENTRIES);
                List<Entry> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    list.add(new Entry(buf.readUtf(), buf.readUtf(), buf.readUtf(512), buf.readUtf(), buf.readUtf(512)));
                }
                return new OpenCharacterSelectPayload(current, list, selectId, secondsLeft);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
