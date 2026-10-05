package com.solme.emberfall.entity;

import com.solme.emberfall.relic.MerchantManager;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Testificate: a travelling merchant. It is a vanilla {@link Villager} (so the client draws the real Nitwit model with no
 * renderer of our own) with everything that makes a villager expensive or unpredictable switched off:
 * no brain tick (no schedule, sleep, panic, job site, gossip, raid checks), no breeding, no pushing, no damage, no despawn.
 * The {@link MerchantManager} owns its whole life (spawn, visit, firework exit), and a right click opens OUR screen, never
 * the vanilla trade window.
 */
public class Testificate extends Villager {

    public Testificate(EntityType<? extends Villager> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
        setCustomNameVisible(true);
    }

    /** Sets the Nitwit outfit. Needs the live registry, so the manager calls it right after creating the entity. */
    public void dressAsNitwit(ServerLevel level) {
        Holder<VillagerType> plains = level.registryAccess().lookupOrThrow(Registries.VILLAGER_TYPE).getOrThrow(VillagerType.PLAINS);
        Holder<VillagerProfession> nitwit = level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.NITWIT);
        setVillagerData(new VillagerData(plains, nitwit, 1));
    }

    /** No brain, no schedule, no trade timer, no raid checks: a shopkeeper only stands and reacts. */
    @Override
    protected void customServerAiStep(ServerLevel level) {
        // intentionally empty: the manager drives everything that happens to a Testificate
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND && level() instanceof ServerLevel) {
            MerchantManager.onInteract(sp, this);
        }
        return InteractionResult.SUCCESS; // swallow the click so vanilla never opens its trade window
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false; // invulnerable to everything, including the void/kill paths that respect this hook
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBreed() {
        return false;
    }

    @Override
    public Villager getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
        return false;
    }

    /** The idle "hmm": the Testificate hums only when the manager asks, never on the vanilla random ambient timer. */
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }
}
