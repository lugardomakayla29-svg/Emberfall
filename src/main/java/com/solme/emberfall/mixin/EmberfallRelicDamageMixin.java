package com.solme.emberfall.mixin;

import com.solme.emberfall.relic.RelicHitEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Lets the damage relics (Anvil of Dawn, Wither Crown, Soul Lantern, Big Bonk Hammer) scale a player's hit BEFORE it is
 * applied. The eight weapons and the Tome effects reach a foe through 31 separate {@code hurtServer(playerAttack)}
 * calls in 17 files with no shared damage stat, so one hook on the victim is the only place that covers them all.
 * Every Emberfall mob that overrides hurtServer calls super (checked), so none bypasses it. The mixin only forwards:
 * what counts as a relic hit, and by how much, is decided in {@link RelicHitEvents}.
 */
@Mixin(LivingEntity.class)
public abstract class EmberfallRelicDamageMixin {
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float emberfall$relicScale(float amount, net.minecraft.server.level.ServerLevel level, DamageSource source) {
        return RelicHitEvents.scale((LivingEntity) (Object) this, source, amount);
    }
}
