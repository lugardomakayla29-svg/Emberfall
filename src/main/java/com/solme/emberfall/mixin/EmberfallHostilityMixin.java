package com.solme.emberfall.mixin;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Emberfall runs now happen in the player's REAL world, where villagers, iron golems, turtles and
 * other players' pets are standing around. Vanilla Zombie AI hunts villagers/golems/turtles, and
 * our mobs extend Zombie/Skeleton/Spider, so without this they would wander off and wreck a village.
 *
 * Every vanilla targeting goal filters candidates through {@code LivingEntity.canAttack}
 * (via TargetingConditions), so gating it here is the one hook that covers all 20 mob types
 * without touching each class or using reflection. Rule: an Emberfall mob may only ever attack
 * players. Vanilla mobs are untouched (gated on the attacker's registry namespace).
 */
@Mixin(LivingEntity.class)
public abstract class EmberfallHostilityMixin {
    @Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
    private void emberfall$onlyPlayers(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (target instanceof Player) {
            return;
        }
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(self.getType());
        if (key != null && EmberfallMod.MOD_ID.equals(key.getNamespace())) {
            cir.setReturnValue(false);
        }
    }
}
