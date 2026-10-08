package com.solme.emberfall.hub;

import com.solme.emberfall.command.CharacterCommand;
import com.solme.emberfall.command.RunCommand;
import com.solme.emberfall.progression.ShopManager;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;

/**
 * Routes a right-click on a hub hotspot to the action it stands for.
 *
 * Each hotspot is an invisible {@link Interaction} entity (spawned by {@link HubBuilder}) tagged with
 * both the hub tag and one {@code hubact_*} tag. The action is read straight off that tag, so there is
 * no side table to lose and the hub's normal teardown removes the hotspots along with everything else.
 * Every action goes through the same shared method its old typed command used, so the rules cannot
 * drift between the command and the click.
 */
public final class HubInteractions {
    public static final String ACT_BUST = "hubact_bust_";
    public static final String ACT_SHOP = "hubact_shop";
    public static final String ACT_GATE = "hubact_gate";

    private HubInteractions() {}

    public static void register() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (entity instanceof Interaction && entity.getTags().contains(com.solme.emberfall.rift.RiftEntry.TAG)) {
                return riftClick(player, level, hand, entity);
            }
            if (!(entity instanceof Interaction) || !hasHubTag(entity)) {
                return InteractionResult.PASS;
            }
            // Swallow the off-hand click too, otherwise one click would run the action twice.
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.SUCCESS;
            }
            if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
                return InteractionResult.SUCCESS;
            }
            return runAction(sp, entity) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
        });
    }

    /**
     * A right click on a Rift's click target. The off-hand click is swallowed so one click never starts two countdowns, and a refusal is shown to
     * the player as a plain chat line (the same way the gate shows its refusals).
     */
    private static InteractionResult riftClick(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level, InteractionHand hand, Entity entity) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        String refusal = com.solme.emberfall.rift.RiftGate.click(sp, entity, level.getServer().getTickCount());
        if (refusal != null) {
            sp.sendSystemMessage(Component.literal("\u00A7c" + capitalise(refusal) + "."));
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Runs the action named by a hotspot's {@code hubact_*} tag. Kept separate from the click callback
     * so the debug command can drive exactly the same code path without a real client interaction.
     * Returns false if the entity carries no known action.
     */
    public static boolean runAction(ServerPlayer sp, Entity hotspot) {
        for (String tag : hotspot.getTags()) {
            if (tag.startsWith(ACT_BUST)) {
                String error = CharacterCommand.trySelect(sp, tag.substring(ACT_BUST.length()));
                if (error != null) {
                    sp.sendSystemMessage(Component.literal("§c" + error));
                }
                return true;
            }
            if (tag.equals(ACT_GATE)) {
                String refusal = GateManager.click(sp, hotspot.blockPosition(), sp.level().getServer().getTickCount());
                if (refusal != null) {
                    sp.sendSystemMessage(Component.literal("§c" + refusal));
                }
                return true;
            }
            if (tag.equals(ACT_SHOP)) {
                ShopManager.open(sp);
                return true;
            }
        }
        return false;
    }

    private static boolean hasHubTag(Entity e) {
        for (String t : e.getTags()) {
            if (t.startsWith(HubBuilder.TAG_PREFIX)) {
                return true;
            }
        }
        return false;
    }
}
