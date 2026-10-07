package com.solme.emberfall.wave;

import com.solme.emberfall.world.ArenaInstance;
import com.solme.emberfall.world.RunManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The sound the party hears when the first boss falls and the horde turns worse ({@link AfterBossOne}). Vanilla sounds only, no
 * entities, no particles: two calls per player, once per run. Deliberately NOT the Final Swarm's scream (ravager roar + wither
 * spawn, {@link SwarmWrath}) so the two milestones never sound alike.
 */
final class AfterBossOneSound {
    private AfterBossOneSound() {}

    /**
     * Plays the sound to every living, non-spectating player in this slot.
     *
     * @return how many players it was played to (written to the AFTERBOSS1 log line so a test can prove it fired)
     */
    static int play(ServerLevel level, ArenaInstance arena) {
        int heard = 0;
        for (ServerPlayer p : level.players()) {
            Integer slot = RunManager.slotOf(p);
            if (slot == null || slot != arena.slot() || !p.isAlive() || p.isSpectator()) {
                continue;
            }
            level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_AGITATED, SoundSource.HOSTILE, 1.4F, 0.7F);
            level.playSound(null, p.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.8F, 0.5F);
            heard++;
        }
        return heard;
    }
}
