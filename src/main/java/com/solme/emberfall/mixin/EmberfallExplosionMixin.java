package com.solme.emberfall.mixin;

import com.solme.emberfall.world.MapProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Explosions in the designed expedition map never touch a block.
 *
 * ServerExplosion.explode() runs hurtEntities() FIRST, so players and mobs still take blast damage and knockback.
 * It then calls interactsWithBlocks() to decide whether to destroy blocks (interactWithBlocks), and separately
 * createFire() when its own fire flag is set. Those are two independent paths, so both are closed here. Scope is
 * only the emberfall:expedition dimension: every other dimension keeps vanilla behaviour.
 */
@Mixin(ServerExplosion.class)
public abstract class EmberfallExplosionMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "interactsWithBlocks", at = @At("HEAD"), cancellable = true)
    private void emberfall$noBlockDamage(CallbackInfoReturnable<Boolean> cir) {
        if (MapProtection.isProtected(this.level)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "createFire", at = @At("HEAD"), cancellable = true)
    private void emberfall$noExplosionFire(List<BlockPos> positions, CallbackInfo ci) {
        if (MapProtection.isProtected(this.level)) {
            ci.cancel();
        }
    }
}
