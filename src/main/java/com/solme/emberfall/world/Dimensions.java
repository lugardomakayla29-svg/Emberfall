package com.solme.emberfall.world;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class Dimensions {
    /** The instanced-run dimension: emberfall:expedition (void flat world, see data/emberfall/dimension/expedition.json). */
    public static final ResourceKey<Level> EXPEDITION = ResourceKey.create(Registries.DIMENSION, EmberfallMod.id("expedition"));

    private Dimensions() {}
}
