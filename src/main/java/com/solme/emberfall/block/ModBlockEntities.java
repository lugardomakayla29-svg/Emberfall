package com.solme.emberfall.block;

import com.solme.emberfall.EmberfallMod;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    public static final BlockEntityType<MarkerBlockEntity> MARKER =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    EmberfallMod.id("marker"),
                    FabricBlockEntityTypeBuilder.create(MarkerBlockEntity::new, ModBlocks.MARKER).build()
            );

    private ModBlockEntities() {}

    public static void init() {
        // classload trigger
    }
}
