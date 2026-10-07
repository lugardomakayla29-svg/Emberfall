package com.solme.emberfall.item;

import com.solme.emberfall.bot.BotEggRules;
import com.solme.emberfall.bot.EmberBot;
import com.solme.emberfall.wave.WaveDirector;
import com.solme.emberfall.world.ArenaInstance;
import com.solme.emberfall.world.Dimensions;
import com.solme.emberfall.world.RunManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

/**
 * Creative-tab toy that adds an EmberTester to the run the holder is in, so one operator can test a party alone. The bot joins through
 * {@link RunManager#joinPlayer}, the same call a gate party ends in, so it raises the run's party size and therefore the difficulty
 * exactly as a real player would. Every refusal is decided by {@link BotEggRules}, which is checked without a server.
 */
public final class BotEggItem extends Item {
    public BotEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        var mc = server.getServer();
        boolean operator = net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(sp.createCommandSourceStack().permissions());
        Integer slot = RunManager.slotOf(sp);
        WaveDirector director = slot == null ? null : WaveDirector.get(slot);
        Set<String> taken = new HashSet<>();
        for (ServerPlayer p : mc.getPlayerList().getPlayers()) {
            taken.add(p.getGameProfile().name());
        }
        String name = BotEggRules.nextName(taken);
        BotEggRules.Verdict verdict = BotEggRules.decide(operator, director != null,
                director == null ? 0 : director.elapsedTicksNow(), WaveDirector.partyFreezeAtTick(),
                slot == null ? 0 : RunManager.partySize(slot), name == null);
        if (verdict != BotEggRules.Verdict.OK) {
            sp.sendSystemMessage(Component.literal("\u00A7c" + BotEggRules.message(verdict, WaveDirector.partyFreezeAtTick())), true);
            return InteractionResult.FAIL;
        }
        ServerLevel expedition = mc.getLevel(Dimensions.EXPEDITION);
        ArenaInstance instance = RunManager.getActive(slot);
        if (expedition == null || instance == null) {
            sp.sendSystemMessage(Component.literal("\u00A7cThe run is not available."), true);
            return InteractionResult.FAIL;
        }
        ServerPlayer bot = EmberBot.spawn(mc, (ServerLevel) sp.level(), name, sp.getX(), sp.getY(), sp.getZ());
        if (bot == null) {
            sp.sendSystemMessage(Component.literal("\u00A7cCould not create the EmberTester."), true);
            return InteractionResult.FAIL;
        }
        RunManager.joinPlayer(expedition, instance, bot);
        // The warning the owner asked for: the party just grew, so the run gets harder. Everyone in the run hears it.
        RunManager.broadcastToSlot(mc, slot, Component.literal("\u00A76" + name + " joined the party. The expedition grows harder."));
        for (ServerPlayer member : mc.getPlayerList().getPlayers()) {
            Integer s = RunManager.slotOf(member);
            if (s != null && s.equals(slot) && !EmberBot.isBot(member)) {
                ((ServerLevel) member.level()).playSound(null, member.blockPosition(), net.minecraft.sounds.SoundEvents.RAID_HORN.value(), net.minecraft.sounds.SoundSource.HOSTILE, 0.6F, 1.0F);
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
