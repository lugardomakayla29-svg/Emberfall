package com.solme.emberfall.entity;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * One place that knows how to spawn each Emberfall mob correctly: the same construct, position, prepare and attach steps
 * the Wave Director and the admin commands use. A raw vanilla spawn egg skips those steps (a Broodmother without her rig
 * crashed the server once), so the creative-tab eggs call this instead. Every mob is the ordinary BASE tier, exactly what
 * a wave would roll most often; the admin command spawnveteran/spawnelite stay the way to get the stronger tiers.
 *
 * <p>Bosses are deliberately absent: the Ember Guardian and the Devourer need a run's arena, pylons and circle boundary,
 * so a bare spawn would be a broken half-boss.
 */
public final class MobSpawner {
    private MobSpawner() {}

    /** A spawn recipe: builds the mob at the given block, already added to the level. */
    @FunctionalInterface
    public interface Recipe {
        void spawn(ServerLevel level, BlockPos pos);
    }

    /** Stable id to recipe, in the order the creative tab lists them. */
    public static final Map<String, Recipe> RECIPES = new LinkedHashMap<>();

    static {
        // Horde fodder: construct, place, face a random way, run the extra setup the wave director runs, add.
        RECIPES.put("horde_zombie", (level, pos) -> place(level, pos, new HordeZombie(ModEntities.HORDE_ZOMBIE, level)));
        RECIPES.put("horde_skeleton", (level, pos) -> {
            HordeSkeleton skeleton = new HordeSkeleton(ModEntities.HORDE_SKELETON, level);
            skeleton.equipBow();
            place(level, pos, skeleton);
        });
        RECIPES.put("horde_spider", (level, pos) -> place(level, pos, new HordeSpider(ModEntities.HORDE_SPIDER, level)));
        RECIPES.put("horde_witch", (level, pos) -> place(level, pos, new HordeWitch(ModEntities.HORDE_WITCH, level)));
        RECIPES.put("horde_bomber", (level, pos) -> {
            HordeBomber bomber = new HordeBomber(ModEntities.HORDE_BOMBER, level);
            bomber.prepare();
            place(level, pos, bomber);
        });
        RECIPES.put("horde_charger", (level, pos) -> {
            HordeCharger charger = new HordeCharger(ModEntities.HORDE_CHARGER, level);
            charger.prepare();
            place(level, pos, charger);
        });
        RECIPES.put("horde_shieldbearer", (level, pos) -> {
            HordeShieldbearer shield = new HordeShieldbearer(ModEntities.HORDE_SHIELDBEARER, level);
            shield.prepare();
            place(level, pos, shield);
        });
        RECIPES.put("horde_spitter", (level, pos) -> {
            HordeSpitter spitter = new HordeSpitter(ModEntities.HORDE_SPITTER, level);
            spitter.prepare();
            place(level, pos, spitter);
        });
        RECIPES.put("horde_imp", (level, pos) -> {
            HordeImp imp = new HordeImp(ModEntities.HORDE_IMP, level);
            imp.prepare();
            // An imp hovers above eye level, so lift it off the ground the way a wave spawn does.
            imp.setPos(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
            imp.setYRot(level.getRandom().nextFloat() * 360.0F);
            level.addFreshEntity(imp);
        });
        // Elites: each has its own factory that builds rigs, mounts and attaches parts, and adds itself.
        RECIPES.put("tiki_magma", (level, pos) -> {
            TikiMagma tiki = TikiMagma.spawn(level, new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), false);
            tiki.setYRot(level.getRandom().nextFloat() * 360.0F);
        });
        RECIPES.put("cinderbrand_reaver", (level, pos) -> CinderbrandReaver.spawn(level, pos, 1.0));
        RECIPES.put("blightfeather_marksman", (level, pos) -> BlightfeatherMarksman.spawn(level, pos, 1.0));
        RECIPES.put("umbral_magus", (level, pos) -> UmbralMagus.spawn(level, pos, 1.0));
        RECIPES.put("corrupted_sentinel", (level, pos) -> CorruptedSentinel.spawn(level, pos, 1.0));
        RECIPES.put("bonecaller_necromancer", (level, pos) -> BonecallerNecromancer.spawn(level, pos, 1.0));
        RECIPES.put("plague_colossus", (level, pos) -> PlagueColossus.spawn(level, pos, 1.0));
        RECIPES.put("boil_ridden_marksman", (level, pos) -> BoilRiddenMarksman.spawn(level, pos, 1.0));
        RECIPES.put("broodmother_stalker", (level, pos) -> BroodmotherStalker.spawn(level, pos, 1.0));
        RECIPES.put("pink_slime", (level, pos) ->
                PinkSlime.spawn(level, new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), null));
    }

    private static void place(ServerLevel level, BlockPos pos, net.minecraft.world.entity.Mob mob) {
        mob.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        mob.setYRot(level.getRandom().nextFloat() * 360.0F);
        level.addFreshEntity(mob);
    }

    /** Spawns the mob with this id at the block, returning false for an unknown id. */
    public static boolean spawn(String id, ServerLevel level, BlockPos pos) {
        Recipe recipe = RECIPES.get(id);
        if (recipe == null) {
            return false;
        }
        recipe.spawn(level, pos);
        return true;
    }
}
