package com.solme.emberfall.character;

import com.solme.emberfall.command.CharacterCommand;
import com.solme.emberfall.item.WeaponPool;
import com.solme.emberfall.item.WeaponType;
import com.solme.emberfall.network.ChooseCharacterPayload;
import com.solme.emberfall.network.OpenCharacterSelectPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Server half of the character-selection screen. {@link #open} builds the roster from the real
 * {@link CharacterPool} (so the screen can never show something the server would refuse), and
 * {@link #onChoiceReceived} answers a click through {@link CharacterCommand#trySelect}, the one place that enforces
 * "unknown or locked id" and "no change mid-expedition". A forged packet therefore gets exactly what the command gets.
 */
public final class CharacterSelectManager {
    private CharacterSelectManager() {}

    public static void open(ServerPlayer player) {
        String current = PlayerCharacterSelection.get(player.level().getServer())
                .selectedOrFallback(player.getUUID()).id();
        List<OpenCharacterSelectPayload.Entry> entries = new ArrayList<>();
        for (CharacterType c : CharacterPool.defaultUnlocked()) {
            WeaponType weapon = WeaponPool.byId(c.weaponId());
            entries.add(new OpenCharacterSelectPayload.Entry(
                    c.id(), c.displayName(), c.description(),
                    weapon != null ? weapon.displayName() : c.weaponId(), statsLine(c)));
        }
        ServerPlayNetworking.send(player, new OpenCharacterSelectPayload(current, entries));
    }

    public static void onChoiceReceived(ServerPlayer player, ChooseCharacterPayload payload) {
        String error = CharacterCommand.trySelect(player, payload.characterId());
        if (error != null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7c" + error));
        }
    }

    /** One line: health and speed bias as percentages, then the character's passive. */
    static String statsLine(CharacterType c) {
        return "Health " + signed(c.maxHealthMultiplierDelta()) + "  Speed " + signed(c.movementSpeedMultiplierDelta())
                + "  " + c.passiveDescription();
    }

    private static String signed(double delta) {
        long pct = Math.round(delta * 100.0);
        return (pct >= 0 ? "+" : "") + pct + "%";
    }
}
