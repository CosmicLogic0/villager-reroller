package dev.villagerreroller.game;

import dev.villagerreroller.VillagerRerollerClient;
import dev.villagerreroller.port.V;
import net.minecraft.client.Minecraft;

/** The mod's feedback sounds. Only you hear them (they are played on your client, not in the world). */
public final class Sounds {

    private Sounds() {
    }

    private static boolean enabled() {
        return VillagerRerollerClient.config == null || VillagerRerollerClient.config.sounds;
    }

    /** Rerolling started: a short, bright pling. */
    public static void started(Minecraft mc) {
        if (enabled()) {
            V.play(mc, V.Sound.START);
        }
    }

    /** Rerolling stopped without finding anything: a low bass note. */
    public static void stopped(Minecraft mc) {
        if (enabled()) {
            V.play(mc, V.Sound.STOP);
        }
    }

    /** A wanted book was found: the advancement "challenge complete" fanfare. */
    public static void found(Minecraft mc) {
        if (enabled()) {
            V.play(mc, V.Sound.FOUND);
        }
    }
}
