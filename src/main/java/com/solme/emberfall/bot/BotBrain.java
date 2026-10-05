package com.solme.emberfall.bot;

import com.solme.emberfall.EmberfallMod;
import com.solme.emberfall.item.Loadout;
import com.solme.emberfall.network.ChooseTomePayload;
import com.solme.emberfall.network.ChooseWeaponPayload;
import com.solme.emberfall.network.OpenTomeChoicePayload;
import com.solme.emberfall.network.OpenWeaponChoicePayload;
import com.solme.emberfall.tome.PlayerBuild;
import com.solme.emberfall.tome.SynergyTag;
import com.solme.emberfall.tome.Tome;
import com.solme.emberfall.tome.TomeCategory;
import com.solme.emberfall.tome.TomePool;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The ears and hands of one EmberTester. The bot has no client, so a screen the game would open for a human (tome offer,
 * weapon offer) reaches it as a typed payload on its own listener. {@link #hear} records it; {@link #tick} answers a
 * moment later, as a human reading the screen would, by calling the SAME {@code onChoiceReceived} a real client packet
 * ends in. The game validates every answer exactly as it would for a person, so the bot cannot cheat or desync.
 */
public final class BotBrain {
    /** Ticks a bot "reads" a screen before answering. */
    static final int THINK_TICKS = 24;

    private record Pending(Object payload, long answerAtTick) {}

    private static final Map<UUID, Pending> TOME = new ConcurrentHashMap<>();
    private static final Map<UUID, Pending> WEAPON = new ConcurrentHashMap<>();

    private BotBrain() {}

    /** Called from the send hook for every packet a bot's listener is about to send. Cheap for non-screen packets. */
    public static void hear(ServerPlayer bot, Packet<?> packet) {
        if (!(packet instanceof ClientboundCustomPayloadPacket custom)) {
            return;
        }
        long now = bot.level().getGameTime();
        if (custom.payload() instanceof OpenTomeChoicePayload tome) {
            TOME.put(bot.getUUID(), new Pending(tome, now + THINK_TICKS));
        } else if (custom.payload() instanceof OpenWeaponChoicePayload weapon) {
            WEAPON.put(bot.getUUID(), new Pending(weapon, now + THINK_TICKS));
        }
    }

    public static void forget(UUID id) {
        TOME.remove(id);
        WEAPON.remove(id);
    }

    public static void tickAll(net.minecraft.server.MinecraftServer server) {
        if (TOME.isEmpty() && WEAPON.isEmpty()) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (BotRoster.isBot(p.getUUID())) {
                tick(p);
            }
        }
    }

    private static void tick(ServerPlayer bot) {
        long now = bot.level().getGameTime();
        Pending t = TOME.get(bot.getUUID());
        if (t != null && now >= t.answerAtTick()) {
            TOME.remove(bot.getUUID());
            answerTome(bot, (OpenTomeChoicePayload) t.payload());
        }
        Pending w = WEAPON.get(bot.getUUID());
        if (w != null && now >= w.answerAtTick()) {
            WEAPON.remove(bot.getUUID());
            answerWeapon(bot, (OpenWeaponChoicePayload) w.payload());
        }
    }

    private static void answerTome(ServerPlayer bot, OpenTomeChoicePayload offer) {
        Set<String> ownedTags = new HashSet<>();
        for (String id : PlayerBuild.allOf(bot).keySet()) {
            Tome owned = TomePool.byId(id);
            if (owned != null) {
                for (SynergyTag tag : owned.tags()) {
                    ownedTags.add(tag.name());
                }
            }
        }
        List<BotChoices.TomeOffer> offers = new ArrayList<>();
        for (OpenTomeChoicePayload.OfferInfo o : offer.offers()) {
            Tome tome = TomePool.byId(o.id());
            Set<String> tags = new HashSet<>();
            if (tome != null) {
                for (SynergyTag tag : tome.tags()) {
                    tags.add(tag.name());
                }
            }
            offers.add(new BotChoices.TomeOffer(o.id(), tags, tome != null && tome.category() == TomeCategory.PASSIVE));
        }
        boolean lowHealth = bot.getHealth() < bot.getMaxHealth() * 0.5F;
        int index = BotChoices.pickTome(offers, ownedTags, lowHealth, id -> PlayerBuild.canTake(bot, id));
        com.solme.emberfall.tome.TomeChoiceManager.onChoiceReceived(bot, new ChooseTomePayload(offer.newLevel(), index));
    }

    private static void answerWeapon(ServerPlayer bot, OpenWeaponChoicePayload offer) {
        Set<String> ownedIds = new HashSet<>();
        Set<String> ownedMovesets = new HashSet<>();
        Loadout loadout = Loadout.peek(bot);
        if (loadout != null) {
            for (int i = 0; i < loadout.size(); i++) {
                var weapon = loadout.slot(i).weapon();
                ownedIds.add(weapon.id());
                ownedMovesets.add(weapon.moveset().name());
            }
        }
        List<BotChoices.WeaponOffer> offers = new ArrayList<>();
        for (OpenWeaponChoicePayload.OfferInfo o : offer.offers()) {
            offers.add(new BotChoices.WeaponOffer(o.id(), o.moveset()));
        }
        int index = BotChoices.pickWeapon(offers, ownedIds, ownedMovesets, offer.isStartingPick());
        String id = index < 0 ? "" : offers.get(index).id();
        com.solme.emberfall.item.WeaponChoiceManager.onChoiceReceived(bot, new ChooseWeaponPayload(offer.isStartingPick(), offer.requestId(), id));
    }
}
