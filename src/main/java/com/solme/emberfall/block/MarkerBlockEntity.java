package com.solme.emberfall.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Block entity for emberfall:marker. Placed by mapmakers inside hand-authored
 * arena structures to mark functional points (spawn points, shrine locations,
 * chest locations, the player entry point, etc). When a structure template
 * containing these is pasted at runtime, RunManager scans for them, reads
 * markerType, records the world position, then removes the marker block.
 */
public class MarkerBlockEntity extends BlockEntity {
    private String markerType = "";

    public MarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MARKER, pos, state);
    }

    public String getMarkerType() {
        return markerType;
    }

    public void setMarkerType(String markerType) {
        this.markerType = markerType == null ? "" : markerType;
        setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.markerType = input.getStringOr("MarkerType", "");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("MarkerType", this.markerType);
    }
}
