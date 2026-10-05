package com.solme.emberfall.shrine;

import com.solme.emberfall.EmberfallMod;
import com.solme.emberfall.entity.BlightfeatherMarksman;
import com.solme.emberfall.entity.BonecallerNecromancer;
import com.solme.emberfall.entity.CinderbrandReaver;
import com.solme.emberfall.entity.CorruptedSentinel;
import com.solme.emberfall.entity.UmbralMagus;
import com.solme.emberfall.tome.PlayerBuild;
import com.solme.emberfall.tome.TomeChoiceManager;
import com.solme.emberfall.tome.TomeOfferGenerator;
import com.solme.emberfall.wave.WaveDirector;
import com.solme.emberfall.world.ArenaInstance;
import com.solme.emberfall.world.Dimensions;
import com.solme.emberfall.world.RunManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.world.entity.Display.ItemDisplay;
import com.mojang.math.Transformation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Design doc Section 8: rolls which of an arena's "shrine" markers (3.3) go
 * live for a given run, which {@link ShrineType} each active one gets, and
 * owns the resulting risk/reward interaction. Trigger is proximity-based
 * (design doc's own suggested implementation: "a block-entity or
 * marker-armor-stand with an interaction-range check") rather than a
 * right-click, checked once per server tick against every in-run player.
 *
 * Each active shrine is a real, physical two-block pedestal + floating
 * custom head (see {@link ShrineType}) placed directly into the pasted
 * arena's world - not baked into the arena's own structure template, since
 * which of the 3 types occupies a given marker is rolled fresh per run
 * (3.3's whole point). Cleanup of the placed blocks/entities rides along
 * for free on {@link RunManager#teardownArena}, which already wipes the
 * entire arena's bounds and discards every non-player entity in it; {@link
 * #stop} only needs to drop this class's own bookkeeping.
 */
public final class ShrineManager {
    private static final double ACTIVATION_CHANCE = 0.65; // per shrine marker, per run
    private static final double TRIGGER_RADIUS = 2.5;
    private static final double DISPLAY_SCALE = 1.4;

    private static final double GREED_THREAT_BONUS = 2.0; // permanent addition to that run's WaveDirector.threatLevel

    private static final double CURSE_MAX_HEALTH_PENALTY = -0.20; // ADD_MULTIPLIED_BASE, run-long

    private static final int CHALLENGE_MOB_COUNT = 3;
    private static final double CHALLENGE_MOB_STAT_MULTIPLIER = 1.4; // "tougher-than-normal", per 8's Challenge Shrine
    private static final double CHALLENGE_SPAWN_SPREAD = 3.0; // blocks, "confined to the shrine's immediate area"
    private static final long CHALLENGE_TIME_LIMIT_TICKS = 500; // 25s

    private static final Map<Integer, List<ActiveShrine>> bySlot = new HashMap<>();

    private ShrineManager() {}

    private static final class ActiveShrine {
        final ShrineType type;
        final BlockPos pos;
        boolean triggered = false;
        UUID displayId;

        boolean challengeRunning = false;
        UUID challengingPlayer;
        long challengeTicksRemaining;
        final List<UUID> challengeMobs = new ArrayList<>();

        ActiveShrine(ShrineType type, BlockPos pos) {
            this.type = type;
            this.pos = pos;
        }
    }

    /** Call once right after an arena is pasted and scanned (RunManager.pasteArena). */
    public static void rollShrines(ServerLevel level, ArenaInstance instance) {
        List<BlockPos> markers = instance.markers("shrine");
        if (markers.isEmpty()) {
            return;
        }

        List<ActiveShrine> active = new ArrayList<>();
        RandomSource random = level.getRandom();
        ShrineType[] types = ShrineType.values();

        for (BlockPos markerPos : markers) {
            if (random.nextDouble() >= ACTIVATION_CHANCE) {
                continue; // this marker stays dormant this run, per 3.3
            }
            ShrineType type = types[random.nextInt(types.length)];
            ActiveShrine shrine = new ActiveShrine(type, markerPos.immutable());
            place(level, shrine);
            active.add(shrine);
        }

        bySlot.put(instance.slot(), active);
        EmberfallMod.LOGGER.info("Shrine Manager: slot {} rolled {} active shrine(s) from {} marker(s)",
                instance.slot(), active.size(), markers.size());
    }

    /** Drops this slot's shrine bookkeeping. Physical cleanup rides along on arena teardown. */
    public static void stop(int slot) {
        bySlot.remove(slot);
    }

    private static void place(ServerLevel level, ActiveShrine shrine) {
        BlockPos pos = shrine.pos;
        RunManager.setBlockJournaled(level, pos.below(), shrine.type.baseBlock().defaultBlockState());
        RunManager.setBlockJournaled(level, pos, shrine.type.accentBlock().defaultBlockState());

        ItemDisplay display = new ItemDisplay(EntityType.ITEM_DISPLAY, level);
        display.setPos(pos.getX() + 0.5, pos.getY() + 1.15, pos.getZ() + 0.5);
        display.setItemStack(shrine.type.headItem());
        display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        float scale = (float) DISPLAY_SCALE;
        display.setTransformation(new Transformation(
                new Vector3f(-scale / 2.0F, -scale / 2.0F, -scale / 2.0F),
                new Quaternionf(),
                new Vector3f(scale, scale, scale),
                new Quaternionf()));
        display.setNoGravity(true);
        display.setInvulnerable(true);
        level.addFreshEntity(display);
        shrine.displayId = display.getUUID();
    }

    public static void tickAll(MinecraftServer server) {
        if (bySlot.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, List<ActiveShrine>> entry : bySlot.entrySet()) {
            int slot = entry.getKey();
            ArenaInstance runInstance = RunManager.getActive(slot);
            if (runInstance == null) {
                continue;
            }
            ServerLevel level = runInstance.level();
            for (ActiveShrine shrine : entry.getValue()) {
                if (shrine.challengeRunning) {
                    tickChallenge(server, level, shrine);
                } else if (!shrine.triggered) {
                    checkProximity(level, slot, shrine);
                }
            }
        }
    }

    private static void checkProximity(ServerLevel level, int slot, ActiveShrine shrine) {
        AABB box = new AABB(
                shrine.pos.getX() - TRIGGER_RADIUS, shrine.pos.getY() - TRIGGER_RADIUS, shrine.pos.getZ() - TRIGGER_RADIUS,
                shrine.pos.getX() + 1 + TRIGGER_RADIUS, shrine.pos.getY() + 1 + TRIGGER_RADIUS, shrine.pos.getZ() + 1 + TRIGGER_RADIUS);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box, p -> true)) {
            Integer playerSlot = RunManager.slotOf(player);
            if (playerSlot != null && playerSlot == slot) {
                trigger(level, slot, shrine, player);
                return;
            }
        }
    }

    private static void trigger(ServerLevel level, int slot, ActiveShrine shrine, ServerPlayer player) {
        shrine.triggered = true;
        removeDisplay(level, shrine);
        switch (shrine.type) {
            case GREED -> triggerGreed(slot, player);
            case CURSE -> triggerCurse(player);
            case CHALLENGE -> startChallenge(level, shrine, player);
        }
    }

    private static void removeDisplay(ServerLevel level, ActiveShrine shrine) {
        if (shrine.displayId == null) {
            return;
        }
        Entity entity = level.getEntity(shrine.displayId);
        if (entity != null) {
            entity.discard();
        }
    }

    private static void triggerGreed(int slot, ServerPlayer player) {
        WaveDirector director = WaveDirector.get(slot);
        if (director != null) {
            director.addBonusThreat(GREED_THREAT_BONUS);
        }
        TomeOfferGenerator.markNextOfferGuaranteedWeapon(player);
        player.sendSystemMessage(Component.literal(
                "§6You channel the Greed Shrine - the danger rises for the rest of this run, but your next Tome offer is a sure thing."));
    }

    private static void triggerCurse(ServerPlayer player) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            // Random id (not player-keyed): a run can roll more than one Curse
            // Shrine, and each must get its own modifier id or the second
            // addPermanentModifier throws ("Modifier is already applied").
            Identifier modifierId = EmberfallMod.id("shrine_curse_" + UUID.randomUUID());
            maxHealth.addPermanentModifier(new AttributeModifier(modifierId, CURSE_MAX_HEALTH_PENALTY, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            PlayerBuild.addCleanup(player, p -> {
                AttributeInstance instance = p.getAttribute(Attributes.MAX_HEALTH);
                if (instance != null) {
                    instance.removeModifier(modifierId);
                }
            });
        }
        player.sendSystemMessage(Component.literal(
                "§5The Curse Shrine takes its toll (-20% Max Health for the rest of this run) - but grants you a free Tome pick, right now."));
        TomeChoiceManager.openChoice(player, player.experienceLevel);
    }

    private static void startChallenge(ServerLevel level, ActiveShrine shrine, ServerPlayer player) {
        shrine.challengeRunning = true;
        shrine.challengingPlayer = player.getUUID();
        shrine.challengeTicksRemaining = CHALLENGE_TIME_LIMIT_TICKS;

        RandomSource random = level.getRandom();
        int spread = (int) CHALLENGE_SPAWN_SPREAD;
        for (int i = 0; i < CHALLENGE_MOB_COUNT; i++) {
            BlockPos spawnPos = shrine.pos.offset(
                    random.nextInt(spread * 2 + 1) - spread, 0, random.nextInt(spread * 2 + 1) - spread);
            Mob mob = spawnChallengeMob(level, spawnPos, random);
            if (mob != null) {
                shrine.challengeMobs.add(mob.getUUID());
            }
        }
        player.sendSystemMessage(Component.literal(
                "§cThe Challenge Shrine wakes - clear its guardians in 25s for a guaranteed strong reward!"));
    }

    private static Mob spawnChallengeMob(ServerLevel level, BlockPos pos, RandomSource random) {
        return switch (random.nextInt(5)) {
            case 0 -> CinderbrandReaver.spawn(level, pos, CHALLENGE_MOB_STAT_MULTIPLIER);
            case 1 -> BlightfeatherMarksman.spawn(level, pos, CHALLENGE_MOB_STAT_MULTIPLIER);
            case 2 -> UmbralMagus.spawn(level, pos, CHALLENGE_MOB_STAT_MULTIPLIER);
            case 3 -> CorruptedSentinel.spawn(level, pos, CHALLENGE_MOB_STAT_MULTIPLIER);
            default -> BonecallerNecromancer.spawn(level, pos, CHALLENGE_MOB_STAT_MULTIPLIER);
        };
    }

    private static void tickChallenge(MinecraftServer server, ServerLevel level, ActiveShrine shrine) {
        shrine.challengeMobs.removeIf(uuid -> {
            Entity e = level.getEntity(uuid);
            return !(e instanceof Mob m) || !m.isAlive();
        });

        if (shrine.challengeMobs.isEmpty()) {
            shrine.challengeRunning = false;
            ServerPlayer player = server.getPlayerList().getPlayer(shrine.challengingPlayer);
            if (player != null) {
                grantChallengeReward(player);
            }
            return;
        }

        shrine.challengeTicksRemaining--;
        if (shrine.challengeTicksRemaining <= 0) {
            for (UUID uuid : shrine.challengeMobs) {
                Entity e = level.getEntity(uuid);
                if (e != null) {
                    e.discard();
                }
            }
            shrine.challengeMobs.clear();
            shrine.challengeRunning = false;
            ServerPlayer player = server.getPlayerList().getPlayer(shrine.challengingPlayer);
            if (player != null) {
                player.sendSystemMessage(Component.literal(
                        "§7The Challenge Shrine's guardians outlasted you - no reward this time."));
            }
        }
    }

    private static void grantChallengeReward(ServerPlayer player) {
        TomeOfferGenerator.markNextOfferGuaranteedWeapon(player);
        player.sendSystemMessage(Component.literal("§aChallenge cleared! A guaranteed strong Tome pick is yours, right now."));
        TomeChoiceManager.openChoice(player, player.experienceLevel);
    }
}
