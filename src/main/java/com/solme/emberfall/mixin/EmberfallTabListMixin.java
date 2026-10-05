package com.solme.emberfall.mixin;

import com.solme.emberfall.bot.BotRoster;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps EmberTester bots off every tab list. A bot is a real ServerPlayer, so vanilla announces it to everyone through
 * six different paths (join, leave, latency ticks, game-mode change, client information, chat session). All of them end
 * in {@code ServerCommonPacketListenerImpl.send(Packet)}, so this one hook covers them all:
 * <ul>
 *   <li>an info UPDATE whose every entry is a bot is dropped;</li>
 *   <li>a mixed UPDATE is rebuilt from the human players only;</li>
 *   <li>an info REMOVE loses the bot UUIDs (and is dropped when nothing is left).</li>
 * </ul>
 * The bot's own listener receives nothing useful anyway (its connection is inert), so it is left alone.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class EmberfallTabListMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void emberfall$hideBots(Packet<?> packet, CallbackInfo ci) {
        if (BotRoster.count() == 0) {
            return; // the common case costs one volatile read
        }
        if ((Object) this instanceof ServerGamePacketListenerImpl own && BotRoster.isBot(own.player.getUUID())) {
            com.solme.emberfall.bot.BotBrain.hear(own.player, packet); // a bot's screens arrive here, see BotBrain
            return;
        }
        if (packet instanceof ClientboundPlayerInfoUpdatePacket update) {
            var entries = update.entries();
            if (BotRoster.allBots(entries, ClientboundPlayerInfoUpdatePacket.Entry::profileId)) {
                ci.cancel();
                return;
            }
            boolean hasBot = false;
            for (var e : entries) {
                if (BotRoster.isBot(e.profileId())) {
                    hasBot = true;
                    break;
                }
            }
            if (hasBot && (Object) this instanceof ServerGamePacketListenerImpl game) {
                List<ServerPlayer> humans = new ArrayList<>();
                for (var e : BotRoster.humansOnly(entries, ClientboundPlayerInfoUpdatePacket.Entry::profileId)) {
                    ServerPlayer p = game.player.level().getServer().getPlayerList().getPlayer(e.profileId());
                    if (p != null) {
                        humans.add(p);
                    }
                }
                ci.cancel();
                if (!humans.isEmpty()) {
                    game.send(new ClientboundPlayerInfoUpdatePacket(update.actions(), humans));
                }
            }
        } else if (packet instanceof ClientboundPlayerInfoRemovePacket remove) {
            List<java.util.UUID> keep = BotRoster.humanIds(remove.profileIds());
            if (keep.size() != remove.profileIds().size()) {
                ci.cancel();
                if (!keep.isEmpty() && (Object) this instanceof ServerGamePacketListenerImpl game) {
                    game.send(new ClientboundPlayerInfoRemovePacket(keep));
                }
            }
        }
    }
}
