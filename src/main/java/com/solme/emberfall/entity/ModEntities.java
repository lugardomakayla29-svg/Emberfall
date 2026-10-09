package com.solme.emberfall.entity;

import com.solme.emberfall.EmberfallMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.zombie.Zombie;

public final class ModEntities {
    /** The travelling merchant: a Nitwit-model Villager that only stands, sells and leaves, see {@link Testificate}. MISC, so no mob cap or monster rules touch it. */
    public static final EntityType<Testificate> TESTIFICATE = register(
            "testificate",
            EntityType.Builder.of(Testificate::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
    );

    public static final EntityType<HordeZombie> HORDE_ZOMBIE = register(
            "horde_zombie",
            EntityType.Builder.of(HordeZombie::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.74F)
                    .clientTrackingRange(6)
    );

    /** Second horde-filler enemy (2026-09-27 elite/horde-variety pass) - ranged Skeleton-based, see {@link HordeSkeleton}. */
    public static final EntityType<HordeSkeleton> HORDE_SKELETON = register(
            "horde_skeleton",
            EntityType.Builder.of(HordeSkeleton::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.99F)
                    .eyeHeight(1.74F)
                    .clientTrackingRange(8)
    );

    /** Fourth horde filler: a ranged caster that circles four Star Bits and lobs them, see {@link HordeWitch}. */
    public static final EntityType<HordeWitch> HORDE_WITCH = register(
            "horde_witch",
            EntityType.Builder.of(HordeWitch::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(8)
    );

    /** Eighth horde filler: a small flying imp that arrives in packs, see {@link HordeImp}. */
    public static final EntityType<HordeImp> HORDE_IMP = register(
            "horde_imp",
            EntityType.Builder.of(HordeImp::new, MobCategory.MONSTER)
                    .sized(0.4F, 0.8F)
                    .eyeHeight(0.5F)
                    .clientTrackingRange(8)
    );

    /** Seventh horde filler: a slow zombie behind a shield that blocks frontal hits, see {@link HordeShieldbearer}. */
    public static final EntityType<HordeShieldbearer> HORDE_SHIELDBEARER = register(
            "horde_shieldbearer",
            EntityType.Builder.of(HordeShieldbearer::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.74F)
                    .clientTrackingRange(8)
    );

    /** Ninth horde filler: a ranged acid spitter that keeps its distance and leaves puddles, see {@link HordeSpitter}. */
    public static final EntityType<HordeSpitter> HORDE_SPITTER = register(
            "horde_spitter",
            EntityType.Builder.of(HordeSpitter::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.74F)
                    .clientTrackingRange(8)
    );

    /** Sixth horde filler: a slow brute that telegraphs a straight-line rush and stuns itself on a wall, see {@link HordeCharger}. */
    public static final EntityType<HordeCharger> HORDE_CHARGER = register(
            "horde_charger",
            EntityType.Builder.of(HordeCharger::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.74F)
                    .clientTrackingRange(8)
    );

    /** Fifth horde filler: a fast fragile runner that detonates on a player, see {@link HordeBomber}. */
    public static final EntityType<HordeBomber> HORDE_BOMBER = register(
            "horde_bomber",
            EntityType.Builder.of(HordeBomber::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.7F)
                    .eyeHeight(1.44F)
                    .clientTrackingRange(8)
    );

    /** Third horde-filler enemy (2026-09-27 elite/horde-variety pass) - melee Spider-based, see {@link HordeSpider}. */
    public static final EntityType<HordeSpider> HORDE_SPIDER = register(
            "horde_spider",
            EntityType.Builder.of(HordeSpider::new, MobCategory.MONSTER)
                    .sized(1.4F, 0.9F)
                    .eyeHeight(0.65F)
                    .clientTrackingRange(8)
    );

    /** A Broodmother's hatchling - a real emberfall mob so weapons can hit it and the run purge keeps it, see {@link Broodling}. */
    public static final EntityType<Broodling> BROODLING = register(
            "broodling",
            EntityType.Builder.of(Broodling::new, MobCategory.MONSTER)
                    .sized(1.4F, 0.9F)
                    .eyeHeight(0.65F)
                    .clientTrackingRange(8)
    );

    /** One of the Ember Guardian's lit pylons: a real, breakable, invisible Silverfish with a lit-magma display rider. */
    public static final EntityType<CinderPylon> CINDER_PYLON = register(
            "cinder_pylon",
            EntityType.Builder.of(CinderPylon::new, MobCategory.MONSTER)
                    .sized(0.9F, 1.6F)
                    .clientTrackingRange(12)
                    .fireImmune()
    );

    /** EMBERFALL 1st boss ("The Ember Guardian", replaces the Hydra) - a fully invisible Silverfish hitbox with display parts. */
    public static final EntityType<EmberGuardian> EMBER_GUARDIAN = register(
            "ember_guardian",
            EntityType.Builder.of(EmberGuardian::new, MobCategory.MONSTER)
                    .sized(2.4F, 4.4F)
                    .clientTrackingRange(14)
                    .fireImmune()
    );

    /**
     * EMBERFALL 1st boss ("The Broodtide", replaces the Ember Guardian): a real rooted Slime. The type's box is the VANILLA slime's 0.52 because
     * {@code Slime.getDefaultDimensions} scales it by getSize() (size 6 gives a 3.1 block body); sizing a big box here would multiply by the size.
     */
    public static final EntityType<BroodtideBody> BROODTIDE = register(
            "broodtide",
            EntityType.Builder.of(BroodtideBody::new, MobCategory.MONSTER)
                    .sized(0.52F, 0.52F)
                    .eyeHeight(0.325F)
                    .clientTrackingRange(14)
                    .fireImmune()
    );

    /** Design doc 6.2 Hydra recipe - the real brain. Sized like a slightly larger Ravager. */

    /** Design doc 6.1 - invisible rigid attachment point, never spawned by itself. */

    /** Design doc 6.2 Hydra recipe - a visible, independently-firing "head" part. */

    /** EMBERFALL 2nd boss ("The Devourer") - the real brain, a fully invisible Silverfish wearing a Sandworm head display. */
    public static final EntityType<DevourerBrain> DEVOURER_BRAIN = register(
            "devourer_brain",
            EntityType.Builder.of(DevourerBrain::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.4F)
                    .clientTrackingRange(14)
                    .fireImmune()
    );

    /** Devourer Phase 2/3 payoff - a real independent mini-worm mob (Zombie-based AI), not a rig part. */
    public static final EntityType<DevourerSpawn> DEVOURER_SPAWN = register(
            "devourer_spawn",
            EntityType.Builder.of(DevourerSpawn::new, MobCategory.MONSTER)
                    .sized(0.5F, 1.3F)
                    .clientTrackingRange(10)
                    .fireImmune()
    );

    /** SlopPack Corrupted archetype #1 (ported + refined) - Vindicator-based fire berserker. */
    public static final EntityType<CinderbrandReaver> CINDERBRAND_REAVER = register(
            "cinderbrand_reaver",
            EntityType.Builder.of(CinderbrandReaver::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.74F)
                    .clientTrackingRange(10)
                    .fireImmune()
    );

    /** SlopPack Corrupted archetype #2 (ported + refined) - Skeleton-based mode-switching sniper. */
    public static final EntityType<BlightfeatherMarksman> BLIGHTFEATHER_MARKSMAN = register(
            "blightfeather_marksman",
            EntityType.Builder.of(BlightfeatherMarksman::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.99F)
                    .eyeHeight(1.74F)
                    .clientTrackingRange(12)
    );

    /** SlopPack Corrupted archetype #3 (ported + refined) - Witch-based shadow caster/summoner. */
    public static final EntityType<UmbralMagus> UMBRAL_MAGUS = register(
            "umbral_magus",
            EntityType.Builder.of(UmbralMagus::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(12)
    );

    /**
     * The Umbral Magus's cauldron payoff (the Magus's Cauldron-ritual payoff). Base box is the VANILLA slime's 0.52:
     * {@code Slime.getDefaultDimensions} scales the type's box by getSize(), so size 5 gives a 2.6 block body. Sizing
     * a 1.0 x 1.4 box would make a size-5 slime 5 blocks wide.
     */
    public static final EntityType<PinkSlime> PINK_SLIME = register(
            "pink_slime",
            EntityType.Builder.of(PinkSlime::new, MobCategory.MONSTER)
                    .sized(0.52F, 0.52F)
                    .eyeHeight(0.325F)
                    .clientTrackingRange(10)
    );

    /**
     * SlopPack Corrupted archetype #4 (ported + refined) - Zombie-based tank
     * boss. Base size stays normal-Zombie (0.6x1.95) - the 3x visual/hitbox
     * scale-up is applied at runtime via {@code Attributes.SCALE}, which
     * multiplies entity dimensions automatically; setting both the base
     * size AND the scale attribute would double-apply the multiplier.
     */
    public static final EntityType<CorruptedSentinel> CORRUPTED_SENTINEL = register(
            "corrupted_sentinel",
            EntityType.Builder.of(CorruptedSentinel::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.74F)
                    .clientTrackingRange(14)
    );

    /** 2026-09-28 elite pass - Zombie-based bloating plague tank/horde-caller, see {@link PlagueColossus}. Scale is runtime (Attributes.SCALE), so base size stays vanilla. */
    public static final EntityType<PlagueColossus> PLAGUE_COLOSSUS = register(
            "plague_colossus",
            EntityType.Builder.of(PlagueColossus::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.74F)
                    .clientTrackingRange(14)
    );

    /** 2026-09-28 elite pass - Skeleton-based plague archer, see {@link BoilRiddenMarksman}. */
    public static final EntityType<BoilRiddenMarksman> BOIL_RIDDEN_MARKSMAN = register(
            "boil_ridden_marksman",
            EntityType.Builder.of(BoilRiddenMarksman::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.99F)
                    .eyeHeight(1.74F)
                    .clientTrackingRange(14)
    );

    /** 2026-09-28 elite pass - Spider-based brood mother, see {@link BroodmotherStalker}. */
    public static final EntityType<BroodmotherStalker> BROODMOTHER_STALKER = register(
            "broodmother_stalker",
            EntityType.Builder.of(BroodmotherStalker::new, MobCategory.MONSTER)
                    .sized(1.4F, 0.9F)
                    .eyeHeight(0.65F)
                    .clientTrackingRange(14)
    );

    /** SlopPack Corrupted archetype #5 (ported + refined) - ZombieVillager-based mounted summoner. */
    public static final EntityType<BonecallerNecromancer> BONECALLER_NECROMANCER = register(
            "bonecaller_necromancer",
            EntityType.Builder.of(BonecallerNecromancer::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.74F)
                    .clientTrackingRange(14)
    );

    /** Fourth horde-filler enemy (2026-09-28 non-Corrupted refinement pass) - stacked totem-pole MagmaCube-based, see {@link TikiMagma}. */
    public static final EntityType<TikiMagma> TIKI_MAGMA = register(
            "tiki_magma",
            EntityType.Builder.of(TikiMagma::new, MobCategory.MONSTER)
                    .sized(0.8F, 0.6F)
                    .clientTrackingRange(8)
                    .fireImmune()
    );

    /** TikiMagma's stacked body segment - invisible rigid attachment point, never spawned by itself. */
    public static final EntityType<TikiSegment> TIKI_SEGMENT = register(
            "tiki_segment",
            EntityType.Builder.of(TikiSegment::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(8)
                    .fireImmune()
    );

    /** The second, decorative magma cube of a Fodder {@link TikiMagma} (a real MagmaCube so it looks identical to the first). */
    public static final EntityType<TikiCube> TIKI_CUBE = register(
            "tiki_cube",
            EntityType.Builder.of(TikiCube::new, MobCategory.MISC)
                    .sized(0.52F, 0.52F)
                    .clientTrackingRange(8)
                    .fireImmune()
    );

    private ModEntities() {}

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(
            String path, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, EmberfallMod.id(path));
        EntityType<T> type = builder.build(key);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
    }

    /** How far a hunting mob can notice a player. Vanilla's 16 (Skeleton, Spider) leaves them idle across most of the 28 block arena. */
    public static final double HUNT_RANGE = 48.0;

    private static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder wide(
            net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder builder) {
        return builder.add(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE, HUNT_RANGE);
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(TESTIFICATE, net.minecraft.world.entity.npc.villager.Villager.createAttributes());
        FabricDefaultAttributeRegistry.register(HORDE_ZOMBIE, wide(Zombie.createAttributes()));
        FabricDefaultAttributeRegistry.register(HORDE_SKELETON, wide(AbstractSkeleton.createAttributes()));
        FabricDefaultAttributeRegistry.register(HORDE_SPIDER, wide(Spider.createAttributes()));
        FabricDefaultAttributeRegistry.register(HORDE_IMP, wide(HordeImp.createImpAttributes()));
        FabricDefaultAttributeRegistry.register(HORDE_SPITTER, wide(HordeSpitter.createSpitterAttributes()));
        FabricDefaultAttributeRegistry.register(HORDE_SHIELDBEARER, wide(HordeShieldbearer.createShieldbearerAttributes()));
        FabricDefaultAttributeRegistry.register(HORDE_CHARGER, wide(HordeCharger.createChargerAttributes()));
        FabricDefaultAttributeRegistry.register(HORDE_BOMBER, wide(HordeBomber.createBomberAttributes()));
        FabricDefaultAttributeRegistry.register(HORDE_WITCH, wide(net.minecraft.world.entity.monster.Witch.createAttributes()));
        FabricDefaultAttributeRegistry.register(BROODLING, wide(Spider.createAttributes()));
        FabricDefaultAttributeRegistry.register(EMBER_GUARDIAN, EmberGuardian.createBossAttributes());
        FabricDefaultAttributeRegistry.register(CINDER_PYLON, CinderPylon.createPylonAttributes());
        FabricDefaultAttributeRegistry.register(DEVOURER_BRAIN, DevourerBrain.createBossAttributes());
        FabricDefaultAttributeRegistry.register(DEVOURER_SPAWN, wide(DevourerSpawn.createAttributes()));
        FabricDefaultAttributeRegistry.register(CINDERBRAND_REAVER, wide(Vindicator.createAttributes()));
        FabricDefaultAttributeRegistry.register(BLIGHTFEATHER_MARKSMAN, wide(AbstractSkeleton.createAttributes()));
        FabricDefaultAttributeRegistry.register(UMBRAL_MAGUS, wide(Witch.createAttributes()));
        FabricDefaultAttributeRegistry.register(PINK_SLIME, wide(PinkSlime.createAttributes()));
        FabricDefaultAttributeRegistry.register(BROODTIDE, BroodtideBody.createBossAttributes());
        FabricDefaultAttributeRegistry.register(CORRUPTED_SENTINEL, wide(Zombie.createAttributes()));
        FabricDefaultAttributeRegistry.register(BONECALLER_NECROMANCER, wide(BonecallerNecromancer.createAttributes()));
        FabricDefaultAttributeRegistry.register(TIKI_MAGMA, wide(TikiMagma.createAttributes()));
        FabricDefaultAttributeRegistry.register(TIKI_SEGMENT, ArmorStand.createAttributes());
        FabricDefaultAttributeRegistry.register(TIKI_CUBE, TikiMagma.createAttributes());
        FabricDefaultAttributeRegistry.register(PLAGUE_COLOSSUS, wide(Zombie.createAttributes()));
        FabricDefaultAttributeRegistry.register(BOIL_RIDDEN_MARKSMAN, wide(AbstractSkeleton.createAttributes()));
        FabricDefaultAttributeRegistry.register(BROODMOTHER_STALKER, wide(Spider.createAttributes()));
    }
}
