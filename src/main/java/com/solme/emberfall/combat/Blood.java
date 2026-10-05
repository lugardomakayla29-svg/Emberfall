package com.solme.emberfall.combat;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.phys.Vec3;

/**
 * Hit blood. One call from {@link Fx#hit}, which every landed player hit already goes through, so all eight weapons
 * get it with no per-weapon code.
 *
 * <ul>
 *   <li>Colour follows the body: green ichor for spiders and worms, orange for magma, pale bone dust for skeletons
 *       (nothing to bleed), red for everything humanoid.</li>
 *   <li>A normal hit is only a 2 particle drip. A heavy hit (a crit or a big chunk of health) is a real spray with
 *       darker specks lower on the body. A target carrying Rend (the daggers' Wither bleed) leaks a little on every hit.</li>
 * </ul>
 *
 * <p>Cost: dust particles only, no entities, no ticker, nothing when nobody is fighting. The counts are small on
 * purpose: this runs on every landed hit, and a horde fight is many hits per second.
 */
public final class Blood {
    private Blood() {}

    /** Body types, deliberately few so the colour is readable at a glance. */
    public enum Kind {
        RED(0xB01010, 0x780A0A),
        GREEN(0x6FD21E, 0x3F8A10),
        MAGMA(0xFF7A1A, 0xC04A0A),
        BONE(0xD8D2BC, 0xA8A38E);

        final int main;
        final int dark;

        Kind(int main, int dark) {
            this.main = main;
            this.dark = dark;
        }
    }

    /** Pure decision, no side effects: which blood a target bleeds. */
    public static Kind kindOf(LivingEntity target) {
        String path = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath();
        if (target instanceof Spider || path.contains("spider") || path.contains("broodling")
                || path.contains("broodmother") || path.contains("devourer") || path.contains("worm")) {
            return Kind.GREEN;
        }
        if (target instanceof Slime || path.contains("tiki")) {
            return Kind.MAGMA;
        }
        if (target instanceof AbstractSkeleton || path.contains("skeleton") || path.contains("marksman")
                || path.contains("necromancer")) {
            return Kind.BONE;
        }
        return Kind.RED;
    }

    /** Called for every landed hit. {@code heavy} is the crit-like flag Fx.hit already receives. */
    public static void onHit(ServerLevel level, LivingEntity target, boolean heavy) {
        Kind kind = kindOf(target);
        Vec3 c = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
        boolean bleeding = target.hasEffect(MobEffects.WITHER);
        if (heavy) {
            level.sendParticles(new DustParticleOptions(kind.main, 1.3F), c.x, c.y, c.z, 14, 0.3, 0.3, 0.3, 0.25);
            level.sendParticles(new DustParticleOptions(kind.dark, 1.0F), c.x, c.y, c.z, 8, 0.25, 0.3, 0.25, 0.15);
            if (kind != Kind.BONE) {
                // A few darker specks a little lower on the body. Dust ignores velocity in vanilla, so these are
                // placed, not thrown: honest 'splatter', not falling drops.
                level.sendParticles(new DustParticleOptions(kind.dark, 1.1F),
                        c.x, c.y - target.getBbHeight() * 0.25, c.z, 4, 0.3, 0.25, 0.3, 0.0);
            }
        } else {
            level.sendParticles(new DustParticleOptions(kind.main, 0.9F), c.x, c.y, c.z, 2, 0.15, 0.2, 0.15, 0.08);
        }
        if (bleeding && kind != Kind.BONE) {
            // Rend: an extra dark trickle from lower on the body, so a bleeding target reads as bleeding.
            level.sendParticles(new DustParticleOptions(kind.dark, 0.8F),
                    target.getX(), target.getY() + target.getBbHeight() * 0.35, target.getZ(), 3, 0.2, 0.1, 0.2, 0.02);
        }
    }
}
