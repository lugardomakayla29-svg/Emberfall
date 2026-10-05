package com.solme.emberfall.block;

import com.solme.emberfall.character.CharacterSelectManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Character Table: right-click opens the character-selection screen (lore on hover). It holds no items and no
 * inventory, so there is nothing to duplicate or steal; the screen only sends back an id, which the server validates
 * exactly like /character select. Works in survival, needs no permission level.
 */
public class CharacterSelectBlock extends Block {
    public CharacterSelectBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        CharacterSelectManager.open(sp);
        return InteractionResult.SUCCESS_SERVER;
    }
}
