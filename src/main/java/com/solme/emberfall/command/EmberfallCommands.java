package com.solme.emberfall.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.solme.emberfall.EmberfallMod;
import com.solme.emberfall.wave.WaveDirector;
import com.solme.emberfall.world.ArenaInstance;
import com.solme.emberfall.world.RunManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.solme.emberfall.combat.AutoAttackSystem;
import com.solme.emberfall.entity.CinderbrandReaver;
import com.solme.emberfall.entity.BlightfeatherMarksman;
import com.solme.emberfall.entity.UmbralMagus;
import com.solme.emberfall.entity.CorruptedSentinel;
import com.solme.emberfall.entity.BonecallerNecromancer;
import com.solme.emberfall.entity.BoilRiddenMarksman;
import com.solme.emberfall.entity.BroodmotherStalker;
import com.solme.emberfall.entity.PlagueColossus;
import com.solme.emberfall.entity.HordeZombie;
import com.solme.emberfall.entity.HordeSkeleton;
import com.solme.emberfall.entity.HordeSpider;
import com.solme.emberfall.entity.ModEntities;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.List;
import java.util.Map;

/**
 * Dev/test-only debug commands for the arena paste + wave director pipeline.
 * Not meant to survive into a real release build of the mod - these exist so
 * the paste -> scan-markers -> teardown flow, and now the Wave Director
 * (4.3), can be exercised and verified from the server console, without
 * needing a connected player or a hand-built structure block setup. Requires
 * gamemaster permission (same as vanilla /give etc.).
 */
public final class EmberfallCommands {
    private EmberfallCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("emberfall")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("capture")
                            .then(Commands.argument("name", StringArgumentType.word())
                                    .then(Commands.argument("x1", IntegerArgumentType.integer())
                                    .then(Commands.argument("y1", IntegerArgumentType.integer())
                                    .then(Commands.argument("z1", IntegerArgumentType.integer())
                                    .then(Commands.argument("x2", IntegerArgumentType.integer())
                                    .then(Commands.argument("y2", IntegerArgumentType.integer())
                                    .then(Commands.argument("z2", IntegerArgumentType.integer())
                                    .executes(ctx -> {
                                        String name = StringArgumentType.getString(ctx, "name");
                                        BlockPos from = new BlockPos(
                                                IntegerArgumentType.getInteger(ctx, "x1"),
                                                IntegerArgumentType.getInteger(ctx, "y1"),
                                                IntegerArgumentType.getInteger(ctx, "z1"));
                                        BlockPos to = new BlockPos(
                                                IntegerArgumentType.getInteger(ctx, "x2"),
                                                IntegerArgumentType.getInteger(ctx, "y2"),
                                                IntegerArgumentType.getInteger(ctx, "z2"));
                                        return capture(ctx.getSource(), name, from, to);
                                    })))))))))
                    .then(Commands.literal("paste")
                            .then(Commands.argument("structure", StringArgumentType.word())
                                    .executes(ctx -> paste(ctx.getSource(), StringArgumentType.getString(ctx, "structure")))))
                    .then(Commands.literal("rift")
                            .then(Commands.literal("open").executes(ctx -> riftShow(ctx.getSource(), false)))
                            .then(Commands.literal("close").executes(ctx -> riftShow(ctx.getSource(), true)))
                            .then(Commands.literal("clear").executes(ctx -> {
                                com.solme.emberfall.rift.RiftStage.clear();
                                ctx.getSource().sendSuccess(() -> Component.literal("RIFT cleared"), false);
                                return 1;
                            }))
                            .then(Commands.literal("natural").executes(ctx -> {
                                var pl = ctx.getSource().getPlayer();
                                if (pl == null) { ctx.getSource().sendFailure(Component.literal("Run this as a player.")); return 0; }
                                var r = com.solme.emberfall.rift.RiftManager.tryOpenNear(pl);
                                ctx.getSource().sendSuccess(() -> Component.literal(r == null ? "RIFT natural refused" : "RIFT natural opened"), false);
                                return r == null ? 0 : 1;
                            }))
                            .then(Commands.literal("closeall").executes(ctx -> {
                                for (var r : com.solme.emberfall.rift.RiftManager.all()) { com.solme.emberfall.rift.RiftManager.close(r); }
                                ctx.getSource().sendSuccess(() -> Component.literal("RIFT closing all"), false);
                                return 1;
                            }))
                            .then(Commands.literal("selectstate").executes(ctx -> {
                                ctx.getSource().sendSuccess(() -> Component.literal(com.solme.emberfall.rift.RiftGate.selectState()), false);
                                return 1;
                            }))
                            .then(Commands.literal("state").executes(ctx -> {
                                int ents = 0;
                                for (var e : ctx.getSource().getLevel().getAllEntities()) { ents++; }
                                final int n = ents;
                                var all = com.solme.emberfall.rift.RiftManager.all();
                                StringBuilder sb = new StringBuilder();
                                long now = ctx.getSource().getLevel().getGameTime();
                                for (var r : all) { sb.append(String.format(" [%.0f,%.0f,%.0f f%d open=%b age=%d]", r.x, r.y, r.z, r.facing, r.isOpen(now), now - r.openedAt)); }
                                ctx.getSource().sendSuccess(() -> Component.literal("RIFT active=" + com.solme.emberfall.rift.RiftStage.activeCount() + " entities=" + n + " rifts=" + all.size() + sb), false);
                                return 1;
                            })))
                    .then(Commands.literal("teardown")
                            .then(Commands.argument("slot", IntegerArgumentType.integer())
                                    .executes(ctx -> teardown(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))))
                    .then(Commands.literal("mapbuild")
                            .then(Commands.argument("slot", IntegerArgumentType.integer(0, 63))
                                    .executes(ctx -> mapBuild(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))))
                    .then(Commands.literal("pickupstate")
                            .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                    .executes(ctx -> {
                                        var p = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                                        String msg = String.format("PICKUPSTATE gold=%d xpTotal=%d level=%d",
                                                com.solme.emberfall.pickup.PickupSystem.gold(p), p.totalExperience, p.experienceLevel);
                                        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                                        return 1;
                                    })))
                    .then(Commands.literal("shrinestate")
                            .then(Commands.argument("slot", IntegerArgumentType.integer(0, 63))
                                    .executes(ctx -> {
                                        int slot = IntegerArgumentType.getInteger(ctx, "slot");
                                        var mods = com.solme.emberfall.shrine.RunModifiers.peek(slot);
                                        var director = com.solme.emberfall.wave.WaveDirector.get(slot);
                                        String msg = com.solme.emberfall.world.RunManager.getActive(slot) == null
                                                ? "SHRINESTATE none"
                                                : mods == null
                                                ? String.format("SHRINESTATE curse=0 greed=0 challenge=false stat=1.00 spawn=1.00 silver=1.00 threat=%.2f",
                                                        director == null ? -1.0 : director.threatLevel())
                                                : String.format("SHRINESTATE curse=%d greed=%d challenge=%b stat=%.2f spawn=%.2f silver=%.2f threat=%.2f",
                                                        mods.curseTier(), mods.greedStep(), mods.challengeCleared(), mods.bossStatMultiplier(),
                                                        mods.bossSpawnMultiplier(), mods.silverMultiplier(), director == null ? -1.0 : director.threatLevel());
                                        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                                        return 1;
                                    })))
                    .then(Commands.literal("mapreport")
                            .executes(ctx -> { ctx.getSource().sendSuccess(() -> Component.literal("MAP " + com.solme.emberfall.world.MapManager.lastReport()), false); return 1; }))
                    .then(Commands.literal("wavestart")
                            .then(Commands.argument("slot", IntegerArgumentType.integer())
                                    .executes(ctx -> waveStart(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))))
                    .then(Commands.literal("wavestop")
                            .then(Commands.argument("slot", IntegerArgumentType.integer())
                                    .executes(ctx -> waveStop(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))))
                    .then(Commands.literal("wavestatus")
                            .then(Commands.argument("slot", IntegerArgumentType.integer())
                                    .executes(ctx -> waveStatus(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))))
                    .then(Commands.literal("join")
                            .then(Commands.argument("slot", IntegerArgumentType.integer())
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .executes(ctx -> join(ctx.getSource(),
                                                    IntegerArgumentType.getInteger(ctx, "slot"),
                                                    EntityArgument.getPlayer(ctx, "player"))))))
                    .then(Commands.literal("leave")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> leave(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                    .then(Commands.literal("balance")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> balance(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                    .then(Commands.literal("boss")
                            .then(Commands.argument("slot", IntegerArgumentType.integer())
                                    .executes(ctx -> bossTest(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))))
                    .then(Commands.literal("devdump")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        var lvl = (ServerLevel) target.level();
                                        var box = target.getBoundingBox().inflate(64);
                                        StringBuilder sb = new StringBuilder("DEVDUMP px=" + String.format("%.1f,%.1f,%.1f", target.getX(), target.getY(), target.getZ()));
                                        int parts = 0;
                                        for (var e : lvl.getEntities((net.minecraft.world.entity.Entity) null, box, en ->
                                                en instanceof com.solme.emberfall.entity.DevourerBrain
                                                        || (en instanceof net.minecraft.world.entity.Display.ItemDisplay
                                                        && en.getTags().contains(com.solme.emberfall.entity.WormBody.WORM_TAG)))) {
                                            String kind = e instanceof net.minecraft.world.entity.Display.ItemDisplay ? "ItemDisplay" : e.getClass().getSimpleName();
                                            String idx = "";
                                            for (String tg : e.getTags()) {
                                                if (tg.startsWith(com.solme.emberfall.entity.WormBody.WORM_TAG + "_")) {
                                                    idx = "#" + tg.substring(com.solme.emberfall.entity.WormBody.WORM_TAG.length() + 1);
                                                }
                                            }
                                            sb.append(String.format(" [%s%s %.1f,%.1f,%.1f%s]", kind, idx, e.getX(), e.getY(), e.getZ(), e.isInvisible() ? " invis" : ""));
                                            parts++;
                                        }
                                        String out = sb + " parts=" + parts;
                                        ctx.getSource().sendSuccess(() -> Component.literal(out), false);
                                        return 1;
                                    })))
                    .then(Commands.literal("tikidump")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        var lvl = (ServerLevel) target.level();
                                        var box = target.getBoundingBox().inflate(40);
                                        int tikis = 0, cubes = 0, segs = 0, items = 0, blocks = 0;
                                        StringBuilder sb = new StringBuilder();
                                        for (var e : lvl.getEntities((net.minecraft.world.entity.Entity) null, box, en ->
                                                en instanceof com.solme.emberfall.entity.TikiMagma
                                                        || en instanceof com.solme.emberfall.entity.TikiCube
                                                        || en instanceof com.solme.emberfall.entity.TikiSegment
                                                        || (en instanceof net.minecraft.world.entity.Display
                                                        && en.distanceToSqr(target) < 30 * 30))) {
                                            String k;
                                            if (e instanceof com.solme.emberfall.entity.TikiMagma) { tikis++; k = "Mob"; }
                                            else if (e instanceof com.solme.emberfall.entity.TikiCube) { cubes++; k = "Cube"; }
                                            else if (e instanceof com.solme.emberfall.entity.TikiSegment) { segs++; k = "OLDSEGMENT"; }
                                            else if (e instanceof net.minecraft.world.entity.Display.ItemDisplay) { items++; k = "Head"; }
                                            else { blocks++; k = "Roof"; }
                                            sb.append(String.format(" [%s y=%.2f]", k, e.getY()));
                                        }
                                        String out = "TIKICOUNT mobs=" + tikis + " cubes=" + cubes + " oldsegments=" + segs
                                                + " heads=" + items + " roofs=" + blocks + " |" + sb;
                                        ctx.getSource().sendSuccess(() -> Component.literal(out), false);
                                        return 1;
                                    })))
                    .then(Commands.literal("bosstide")
                            .then(Commands.argument("slot", IntegerArgumentType.integer())
                                    .executes(ctx -> broodtideTideState(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))))
                    .then(Commands.literal("bossdevourer")
                            .then(Commands.argument("slot", IntegerArgumentType.integer())
                                    .executes(ctx -> devourerBossTest(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))))
                    .then(Commands.literal("attacktest")
                            .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                            .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                            .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                            .then(Commands.argument("range", DoubleArgumentType.doubleArg())
                                    .executes(ctx -> attackTest(ctx.getSource(),
                                            DoubleArgumentType.getDouble(ctx, "x"),
                                            DoubleArgumentType.getDouble(ctx, "y"),
                                            DoubleArgumentType.getDouble(ctx, "z"),
                                            DoubleArgumentType.getDouble(ctx, "range"))))))))
                    .then(Commands.literal("spawnelite")
                            .then(Commands.argument("name", StringArgumentType.word())
                                    .executes(ctx -> spawnElite(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                    .then(Commands.literal("trypick")
                            .then(Commands.argument("id", StringArgumentType.word())
                                    .executes(ctx -> {
                                        var p = ctx.getSource().getPlayer();
                                        if (p == null) {
                                            return 0;
                                        }
                                        String err = CharacterCommand.trySelect(p, StringArgumentType.getString(ctx, "id"));
                                        ctx.getSource().sendSuccess(() -> Component.literal("TRYPICK " + (err == null ? "ok" : "error: " + err)), false);
                                        return 1;
                                    })))
                    .then(Commands.literal("blockat")
                            .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                    .executes(ctx -> {
                                        var pos = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                        var state = ctx.getSource().getLevel().getBlockState(pos);
                                        ctx.getSource().sendSuccess(() -> Component.literal("BLOCKAT " + pos.getX() + "," + pos.getY() + ","
                                                + pos.getZ() + " = " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock())), false);
                                        return 1;
                                    })))
                    .then(Commands.literal("rigreport")
                            .executes(ctx -> rigReport(ctx.getSource())))
                    .then(Commands.literal("spawnveteran")
                            .then(Commands.argument("name", StringArgumentType.word())
                                    .executes(ctx -> spawnVeteran(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                    .then(Commands.literal("granttome")
                            .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("tomeId", StringArgumentType.word())
                                    .executes(ctx -> grantTome(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player"),
                                            StringArgumentType.getString(ctx, "tomeId"))))))
                    .then(Commands.literal("hudstate")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        String d = com.solme.emberfall.network.HudSync.describeLast(target);
                                        int n = com.solme.emberfall.network.HudSync.sendCount(target);
                                        ctx.getSource().sendSuccess(() -> Component.literal("HUD sends=" + n + " " + d), false);
                                        return 1;
                                    })))
                    .then(Commands.literal("weaponoffer")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        com.solme.emberfall.item.WeaponChoiceManager.openMidRunChoice(target, 5);
                                        return 1;
                                    })))
                    .then(Commands.literal("weaponpending")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> {
                                        String d = com.solme.emberfall.item.WeaponChoiceManager
                                                .describePending(EntityArgument.getPlayer(ctx, "player"));
                                        ctx.getSource().sendSuccess(() -> Component.literal("WPEND " + d), false);
                                        return 1;
                                    })))
                    .then(Commands.literal("weaponanswer")
                            .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("id", StringArgumentType.word())
                                    .executes(ctx -> {
                                        String id = StringArgumentType.getString(ctx, "id");
                                        com.solme.emberfall.item.WeaponChoiceManager.answerPending(
                                                EntityArgument.getPlayer(ctx, "player"), id.equals("skip") ? "" : id);
                                        return 1;
                                    }))))
                    .then(Commands.literal("shopbuy")
                            .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("kind", StringArgumentType.word())
                            .then(Commands.argument("item", StringArgumentType.word())
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        // The exact call the client's buy packet makes: the real shop path, not a copy.
                                        com.solme.emberfall.progression.ShopManager.onBuyReceived(target,
                                                new com.solme.emberfall.network.BuyShopItemPayload(
                                                        StringArgumentType.getString(ctx, "kind"),
                                                        StringArgumentType.getString(ctx, "item")));
                                        return 1;
                                    })))))
                    .then(Commands.literal("rolloffers")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        var ids = com.solme.emberfall.tome.TomeOfferGenerator.generateOffers(target)
                                                .stream().map(com.solme.emberfall.tome.Tome::id).toList();
                                        ctx.getSource().sendSuccess(() -> Component.literal("ROLL " + ids), false);
                                        return ids.size();
                                    })))
                    .then(Commands.literal("debugguarantee")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        com.solme.emberfall.tome.TomeOfferGenerator.markNextOfferGuaranteedWeapon(target);
                                        ctx.getSource().sendSuccess(() -> Component.literal("GUARANTEE set"), false);
                                        return 1;
                                    })))
                    .then(Commands.literal("guaranteeflag")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        boolean f = com.solme.emberfall.tome.TomeOfferGenerator.hasGuaranteedWeaponOffer(target);
                                        ctx.getSource().sendSuccess(() -> Component.literal("GUARANTEEFLAG " + f), false);
                                        return f ? 1 : 0;
                                    })))
                    .then(Commands.literal("runhud")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        String d = com.solme.emberfall.network.RunHudSync.describeLast(target);
                                        int n = com.solme.emberfall.network.RunHudSync.sendCount(target);
                                        ctx.getSource().sendSuccess(() -> Component.literal("RUNHUD " + d + " sends=" + n), false);
                                        return 1;
                                    })))
                    .then(Commands.literal("arenabox")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        String d = com.solme.emberfall.world.ArenaBoundary.describe(target);
                                        ctx.getSource().sendSuccess(() -> Component.literal("ARENABOX " + d), false);
                                        return 1;
                                    })))
                    .then(Commands.literal("musicnow")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        String now = com.solme.emberfall.music.RunMusic.nowPlaying(target);
                                        ctx.getSource().sendSuccess(() -> Component.literal("MUSIC " + now), false);
                                        return 1;
                                    })))
                    .then(Commands.literal("musicskip")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        com.solme.emberfall.music.RunMusic.debugFinishCurrent(target);
                                        return 1;
                                    })))
                    .then(Commands.literal("debugaggro")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> {
                                        ServerPlayer who = EntityArgument.getPlayer(ctx, "player");
                                        var box = who.getBoundingBox().inflate(60.0);
                                        var mobs = who.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box,
                                                m -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(m.getType())
                                                        .getNamespace().equals(com.solme.emberfall.EmberfallMod.MOD_ID));
                                        int withTarget = 0;
                                        for (var m : mobs) {
                                            var t = m.getTarget();
                                            if (t != null) {
                                                withTarget++;
                                            }
                                            var fr = m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
                                            String line = "aggro " + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                                                    .getKey(m.getType()).getPath() + " dist=" + String.format("%.1f", m.distanceTo(who))
                                                    + " target=" + (t == null ? "none" : t.getName().getString())
                                                    + " follow=" + (fr == null ? "n/a" : String.format("%.0f", fr.getValue()));
                                            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                                        }
                                        String summary = "aggro summary: " + mobs.size() + " mobs, " + withTarget + " with a target";
                                        ctx.getSource().sendSuccess(() -> Component.literal(summary), false);
                                        return mobs.size();
                                    })))
                    .then(Commands.literal("debugsummons")   // DEBUGSUMMONS: lists Umbral summons (vanilla mobs) with what each is targeting
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> {
                                        ServerPlayer who = EntityArgument.getPlayer(ctx, "player");
                                        var mobs = who.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, who.getBoundingBox().inflate(80.0),
                                                m -> m.getCustomName() != null && m.getCustomName().getString().contains("Umbral "));
                                        int atPlayer = 0, atOther = 0, none = 0;
                                        for (var m : mobs) {
                                            var t = m.getTarget();
                                            String kind = t == null ? "none" : (t instanceof net.minecraft.world.entity.player.Player ? "player" : "OTHER:" + t.getType().toShortString());
                                            if (t == null) none++; else if (t instanceof net.minecraft.world.entity.player.Player) atPlayer++; else atOther++;
                                            String line = "summon " + m.getCustomName().getString().replaceAll("§.", "") + " target=" + kind;
                                            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                                        }
                                        String summary = "summons summary: " + mobs.size() + " total, atPlayer " + atPlayer + ", atOther " + atOther + ", none " + none + ", vetoed " + com.solme.emberfall.world.RunMobTeam.vetoedHits + ", cleared " + com.solme.emberfall.world.RunMobTeam.clearedTargets;
                                        ctx.getSource().sendSuccess(() -> Component.literal(summary), false);
                                        return mobs.size();
                                    })))
                    .then(Commands.literal("debugloadout")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        var lo = com.solme.emberfall.item.Loadout.peek(target);
                                        StringBuilder sb = new StringBuilder("Loadout:");
                                        if (lo != null) {
                                            for (int i = 0; i < lo.size(); i++) {
                                                sb.append(' ').append(lo.slot(i).weapon().id());
                                            }
                                        }
                                        var server = target.level().getServer();
                                        var unlocks = com.solme.emberfall.progression.SlotUnlocks.get(server);
                                        sb.append(" | held=").append(net.minecraft.core.registries.BuiltInRegistries.ITEM
                                                .getKey(target.getMainHandItem().getItem()).getPath());
                                        sb.append(" | weaponSlots=").append(unlocks.slots(target.getUUID(),
                                                com.solme.emberfall.progression.SlotUnlocks.Kind.WEAPON));
                                        sb.append(" tomeSlots=").append(unlocks.slots(target.getUUID(),
                                                com.solme.emberfall.progression.SlotUnlocks.Kind.TOME));
                                        String line = sb.toString();
                                        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                                        return lo == null ? 0 : lo.size();
                                    })))
                    // Weapon growth read-out: one line per slot, "slot i id kills=K level=L meter=M". Separate from debugloadout on
                    // purpose, because older tests parse that command's exact format.
                    .then(Commands.literal("debugweapongrowth")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        var lo = com.solme.emberfall.item.Loadout.peek(target);
                                        if (lo == null || lo.isEmpty()) {
                                            ctx.getSource().sendSuccess(() -> Component.literal("growth: no loadout"), false);
                                            return 0;
                                        }
                                        for (int i = 0; i < lo.size(); i++) {
                                            var sl = lo.slot(i);
                                            String line = "growth slot " + i + " " + sl.weapon().id() + " kills=" + sl.kills()
                                                    + " level=" + sl.level() + " meter=" + sl.meter() + " ults=" + sl.ultimates();
                                            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                                        }
                                        return lo.size();
                                    })
                                    // debugweapongrowth <player> grant <slot> <kills> <meter>: test-only, adds kills and sets the meter
                                    .then(Commands.literal("grant")
                                            .then(Commands.argument("slot", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 3))
                                                    .then(Commands.argument("kills", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 1000))
                                                            .then(Commands.argument("meter", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 2000))
                                                                    .executes(ctx -> {
                                                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                                        var lo = com.solme.emberfall.item.Loadout.peek(target);
                                                                        int si = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "slot");
                                                                        if (lo == null || si >= lo.size()) {
                                                                            ctx.getSource().sendSuccess(() -> Component.literal("growth grant: no such slot"), false);
                                                                            return 0;
                                                                        }
                                                                        int k = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "kills");
                                                                        int m = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "meter");
                                                                        for (int n = 0; n < k; n++) {
                                                                            lo.slot(si).addKill();
                                                                        }
                                                                        lo.slot(si).setMeter(m);
                                                                        var sl = lo.slot(si);
                                                                        String line = "growth granted slot " + si + " kills=" + sl.kills() + " level=" + sl.level() + " meter=" + sl.meter() + " ults=" + sl.ultimates();
                                                                        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                                                                        return 1;
                                                                    })))))))
                    .then(Commands.literal("debuggold")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        int gold = com.solme.emberfall.pickup.PickupSystem.gold(target);
                                        ctx.getSource().sendSuccess(() -> Component.literal("Gold: " + gold), false);
                                        return gold;
                                    })))
                    .then(Commands.literal("debugspendgold")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100000))
                                            .executes(ctx -> {
                                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                int n = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount");
                                                boolean spent = com.solme.emberfall.pickup.PickupSystem.spendGold(target, n);
                                                int left = com.solme.emberfall.pickup.PickupSystem.gold(target);
                                                ctx.getSource().sendSuccess(() -> Component.literal(
                                                        "spendgold " + n + " -> " + (spent ? "spent" : "refused") + ", gold now " + left), false);
                                                return spent ? 1 : 0;
                                            }))))
                    .then(Commands.literal("debugpickup")
                            .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("kind", StringArgumentType.word())
                            .then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100000))
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        String kind = StringArgumentType.getString(ctx, "kind");
                                        com.solme.emberfall.pickup.PickupSystem.Kind k = "gold".equals(kind)
                                                ? com.solme.emberfall.pickup.PickupSystem.Kind.GOLD
                                                : com.solme.emberfall.pickup.PickupSystem.Kind.XP;
                                        int amount = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount");
                                        com.solme.emberfall.pickup.PickupSystem.spawn(
                                                (net.minecraft.server.level.ServerLevel) target.level(),
                                                target.position().add(2.5, 0.4, 0.0), k, amount);
                                        return 1;
                                    })))))
                    .then(Commands.literal("debugstreak")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> debugStreak(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player")))))
                    .then(Commands.literal("debugarmed")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> debugArmed(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player")))))
                    .then(Commands.literal("selectweapon")
                            .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("weaponId", StringArgumentType.word())
                                    .executes(ctx -> selectWeapon(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player"),
                                            StringArgumentType.getString(ctx, "weaponId"))))))
                    .then(Commands.literal("buyweapon")
                            .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("weaponId", StringArgumentType.word())
                                    .executes(ctx -> buyWeapon(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player"),
                                            StringArgumentType.getString(ctx, "weaponId"))))))
                    .then(Commands.literal("weapons")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> listWeapons(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player")))))
                    .then(Commands.literal("givecurrency")
                            .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("amount", IntegerArgumentType.integer())
                                    .executes(ctx -> giveCurrency(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player"),
                                            IntegerArgumentType.getInteger(ctx, "amount"))))))
                    .then(Commands.literal("buyupgrade")
                            .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("upgradeId", StringArgumentType.word())
                                    .executes(ctx -> buyUpgrade(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player"),
                                            StringArgumentType.getString(ctx, "upgradeId"))))))
                    .then(Commands.literal("upgrades")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> listUpgrades(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player")))))
                    .then(Commands.literal("tomeopen")
                            .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("level", IntegerArgumentType.integer())
                                    .executes(ctx -> tomeOpen(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player"),
                                            IntegerArgumentType.getInteger(ctx, "level"))))))
                    .then(Commands.literal("tomereroll")
                            .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("level", IntegerArgumentType.integer())
                                    .executes(ctx -> tomeReroll(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player"),
                                            IntegerArgumentType.getInteger(ctx, "level"))))))
                    .then(Commands.literal("tomebanish")
                            .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("level", IntegerArgumentType.integer())
                            .then(Commands.argument("tomeId", StringArgumentType.word())
                                    .executes(ctx -> tomeBanish(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player"),
                                            IntegerArgumentType.getInteger(ctx, "level"),
                                            StringArgumentType.getString(ctx, "tomeId")))))))
                    .then(Commands.literal("tomeskip")
                            .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("level", IntegerArgumentType.integer())
                                    .executes(ctx -> tomeSkip(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player"),
                                            IntegerArgumentType.getInteger(ctx, "level"))))))
                    .then(Commands.literal("tomecharges")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> tomeCharges(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player")))))
                    .then(Commands.literal("tomeoffers")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> tomeOffers(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player")))))
                    .then(Commands.literal("buildinfo")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> buildInfo(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "player")))))
            );
        });
    }

    /** Debug/test-only: lists every Tome id and stack count currently in this player's run build. */
    private static int buildInfo(CommandSourceStack source, ServerPlayer player) {
        java.util.Map<String, Integer> all = com.solme.emberfall.tome.PlayerBuild.allOf(player);
        source.sendSuccess(() -> Component.literal(
                player.getGameProfile().name() + "'s build: " + all), true);
        return 1;
    }

    /** Debug/test-only: lists the currently-offered Tome ids for this player's pending choice screen. */
    private static int tomeOffers(CommandSourceStack source, ServerPlayer player) {
        java.util.List<String> ids = com.solme.emberfall.tome.TomeChoiceManager.currentOfferIds(player);
        source.sendSuccess(() -> Component.literal(
                player.getGameProfile().name() + "'s current offers: " + ids), true);
        return 1;
    }

    /**
     * Dev/test-only mirror of the Tome Choice screen's C2S packets
     * ({@link com.solme.emberfall.network.ChooseTomePayload}/{@code RerollTomePayload}/{@code BanishTomePayload}) -
     * the headless bot test client can't render/click
     * {@link com.solme.emberfall.tome.TomeChoiceManager}'s client-only
     * {@code TomeChoiceScreen}, so these drive the exact same server-side
     * state machine directly by constructing the payload records and
     * calling the manager's real handlers - not a re-implementation, the
     * literal same code path a real client's clicks would hit.
     */
    private static int tomeOpen(CommandSourceStack source, ServerPlayer player, int level) {
        com.solme.emberfall.tome.TomeChoiceManager.openChoice(player, level);
        source.sendSuccess(() -> Component.literal("Opened Tome Choice at level " + level + " for " + player.getGameProfile().name()), true);
        return 1;
    }

    private static int tomeReroll(CommandSourceStack source, ServerPlayer player, int level) {
        com.solme.emberfall.tome.TomeChoiceManager.onRerollReceived(player, new com.solme.emberfall.network.RerollTomePayload(level));
        source.sendSuccess(() -> Component.literal("Reroll requested for " + player.getGameProfile().name()), true);
        return 1;
    }

    private static int tomeBanish(CommandSourceStack source, ServerPlayer player, int level, String tomeId) {
        com.solme.emberfall.tome.TomeChoiceManager.onBanishReceived(player, new com.solme.emberfall.network.BanishTomePayload(level, tomeId));
        source.sendSuccess(() -> Component.literal("Banish requested for " + tomeId + " by " + player.getGameProfile().name()), true);
        return 1;
    }

    private static int tomeSkip(CommandSourceStack source, ServerPlayer player, int level) {
        com.solme.emberfall.tome.TomeChoiceManager.onChoiceReceived(player, new com.solme.emberfall.network.ChooseTomePayload(level, -1));
        source.sendSuccess(() -> Component.literal("Skip requested for " + player.getGameProfile().name()), true);
        return 1;
    }

    private static int tomeCharges(CommandSourceStack source, ServerPlayer player) {
        int rerolls = com.solme.emberfall.tome.PlayerTomeCharges.rerollsRemaining(player);
        int banishes = com.solme.emberfall.tome.PlayerTomeCharges.banishesRemaining(player);
        int price = com.solme.emberfall.tome.PlayerTomeCharges.nextGoldRerollPrice(player);
        int gold = com.solme.emberfall.pickup.PickupSystem.gold(player);
        source.sendSuccess(() -> Component.literal(
                player.getGameProfile().name() + ": rerollsRemaining=" + rerolls + " banishesRemaining=" + banishes
                        + " goldPrice=" + price + " gold=" + gold), true);
        return 1;
    }

    private static int capture(CommandSourceStack source, String name, BlockPos from, BlockPos to) {
        ServerLevel level = source.getLevel();
        BlockPos min = new BlockPos(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()));
        Vec3i size = new Vec3i(
                Math.abs(from.getX() - to.getX()) + 1,
                Math.abs(from.getY() - to.getY()) + 1,
                Math.abs(from.getZ() - to.getZ()) + 1);

        Identifier id = EmberfallMod.id(name);
        StructureTemplate template = level.getStructureManager().getOrCreate(id);
        template.fillFromWorld(level, min, size, true, List.of(Blocks.STRUCTURE_VOID));
        boolean saved = level.getStructureManager().save(id);

        if (saved) {
            source.sendSuccess(() -> Component.literal(
                    "Captured " + size.getX() + "x" + size.getY() + "x" + size.getZ()
                            + " region as '" + name + "' from " + min), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to save structure '" + name + "'"));
            return 0;
        }
    }

    private static int paste(CommandSourceStack source, String structureName) {
        try {
            ArenaInstance instance = RunManager.pasteArena(source.getServer(), EmberfallMod.id(structureName));
            StringBuilder report = new StringBuilder();
            report.append("Pasted '").append(structureName).append("' -> slot ").append(instance.slot())
                    .append(" at ").append(instance.origin()).append(". Markers: ");
            if (instance.markers().isEmpty()) {
                report.append("none");
            } else {
                for (Map.Entry<String, List<BlockPos>> entry : instance.markers().entrySet()) {
                    report.append("[").append(entry.getKey().isEmpty() ? "<untyped>" : entry.getKey())
                            .append(" x").append(entry.getValue().size()).append("] ");
                }
            }
            String message = report.toString();
            source.sendSuccess(() -> Component.literal(message), true);
            return instance.slot();
        } catch (IllegalStateException e) {
            source.sendFailure(Component.literal("Paste failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int mapBuild(CommandSourceStack src, int slot) {
        net.minecraft.server.level.ServerLevel level = src.getServer().getLevel(com.solme.emberfall.world.Dimensions.EXPEDITION);
        if (level == null) {
            src.sendFailure(Component.literal("expedition level not loaded"));
            return 0;
        }
        net.minecraft.core.BlockPos origin = RunManager.originForSlot(slot);
        com.solme.emberfall.world.MapManager.ensureBuilt(level, slot, origin,
                b -> src.sendSuccess(() -> Component.literal("MAPDONE " + com.solme.emberfall.world.MapManager.lastReport()), false));
        src.sendSuccess(() -> Component.literal("MAPSTART slot " + slot + " origin " + origin.toShortString()), false);
        return 1;
    }

    /** Debug trigger for the Rift show: opens (or closes) one 6 blocks in front of whoever ran it, facing them. Entity-free. */
    private static int riftShow(CommandSourceStack source, boolean closing) {
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        if (closing) {
            int n = 0;
            int ev = 0;
            for (var r : com.solme.emberfall.rift.RiftManager.all()) { com.solme.emberfall.rift.RiftManager.close(r); n++; ev = r.showEvents; }
            final int closed = n;
            final int events = ev;
            source.sendSuccess(() -> Component.literal("RIFT closing events=" + events + " rifts=" + closed), false);
            return 1;
        }
        var look = player.getLookAngle();
        double x = player.getX() + look.x * 6.0;
        double z = player.getZ() + look.z * 6.0;
        double y = player.getY() + 5.0;
        int facing = com.solme.emberfall.rift.RiftSpot.facingToward(player.getX() - x, player.getZ() - z);
        var res = com.solme.emberfall.rift.RiftManager.open(source.getLevel(), x, y, z, facing, source.getLevel().getGameTime());
        source.sendSuccess(() -> Component.literal(res.ok() ? "RIFT opening events=" + res.rift().showEvents : "RIFT refused: " + res.refusal()), false);
        return res.ok() ? 1 : 0;
    }

    private static int teardown(CommandSourceStack source, int slot) {
        ArenaInstance instance = RunManager.getActive(slot);
        if (instance == null) {
            source.sendFailure(Component.literal("No active arena in slot " + slot));
            return 0;
        }
        WaveDirector.stop(slot);
        RunManager.teardownArena(source.getServer(), instance);
        source.sendSuccess(() -> Component.literal("Tore down arena in slot " + slot), true);
        return 1;
    }

    private static int waveStart(CommandSourceStack source, int slot) {
        ArenaInstance instance = RunManager.getActive(slot);
        if (instance == null) {
            source.sendFailure(Component.literal("No active arena in slot " + slot));
            return 0;
        }
        if (instance.markers("spawn_point").isEmpty()) {
            source.sendFailure(Component.literal("Arena in slot " + slot + " has no 'spawn_point' markers"));
            return 0;
        }
        WaveDirector.start(instance);
        source.sendSuccess(() -> Component.literal("Wave Director started for slot " + slot
                + " (" + instance.markers("spawn_point").size() + " spawn points)"), true);
        return 1;
    }

    private static int waveStop(CommandSourceStack source, int slot) {
        if (WaveDirector.get(slot) == null) {
            source.sendFailure(Component.literal("No active Wave Director for slot " + slot));
            return 0;
        }
        WaveDirector.stop(slot);
        source.sendSuccess(() -> Component.literal("Wave Director stopped for slot " + slot), true);
        return 1;
    }

    private static int waveStatus(CommandSourceStack source, int slot) {
        WaveDirector director = WaveDirector.get(slot);
        if (director == null) {
            source.sendFailure(Component.literal("No active Wave Director for slot " + slot));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Slot " + slot + ": tier=" + director.tier()
                        + " threat=" + String.format("%.2f", director.threatLevel())
                        + " totalSpawned=" + director.totalSpawned()
                        + " totalVeteransSpawned=" + director.totalVeteransSpawned()
                        + " totalElitesSpawned=" + director.totalElitesSpawned()
                        + " partySize=" + RunManager.partySize(slot)), true);
        return 1;
    }

    private static int join(CommandSourceStack source, int slot, ServerPlayer player) {
        ArenaInstance instance = RunManager.getActive(slot);
        if (instance == null) {
            source.sendFailure(Component.literal("No active arena in slot " + slot));
            return 0;
        }
        ServerLevel level = source.getServer().getLevel(com.solme.emberfall.world.Dimensions.EXPEDITION);
        RunManager.joinPlayer(level, instance, player);
        source.sendSuccess(() -> Component.literal(
                player.getGameProfile().name() + " joined slot " + slot), true);
        return 1;
    }

    private static int leave(CommandSourceStack source, ServerPlayer player) {
        Integer slot = RunManager.slotOf(player);
        long reward = 0;
        if (slot != null) {
            reward = com.solme.emberfall.progression.RunRewardCalculator.awardRunReward(source.getServer(), player, slot);
        }
        RunManager.leavePlayer(player);
        long finalReward = reward;
        source.sendSuccess(() -> Component.literal(
                player.getGameProfile().name() + " left their run (+" + finalReward + " Silver)"), true);
        return 1;
    }

    private static int balance(CommandSourceStack source, ServerPlayer player) {
        long balance = com.solme.emberfall.progression.MetaProgressionData.get(source.getServer()).getBalance(player.getUUID());
        source.sendSuccess(() -> Component.literal(
                player.getGameProfile().name() + " has " + balance + " Silver"), true);
        return (int) balance;
    }

    /**
     * Headless test for AutoAttackSystem.findNearestHostile - exercises the
     * real nearest-in-range targeting query against whatever emberfall
     * hostiles are actually alive in the expedition dimension right now, from
     * an arbitrary point, without needing a connected client.
     */
    private static int attackTest(CommandSourceStack source, double x, double y, double z, double range) {
        ServerLevel level = source.getServer().getLevel(com.solme.emberfall.world.Dimensions.EXPEDITION);
        if (level == null) {
            source.sendFailure(Component.literal("expedition dimension not loaded"));
            return 0;
        }
        Vec3 origin = new Vec3(x, y, z);
        LivingEntity found = AutoAttackSystem.findNearestHostile(level, origin, range);
        if (found == null) {
            source.sendSuccess(() -> Component.literal(
                    "attacktest from " + origin + " range " + range + ": no target found"), true);
            return 1;
        }
        double dist = found.position().distanceTo(origin);
        source.sendSuccess(() -> Component.literal(
                "attacktest from " + origin + " range " + range + ": found "
                        + found.getType().toShortString() + " id=" + found.getId()
                        + " at distance " + String.format("%.2f", dist)), true);
        return 1;
    }

    /**
     * Manually forces slot's Wave Director to spawn the Hydra boss right
     * now instead of waiting for the real time mark - for fast iteration on
     * the composite-entity rig (design doc 6.2) without sitting through
     * BOSS_SPAWN_AT_TICK every test run.
     */
    private static int bossTest(CommandSourceStack source, int slot) {
        WaveDirector director = WaveDirector.get(slot);
        if (director == null) {
            source.sendFailure(Component.literal("No active Wave Director for slot " + slot));
            return 0;
        }
        if (director.isBossActive()) {
            source.sendFailure(Component.literal("Boss already active for slot " + slot));
            return 0;
        }
        // An in-place run happens in whatever level the player stands in, so spawn the boss in the arena's OWN
        // level. The old fixed EXPEDITION dimension put the boss somewhere the player never was.
        var arena = com.solme.emberfall.world.RunManager.getActive(slot);
        ServerLevel level = arena != null ? arena.level() : source.getServer().getLevel(com.solme.emberfall.world.Dimensions.EXPEDITION);
        director.triggerBossNow(level);
        source.sendSuccess(() -> Component.literal(com.solme.emberfall.boss.FirstBoss.displayName(com.solme.emberfall.boss.FirstBoss.current()) + " boss triggered for slot " + slot), true);
        return 1;
    }

    /** Read-only test hook: the Broodtide's fight tick, Tide state and the armour factor right now, so a live test can compare damage dealt with what the clock says. */
    private static int broodtideTideState(CommandSourceStack source, int slot) {
        var arena = com.solme.emberfall.world.RunManager.getActive(slot);
        if (arena == null) {
            source.sendFailure(Component.literal("No active run for slot " + slot));
            return 0;
        }
        var bodies = arena.level().getEntitiesOfClass(com.solme.emberfall.entity.BroodtideBody.class,
                new net.minecraft.world.phys.AABB(-30000000, -64, -30000000, 30000000, 400, 30000000));
        if (bodies.isEmpty()) {
            source.sendFailure(Component.literal("No Broodtide in slot " + slot));
            return 0;
        }
        var b = bodies.get(0);
        var gr = b.grabber();
        source.sendSuccess(() -> Component.literal("BROODTIDE tick=" + b.fightTick() + " tide=" + b.tide()
                + " armour=" + com.solme.emberfall.boss.TideClock.armourAt(b.fightTick())
                + (gr == null ? "" : " phase=" + gr.phase() + " grabs=" + gr.grabsStarted() + " impulses=" + gr.impulsesApplied() + " active=" + gr.activeGrabs())), false);
        return 1;
    }

    /** Same fast-iteration purpose as {@link #bossTest}, but for the Devourer rig. */
    private static int devourerBossTest(CommandSourceStack source, int slot) {
        WaveDirector director = WaveDirector.get(slot);
        if (director == null) {
            source.sendFailure(Component.literal("No active Wave Director for slot " + slot));
            return 0;
        }
        if (director.isBossActive()) {
            source.sendFailure(Component.literal("Boss already active for slot " + slot));
            return 0;
        }
        // Same in-place-run rule as bossTest: spawn in the arena's own level, not the retired EXPEDITION dimension.
        var arena = com.solme.emberfall.world.RunManager.getActive(slot);
        ServerLevel level = arena != null ? arena.level() : source.getServer().getLevel(com.solme.emberfall.world.Dimensions.EXPEDITION);
        director.triggerDevourerNow(level);
        source.sendSuccess(() -> Component.literal("Devourer boss triggered for slot " + slot), true);
        return 1;
    }

    /**
     * Debug-only direct spawn for a Corrupted archetype at the command
     * source's position, bypassing WaveDirector's rare roll - so each
     * archetype's stats/equipment/abilities can be exercised live without
     * waiting on the real spawn chance during dev iteration.
     */
    /**
     * Debug/test-only: for every display entity within 8 blocks of the caller, logs its offset from the
     * nearest Emberfall mob expressed in that mob's BODY frame (right / up / forward, in blocks). Lets a
     * headless test verify accessory placement numerically, since a bot cannot see rendering.
     */
    private static int rigReport(CommandSourceStack source) {
        net.minecraft.server.level.ServerLevel level = source.getLevel();
        net.minecraft.world.phys.Vec3 c = source.getPosition();
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(c, c).inflate(8.0);
        java.util.List<net.minecraft.world.entity.Mob> mobs = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box,
                m -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getNamespace().equals("emberfall"));
        java.util.List<net.minecraft.world.entity.Display> displays = level.getEntitiesOfClass(net.minecraft.world.entity.Display.class, box);
        int reported = 0;
        for (net.minecraft.world.entity.Display d : displays) {
            net.minecraft.world.entity.Mob best = null;
            double bestD = Double.MAX_VALUE;
            for (net.minecraft.world.entity.Mob m : mobs) {
                double dist = m.distanceToSqr(d);
                if (dist < bestD) { bestD = dist; best = m; }
            }
            if (best == null || bestD > 9.0) continue;
            double dx = d.getX() - best.getX(), dy = d.getY() - best.getY(), dz = d.getZ() - best.getZ();
            double yaw = Math.toRadians(best.yBodyRot);
            double fwd = dx * -Math.sin(yaw) + dz * Math.cos(yaw);
            double right = dx * -Math.cos(yaw) + dz * -Math.sin(yaw);
            EmberfallMod.LOGGER.info("RIGREPORT mob={} scale={} display={} right={} up={} forward={} vehicle={} passengers={}",
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(best.getType()).getPath(),
                    String.format("%.2f", best.getScale()),
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(d.getType()).getPath(),
                    String.format("%.3f", right / best.getScale()), String.format("%.3f", dy / best.getScale()),
                    String.format("%.3f", fwd / best.getScale()), d.isPassenger(), best.getPassengers().size());
            reported++;
        }
        final int n = reported;
        source.sendSuccess(() -> Component.literal("rigreport: " + n + " display(s) reported (see server log)"), false);
        return n;
    }

    /** Testing hook for the 3 horde-filler types' Veteran tier (design doc 2.4/4.3) - mirrors {@link #spawnElite}'s spirit. */
    private static int spawnVeteran(CommandSourceStack source, String name) {
        try {
            ServerLevel level = source.getLevel();
            BlockPos pos = BlockPos.containing(source.getPosition());
            switch (name) {
                case "horde_zombie" -> {
                    HordeZombie zombie = new HordeZombie(ModEntities.HORDE_ZOMBIE, level);
                    zombie.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    zombie.becomeVeteran();
                    level.addFreshEntity(zombie);
                    source.sendSuccess(() -> Component.literal("Spawned Veteran Horde Zombie at " + pos), true);
                    return 1;
                }
                case "horde_skeleton" -> {
                    HordeSkeleton skeleton = new HordeSkeleton(ModEntities.HORDE_SKELETON, level);
                    skeleton.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    skeleton.equipBow();
                    skeleton.becomeVeteran();
                    level.addFreshEntity(skeleton);
                    source.sendSuccess(() -> Component.literal("Spawned Veteran Horde Skeleton at " + pos), true);
                    return 1;
                }
                case "horde_imp" -> {
                    com.solme.emberfall.entity.HordeImp imp = new com.solme.emberfall.entity.HordeImp(ModEntities.HORDE_IMP, level);
                    imp.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                    imp.prepare();
                    imp.becomeVeteran();
                    level.addFreshEntity(imp);
                    source.sendSuccess(() -> Component.literal("Spawned Veteran Horde Imp at " + pos), true);
                    return 1;
                }
                case "horde_shieldbearer" -> {
                    com.solme.emberfall.entity.HordeShieldbearer shield = new com.solme.emberfall.entity.HordeShieldbearer(ModEntities.HORDE_SHIELDBEARER, level);
                    shield.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    shield.prepare();
                    shield.becomeVeteran();
                    level.addFreshEntity(shield);
                    source.sendSuccess(() -> Component.literal("Spawned Veteran Horde Shieldbearer at " + pos), true);
                    return 1;
                }
                case "horde_charger" -> {
                    com.solme.emberfall.entity.HordeCharger charger = new com.solme.emberfall.entity.HordeCharger(ModEntities.HORDE_CHARGER, level);
                    charger.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    charger.prepare();
                    charger.becomeVeteran();
                    level.addFreshEntity(charger);
                    source.sendSuccess(() -> Component.literal("Spawned Veteran Horde Charger at " + pos), true);
                    return 1;
                }
                case "horde_spitter" -> {
                    com.solme.emberfall.entity.HordeSpitter spitter = new com.solme.emberfall.entity.HordeSpitter(ModEntities.HORDE_SPITTER, level);
                    spitter.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    spitter.prepare();
                    spitter.becomeVeteran();
                    level.addFreshEntity(spitter);
                    source.sendSuccess(() -> Component.literal("Spawned Veteran Horde Spitter at " + pos), true);
                    return 1;
                }
                case "horde_bomber" -> {
                    com.solme.emberfall.entity.HordeBomber bomber = new com.solme.emberfall.entity.HordeBomber(ModEntities.HORDE_BOMBER, level);
                    bomber.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    bomber.prepare();
                    bomber.becomeVeteran();
                    level.addFreshEntity(bomber);
                    source.sendSuccess(() -> Component.literal("Spawned Veteran Horde Bomber at " + pos), true);
                    return 1;
                }
                case "horde_witch" -> {
                    com.solme.emberfall.entity.HordeWitch witch = new com.solme.emberfall.entity.HordeWitch(ModEntities.HORDE_WITCH, level);
                    witch.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    witch.becomeVeteran();
                    level.addFreshEntity(witch);
                    source.sendSuccess(() -> Component.literal("Spawned Veteran Horde Witch at " + pos), true);
                    return 1;
                }
                case "horde_spider" -> {
                    HordeSpider spider = new HordeSpider(ModEntities.HORDE_SPIDER, level);
                    spider.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    spider.becomeVeteran();
                    level.addFreshEntity(spider);
                    source.sendSuccess(() -> Component.literal("Spawned Veteran Horde Spider at " + pos), true);
                    return 1;
                }
                case "tiki_magma" -> {
                    com.solme.emberfall.entity.TikiMagma.spawn(level,
                            new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), true);
                    source.sendSuccess(() -> Component.literal("Spawned Veteran Tiki Magma at " + pos), true);
                    return 1;
                }
                default -> {
                    source.sendFailure(Component.literal("Unknown horde-filler '" + name + "'"));
                    return 0;
                }
            }
        } catch (Throwable t) {
            EmberfallMod.LOGGER.error("spawnveteran '{}' failed", name, t);
            source.sendFailure(Component.literal("spawnveteran failed: " + t));
            return 0;
        }
    }

    private static int spawnElite(CommandSourceStack source, String name) {
        try {
            ServerLevel level = source.getLevel();
            BlockPos pos = BlockPos.containing(source.getPosition());
            switch (name) {
                case "cinderbrand_reaver" -> {
                    CinderbrandReaver.spawn(level, pos, 1.0);
                    source.sendSuccess(() -> Component.literal("Spawned Cinderbrand Reaver at " + pos), true);
                    return 1;
                }
                case "blightfeather_marksman" -> {
                    BlightfeatherMarksman.spawn(level, pos, 1.0);
                    source.sendSuccess(() -> Component.literal("Spawned Blightfeather Marksman at " + pos), true);
                    return 1;
                }
                case "umbral_magus" -> {
                    UmbralMagus.spawn(level, pos, 1.0);
                    source.sendSuccess(() -> Component.literal("Spawned Umbral Magus at " + pos), true);
                    return 1;
                }
                case "corrupted_sentinel" -> {
                    CorruptedSentinel.spawn(level, pos, 1.0);
                    source.sendSuccess(() -> Component.literal("Spawned Corrupted Sentinel at " + pos), true);
                    return 1;
                }
                case "bonecaller_necromancer" -> {
                    BonecallerNecromancer.spawn(level, pos, 1.0);
                    source.sendSuccess(() -> Component.literal("Spawned Bonecaller Necromancer at " + pos), true);
                    return 1;
                }
                case "plague_colossus" -> {
                    PlagueColossus.spawn(level, pos, 1.0);
                    source.sendSuccess(() -> Component.literal("Spawned Plague Colossus at " + pos), true);
                    return 1;
                }
                case "boil_ridden_marksman" -> {
                    BoilRiddenMarksman.spawn(level, pos, 1.0);
                    source.sendSuccess(() -> Component.literal("Spawned Boil-Ridden Marksman at " + pos), true);
                    return 1;
                }
                case "broodmother_stalker" -> {
                    BroodmotherStalker.spawn(level, pos, 1.0);
                    source.sendSuccess(() -> Component.literal("Spawned Broodmother Stalker at " + pos), true);
                    return 1;
                }
                case "pink_slime" -> {
                    // Debug-only: normally only the Umbral Magus's Cauldron ability spawns this.
                    com.solme.emberfall.entity.PinkSlime.spawn(level,
                            new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), null);
                    source.sendSuccess(() -> Component.literal("Spawned Pink Slime at " + pos), true);
                    return 1;
                }
                case "tiki_magma" -> {
                    // Rolls the normal CORRUPTED_CHANCE the automatic spawn track uses - not deterministic,
                    // see tiki_magma_corrupted below for a guaranteed-Corrupted debug spawn.
                    com.solme.emberfall.entity.TikiMagma.spawnElite(level, pos, 1.0);
                    source.sendSuccess(() -> Component.literal("Spawned Elite-tier Tiki Magma at " + pos), true);
                    return 1;
                }
                case "tiki_magma_corrupted" -> {
                    // Forces the Corrupted roll for deterministic testing by retrying until it lands -
                    // CORRUPTED_CHANCE isn't exposed outside TikiMagma, so this is the simplest reliable
                    // way to get a guaranteed Corrupted spawn from a debug command.
                    com.solme.emberfall.entity.TikiMagma spawned;
                    int attempts = 0;
                    do {
                        spawned = com.solme.emberfall.entity.TikiMagma.spawnElite(level, pos, 1.0);
                        attempts++;
                        if (spawned.getEliteTier() == com.solme.emberfall.entity.TikiMagma.EliteTier.CORRUPTED) {
                            break;
                        }
                        spawned.discard();
                    } while (attempts < 200);
                    int finalAttempts = attempts;
                    source.sendSuccess(() -> Component.literal("Spawned Corrupted Tiki Idol at " + pos + " (attempts: " + finalAttempts + ")"), true);
                    return 1;
                }
                default -> {
                    source.sendFailure(Component.literal("Unknown elite '" + name + "'"));
                    return 0;
                }
            }
        } catch (Throwable t) {
            EmberfallMod.LOGGER.error("spawnelite '{}' failed", name, t);
            source.sendFailure(Component.literal("spawnelite failed: " + t));
            return 0;
        }
    }

    /**
     * Dev/test-only: grants a Tome by id directly, bypassing the level-up
     * choice screen, so a specific Tome's on-hit/synergy behavior can be
     * exercised deterministically instead of waiting on random offers.
     * Runs the exact same apply/synergy/recompute sequence as a real pick
     * ({@link com.solme.emberfall.tome.TomeChoiceManager}'s resolve step).
     */
    /** Debug/test-only: reports the player's current MELEE_DUAL/RANGED_AOE/MELEE_SINGLE-style
     *  hit streak without mutating it - lets a headless test bot precisely time a HP-force
     *  right before the streak's Nth (Empowered/bonus) hit, instead of guessing from cadence
     *  math alone. See PlayerWeapon#peekStreak. */
    private static int debugStreak(CommandSourceStack source, ServerPlayer player) {
        int streak = com.solme.emberfall.item.PlayerWeapon.peekStreak(player);
        source.sendSuccess(() -> Component.literal(player.getGameProfile().name() + " streak: " + streak), true);
        return streak;
    }

    /** Debug/test-only: reports whether Steady Hand's 3rd-copy re-arm flag is currently
     *  set for the player, without consuming it. See PlayerWeapon#peekSteadyHandArmed. */
    private static int debugArmed(CommandSourceStack source, ServerPlayer player) {
        boolean armed = com.solme.emberfall.item.PlayerWeapon.peekSteadyHandArmed(player);
        source.sendSuccess(() -> Component.literal(player.getGameProfile().name() + " steadyHandArmed: " + armed), true);
        return armed ? 1 : 0;
    }

    private static int grantTome(CommandSourceStack source, ServerPlayer player, String tomeId) {
        com.solme.emberfall.tome.Tome tome = com.solme.emberfall.tome.TomePool.byId(tomeId);
        if (tome == null) {
            source.sendFailure(Component.literal("Unknown tome '" + tomeId + "'"));
            return 0;
        }
        int stackIndex = com.solme.emberfall.tome.PlayerBuild.grant(player, tome.id());
        tome.onApply().apply(player, stackIndex);
        com.solme.emberfall.tome.SynergyEffects.checkAndApply(player);
        com.solme.emberfall.tome.CombatStats.recompute(player);
        source.sendSuccess(() -> Component.literal(
                "Granted " + tome.displayName() + " (stack " + stackIndex + ") to " + player.getGameProfile().name()), true);
        return 1;
    }

    /**
     * Dev/test-only: directly force-equips a weapon by id, bypassing the
     * Weapon Choice screen entirely - the headless bot test client can't
     * render/click that GUI, so this is the only way to exercise each
     * moveset deterministically. Does NOT check unlock status (a real
     * player picks only from their own unlocked list via the screen; this
     * is a raw debug override, same spirit as granttome bypassing the Tome
     * Choice screen).
     */
    private static int selectWeapon(CommandSourceStack source, ServerPlayer player, String weaponId) {
        com.solme.emberfall.item.WeaponType weapon = com.solme.emberfall.item.WeaponPool.byId(weaponId);
        if (weapon == null) {
            source.sendFailure(Component.literal("Unknown weapon '" + weaponId + "'"));
            return 0;
        }
        // Raw debug override: adds to a free slot if there is one, otherwise replaces slot 1 (the held weapon).
        // Ignores the player's unlocked slot count on purpose, like it ignores weapon unlocks.
        var loadout = com.solme.emberfall.item.Loadout.of(player);
        if (loadout.isEmpty() || loadout.has(weapon.id())) {
            com.solme.emberfall.item.PlayerWeapon.equip(player, weapon);
        } else if (loadout.size() < com.solme.emberfall.item.Loadout.MAX_SLOTS) {
            loadout.add(weapon, com.solme.emberfall.item.Loadout.MAX_SLOTS);
        } else {
            com.solme.emberfall.item.PlayerWeapon.equipInto(player, weapon, 0);
        }
        source.sendSuccess(() -> Component.literal(
                "Equipped " + weapon.displayName() + " on " + player.getGameProfile().name()
                        + " (" + loadout.size() + " weapon slots in use)"), true);
        return 1;
    }

    /**
     * Spends meta-currency to permanently unlock a weapon (design doc:
     * "buy them with the meta-currency shop") - a plain command interface
     * for now; the polished shop GUI is the next task in the owner's
     * stated ordering (weapon system, then currency shop). Fails cleanly
     * if the weapon is unknown, already unlocked, or the balance is short.
     */
    private static int buyWeapon(CommandSourceStack source, ServerPlayer player, String weaponId) {
        com.solme.emberfall.item.WeaponType weapon = com.solme.emberfall.item.WeaponPool.byId(weaponId);
        if (weapon == null) {
            source.sendFailure(Component.literal("Unknown weapon '" + weaponId + "'"));
            return 0;
        }
        com.solme.emberfall.progression.WeaponUnlocks unlocks =
                com.solme.emberfall.progression.WeaponUnlocks.get(source.getServer());
        if (unlocks.isUnlocked(player.getUUID(), weaponId)) {
            source.sendFailure(Component.literal(player.getGameProfile().name() + " already has " + weapon.displayName()));
            return 0;
        }
        com.solme.emberfall.progression.MetaProgressionData currency =
                com.solme.emberfall.progression.MetaProgressionData.get(source.getServer());
        long balance = currency.getBalance(player.getUUID());
        if (balance < weapon.unlockCost()) {
            source.sendFailure(Component.literal(
                    player.getGameProfile().name() + " needs " + weapon.unlockCost() + " Silver for "
                            + weapon.displayName() + " (has " + balance + ")"));
            return 0;
        }
        currency.addCurrency(player.getUUID(), -weapon.unlockCost());
        unlocks.unlock(player.getUUID(), weaponId);
        source.sendSuccess(() -> Component.literal(
                player.getGameProfile().name() + " unlocked " + weapon.displayName()
                        + " for " + weapon.unlockCost() + " meta-currency"), true);
        return 1;
    }

    /** Lists every weapon in the pool with cost and this player's unlock status. */
    private static int listWeapons(CommandSourceStack source, ServerPlayer player) {
        com.solme.emberfall.progression.WeaponUnlocks unlocks =
                com.solme.emberfall.progression.WeaponUnlocks.get(source.getServer());
        StringBuilder sb = new StringBuilder(player.getGameProfile().name()).append("'s weapons:");
        for (com.solme.emberfall.item.WeaponType weapon : com.solme.emberfall.item.WeaponPool.ALL) {
            boolean owned = unlocks.isUnlocked(player.getUUID(), weapon.id());
            sb.append("\n  ").append(owned ? "[OWNED] " : "[LOCKED " + weapon.unlockCost() + "] ")
                    .append(weapon.displayName()).append(" (").append(weapon.moveset()).append(") - id=").append(weapon.id());
        }
        String message = sb.toString();
        source.sendSuccess(() -> Component.literal(message), true);
        return 1;
    }
/**
     * Dev/test-only: grants (or, with a negative amount, removes)
     * meta-currency directly, bypassing the normal "earned on run end"
     * path ({@link com.solme.emberfall.progression.RunRewardCalculator}) -
     * the headless bot test client needs a fast way to fund shop
     * purchases without grinding a full run's worth of kills for every
     * test. Genuinely useful as a standing admin command too, not just a
     * test shim (same spirit as vanilla's own /xp for currency).
     */
    private static int giveCurrency(CommandSourceStack source, ServerPlayer player, int amount) {
        com.solme.emberfall.progression.MetaProgressionData currency =
                com.solme.emberfall.progression.MetaProgressionData.get(source.getServer());
        long updated = currency.addCurrency(player.getUUID(), amount);
        source.sendSuccess(() -> Component.literal(
                player.getGameProfile().name() + " now has " + updated + " meta-currency"), true);
        return 1;
    }

    /**
     * Spends meta-currency to buy the next level of a permanent shop
     * upgrade (design decision: weapons AND permanent stat upgrades, both
     * sold by the currency shop) - the debug-command mirror of
     * {@link com.solme.emberfall.progression.ShopManager#onBuyReceived},
     * same relationship buyweapon already has to the real Weapon Choice
     * screen. Fails cleanly if the upgrade is unknown, already maxed, or
     * the balance is short. Applies immediately via
     * {@link com.solme.emberfall.progression.UpgradeEffects#apply} so the
     * attribute change is observable right away, mid-run or not.
     */
    private static int buyUpgrade(CommandSourceStack source, ServerPlayer player, String upgradeId) {
        com.solme.emberfall.progression.UpgradeType upgrade = com.solme.emberfall.progression.UpgradePool.byId(upgradeId);
        if (upgrade != null) {
            return buyStatUpgrade(source, player, upgrade);
        }
        com.solme.emberfall.progression.ChargeUpgradeType chargeUpgrade =
                com.solme.emberfall.progression.ChargeUpgradePool.byId(upgradeId);
        if (chargeUpgrade != null) {
            return buyChargeUpgrade(source, player, chargeUpgrade);
        }
        source.sendFailure(Component.literal("Unknown upgrade '" + upgradeId + "'"));
        return 0;
    }

    private static int buyStatUpgrade(CommandSourceStack source, ServerPlayer player,
                                       com.solme.emberfall.progression.UpgradeType upgrade) {
        com.solme.emberfall.progression.PlayerUpgrades owned =
                com.solme.emberfall.progression.PlayerUpgrades.get(source.getServer());
        int currentLevel = owned.getLevel(player.getUUID(), upgrade.id());
        if (currentLevel >= upgrade.maxLevel()) {
            source.sendFailure(Component.literal(
                    player.getGameProfile().name() + " already has " + upgrade.displayName() + " at max level"));
            return 0;
        }
        long cost = upgrade.costForNextLevel(currentLevel);
        com.solme.emberfall.progression.MetaProgressionData currency =
                com.solme.emberfall.progression.MetaProgressionData.get(source.getServer());
        long balance = currency.getBalance(player.getUUID());
        if (balance < cost) {
            source.sendFailure(Component.literal(
                    player.getGameProfile().name() + " needs " + cost + " meta-currency for "
                            + upgrade.displayName() + " level " + (currentLevel + 1) + " (has " + balance + ")"));
            return 0;
        }
        currency.addCurrency(player.getUUID(), -cost);
        int newLevel = owned.levelUp(player.getUUID(), upgrade.id());
        com.solme.emberfall.progression.UpgradeEffects.apply(player);
        source.sendSuccess(() -> Component.literal(
                player.getGameProfile().name() + " bought " + upgrade.displayName() + " level " + newLevel
                        + "/" + upgrade.maxLevel() + " for " + cost + " meta-currency"), true);
        return 1;
    }

    /** Debug mirror of {@link com.solme.emberfall.progression.ShopManager}'s charge-upgrade purchase path (see {@link #buyStatUpgrade}). */
    private static int buyChargeUpgrade(CommandSourceStack source, ServerPlayer player,
                                         com.solme.emberfall.progression.ChargeUpgradeType upgrade) {
        com.solme.emberfall.progression.PlayerUpgrades owned =
                com.solme.emberfall.progression.PlayerUpgrades.get(source.getServer());
        int currentLevel = owned.getLevel(player.getUUID(), upgrade.id());
        if (currentLevel >= upgrade.maxLevel()) {
            source.sendFailure(Component.literal(
                    player.getGameProfile().name() + " already has " + upgrade.displayName() + " at max level"));
            return 0;
        }
        long cost = upgrade.costForNextLevel(currentLevel);
        com.solme.emberfall.progression.MetaProgressionData currency =
                com.solme.emberfall.progression.MetaProgressionData.get(source.getServer());
        long balance = currency.getBalance(player.getUUID());
        if (balance < cost) {
            source.sendFailure(Component.literal(
                    player.getGameProfile().name() + " needs " + cost + " meta-currency for "
                            + upgrade.displayName() + " level " + (currentLevel + 1) + " (has " + balance + ")"));
            return 0;
        }
        currency.addCurrency(player.getUUID(), -cost);
        int newLevel = owned.levelUp(player.getUUID(), upgrade.id());
        source.sendSuccess(() -> Component.literal(
                player.getGameProfile().name() + " bought " + upgrade.displayName() + " level " + newLevel
                        + "/" + upgrade.maxLevel() + " for " + cost + " meta-currency (takes effect next run)"), true);
        return 1;
    }

    /** Lists every upgrade in the pool with this player's current level and next cost. */
    private static int listUpgrades(CommandSourceStack source, ServerPlayer player) {
        com.solme.emberfall.progression.PlayerUpgrades owned =
                com.solme.emberfall.progression.PlayerUpgrades.get(source.getServer());
        StringBuilder sb = new StringBuilder(player.getGameProfile().name()).append("'s upgrades:");
        for (com.solme.emberfall.progression.UpgradeType upgrade : com.solme.emberfall.progression.UpgradePool.ALL) {
            int level = owned.getLevel(player.getUUID(), upgrade.id());
            String status = level >= upgrade.maxLevel()
                    ? "[MAXED " + level + "/" + upgrade.maxLevel() + "] "
                    : "[" + level + "/" + upgrade.maxLevel() + ", next=" + upgrade.costForNextLevel(level) + "] ";
            sb.append("\n  ").append(status).append(upgrade.displayName()).append(" - id=").append(upgrade.id());
        }
        for (com.solme.emberfall.progression.ChargeUpgradeType upgrade : com.solme.emberfall.progression.ChargeUpgradePool.ALL) {
            int level = owned.getLevel(player.getUUID(), upgrade.id());
            String status = level >= upgrade.maxLevel()
                    ? "[MAXED " + level + "/" + upgrade.maxLevel() + "] "
                    : "[" + level + "/" + upgrade.maxLevel() + ", next=" + upgrade.costForNextLevel(level) + "] ";
            sb.append("\n  ").append(status).append(upgrade.displayName()).append(" - id=").append(upgrade.id());
        }
        String message = sb.toString();
        source.sendSuccess(() -> Component.literal(message), true);
        return 1;
    }




}
