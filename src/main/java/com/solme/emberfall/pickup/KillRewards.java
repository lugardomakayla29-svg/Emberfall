package com.solme.emberfall.pickup;

import com.solme.emberfall.combat.MobPresentation;
import com.solme.emberfall.world.RunManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Pays out a virtual XP pickup and (by tier) a gold pickup when a run player kills an Emberfall mob.
 * Nothing is paid for kills with no player killer, such as /kill, teardown purges or mob-on-mob damage,
 * so cleanup can never hand out rewards.
 */
public final class KillRewards {
    /** Gold per kill by {@link MobPresentation#tierOf}: fodder is a coin toss for 1, veterans 3, elites and bosses 10. */
    static final int GOLD_VETERAN = 3;
    static final int GOLD_ELITE = 10;
    static final double FODDER_GOLD_CHANCE = 0.6;
    /** Vanilla's level cost keeps climbing (5 kills a level at 10, 21 at 30, 57 at 50) while a kill pays a flat amount: late levels crawl. */
    static final int LATE_XP_FROM_LEVEL = 20;        // nothing changes up to here (the user found early and mid game fine)
    static final double LATE_XP_PER_LEVEL = 0.03;    // +3% kill XP per level past it
    static final double LATE_XP_MAX = 2.5;

    /** Kill XP multiplier for a player who is at {@code level}: 1.0 up to level 20, then +3% per level, capped at 2.5x. */
    public static double lateXpMultiplier(int level) {
        return Math.min(LATE_XP_MAX, 1.0 + LATE_XP_PER_LEVEL * Math.max(0, level - LATE_XP_FROM_LEVEL));
    }

    private KillRewards() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof Mob mob) || !(entity.level() instanceof ServerLevel level) || !isEmberfall(mob)) {
                return;
            }
            if (!(source.getEntity() instanceof ServerPlayer killer) || RunManager.slotOf(killer) == null) {
                return;
            }
            com.solme.emberfall.world.RunStats.addKill(killer);
            // The weapon that landed this kill grows. Only paying kills reach here (an Emberfall mob killed by a run player),
            // so a Magus's temporary summons can never be farmed to level a weapon.
            com.solme.emberfall.item.WeaponProgress.onKill(killer, mob);
            Vec3 at = mob.position().add(0.0, 0.4, 0.0);
            // Relics (Clockwork Charm / Golden Nugget) scale the payout; with none held both multipliers are exactly 1.0.
            var relics = com.solme.emberfall.relic.RelicEffects.stats(killer);
            int xp = Math.max(1, (int) Math.round(mob.getExperienceReward(level, killer) * lateXpMultiplier(killer.experienceLevel) * relics.xpMultiplier()));
            PickupSystem.spawn(level, at, PickupSystem.Kind.XP, xp);
            int gold = switch (MobPresentation.tierOf(mob)) {
                case 0 -> level.getRandom().nextDouble() < FODDER_GOLD_CHANCE ? 1 : 0;
                case 1 -> GOLD_VETERAN;
                default -> GOLD_ELITE;
            };
            gold = com.solme.emberfall.relic.RelicMath.scaleWhole(gold, relics.goldMultiplier(), level.getRandom()::nextDouble);
            PickupSystem.spawn(level, at.add(0.0, 0.2, 0.0), PickupSystem.Kind.GOLD, gold);
            // An elite the wave director spawned may leave a free chest where it fell (chance falls as the run hands them out).
            if (mob.getTags().contains(com.solme.emberfall.relic.ChestManager.ELITE_TAG)) {
                Integer slot = RunManager.slotOf(killer);
                var arena = slot == null ? null : RunManager.getActive(slot);
                if (arena != null) {
                    com.solme.emberfall.relic.ChestManager.dropFree(level, arena, mob.blockPosition(),
                            com.solme.emberfall.relic.FreeChestRule.Source.ELITE);
                }
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(PickupSystem::tick);
    }

    private static boolean isEmberfall(LivingEntity e) {
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        return key != null && "emberfall".equals(key.getNamespace());
    }
}
