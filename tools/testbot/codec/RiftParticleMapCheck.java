package com.solme.emberfall.rift;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

/**
 * RiftParticleMapCheck: the adapter RiftStage.particle turns each schedule key into a real particle. Its default branch silently returns END_ROD,
 * so a key that is forgotten there still "works" and just draws a white streak. This check closes that hole. It needs the Minecraft jar, so like
 * ChestRevealCodecCheck it is run by hand (run_particle_check.sh) and CI does NOT run it. Lives in the rift package to reach the package-private method.
 */
public class RiftParticleMapCheck {
    static int fails = 0, total = 0;

    static void check(String name, boolean ok, String extra) {
        total++;
        System.out.println((ok ? "PASS " : "FAIL ") + name + (extra.isEmpty() ? "" : "  " + extra));
        if (!ok) {
            fails++;
        }
    }

    public static void main(String[] a) {
        // ParticleTypes needs the game registries; this is the standard headless bootstrap.
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        // Every distinct PARTICLE key the schedule emits, with one example event (opening and closing, many shapes).
        Map<String, RiftFx.Event> seen = new HashMap<>();
        for (long s = 0; s < 200; s++) {
            for (RiftFx.Event e : RiftFx.opening(RiftShape.generate(s))) {
                if (e.kind == RiftFx.Kind.PARTICLE) {
                    seen.putIfAbsent(e.key, e);
                }
            }
            for (RiftFx.Event e : RiftFx.closing(RiftShape.generate(s))) {
                if (e.kind == RiftFx.Kind.PARTICLE) {
                    seen.putIfAbsent(e.key, e);
                }
            }
        }
        check("the schedule emits exactly these particle keys: end_rod, electric_spark, dust_ring, glow, rim_dust", seen.keySet().equals(java.util.Set.of("end_rod", "electric_spark", "dust_ring", "glow", "rim_dust")), seen.keySet().toString());
        check("end_rod maps to END_ROD", RiftStage.particle(seen.get("end_rod")) == ParticleTypes.END_ROD, "");
        check("electric_spark maps to ELECTRIC_SPARK", RiftStage.particle(seen.get("electric_spark")) == ParticleTypes.ELECTRIC_SPARK, "");
        check("dust_ring maps to DUST_PLUME", RiftStage.particle(seen.get("dust_ring")) == ParticleTypes.DUST_PLUME, "");
        ParticleOptions glow = RiftStage.particle(seen.get("glow"));
        check("glow is a coloured dust", glow instanceof DustParticleOptions, glow.getClass().getSimpleName());
        ParticleOptions rim = RiftStage.particle(seen.get("rim_dust"));
        check("rim_dust is NOT the END_ROD fallback", rim != ParticleTypes.END_ROD, rim.getClass().getSimpleName());
        boolean isDust = rim instanceof DustParticleOptions;
        check("rim_dust is a coloured dust particle", isDust, rim.getClass().getSimpleName());
        if (isDust) {
            DustParticleOptions d = (DustParticleOptions) rim;
            org.joml.Vector3f c = d.getColor();
            float er = ((RiftFx.RIM_DUST >> 16) & 255) / 255.0F, eg = ((RiftFx.RIM_DUST >> 8) & 255) / 255.0F, eb = (RiftFx.RIM_DUST & 255) / 255.0F;
            boolean same = Math.abs(c.x - er) < 0.004F && Math.abs(c.y - eg) < 0.004F && Math.abs(c.z - eb) < 0.004F;
            check("rim_dust carries the event's colour, which is RiftFx.RIM_DUST (components 0..1)", same, String.format("got %.3f,%.3f,%.3f want %.3f,%.3f,%.3f", c.x, c.y, c.z, er, eg, eb));
            check("rim_dust is a thin layer (scale below the fill's 1.1)", d.getScale() < 1.1F && d.getScale() > 0.0F, "scale " + d.getScale());
        } else {
            check("rim_dust carries the event's colour, which is RiftFx.RIM_DUST (components 0..1)", false, "not a dust particle");
            check("rim_dust is a thin layer (scale below the fill's 1.1)", false, "not a dust particle");
        }
        // The fallback must not be reachable by a real key: an unknown key still gives END_ROD, which is why the line above matters.
        RiftFx.Event unknown = new RiftFx.Event(0, RiftFx.Kind.PARTICLE, "no_such_key", 0, 0, 0xFF0000, 0, 1);
        check("CONTROL: an unknown key still falls back to END_ROD (the trap this check guards)", RiftStage.particle(unknown) == ParticleTypes.END_ROD, "");
        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "FAILED " + fails + " of " + total);
        System.exit(fails == 0 ? 0 : 1);
    }
}
