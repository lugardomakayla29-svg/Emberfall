package com.solme.emberfall.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Authoring-only marker block. Invisible/non-solid, used purely as a tag
 * inside hand-built arena structures so RunManager knows where to spawn
 * things after pasting a structure. Never meant to persist in a finished,
 * pasted-and-scanned arena (the paste pipeline strips these immediately).
 */
public class MarkerBlock extends Block implements EntityBlock {
    public MarkerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MarkerBlockEntity(pos, state);
    }
}
