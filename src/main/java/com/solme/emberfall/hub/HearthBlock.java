package com.solme.emberfall.hub;

import com.solme.emberfall.world.BlockJournal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Ember Hearth. Placing it lights it at once: the ground is checked and either the whole hub is built,
 * or the placement is refused, the block is taken back and the Hearth item is returned with the nearest good
 * spot shown. Right-clicking a placed, unlit Hearth (only possible if it arrived some other way, e.g. /setblock)
 * retries the same check.
 *
 * It never edits the terrain. If the surrounding 9x9 is not already flat, clear and natural, it
 * refuses (see {@link HubSiteAnalyzer}) and {@link HubSiteSearch} finds the closest place that is.
 * Breaking the Hearth removes the hub: {@link #playerWillDestroy} calls
 * {@link HubBuilder#removeEntities} and restores the journaled blocks.
 */
public class HearthBlock extends Block {
    public HearthBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        activate(serverLevel, pos, sp);
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Runs the moment a player places the Hearth. Lights it straight away; if the ground is not suitable the
     * block is removed again and the item goes back to the player, so a failed placement never leaves a dead
     * magma block standing there. Creative players keep their stack untouched, so only survival is refunded.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel) || !(placer instanceof ServerPlayer sp)) {
            return;
        }
        if (!activate(serverLevel, pos, sp)) {
            serverLevel.removeBlock(pos, false);
            if (!sp.getAbilities().instabuild) {
                ItemStack back = new ItemStack(this.asItem());
                if (!sp.getInventory().add(back)) {
                    sp.drop(back, false);
                }
            }
        }
    }

    /**
     * The whole "light the Hearth" behaviour. Kept out of {@link #useWithoutItem} so the debug command
     * can run exactly the same code as a right-click without needing a real client interaction.
     */
    public static boolean activate(ServerLevel serverLevel, BlockPos pos, ServerPlayer sp) {
        if (HubRegistry.get(serverLevel.getServer()).isBuilt(serverLevel, pos)) {
            sp.displayClientMessage(Component.literal("§6The hub is already built. Right-click a bust to choose a character."), true);
            return true;
        }

        // The Hearth stands ON the ground, so the ground surface is one block below it.
        HubSiteAnalyzer.Result site = HubSiteAnalyzer.analyse(serverLevel, pos.getX(), pos.getZ());
        if (!site.ok() || site.ground() == null || site.ground().getY() != pos.getY() - 1) {
            String why = site.ok() ? "the Hearth is not resting on the ground surface" : site.reason();
            sp.sendSystemMessage(Component.literal("§cThe Hearth cannot light here: " + why + "."));
            HubSiteSearch.begin(sp, pos);
            return false;
        }

        BlockJournal journal = new BlockJournal(serverLevel);
        journal.exclude(pos); // the Hearth's own cell must never be journaled: restoring it would resurrect the Hearth
        journal.persistTo(BlockJournal.hubJournalDir(serverLevel.getServer()), HubRegistry.journalName(serverLevel, pos));
        int entities = HubBuilder.build(serverLevel, pos, journal);
        HubRegistry.get(serverLevel.getServer()).add(serverLevel, pos);
        sp.sendSystemMessage(Component.literal("§6The Hearth ignites. §7(" + entities + " figures appear)"));
        return true;
    }

    /**
     * Fires whenever the Hearth stops being a Hearth for ANY reason (player break, TNT, creeper, piston,
     * /setblock, /fill), not only a player breaking it, so the hub can never be orphaned. The
     * {@code movedByPiston} flag is ignored on purpose: a moved Hearth is a removed Hearth here.
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
        BlockJournal.restoreFile(level.getServer(), BlockJournal.hubJournalDir(level.getServer())
                .resolve(HubRegistry.journalName(level, pos) + ".dat"));
        registry.remove(level, pos);
    }
}
