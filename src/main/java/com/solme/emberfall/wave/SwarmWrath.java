package com.solme.emberfall.wave;

import com.solme.emberfall.world.ArenaInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * The Wrath of a Final Swarm that has reached its cap: a player still fighting at 5.0x is screamed at, terrified, thrown about
 * and set alight. Everything here is a vanilla effect, sound, particle or shove: ZERO entities, bounded cost (a handful of
 * calls per player per second). {@link #due} is the pure cadence, so what fires on which tick is provable without a server.
 */
public final class SwarmWrath {
    private SwarmWrath() {}

    /** Called once a second by the director (ticks are multiples of 20). Applies every beat that is due to the slot's live players. */
    static void tick(ServerLevel level, ArenaInstance arena, int tenths, long tick) {
        if (!FinalSwarm.wrath(tenths)) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            Integer slot = com.solme.emberfall.world.RunManager.slotOf(p);
            if (slot == null || slot != arena.slot() || !p.isAlive() || p.isSpectator()) {
                continue;
            }
            if (WrathCadence.due(WrathCadence.Beat.SCREAM, tenths, tick)) {
                level.playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.6F, 0.6F);
                level.playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.7F, 1.6F);
                level.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0, 0, 0, 0);
            }
            if (WrathCadence.due(WrathCadence.Beat.TERRIFY, tenths, tick)) {
                p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0, false, false));
            }
            if (WrathCadence.due(WrathCadence.Beat.THRASH, tenths, tick)) {
                double a = level.getRandom().nextDouble() * Math.PI * 2;
                p.push(new Vec3(Math.cos(a) * WrathCadence.THRASH_SHOVE, 0.15, Math.sin(a) * WrathCadence.THRASH_SHOVE));
                p.hurtMarked = true;
            }
            if (WrathCadence.due(WrathCadence.Beat.COMBUST, tenths, tick)) {
                p.igniteForSeconds(WrathCadence.FIRE_SECONDS);
                level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.4, 0.8, 0.4, 0.03);
            }
        }
    }
}
