package com.solme.emberfall.entity;

import com.mojang.math.Transformation;
import com.solme.emberfall.combat.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The Umbral Magus's "Star Bit Lob", adapted from SlopPack's Star Bit / Amethyst Steve (which was a
 * player armor-set ability, not a Magus move): a star head is hurled in a real gravity arc, and when
 * it lands (or strikes someone) it bursts into a ring of amethyst shard bolts.
 *
 * <p>Cost model (entity-count first): each star is ONE item display plus one particle per tick, and
 * the fan is capped at {@link #MAX_STARS}. The shard burst is fully virtual (particles plus a radius
 * test), so it adds no entities. Everything is driven from one ticker, like {@link SmokeCloud}.
 *
 * <p>Cleanup: every way a star can end (hit, ground, timeout, caster dead, level gone) goes through
 * {@link Star#end}, which discards the display. As a second layer, the display is tagged
 * {@code emberfall_run} so the run teardown removes any that somehow survive.
 */
public final class StarBitLob {
    private StarBitLob() {}

    /** The seven verified minecraft-heads.com "Star Bit" colours (Super Mario candy set). */
    public enum Colour {
        BLUE("blue", 0x4FA0FF,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODdjMmU0YWVjYjExNjVlNTgzMjkwNTNkZGM5ZDBhMGY0MzczNDU4Mzc0NTExZGI3MDdhMDgxY2MyM2EwNDUwMyJ9fX0="),
        RED("red", 0xFF4040,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjAzMzVjNTA4OWIyOGY3ZTEyNjE3NDliZjEwN2ZhNjc1OTAwYzE5NTZjYTllNmQ4NTFiYTU1YTVlMWY4NjI0YSJ9fX0="),
        YELLOW("yellow", 0xFFE040,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTk5MGI4MTMzZmRmYzNjMDQ3NTNmYzQ0NWUwODhkYTdhZTdhNzg3MGJlNWUzNDRiZWEzMDgyOWFhNmVkY2IzMCJ9fX0="),
        PURPLE("purple", 0xB060FF,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzk1NDgyMmVlZGM5YjY5N2U5MjU1NjA1NjJiODJmOGY2ZjA2Y2Y0MGRiZTQ2YmIxOWI1MDRiMGEyMjU4Y2YyZiJ9fX0="),
        GREEN("green", 0x50E070,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTFjN2FhM2MwMWQ5NDY0ODZjYTAxYTA1N2E1OGZlOWEzYmMxMDQ2OTMwYmNkYzJhZmFkMGI0NmQ5YmY4OWQwYSJ9fX0="),
        WHITE("white", 0xF0F0FF,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjQ4OWM1MDJmMTk0MTdhM2RkNjYzM2RlOGRlNTNjM2ZlNzI5OTQwMjBjZjlkMzRjMjA0ZjZlNzY0YTJhZWRmZCJ9fX0="),
        ORANGE("orange", 0xFF9A30,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjIxNWJkYzdmZmQwNjFkODQxNGZkYWI4OWViYjgxYjYxMDg4NzQwZGI2MzYyMTgzMzNlMTJiYjUzODczZmVhNyJ9fX0=");

        final String label;
        final int rgb;
        final String texture;

        Colour(String label, int rgb, String texture) {
            this.label = label;
            this.rgb = rgb;
            this.texture = texture;
        }

        /** The colour the landing ring is drawn in, so each star's warning matches the star that will fall on it. */
        public int rgbForWarning() { return rgb; }

        /** Built once per colour: an ItemStack is immutable in use here and shared by every star of that colour. */
        ItemStack stack() {
            return EliteHeads.customHead(texture, "starbit_" + label.substring(0, Math.min(label.length(), 7)));
        }
    }

    // ---- tuning (values from SlopPack: 6 direct hit, 6 to 8 shards of 2 damage, 14 ticks, 0.45 per tick) ----
    public static final int MAX_STARS = 3;
    public static final int FLIGHT_TICKS = 26;            // time from release to the marked spot
    private static final double GRAVITY = 0.05;           // blocks per tick squared (a light, floaty lob)
    private static final double HIT_RADIUS = 0.9;
    private static final double DIRECT_DAMAGE = 6.0;
    private static final double SHARD_DAMAGE = 2.0;
    private static final int SHARD_COUNT = 7;
    private static final double SHARD_SPEED = 0.45;
    private static final int SHARD_TICKS = 14;
    private static final double SHARD_HIT_RADIUS = 0.6;
    private static final float DISPLAY_SCALE = 0.9F;
    /** Radius the burst is warned at: the shards travel 0.45 x 14 = 6.3 blocks, so the ring is the honest reach. */
    public static final double LANDING_WARN_RADIUS = 1.6;

    private static final List<Star> STARS = new ArrayList<>();
    private static final List<Shard> SHARDS = new ArrayList<>();
    /**
     * True while tickAll runs. A hit can kill the player, which ends the run and discards the caster, whose remove()
     * calls {@link #clearFor} from INSIDE the tick: editing STARS / SHARDS there made the tick's own iterator throw
     * ConcurrentModificationException. During a tick clearFor therefore only marks items ended / dead, and the tick
     * removes them itself.
     */
    private static boolean ticking;

    private static final class Star {
        final ServerLevel level;
        final LivingEntity caster;
        final Colour colour;
        final double damageScale;
        final Vec3 landing;
        double burstReach = SHARD_SPEED * SHARD_TICKS;   // blocks the shards travel; the Magus keeps 6.3, small casters override
        Vec3 pos;
        Vec3 velocity;
        int ticks;
        Display.ItemDisplay display;
        boolean ended;

        Star(ServerLevel level, LivingEntity caster, Colour colour, double damageScale, Vec3 from, Vec3 landing) {
            this.level = level;
            this.caster = caster;
            this.colour = colour;
            this.damageScale = damageScale;
            this.landing = landing;
            this.pos = from;
            // Solve the launch velocity so the arc lands exactly on the marked spot after FLIGHT_TICKS:
            // The ticker moves by the current velocity THEN applies gravity, so the summed height after T ticks is
            // T*vy - 0.5*g*T*(T-1), hence vy = (dy + 0.5*g*T*(T-1)) / T (simulated: lands within 0.001 of dy).
            double t = FLIGHT_TICKS;
            Vec3 d = landing.subtract(from);
            this.velocity = new Vec3(d.x / t, (d.y + 0.5 * GRAVITY * t * (t - 1.0)) / t, d.z / t);
        }

        void end() {
            if (ended) {
                return;
            }
            ended = true;
            if (display != null) {
                display.discard();
                display = null;
            }
        }
    }

    private static final class Shard {
        final ServerLevel level;
        final LivingEntity caster;
        final double damage;
        Vec3 pos;
        final Vec3 velocity;
        final int rgb;
        final int maxTicks;
        int ticks;
        /** Set by clearFor while tickAll is running, so the list is never edited under its own iterator. */
        boolean dead;

        Shard(ServerLevel level, LivingEntity caster, double damage, Vec3 pos, Vec3 velocity, int rgb, int maxTicks) {
            this.maxTicks = maxTicks;
            this.level = level;
            this.caster = caster;
            this.damage = damage;
            this.pos = pos;
            this.velocity = velocity;
            this.rgb = rgb;
        }
    }

    /**
     * Throws one star from {@code from} so it lands on {@code landing}. Returns false (and spawns
     * nothing) if the fan is already at its cap, so a caller can never exceed {@link #MAX_STARS}
     * displays for one Magus no matter how the scheduler misbehaves.
     */
    public static boolean launch(ServerLevel level, LivingEntity caster, Colour colour, double damageScale,
                                 Vec3 from, Vec3 landing) {
        return launch(level, caster, colour, damageScale, from, landing, SHARD_SPEED * SHARD_TICKS, MAX_STARS);
    }

    /**
     * Same throw with a chosen burst reach (how far the shards fly, so it is also the honest damage radius) and a
     * per-caster cap. The Horde Witch uses a 2 block reach; the Magus keeps the 6.3 default above.
     */
    public static boolean launch(ServerLevel level, LivingEntity caster, Colour colour, double damageScale,
                                 Vec3 from, Vec3 landing, double burstReach, int maxStars) {
        int mine = 0;
        for (Star s : STARS) {
            if (s.caster == caster && !s.ended) {
                mine++;
            }
        }
        if (mine >= maxStars) {
            return false;
        }
        Star star = new Star(level, caster, colour, damageScale, from, landing);
        star.burstReach = burstReach;
        Display.ItemDisplay d = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        d.setPos(from.x, from.y, from.z);
        d.addTag("emberfall_run");
        // The Magus keeps the original tag (its tests and cleanup count it); any other caster (the Horde Witch) is
        // tagged separately so a fight with both never mixes their stars.
        d.addTag(caster instanceof UmbralMagus ? "emberfall_starbit" : "emberfall_witchbolt");
        d.setItemStack(colour.stack());
        d.setViewRange(2.0F);
        d.setPosRotInterpolationDuration(2);
        d.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        d.setTransformation(new Transformation(
                new Vector3f(), new Quaternionf(), new Vector3f(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE), new Quaternionf()));
        d.setNoGravity(true);
        d.setInvulnerable(true);
        level.addFreshEntity(d);
        star.display = d;
        STARS.add(star);
        Fx.sound(level, from, SoundEvents.ALLAY_ITEM_GIVEN, 0.9F, 1.5F);
        return true;
    }

    public static void tickAll(MinecraftServer server) {
        if (STARS.isEmpty() && SHARDS.isEmpty()) {
            return;
        }
        ticking = true;
        try {
            tickAllInner();
        } finally {
            ticking = false;
        }
    }

    private static void tickAllInner() {
        Iterator<Star> it = STARS.iterator();
        List<Star> burst = new ArrayList<>();
        while (it.hasNext()) {
            Star s = it.next();
            if (s.ended) {
                it.remove();
                continue;
            }
            // Caster died / left, level is gone, or the display was removed by something else: end quietly.
            if (!s.caster.isAlive() || s.caster.level() != s.level || s.display == null || !s.display.isAlive()) {
                s.end();
                it.remove();
                continue;
            }
            s.ticks++;
            Vec3 next = s.pos.add(s.velocity);
            s.velocity = s.velocity.subtract(0.0, GRAVITY, 0.0);

            BlockPos bp = BlockPos.containing(next);
            boolean blocked = !s.level.isLoaded(bp)
                    || !s.level.getBlockState(bp).getCollisionShape(s.level, bp).isEmpty();
            boolean struck = false;
            LivingEntity victim = null;
            if (!blocked) {
                for (Player p : s.level.getEntitiesOfClass(Player.class,
                        new net.minecraft.world.phys.AABB(next, next).inflate(HIT_RADIUS),
                        pl -> pl.isAlive() && !pl.isSpectator() && !pl.isCreative())) {
                    victim = p;
                    struck = true;
                    break;
                }
            }
            if (blocked) {
                // Burst where it was last free, not inside the wall.
                s.end();
                burst.add(s);
                it.remove();
                continue;
            }
            s.pos = next;
            s.display.setPos(next.x, next.y, next.z);
            s.level.sendParticles(new DustParticleOptions(s.colour.rgb, 0.9F), next.x, next.y, next.z, 1, 0.0, 0.0, 0.0, 0.0);
            if (struck) {
                DamageSource source = s.caster.damageSources().mobAttack(s.caster);
                victim.hurtServer(s.level, source, (float) (DIRECT_DAMAGE * s.damageScale));
                s.end();
                burst.add(s);
                it.remove();
            } else if (s.ticks >= FLIGHT_TICKS + 8) {
                // Overshot the marked spot without touching anything: burst where it is, never linger.
                s.end();
                burst.add(s);
                it.remove();
            }
        }
        for (Star s : burst) {
            shatter(s);
        }
        tickShards();
    }

    /** Bursts a landed star into shard bolts spread evenly in a ring, each with a little vertical jitter. */
    private static void shatter(Star s) {
        ServerLevel level = s.level;
        Vec3 at = s.pos.add(0.0, 0.2, 0.0);
        Fx.sound(level, at, SoundEvents.AMETHYST_BLOCK_BREAK, 1.2F, 1.0F);
        Fx.sound(level, at, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 1.2F);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0.08);
        level.sendParticles(new DustParticleOptions(s.colour.rgb, 1.4F), at.x, at.y, at.z, 14, 0.3, 0.3, 0.3, 0.05);
        double phase = level.getRandom().nextDouble() * Math.PI * 2.0;
        for (int i = 0; i < SHARD_COUNT; i++) {
            double a = phase + (Math.PI * 2.0 / SHARD_COUNT) * i;
            Vec3 v = new Vec3(Math.cos(a), 0.0, Math.sin(a)).scale(SHARD_SPEED);
            SHARDS.add(new Shard(level, s.caster, SHARD_DAMAGE * s.damageScale, at, v, s.colour.rgb,
                    Math.max(1, (int) Math.round(s.burstReach / SHARD_SPEED))));
        }
    }

    private static void tickShards() {
        Iterator<Shard> it = SHARDS.iterator();
        while (it.hasNext()) {
            Shard sh = it.next();
            if (sh.dead || !sh.caster.isAlive() || sh.caster.level() != sh.level || ++sh.ticks > sh.maxTicks) {
                it.remove();
                continue;
            }
            Vec3 next = sh.pos.add(sh.velocity);
            BlockPos bp = BlockPos.containing(next);
            if (!sh.level.isLoaded(bp) || !sh.level.getBlockState(bp).getCollisionShape(sh.level, bp).isEmpty()) {
                it.remove(); // stops dead on terrain, nothing persists
                continue;
            }
            sh.pos = next;
            sh.level.sendParticles(new DustParticleOptions(sh.rgb, 0.8F), next.x, next.y, next.z, 1, 0.0, 0.0, 0.0, 0.0);
            boolean hit = false;
            for (Player p : sh.level.getEntitiesOfClass(Player.class,
                    new net.minecraft.world.phys.AABB(next, next).inflate(SHARD_HIT_RADIUS),
                    pl -> pl.isAlive() && !pl.isSpectator() && !pl.isCreative())) {
                p.hurtServer(sh.level, sh.caster.damageSources().mobAttack(sh.caster), (float) sh.damage);
                hit = true;
                break;
            }
            if (hit) {
                sh.level.sendParticles(ParticleTypes.END_ROD, next.x, next.y, next.z, 3, 0.1, 0.1, 0.1, 0.02);
                it.remove();
            }
        }
    }

    /** Ends every star and shard belonging to {@code caster}: called when the Magus dies or is removed. */
    public static void clearFor(LivingEntity caster) {
        if (ticking) {
            // Called from inside tickAll (the run ended on a lethal hit): never edit the lists under the running iterator.
            for (Star s : STARS) {
                if (s.caster == caster) {
                    s.end();
                }
            }
            for (Shard sh : SHARDS) {
                if (sh.caster == caster) {
                    sh.dead = true;
                }
            }
            return;
        }
        for (Iterator<Star> it = STARS.iterator(); it.hasNext(); ) {
            Star s = it.next();
            if (s.caster == caster) {
                s.end();
                it.remove();
            }
        }
        SHARDS.removeIf(sh -> sh.caster == caster);
    }

    /** Live counts for the tests: stars in flight, shards in flight. */
    public static int liveStars() { return STARS.size(); }
    public static int liveShards() { return SHARDS.size(); }
}
