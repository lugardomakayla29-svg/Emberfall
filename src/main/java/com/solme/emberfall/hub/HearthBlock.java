package com.solme.emberfall.hub;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The retired Ember Hearth. It no longer builds anything: the hub is gone and a Rift is the way into a run.
 *
 * The block stays REGISTERED on purpose. Removing a block from the registry turns every placed copy into air in old worlds, and an
 * old world may still hold a built hub (tagged figures and journaled blocks) around a Hearth. So this class keeps exactly one job:
 * whenever a Hearth stops being a Hearth, for ANY reason, it tears down the hub that belonged to it ({@link #tearDown}), so no
 * figure is left orphaned. Its loot table drops a Rift Shard instead of a Hearth.
 */
public class HearthBlock extends Block {
    public HearthBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.literal("\u00A76The Hearth has gone cold. Break it for a Rift Shard."), true);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Fires whenever the Hearth stops being a Hearth for ANY reason (player break, TNT, creeper, piston, /setblock, /fill), so an
     * old hub can never be orphaned. The {@code movedByPiston} flag is ignored on purpose: a moved Hearth is a removed Hearth here.
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        // Only a real removal counts. Block-state changes that keep a Hearth in place do not come through here.
        if (level.getBlockState(pos).getBlock() instanceof HearthBlock) {
            return;
        }
        tearDown(level, pos);
    }

    /** Removes the hub belonging to the Hearth at {@code pos}, if one is built. Safe to call twice. */
    public static void tearDown(ServerLevel level, BlockPos pos) {
        HubRegistry registry = HubRegistry.get(level.getServer());
        if (!registry.isBuilt(level, pos)) {
            return;
        }
        HubBuilder.removeEntities(level, pos);
        com.solme.emberfall.world.BlockJournal.restoreFile(level.getServer(), com.solme.emberfall.world.BlockJournal.hubJournalDir(level.getServer())
                .resolve(HubRegistry.journalName(level, pos) + ".dat"));
        registry.remove(level, pos);
    }
}
