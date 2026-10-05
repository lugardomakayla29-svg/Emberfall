package com.solme.emberfall.relic;

import net.minecraft.server.level.ServerPlayer;

/** The game-facing side of {@link RelicTempo}: reads the player's relics. Kept apart so RelicTempo stays pure and provable. */
public final class RelicTempoGame {
    private RelicTempoGame() {}

    /** Quiver of Plenty strikes for this player: 0 at once (one lookup) when they hold no relics. */
    public static int bonusStrikes(ServerPlayer player) {
        return PlayerRelics.active(player) ? RelicTempo.extraStrikes(RelicEffects.stats(player)) : 0;
    }
}
