package com.solme.emberfall.entity;

import com.mojang.math.Transformation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Spawns and animates a cosmetic block_display column. Vanilla's {@code Display.setTransformation} and
 * {@code Display.BlockDisplay.setBlockState} are both private on this mapping (Paper exposes public wrappers for
 * plugins; Fabric does not), so the supported way to set either from mod code is the one the {@code /summon}
 * command's NBT path uses: build a CompoundTag and feed it through {@code Entity.load(ValueInput)}.
 *
 * <p>Measured on a live server: {@code load} restores EVERYTHING from the tag, so a tag without {@code Pos} moves the
 * display to (0,0,0) and one without {@code Tags} drops them. Both helpers therefore write the position (and, on a
 * resize, the tags) into the tag. Used by the Umbral Magus's cauldron for its rising pink liquid.</p>
 */
public final class BlockDisplayUtil {
    private BlockDisplayUtil() {}

    /**
     * A block display with a separate width and height whose BOTTOM face sits at {@code base.y} and which is centred on
     * x and z. {@code interpolationTicks} makes the client animate any later {@link #resize}.
     */
    public static Display.BlockDisplay spawnColumn(ServerLevel level, Vec3 base, BlockState state, float width,
                                                   float height, int interpolationTicks) {
        Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        display.setPos(base.x, base.y, base.z);
        CompoundTag tag = new CompoundTag();
        tag.put("Pos", listOf(base.x, base.y, base.z));      // load() restores Pos from the tag: without it the display is at (0,0,0)
        tag.put("block_state", NbtUtils.writeBlockState(state));
        putTransform(tag, width, height, interpolationTicks);
        display.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        level.addFreshEntity(display);
        return display;
    }

    /**
     * Animates a column made by {@link #spawnColumn} to a new size. The display is reloaded with a new transformation
     * and {@code interpolation_duration}; the client then slides from the old transform to the new over that many
     * ticks, so one call makes a smooth rise (no per-tick updates, no extra entities).
     */
    public static void resize(ServerLevel level, Display.BlockDisplay display, BlockState state, float width,
                              float height, int interpolationTicks) {
        CompoundTag tag = new CompoundTag();
        // load() restores EVERYTHING from the tag, so a tag without Pos moves the display to (0,0,0) and one without
        // Tags drops them (both measured on a live server). Carry the live position and tags across the reload.
        tag.put("Pos", listOf(display.getX(), display.getY(), display.getZ()));
        if (!display.getTags().isEmpty()) {
            net.minecraft.nbt.ListTag tags = new net.minecraft.nbt.ListTag();
            for (String t : display.getTags()) {
                tags.add(net.minecraft.nbt.StringTag.valueOf(t));
            }
            tag.put("Tags", tags);
        }
        tag.put("block_state", NbtUtils.writeBlockState(state));
        putTransform(tag, width, height, interpolationTicks);
        tag.putInt("start_interpolation", 0);
        display.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
    }

    private static net.minecraft.nbt.ListTag listOf(double x, double y, double z) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        list.add(net.minecraft.nbt.DoubleTag.valueOf(x));
        list.add(net.minecraft.nbt.DoubleTag.valueOf(y));
        list.add(net.minecraft.nbt.DoubleTag.valueOf(z));
        return list;
    }

    private static void putTransform(CompoundTag tag, float width, float height, int interpolationTicks) {
        Transformation transformation = new Transformation(
                new Vector3f(-width / 2.0F, 0.0F, -width / 2.0F),
                new Quaternionf(),
                new Vector3f(width, height, width),
                new Quaternionf());
        Transformation.CODEC.encodeStart(NbtOps.INSTANCE, transformation)
                .resultOrPartial(err -> {})
                .ifPresent(encoded -> tag.put("transformation", encoded));
        tag.putInt("interpolation_duration", interpolationTicks);
    }
}
