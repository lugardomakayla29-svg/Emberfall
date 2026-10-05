package com.solme.emberfall.network;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: the numbers behind the top-middle timer and the resource counters. Sent when a value changes
 * (checked once a second), never per tick. The client runs the clock itself between packets, so the
 * timer costs no traffic; {@code chestPrice} is the gold the next paid chest costs, shown under the level; {@code elapsedSeconds} is only a start point and a periodic correction.
 * {@link #HIDDEN} (active=false) tells the client to draw nothing (the player left the run).
 */
public record RunHudPayload(boolean active, int elapsedSeconds, int level, int xpPercent, int gold,
                            long silver, int kills, int chestPrice, int[] relics) implements CustomPacketPayload {
    public static final Type<RunHudPayload> TYPE = new Type<>(EmberfallMod.id("run_hud"));

    public static final RunHudPayload HIDDEN = new RunHudPayload(false, 0, 0, 0, 0, 0L, 0, 0, new int[0]);

    public static final StreamCodec<RegistryFriendlyByteBuf, RunHudPayload> STREAM_CODEC = CustomPacketPayload.codec(
            (p, buf) -> {
                buf.writeBoolean(p.active);
                buf.writeVarInt(p.elapsedSeconds);
                buf.writeVarInt(p.level);
                buf.writeVarInt(p.xpPercent);
                buf.writeVarInt(p.gold);
                buf.writeVarLong(p.silver);
                buf.writeVarInt(p.kills);
                buf.writeVarInt(p.chestPrice);
                // Owned relics as (pool index, stacks) pairs; the client resolves name and colour from RelicPool.
                buf.writeVarInt(p.relics.length / 2);
                for (int v : p.relics) {
                    buf.writeVarInt(v);
                }
            },
            buf -> {
                boolean active = buf.readBoolean();
                int elapsed = buf.readVarInt();
                int level = buf.readVarInt();
                int xp = buf.readVarInt();
                int gold = buf.readVarInt();
                long silver = buf.readVarLong();
                int kills = buf.readVarInt();
                int price = buf.readVarInt();
                int pairs = Math.min(buf.readVarInt(), 64);
                int[] relics = new int[pairs * 2];
                for (int i = 0; i < relics.length; i++) {
                    relics[i] = buf.readVarInt();
                }
                return new RunHudPayload(active, elapsed, level, xp, gold, silver, kills, price, relics);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
