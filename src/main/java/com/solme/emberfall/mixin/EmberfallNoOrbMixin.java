package com.solme.emberfall.mixin;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Emberfall mobs never drop vanilla experience orbs. A horde dying at once would otherwise spawn
 * hundreds of orb entities; the virtual pickup system pays the same XP with no entities at all.
 * {@code getExperienceReward} is left untouched so the pickup system can still read the value the
 * mob would have dropped, keeping each mob's own XP amount instead of a second table.
 */
@Mixin(LivingEntity.class)
public abstract class EmberfallNoOrbMixin {
    @Inject(method = "shouldDropExperience", at = @At("HEAD"), cancellable = true)
    private void emberfall$noVanillaOrbs(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(self.getType());
        if (key != null && EmberfallMod.MOD_ID.equals(key.getNamespace())) {
            cir.setReturnValue(false);
        }
    }
}
