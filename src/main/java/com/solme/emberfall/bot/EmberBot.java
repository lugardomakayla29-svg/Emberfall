package com.solme.emberfall.bot;

import com.mojang.authlib.GameProfile;
import com.solme.emberfall.EmberfallMod;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.player.Player;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * One EmberTester: a real {@link ServerPlayer} with no client behind it. Being a real player is the point, because weapons,
 * tomes, gold, relics, chests, the merchant, the HUD state and the run bookkeeping are all keyed to a ServerPlayer, so the
 * bot uses the game's own code instead of a second copy of it.
 *
 * <p>Two things keep it cheap and invisible:
 * <ul>
 *   <li><b>Inert connection.</b> A {@link Connection} with no channel queues every packet forever (measured in bytecode:
 *       {@code send} appends to {@code pendingActions} while {@code isConnected()} is false). The bot gets a channel whose
 *       first handler swallows every write, so nothing is ever buffered.</li>
 *   <li><b>No tab list.</b> Its UUID is in {@link BotRoster}, and {@code EmberfallTabListMixin} filters every tab-list packet
 *       at the one method all of them leave through.</li>
 * </ul>
 */
public final class EmberBot {
    private EmberBot() {}

    /** Discards every outbound message: a bot has nobody to send to, and an unread queue would grow without bound. */
    private static final class Swallow extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            ReferenceCountUtil.release(msg);
            promise.setSuccess();
        }
    }

    /** The same name always gives the same UUID, so a bot keeps its player data across restarts. */
    public static UUID idFor(String name) {
        return UUID.nameUUIDFromBytes(("EmberTesterBot:" + name).getBytes(StandardCharsets.UTF_8));
    }

    /** Joins a bot at {@code level}'s spawn-side position. Returns null if that name is already online. */
    public static ServerPlayer spawn(MinecraftServer server, ServerLevel level, String name, double x, double y, double z) {
        UUID id = idFor(name);
        if (server.getPlayerList().getPlayer(id) != null) {
            return null;
        }
        GameProfile profile = new GameProfile(id, name);
        BotRoster.add(id); // before the join, so the join announcement is already filtered
        ServerPlayer bot = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        bot.snapTo(x, y, z, 0.0F, 0.0F);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(new Swallow(), connection);
        server.getPlayerList().placeNewPlayer(connection, bot, CommonListenerCookie.createInitial(profile, false));
        EmberfallMod.LOGGER.info("EmberBot {} joined at {} {} {}", name, x, y, z);
        return bot;
    }

    /** Removes a bot the normal way, so every leave handler (run end, cleanup, saving) runs exactly as for a human. */
    public static boolean remove(MinecraftServer server, String name) {
        UUID id = idFor(name);
        ServerPlayer bot = server.getPlayerList().getPlayer(id);
        if (bot == null) {
            BotRoster.remove(id);
            return false;
        }
        bot.connection.disconnect(Component.literal("EmberBot removed"));
        return true;
    }

    public static boolean isBot(Player player) {
        return player != null && BotRoster.isBot(player.getUUID());
    }
}
