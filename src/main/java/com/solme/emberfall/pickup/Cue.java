package com.solme.emberfall.pickup;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * One place that plays a gameplay cue (see docs/sound_audit.md). Playing the sound and writing the test log line happen in the same
 * method, so "the log line is there but the sound was never played" cannot happen. In test mode a {@code SOUND_TEST id=... at=...}
 * line is written, which the live tests read. The line proves the call was made, not that anyone can hear it.
 */
public final class Cue {
    private Cue() {}

    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");

    /** Plays {@code sound} for everyone near {@code at}. {@code id} names the cue in the test log. */
    public static void play(ServerLevel level, String id, SoundEvent sound, SoundSource source, Vec3 at, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, source, volume, pitch);
        if (TEST_MODE) {
            EmberfallMod.LOGGER.info("SOUND_TEST id={} at={},{},{} vol={} pitch={}", id, Math.round(at.x), Math.round(at.y), Math.round(at.z), volume, pitch);
        }
    }
}
