package com.solme.emberfall.item;

import com.solme.emberfall.wave.WaveDirector;
import com.solme.emberfall.world.RunManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Creative-tab toy that calls a boss (or a Testificate merchant) into the run the holder is in. It does NOT drop a boss where you click: a boss needs its
 * arena and the Wave Director, so this goes through the same {@link WaveDirector#triggerBossNow} / {@link
 * WaveDirector#triggerDevourerNow} the timer uses. It refuses outside a run, while a boss is already fighting, and during the
 * Final Swarm, rather than starting a half-working fight.
 */
public final class BossSummonerItem extends Item {
    public enum Boss { GUARDIAN, DEVOURER, MERCHANT }

    private final Boss boss;

    public BossSummonerItem(Properties properties, Boss boss) {
        super(properties);
        this.boss = boss;
    }

    @Override
    public InteractionResult use(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        if (!net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(sp.createCommandSourceStack().permissions())) {
            sp.sendSystemMessage(Component.literal("§cOnly operators can call a boss."), true);
            return InteractionResult.FAIL;
        }
        Integer slot = RunManager.slotOf(sp);
        WaveDirector director = slot == null ? null : WaveDirector.get(slot);
        if (director == null) {
            sp.sendSystemMessage(Component.literal("§cStart an expedition first: a boss needs its arena."), true);
            return InteractionResult.FAIL;
        }
        if (boss == Boss.MERCHANT) {
            // A visit at the normal odds (the party's best luck decides the tier). Refused while one is already here.
            boolean came = com.solme.emberfall.relic.MerchantManager.arriveNow(server.getServer(), slot, null);
            if (!came) {
                sp.sendSystemMessage(Component.literal("§cA merchant is already here, or there is no room to stand one."), true);
                return InteractionResult.FAIL;
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        if (director.swarmActive()) {
            sp.sendSystemMessage(Component.literal("§cThe Final Swarm has begun. No more bosses."), true);
            return InteractionResult.FAIL;
        }
        if (director.bossActive()) {
            sp.sendSystemMessage(Component.literal("§cA boss is already fighting."), true);
            return InteractionResult.FAIL;
        }
        ServerLevel arenaLevel = (ServerLevel) sp.level();
        if (boss == Boss.GUARDIAN) {
            director.triggerBossNow(arenaLevel);
        } else {
            director.triggerDevourerNow(arenaLevel);
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
