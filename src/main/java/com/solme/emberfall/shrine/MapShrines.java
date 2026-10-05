package com.solme.emberfall.shrine;

import com.solme.emberfall.EmberfallMod;
import com.solme.emberfall.entity.BlightfeatherMarksman;
import com.solme.emberfall.entity.BonecallerNecromancer;
import com.solme.emberfall.entity.CinderbrandReaver;
import com.solme.emberfall.entity.CorruptedSentinel;
import com.solme.emberfall.entity.UmbralMagus;
import com.solme.emberfall.network.ChooseShrinePayload;
import com.solme.emberfall.network.OpenShrinePayload;
import com.solme.emberfall.pickup.PickupSystem;
import com.solme.emberfall.wave.WaveDirector;
import com.solme.emberfall.world.ArenaInstance;
import com.solme.emberfall.world.MapBuilder;
import com.solme.emberfall.world.MapManager;
import com.solme.emberfall.world.RunManager;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The three shrines of the static expedition map: Challenge, Boss Curse and the Statue of Greed. Each is one of the user's
 * own structures standing at a fixed spot. A LEFT CLICK on any block of the structure, from within {@value #REACH} blocks,
 * opens a small window; the choice comes back as a {@link ChooseShrinePayload} and is fully re-validated here (range, map
 * run, shrine unused, option in range), never trusted from the client.
 *
 * Every shrine can be used once per run. Curse and Greed only record a tier in {@link RunModifiers}, which the boss
 * spawners, the wave director and the Silver payout read. Challenge spawns a fight and pays out when it is cleared.
 *
 * The click handler is registered BEFORE {@link com.solme.emberfall.world.MapProtection}, so a click on a shrine opens the
 * window while every other click in the expedition dimension is still refused (and nothing is ever broken).
 */
public final class MapShrines {
    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");

    public static final String CHALLENGE = "challenge";
    public static final String CURSE = "curse";
    public static final String GREED = "greed";

    static final double REACH = 6.0;
    /** Tag on each invisible click box; the second tag names the shrine type. */
    public static final String HOTSPOT_TAG = "emberfall_shrine_hotspot";
    private static final String TYPE_TAG_PREFIX = "emberfall_shrine_";

    static final int[] CHALLENGE_MOBS = {3, 5, 8};
    static final double CHALLENGE_STAT = 1.4;
    static final long CHALLENGE_TICKS = 1200; // 60 s
    static final double CHALLENGE_SPREAD = 4.0;
    static final int[] CHALLENGE_GOLD = {15, 30, 55};
    static final int[] CHALLENGE_XP = {20, 40, 75};

    private static final class Challenge {
        final UUID player;
        final int size;
        final BlockPos anchor;
        long ticksLeft = CHALLENGE_TICKS;
        final List<UUID> mobs = new ArrayList<>();

        Challenge(UUID player, int size, BlockPos anchor) {
            this.player = player;
            this.size = size;
            this.anchor = anchor;
        }
    }

    private static final Map<Integer, Set<String>> USED = new HashMap<>();
    private static final Map<Integer, Challenge> FIGHTS = new HashMap<>();

    private MapShrines() {}

    /** Registers the click hook. Must run before MapProtection.init() so this listener sees the click first. */
    public static void init() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (!(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level)) {
                return InteractionResult.PASS;
            }
            Integer slot = RunManager.slotOf(sp);
            ArenaInstance arena = slot == null ? null : RunManager.getActive(slot);
            if (arena == null || !arena.isMap() || arena.level() != level) {
                return InteractionResult.PASS;
            }
            String type = shrineAt(slot, pos);
            if (type == null) {
                return InteractionResult.PASS;
            }
            open(sp, slot, type);
            return InteractionResult.FAIL; // the click only opens the window; it never breaks the structure
        });
        // A click on the invisible box that covers the whole structure. It makes thin parts (a lightning rod, a fence) and
        // the gaps between blocks clickable, which a block-only hook cannot.
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level) || !entity.getTags().contains(HOTSPOT_TAG)) {
                return InteractionResult.PASS;
            }
            Integer slot = RunManager.slotOf(sp);
            ArenaInstance arena = slot == null ? null : RunManager.getActive(slot);
            if (arena == null || !arena.isMap() || arena.level() != level) {
                return InteractionResult.FAIL;
            }
            for (String tag : entity.getTags()) {
                if (tag.startsWith(TYPE_TAG_PREFIX) && !tag.equals(HOTSPOT_TAG)) {
                    open(sp, slot, tag.substring(TYPE_TAG_PREFIX.length()));
                    break;
                }
            }
            return InteractionResult.FAIL; // never damages or removes the hotspot
        });
        ServerTickEvents.END_SERVER_TICK.register(MapShrines::tick);
        ServerPlayNetworking.registerGlobalReceiver(ChooseShrinePayload.TYPE,
                (payload, context) -> onChoice(context.player(), payload));
    }

    /** Which shrine the block at {@code pos} belongs to in this run's map, or null. */
    static String shrineAt(int slot, BlockPos pos) {
        MapBuilder map = MapManager.builtFor(slot);
        if (map == null) {
            return null;
        }
        for (Map.Entry<String, BlockPos[]> e : map.shrineBounds.entrySet()) {
            BlockPos lo = e.getValue()[0];
            BlockPos hi = e.getValue()[1];
            if (pos.getX() >= lo.getX() && pos.getX() <= hi.getX()
                    && pos.getY() >= lo.getY() && pos.getY() <= hi.getY()
                    && pos.getZ() >= lo.getZ() && pos.getZ() <= hi.getZ()) {
                return e.getKey();
            }
        }
        return null;
    }

    private static boolean inReach(ServerPlayer player, int slot, String type) {
        MapBuilder map = MapManager.builtFor(slot);
        BlockPos anchor = map == null ? null : map.shrineAnchors.get(type);
        return anchor != null && player.position().distanceTo(Vec3.atCenterOf(anchor)) <= REACH + 4.0;
    }

    /**
     * The anchor of this run's Challenge Shrine when it can still be used (not used yet, no trial running), or null. Read-only:
     * the EmberTester bot uses it to decide whether to walk over. It changes nothing.
     */
    public static BlockPos openChallengeAnchor(int slot) {
        if (isUsed(slot, CHALLENGE) || FIGHTS.containsKey(slot)) {
            return null;
        }
        MapBuilder map = MapManager.builtFor(slot);
        return map == null ? null : map.shrineAnchors.get(CHALLENGE);
    }

    /**
     * What a bot does at the shrine, through the SAME two steps as a real click: open (range checked) then choose (range, use and
     * option checked again by {@link #onChoice}). The option is re-read from {@link #describe} so a bot can never pick a disabled
     * one. Returns the option it chose, or -1 if the shrine refused or nothing was enabled.
     */
    public static int botChallenge(ServerPlayer player) {
        Integer slot = RunManager.slotOf(player);
        if (slot == null || !inReach(player, slot, CHALLENGE)) {
            return -1;
        }
        OpenShrinePayload shown = describe(slot, CHALLENGE);
        List<Boolean> enabled = new ArrayList<>();
        for (OpenShrinePayload.Option o : shown.options()) {
            enabled.add(o.enabled());
        }
        int at = com.solme.emberfall.bot.BotChoices.pickEnabled(enabled);
        if (at < 0) {
            return -1;
        }
        int option = shown.options().get(at).index();
        onChoice(player, new ChooseShrinePayload(CHALLENGE, option));
        return isUsed(slot, CHALLENGE) ? option : -1;
    }

    private static boolean isUsed(int slot, String type) {
        return USED.getOrDefault(slot, Set.of()).contains(type);
    }

    private static void markUsed(int slot, String type) {
        USED.computeIfAbsent(slot, s -> new HashSet<>()).add(type);
    }

    public static void stop(int slot, ServerLevel level) {
        removeHotspots(level, slot);
        stop(slot);
    }

    public static void stop(int slot) {
        USED.remove(slot);
        FIGHTS.remove(slot);
    }

    /**
     * Puts one invisible click box over each shrine of this run's map. Called when the run starts. The boxes carry
     * {@link #HOTSPOT_TAG} so run teardown (which discards the run's entities) and {@link #removeHotspots} find them.
     */
    public static void spawnHotspots(ServerLevel level, int slot) {
        MapBuilder map = MapManager.builtFor(slot);
        if (map == null) {
            return;
        }
        removeHotspots(level, slot);
        for (Map.Entry<String, BlockPos[]> e : map.shrineBounds.entrySet()) {
            BlockPos lo = e.getValue()[0];
            BlockPos hi = e.getValue()[1];
            Interaction box = new Interaction(EntityType.INTERACTION, level);
            double cx = (lo.getX() + hi.getX() + 1) / 2.0;
            double cz = (lo.getZ() + hi.getZ() + 1) / 2.0;
            box.setPos(cx, lo.getY(), cz);
            box.setWidth((float) Math.max(hi.getX() - lo.getX() + 1, hi.getZ() - lo.getZ() + 1) + 0.6F);
            box.setHeight((float) (hi.getY() - lo.getY() + 1));
            box.setResponse(false);
            box.setInvulnerable(true);
            box.addTag(HOTSPOT_TAG);
            box.addTag(TYPE_TAG_PREFIX + e.getKey());
            box.addTag("emberfall_run");
            level.addFreshEntity(box);
        }
    }

    /** Removes this map's shrine click boxes (the slot's map is permanent, so a new run must not stack a second set). */
    public static void removeHotspots(ServerLevel level, int slot) {
        MapBuilder map = MapManager.builtFor(slot);
        if (map == null) {
            return;
        }
        var origin = map.origin();
        var box = new net.minecraft.world.phys.AABB(origin.getX() - 100, origin.getY() - 8, origin.getZ() - 100,
                origin.getX() + 100, origin.getY() + 40, origin.getZ() + 100);
        for (Interaction i : level.getEntitiesOfClass(Interaction.class, box, e -> e.getTags().contains(HOTSPOT_TAG))) {
            i.discard();
        }
    }

    // ---- the window ----

    private static void open(ServerPlayer player, int slot, String type) {
        if (!inReach(player, slot, type)) {
            player.displayClientMessage(Component.literal("\u00A77Move closer to the shrine."), true);
            return;
        }
        ServerPlayNetworking.send(player, describe(slot, type));
    }

    /** What the window shows for a shrine right now. Pure data, so a test can read it without a client. */
    static OpenShrinePayload describe(int slot, String type) {
        boolean used = isUsed(slot, type);
        List<OpenShrinePayload.Option> opts = new ArrayList<>();
        switch (type) {
            case CHALLENGE -> {
                boolean busy = FIGHTS.containsKey(slot);
                String[] names = {"Small trial", "Trial", "Great trial"};
                for (int i = 0; i < 3; i++) {
                    opts.add(new OpenShrinePayload.Option(i, names[i] + " (" + CHALLENGE_MOBS[i] + " foes)",
                            "Costs nothing. Clear all " + CHALLENGE_MOBS[i] + " guardians within 60 seconds for "
                                    + CHALLENGE_GOLD[i] + " Gold, " + CHALLENGE_XP[i] + " XP and +" + RunModifiers.CHALLENGE_SILVER_BONUS
                                    + " Silver. Failing costs nothing.", !used && !busy));
                }
                return new OpenShrinePayload(type, "Challenge Shrine",
                        used ? "The shrine has gone quiet." : "Free to use. Fill the trial for a richer run.", opts);
            }
            case CURSE -> {
                for (int t = 1; t <= RunModifiers.CURSE_TIERS; t++) {
                    RunModifiers preview = new RunModifiers();
                    preview.takeCurse(t);
                    opts.add(new OpenShrinePayload.Option(t, "Curse " + roman(t),
                            String.format("Bosses have x%.1f health and damage and x%.1f adds. Silver +%d%%.",
                                    preview.bossStatMultiplier(), preview.bossSpawnMultiplier(), t * 15), !used));
                }
                return new OpenShrinePayload(type, "Boss Curse",
                        used ? "The curse is already set." : "Make the bosses stronger. The run pays more Silver.", opts);
            }
            default -> {
                for (int s = 1; s <= RunModifiers.GREED_STEPS; s++) {
                    opts.add(new OpenShrinePayload.Option(s, "+" + s + " difficulty",
                            "Danger rises by " + (int) (s * RunModifiers.THREAT_PER_GREED_STEP)
                                    + " threat for the whole run. Silver +" + s * 10 + "%.", !used));
                }
                return new OpenShrinePayload(GREED, "Statue of Greed",
                        used ? "The statue has taken its offering." : "Choose how much more dangerous the run becomes.", opts);
            }
        }
    }

    private static String roman(int t) {
        return switch (t) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "IV";
        };
    }

    // ---- the choice ----

    static void onChoice(ServerPlayer player, ChooseShrinePayload payload) {
        Integer slot = RunManager.slotOf(player);
        ArenaInstance arena = slot == null ? null : RunManager.getActive(slot);
        if (arena == null || !arena.isMap()) {
            return;
        }
        String type = payload.shrineType();
        if (!CHALLENGE.equals(type) && !CURSE.equals(type) && !GREED.equals(type)) {
            return;
        }
        if (isUsed(slot, type) || !inReach(player, slot, type)) {
            return;
        }
        int option = payload.option();
        ServerLevel level = arena.level();
        RunModifiers mods = RunModifiers.of(slot);
        MapBuilder map = MapManager.builtFor(slot);
        BlockPos anchor = map == null ? null : map.shrineAnchors.get(type);
        if (anchor == null) {
            return;
        }
        switch (type) {
            case CURSE -> {
                if (!mods.takeCurse(option)) {
                    return;
                }
                markUsed(slot, type);
                player.sendSystemMessage(Component.literal(String.format(
                        "\u00A75The curse takes hold: bosses x%.1f health and damage, x%.1f adds, Silver +%d%%.",
                        mods.bossStatMultiplier(), mods.bossSpawnMultiplier(), option * 15)));
                burst(level, anchor, 1);
            }
            case GREED -> {
                if (!mods.takeGreed(option)) {
                    return;
                }
                markUsed(slot, type);
                WaveDirector director = WaveDirector.get(slot);
                if (director != null) {
                    director.addBonusThreat(mods.bonusThreat());
                }
                player.sendSystemMessage(Component.literal(String.format(
                        "\u00A76The statue accepts: +%d difficulty (threat +%d), Silver +%d%%.",
                        option, (int) mods.bonusThreat(), option * 10)));
                burst(level, anchor, 2);
            }
            default -> {
                if (option < 0 || option >= CHALLENGE_MOBS.length || FIGHTS.containsKey(slot)) {
                    return;
                }
                markUsed(slot, type);
                startChallenge(level, slot, player, option, anchor);
            }
        }
    }

    private static void burst(ServerLevel level, BlockPos anchor, int kind) {
        var particle = kind == 1 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.HAPPY_VILLAGER;
        level.sendParticles(particle, anchor.getX() + 0.5, anchor.getY() + 2.0, anchor.getZ() + 0.5, 60, 1.2, 1.5, 1.2, 0.05);
    }

    // ---- the challenge fight ----

    private static void startChallenge(ServerLevel level, int slot, ServerPlayer player, int size, BlockPos anchor) {
        Challenge fight = new Challenge(player.getUUID(), size, anchor);
        if (TEST_MODE) {
            com.solme.emberfall.EmberfallMod.LOGGER.info("SHRINE_TEST challenge start player={} size={} foes={}", player.getGameProfile().name(), size, CHALLENGE_MOBS[size]);
        }
        RandomSource random = level.getRandom();
        for (int i = 0; i < CHALLENGE_MOBS[size]; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = 2.5 + random.nextDouble() * CHALLENGE_SPREAD;
            BlockPos spot = anchor.offset((int) Math.round(Math.cos(a) * d), 0, (int) Math.round(Math.sin(a) * d));
            Mob mob = spawnFoe(level, spot, random);
            if (mob != null) {
                fight.mobs.add(mob.getUUID());
            }
        }
        FIGHTS.put(slot, fight);
        player.sendSystemMessage(Component.literal("\u00A7cThe trial begins: " + fight.mobs.size()
                + " guardians, 60 seconds. Clear them for a rich reward."));
        EmberfallMod.LOGGER.info("MapShrines: slot {} challenge size {} started with {} mobs", slot, size, fight.mobs.size());
    }

    private static Mob spawnFoe(ServerLevel level, BlockPos pos, RandomSource random) {
        return switch (random.nextInt(5)) {
            case 0 -> CinderbrandReaver.spawn(level, pos, CHALLENGE_STAT);
            case 1 -> BlightfeatherMarksman.spawn(level, pos, CHALLENGE_STAT);
            case 2 -> UmbralMagus.spawn(level, pos, CHALLENGE_STAT);
            case 3 -> CorruptedSentinel.spawn(level, pos, CHALLENGE_STAT);
            default -> BonecallerNecromancer.spawn(level, pos, CHALLENGE_STAT);
        };
    }

    private static void tick(MinecraftServer server) {
        if (FIGHTS.isEmpty()) {
            return;
        }
        var it = FIGHTS.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            int slot = e.getKey();
            Challenge fight = e.getValue();
            ArenaInstance arena = RunManager.getActive(slot);
            if (arena == null) {
                it.remove();
                continue;
            }
            ServerLevel level = arena.level();
            fight.mobs.removeIf(id -> !(level.getEntity(id) instanceof Mob m) || !m.isAlive());
            ServerPlayer player = server.getPlayerList().getPlayer(fight.player);
            if (fight.mobs.isEmpty()) {
                it.remove();
                RunModifiers.of(slot).markChallengeCleared();
                if (player != null) {
                    com.solme.emberfall.relic.RelicUnlocks.announce(player,
                            com.solme.emberfall.relic.RelicUnlocks.get(server).addProgress(player.getUUID(), "clear_3_challenges", 1));
                }
                Vec3 at = Vec3.atCenterOf(fight.anchor).add(0, 1.5, 0);
                PickupSystem.spawn(level, at, PickupSystem.Kind.GOLD, CHALLENGE_GOLD[fight.size]);
                PickupSystem.spawn(level, at, PickupSystem.Kind.XP, CHALLENGE_XP[fight.size]);
                if (player != null) {
                    player.sendSystemMessage(Component.literal("\u00A7aTrial cleared! Gold, XP and a Silver bonus are yours."));
                }
                burst(level, fight.anchor, 2);
                // A cleared trial always leaves a free chest beside the shrine (unless the run is at the free-chest cap).
                com.solme.emberfall.relic.ChestManager.dropFree(level, arena, fight.anchor.above(),
                        com.solme.emberfall.relic.FreeChestRule.Source.SHRINE);
                continue;
            }
            if (--fight.ticksLeft <= 0) {
                it.remove();
                for (UUID id : fight.mobs) {
                    Entity m = level.getEntity(id);
                    if (m != null) {
                        m.discard();
                    }
                }
                if (player != null) {
                    player.sendSystemMessage(Component.literal("\u00A77The trial ends. The guardians outlasted you. No penalty."));
                }
            }
        }
    }
}
