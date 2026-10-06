package com.solme.emberfall.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.solme.emberfall.relic.MerchantManager;
import com.solme.emberfall.relic.PlayerRelics;
import com.solme.emberfall.relic.RelicRarity;
import com.solme.emberfall.relic.Relic;
import com.solme.emberfall.relic.RelicEffects;
import com.solme.emberfall.relic.RelicMath;
import com.solme.emberfall.relic.RelicPool;
import com.solme.emberfall.relic.RelicStats;
import com.solme.emberfall.relic.RelicUnlocks;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Operator-only relic debug commands under {@code /emberfall relic ...}. They merge into the existing
 * {@code /emberfall} root, and carry the same gamemaster requirement explicitly so they can never be reached
 * by a player in a world without cheats.
 *
 * Replies are single lines starting with a stable tag (RELIC ...) so a bot can read them.
 */
public final class RelicCommands {
    private RelicCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("emberfall")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("bot")
                                .then(Commands.literal("spawn")
                                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
                                            var src = ctx.getSource();
                                            String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                                            var pos = src.getPosition();
                                            var bot = com.solme.emberfall.bot.EmberBot.spawn(src.getServer(), src.getLevel(), name, pos.x, pos.y, pos.z);
                                            return say(src, "BOT spawn " + name + " " + (bot == null ? "refused" : "ok id=" + bot.getUUID()));
                                        })))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
                                            String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                                            boolean gone = com.solme.emberfall.bot.EmberBot.remove(ctx.getSource().getServer(), name);
                                            return say(ctx.getSource(), "BOT remove " + name + " " + (gone ? "ok" : "not online"));
                                        })))
                                .then(Commands.literal("run")
                                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .then(Commands.argument("character", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
                                                    // Test hook: the bot picks a character and starts a run through the SAME two calls a human's screens end in.
                                                    String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                                                    String character = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "character");
                                                    ServerPlayer bot = ctx.getSource().getServer().getPlayerList().getPlayer(com.solme.emberfall.bot.EmberBot.idFor(name));
                                                    if (bot == null) {
                                                        return say(ctx.getSource(), "BOT run " + name + " not online");
                                                    }
                                                    String problem = CharacterCommand.trySelect(bot, character);
                                                    if (problem == null) {
                                                        problem = RunCommand.tryStart(bot);
                                                    }
                                                    return say(ctx.getSource(), "BOT run " + name + " " + (problem == null ? "ok" : "refused: " + problem));
                                                }))))
                                .then(Commands.literal("state")
                                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
                                            String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                                            ServerPlayer bot = ctx.getSource().getServer().getPlayerList().getPlayer(com.solme.emberfall.bot.EmberBot.idFor(name));
                                            if (bot == null) {
                                                return say(ctx.getSource(), "BOT state " + name + " not online");
                                            }
                                            var loadout = com.solme.emberfall.item.Loadout.peek(bot);
                                            StringBuilder weapons = new StringBuilder();
                                            for (int i = 0; loadout != null && i < loadout.size(); i++) {
                                                weapons.append(loadout.slot(i).weapon().id()).append(',');
                                            }
                                            return say(ctx.getSource(), "BOT state " + name + " run=" + com.solme.emberfall.world.RunManager.slotOf(bot)
                                                    + " level=" + bot.experienceLevel + " hp=" + Math.round(bot.getHealth())
                                                    + " gold=" + com.solme.emberfall.pickup.PickupSystem.gold(bot)
                                                    + " tomes=" + com.solme.emberfall.tome.PlayerBuild.allOf(bot)
                                                    + " weapons=" + weapons
                                                    + " foes=" + bot.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, bot.getBoundingBox().inflate(60.0, 20.0, 60.0),
                                                            com.solme.emberfall.combat.AutoAttackSystem::isEmberfallHostile).size()
                                                    + " scouts=" + bot.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, bot.getBoundingBox().inflate(300.0, 300.0, 300.0),
                                                            m -> m.getTags().contains(com.solme.emberfall.bot.BotScout.TAG)).size()
                                                    + " pos=" + String.format("%.1f,%.1f", bot.getX(), bot.getZ()));
                                        })))
                                .then(Commands.literal("push")
                                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .then(Commands.argument("mode", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
                                                    // EXPERIMENT: which way of moving a client-less player actually works on this server?
                                                    String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                                                    String mode = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "mode");
                                                    ServerPlayer bot = ctx.getSource().getServer().getPlayerList().getPlayer(com.solme.emberfall.bot.EmberBot.idFor(name));
                                                    if (bot == null) {
                                                        return say(ctx.getSource(), "BOT push " + name + " not online");
                                                    }
                                                    bot.setYRot(0.0F); // facing +z
                                                    switch (mode) {
                                                        case "zza" -> bot.zza = 1.0F;
                                                        case "velocity" -> bot.setDeltaMovement(0.0, 0.0, 0.3);
                                                        case "teleport" -> bot.teleportTo(bot.getX(), bot.getY(), bot.getZ() + 2.0);
                                                        case "snap" -> bot.snapTo(bot.getX(), bot.getY(), bot.getZ() + 2.0, bot.getYRot(), bot.getXRot());
                                                        default -> {
                                                            return say(ctx.getSource(), "BOT push unknown mode");
                                                        }
                                                    }
                                                    return say(ctx.getSource(), "BOT push " + name + " " + mode + " from z=" + bot.getZ());
                                                }))))
                                .then(Commands.literal("mem").executes(ctx -> {
                                    // Test-only: used heap after a full collection, so growth over a window is a leak, not garbage.
                                    System.gc();
                                    Runtime rt = Runtime.getRuntime();
                                    long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
                                    return say(ctx.getSource(), "BOT mem usedMb=" + usedMb);
                                }))
                                .then(Commands.literal("list").executes(ctx -> {
                                    var sb = new StringBuilder("BOT list roster=" + com.solme.emberfall.bot.BotRoster.count() + " online=");
                                    for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                        if (com.solme.emberfall.bot.EmberBot.isBot(p)) {
                                            sb.append(p.getGameProfile().name()).append(' ');
                                        }
                                    }
                                    return say(ctx.getSource(), sb.toString());
                                })))
                        .then(Commands.literal("relic")
                                .then(Commands.literal("list").executes(ctx -> {
                                    StringBuilder sb = new StringBuilder("RELIC pool " + RelicPool.all().size() + ":");
                                    for (Relic r : RelicPool.all()) {
                                        sb.append(' ').append(r.id()).append('/').append(r.rarity().name().charAt(0));
                                    }
                                    return say(ctx.getSource(), sb.toString());
                                }))
                                .then(Commands.literal("give")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("id", StringArgumentType.word())
                                                        .executes(ctx -> give(ctx, 1))
                                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 20))
                                                                .executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "count")))))))
                                .then(Commands.literal("take")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                                    boolean ok = PlayerRelics.take(p, StringArgumentType.getString(ctx, "id"));
                                                    return say(ctx.getSource(), "RELIC take " + ok);
                                                }))))
                                .then(Commands.literal("state")
                                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                            RelicStats s = RelicEffects.stats(p);
                                            Map<String, Integer> owned = PlayerRelics.all(p);
                                            return say(ctx.getSource(), String.format(
                                                    "RELIC state active=%s owned=%s total=%d luck=%.1f gold=%.2f xp=%.2f key=%.2f hp=%.1f max=%.1f speed=%.3f price=%d opened=%d wallet=%d",
                                                    PlayerRelics.active(p), owned, PlayerRelics.totalStacks(p), s.luck(), s.goldMultiplier(),
                                                    s.xpMultiplier(), s.keyChance(), p.getHealth(), p.getMaxHealth(),
                                                    p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED),
                                                    PlayerRelics.nextChestPrice(p), PlayerRelics.chestsOpened(p), com.solme.emberfall.pickup.PickupSystem.gold(p)));
                                        })))
                                .then(Commands.literal("gold")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(0, 100000000)).executes(ctx -> {
                                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                                    com.solme.emberfall.pickup.PickupSystem.setGold(p, IntegerArgumentType.getInteger(ctx, "amount"));
                                                    return say(ctx.getSource(), "RELIC gold " + com.solme.emberfall.pickup.PickupSystem.gold(p));
                                                }))))
                                .then(Commands.literal("chests")
                                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                            Integer slot = com.solme.emberfall.world.RunManager.slotOf(p);
                                            if (slot == null) {
                                                return say(ctx.getSource(), "RELIC chests none");
                                            }
                                            int[] n = com.solme.emberfall.relic.ChestManager.nearestClosed(slot, p.blockPosition());
                                            return say(ctx.getSource(), String.format("RELIC chests total=%d closed=%d free=%d nearest=%s",
                                                    com.solme.emberfall.relic.ChestManager.count(slot), com.solme.emberfall.relic.ChestManager.unopened(slot), com.solme.emberfall.relic.ChestManager.freeGiven(slot),
                                                    n == null ? "none" : n[0] + " " + n[1] + " " + n[2] + " kind=" + com.solme.emberfall.relic.ChestOpening.Kind.values()[n[3]]));
                                        })))
                                .then(Commands.literal("elite")
                                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                            Integer slot = com.solme.emberfall.world.RunManager.slotOf(p);
                                            com.solme.emberfall.wave.WaveDirector d = slot == null ? null : com.solme.emberfall.wave.WaveDirector.get(slot);
                                            if (d == null) {
                                                return say(ctx.getSource(), "RELIC elite none");
                                            }
                                            d.spawnEliteNow((net.minecraft.server.level.ServerLevel) p.level());
                                            return say(ctx.getSource(), "RELIC elite spawned");
                                        })))
                                .then(Commands.literal("free")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("source", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
                                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                                    Integer slot = com.solme.emberfall.world.RunManager.slotOf(p);
                                                    var arena = slot == null ? null : com.solme.emberfall.world.RunManager.getActive(slot);
                                                    if (arena == null) {
                                                        return say(ctx.getSource(), "RELIC free none");
                                                    }
                                                    var src = com.solme.emberfall.relic.FreeChestRule.Source.valueOf(
                                                            com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "source").toUpperCase());
                                                    boolean placed = com.solme.emberfall.relic.ChestManager.dropFree((net.minecraft.server.level.ServerLevel) p.level(), arena, p.blockPosition(), src);
                                                    return say(ctx.getSource(), "RELIC free placed=" + placed + " given=" + com.solme.emberfall.relic.ChestManager.freeGiven(slot));
                                                }))))
                                .then(Commands.literal("threat")
                                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                            Integer slot = com.solme.emberfall.world.RunManager.slotOf(p);
                                            com.solme.emberfall.wave.WaveDirector d = slot == null ? null : com.solme.emberfall.wave.WaveDirector.get(slot);
                                            return say(ctx.getSource(), d == null ? "RELIC threat none" : String.format(
                                                    "RELIC threat total=%.3f relic=%.3f", d.threatLevel(), d.relicThreat()));
                                        })))
                                .then(Commands.literal("price")
                                        .then(Commands.argument("opened", IntegerArgumentType.integer(0, 500)).executes(ctx ->
                                                say(ctx.getSource(), "RELIC price " + IntegerArgumentType.getInteger(ctx, "opened") + "="
                                                        + RelicMath.chestPrice(IntegerArgumentType.getInteger(ctx, "opened"))))))
                                .then(Commands.literal("merchant")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("tier", IntegerArgumentType.integer(0, 3)).executes(ctx -> {
                                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                                    Integer slot = com.solme.emberfall.world.RunManager.slotOf(p);
                                                    boolean ok = slot != null && MerchantManager.arriveNow(ctx.getSource().getServer(), slot,
                                                            RelicRarity.values()[IntegerArgumentType.getInteger(ctx, "tier")]);
                                                    return say(ctx.getSource(), "RELIC merchant " + (ok ? "arrived" : "refused"));
                                                }))))
                                .then(Commands.literal("merchantclick")
                                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                                            // Test stand-in for "the client right-clicked the merchant": a headless bot cannot see a modded
                                            // entity, so this calls the exact method mobInteract calls, on the nearest Testificate.
                                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                            var list = p.level().getEntitiesOfClass(com.solme.emberfall.entity.Testificate.class, p.getBoundingBox().inflate(40));
                                            if (list.isEmpty()) {
                                                return say(ctx.getSource(), "RELIC merchantclick none");
                                            }
                                            MerchantManager.onInteract(p, list.get(0));
                                            return say(ctx.getSource(), "RELIC merchantclick ok");
                                        })))
                                // Debug (op only): the same call a Greed Shrine makes, so a test can push the director to its spawn floor
                                // in seconds instead of waiting for the natural ramp. Used by the hostile-cap measurement.
                                .then(Commands.literal("threatadd")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("amount", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0, 40.0)).executes(ctx -> {
                                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                                    Integer slot = com.solme.emberfall.world.RunManager.slotOf(p);
                                                    com.solme.emberfall.wave.WaveDirector d = slot == null ? null : com.solme.emberfall.wave.WaveDirector.get(slot);
                                                    if (d == null) {
                                                        return say(ctx.getSource(), "RELIC threatadd none");
                                                    }
                                                    d.addBonusThreat(com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "amount"));
                                                    return say(ctx.getSource(), String.format("RELIC threatadd ok total=%.3f", d.threatLevel()));
                                                }))))
                                .then(Commands.literal("summoner")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
                                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                                    String kind = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind");
                                                    net.minecraft.world.item.Item item = switch (kind) {
                                                        case "guardian" -> com.solme.emberfall.item.ModItems.GUARDIAN_SUMMONER;
                                                        case "devourer" -> com.solme.emberfall.item.ModItems.DEVOURER_SUMMONER;
                                                        default -> com.solme.emberfall.item.ModItems.MERCHANT_SUMMONER;
                                                    };
                                                    // The same method a right click calls, so the test exercises the item's real code.
                                                    var result = item.use(p.level(), p, net.minecraft.world.InteractionHand.MAIN_HAND);
                                                    return say(ctx.getSource(), "RELIC summoner " + kind + " " + (result.consumesAction() ? "ok" : "refused"));
                                                }))))
                                .then(Commands.literal("swarm")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.literal("start").executes(ctx -> {
                                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                                    Integer slot = com.solme.emberfall.world.RunManager.slotOf(p);
                                                    var d = slot == null ? null : com.solme.emberfall.wave.WaveDirector.get(slot);
                                                    if (d == null) {
                                                        return say(ctx.getSource(), "RELIC swarm norun");
                                                    }
                                                    d.beginSwarm((net.minecraft.server.level.ServerLevel) p.level());
                                                    return say(ctx.getSource(), "RELIC swarm started");
                                                }))
                                                .then(Commands.literal("skip").then(Commands.argument("seconds", IntegerArgumentType.integer(0, 5000)).executes(ctx -> {
                                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                                    Integer slot = com.solme.emberfall.world.RunManager.slotOf(p);
                                                    var d = slot == null ? null : com.solme.emberfall.wave.WaveDirector.get(slot);
                                                    if (d == null) {
                                                        return say(ctx.getSource(), "RELIC swarm norun");
                                                    }
                                                    d.skipSwarmSeconds(IntegerArgumentType.getInteger(ctx, "seconds"));
                                                    return say(ctx.getSource(), "RELIC swarm skipped");
                                                })))
                                                .then(Commands.literal("state").executes(ctx -> {
                                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                                    Integer slot = com.solme.emberfall.world.RunManager.slotOf(p);
                                                    var d = slot == null ? null : com.solme.emberfall.wave.WaveDirector.get(slot);
                                                    if (d == null) {
                                                        return say(ctx.getSource(), "RELIC swarm norun");
                                                    }
                                                    var lvl = (net.minecraft.server.level.ServerLevel) p.level();
                                                    long mobs = lvl.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(300),
                                                            m -> m.getTags().contains(com.solme.emberfall.wave.WaveDirector.SWARM_TAG)).size();
                                                    return say(ctx.getSource(), "RELIC swarm active=" + d.swarmActive() + " tenths=" + d.swarmTenths() + " seconds=" + d.swarmSeconds()
                                                            + " mobs=" + mobs + " portal=" + com.solme.emberfall.wave.SwarmPortal.isOpen(slot));
                                                }))
                                                .then(Commands.literal("portal").executes(ctx -> {
                                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                                    Integer slot = com.solme.emberfall.world.RunManager.slotOf(p);
                                                    var at = slot == null ? null : com.solme.emberfall.wave.SwarmPortal.where(slot);
                                                    return say(ctx.getSource(), at == null ? "RELIC swarm portal none" : "RELIC swarm portal " + at.getX() + " " + at.getY() + " " + at.getZ());
                                                }))))
                                .then(Commands.literal("merchantstate")
                                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                                            Integer slot = com.solme.emberfall.world.RunManager.slotOf(EntityArgument.getPlayer(ctx, "player"));
                                            return say(ctx.getSource(), "RELIC merchantstate " + (slot == null ? "norun" : MerchantManager.describe(slot)));
                                        })))
                                .then(Commands.literal("unlocks")
                                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                            RelicUnlocks u = RelicUnlocks.get(ctx.getSource().getServer());
                                            StringBuilder sb = new StringBuilder("RELIC unlocks " + u.unlockedIds(p.getUUID()));
                                            for (String id : RelicUnlocks.GOALS.keySet()) {
                                                sb.append(' ').append(id).append('=').append(u.progressOf(p.getUUID(), id));
                                            }
                                            return say(ctx.getSource(), sb.toString());
                                        })
                                                .then(Commands.literal("add")
                                                        .then(Commands.argument("id", StringArgumentType.word())
                                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1000)).executes(ctx -> {
                                                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                                                    var opened = RelicUnlocks.get(ctx.getSource().getServer()).addProgress(p.getUUID(),
                                                                            StringArgumentType.getString(ctx, "id"), IntegerArgumentType.getInteger(ctx, "amount"));
                                                                    return say(ctx.getSource(), "RELIC unlocked-now " + opened.stream().map(Relic::id).toList());
                                                                }))))))
                        )));
    }

    private static int give(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx, int count) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        String id = StringArgumentType.getString(ctx, "id");
        int given = 0;
        for (int i = 0; i < count; i++) {
            if (PlayerRelics.give(p, id)) {
                given++;
            }
        }
        return say(ctx.getSource(), "RELIC give " + id + " requested=" + count + " given=" + given + " now=" + PlayerRelics.stacks(p, id));
    }

    private static int say(net.minecraft.commands.CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal(line), false);
        return 1;
    }
}
