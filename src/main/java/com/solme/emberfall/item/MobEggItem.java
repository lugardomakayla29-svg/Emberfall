package com.solme.emberfall.item;

import com.solme.emberfall.entity.MobSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A creative-tab egg for one Emberfall mob. Right-clicking a block spawns the ordinary base-tier mob on the clicked face
 * through {@link MobSpawner}, which runs the same setup the Wave Director does. (A vanilla egg would skip that setup.)
 */
public final class MobEggItem extends Item {
    private final String mobId;

    public MobEggItem(Properties properties, String mobId) {
        super(properties);
        this.mobId = mobId;
    }

    public String mobId() {
        return mobId;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;   // client: the server does the work
        }
        BlockPos target = context.getClickedPos().relative(context.getClickedFace());
        if (!MobSpawner.spawn(mobId, level, target)) {
            return InteractionResult.FAIL;
        }
        if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
