package com.solme.emberfall.music;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/** The run music tracks. Each id must match a key in assets/emberfall/sounds.json. */
public final class ModSounds {
    private ModSounds() {}

    /** A track: its sound event and its exact length in ticks (from ffprobe of the shipped .ogg). */
    public record Track(String name, SoundEvent event, int lengthTicks) {}

    public static final Track NYNY_08 = track("nyny_08", 132.144762);
    public static final Track PUMP_IT_UP = track("pump_it_up_hyperdron", 206.262857);
    public static final Track MINECART = track("multi_minecart_drifting", 231.990567);
    public static final Track FINAL_CUBIC = track("final_cubic_generator", 618.579592);

    public static final java.util.List<Track> ALL = java.util.List.of(NYNY_08, PUMP_IT_UP, MINECART, FINAL_CUBIC);

    private static Track track(String name, double seconds) {
        var id = EmberfallMod.id("music." + name);
        SoundEvent event = Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
        return new Track(name, event, (int) Math.ceil(seconds * 20.0));
    }

    public static void init() {
        // classload trigger: the static fields above register the events
    }
}
