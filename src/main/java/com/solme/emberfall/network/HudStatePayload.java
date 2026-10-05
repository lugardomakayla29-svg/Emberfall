package com.solme.emberfall.network;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: everything the always-on bottom-left panel draws. Sent only when the content changes
 * (see {@code HudSync}), never per tick. An empty payload with both slot counts 0 tells the client
 * to hide the panel (the player left the run).
 */
public record HudStatePayload(int weaponSlots, int tomeSlots, List<WeaponEntry> weapons, List<TomeEntry> tomes)
        implements CustomPacketPayload {
    public static final Type<HudStatePayload> TYPE = new Type<>(EmberfallMod.id("hud_state"));

    /**
     * One carried weapon: registry id (also the icon key), its display name, its level (1 to 10) and its ultimate meter in
     * {@link #METER_STEPS} steps (0 = empty, METER_STEPS = full). The meter is quantised so it changes the payload only when the bar visibly
     * moves, which keeps the "send only on change" rule meaningful during a fight.
     */
    public record WeaponEntry(String id, String name, int level, int meterStep) {}

    /** The meter bar is drawn in this many steps (5% each). */
    public static final int METER_STEPS = 20;

    /** One held tome: id, display name, current stacks and the cap. */
    public record TomeEntry(String id, String name, int stacks, int maxStacks) {}

    public static final HudStatePayload HIDDEN = new HudStatePayload(0, 0, List.of(), List.of());

    public static final StreamCodec<RegistryFriendlyByteBuf, HudStatePayload> STREAM_CODEC = CustomPacketPayload.codec(
            (payload, buf) -> {
                buf.writeVarInt(payload.weaponSlots);
                buf.writeVarInt(payload.tomeSlots);
                buf.writeVarInt(payload.weapons.size());
                for (WeaponEntry w : payload.weapons) {
                    buf.writeUtf(w.id());
                    buf.writeUtf(w.name());
                    buf.writeVarInt(w.level());
                    buf.writeVarInt(w.meterStep());
                }
                buf.writeVarInt(payload.tomes.size());
                for (TomeEntry t : payload.tomes) {
                    buf.writeUtf(t.id());
                    buf.writeUtf(t.name());
                    buf.writeVarInt(t.stacks());
                    buf.writeVarInt(t.maxStacks());
                }
            },
            buf -> {
                int weaponSlots = buf.readVarInt();
                int tomeSlots = buf.readVarInt();
                int wn = buf.readVarInt();
                List<WeaponEntry> weapons = new ArrayList<>(wn);
                for (int i = 0; i < wn; i++) {
                    weapons.add(new WeaponEntry(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt()));
                }
                int tn = buf.readVarInt();
                List<TomeEntry> tomes = new ArrayList<>(tn);
                for (int i = 0; i < tn; i++) {
                    tomes.add(new TomeEntry(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt()));
                }
                return new HudStatePayload(weaponSlots, tomeSlots, weapons, tomes);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
