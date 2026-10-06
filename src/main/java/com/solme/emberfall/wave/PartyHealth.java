package com.solme.emberfall.wave;

import com.solme.emberfall.world.PartyScaling;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Applies the party's HEALTH multiplier to one freshly created mob or boss (issue #13, steps 3c and 3d). One place for all of them,
 * so the horde sites and both bosses cannot drift apart.
 *
 * <p>Health only. Damage is never touched: a bigger party makes foes last longer, it does not make them hit harder. (The Boss Curse
 * scales both, which is why the party factor does not go through {@code applyCurse}.) For one player the multiplier is exactly 1.0
 * and this method returns without touching the entity, so a solo run is byte for byte unchanged.
 *
 * <p>Call it once, after the mob is fully built (veteran bonus and Boss Curse included) and BEFORE it is added to the world, so the
 * boss bar and the first hit already see the scaled health.
 */
public final class PartyHealth {
    /** Test aid: log one line per scaled mob. Off unless the server runs with -Demberfall.logPartyHp=true. */
    private static final boolean LOG = Boolean.getBoolean("emberfall.logPartyHp");

    private PartyHealth() {
    }

    /** Scales a horde mob's health for a party of {@code n}. */
    public static void applyMob(LivingEntity mob, int n) {
        scale(mob, PartyScaling.mobHealthMultiplier(n));
    }

    /** Vanilla's hard ceiling for {@code max_health}: the attribute silently clamps anything above it. */
    public static final double ATTRIBUTE_CEILING = 1024.0;

    /**
     * Scales a boss's health for a party of {@code n}; multiplies with whatever the Boss Curse already did.
     *
     * <p>Vanilla clamps {@code max_health} at {@link #ATTRIBUTE_CEILING}, and a party boss goes past it (Guardian 600 x 1.75 = 1050
     * at two players). So the pool is carried in two parts: the attribute takes as much as it can hold, and the rest comes back as a
     * damage factor the boss applies to every hit it takes. Effective health is then exactly {@code base x multiplier}, with no cap.
     *
     * @return the factor to multiply incoming damage by: 1.0 when the whole pool fits (always for one player), else below 1.0
     */
    public static float applyBoss(LivingEntity boss, int n) {
        double mult = PartyScaling.bossHealthMultiplier(n);
        AttributeInstance hp = boss.getAttribute(Attributes.MAX_HEALTH);
        if (hp == null || mult <= 1.0) {
            scale(boss, mult);
            return 1.0F;
        }
        double wanted = hp.getBaseValue() * mult;
        float factor = (float) Math.min(1.0, ATTRIBUTE_CEILING / wanted);
        scale(boss, Math.min(mult, ATTRIBUTE_CEILING / hp.getBaseValue()));
        if (LOG) {
            com.solme.emberfall.EmberfallMod.LOGGER.info("PARTYBOSS type={} effective={} attribute={} damageFactor={}",
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()),
                    String.format("%.1f", wanted), String.format("%.1f", boss.getMaxHealth()), String.format("%.4f", factor));
        }
        return factor;
    }

    private static void scale(LivingEntity e, double multiplier) {
        AttributeInstance hp = e.getAttribute(Attributes.MAX_HEALTH);
        if (hp == null) {
            return;
        }
        double before = hp.getBaseValue();
        if (multiplier > 1.0) {
            hp.setBaseValue(before * multiplier);
            e.setHealth(e.getMaxHealth());
        }
        if (LOG) {
            com.solme.emberfall.EmberfallMod.LOGGER.info("PARTYHP type={} mult={} base={} max={}",
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()), String.format("%.2f", multiplier),
                    String.format("%.1f", before), String.format("%.1f", e.getMaxHealth()));
        }
    }
}
