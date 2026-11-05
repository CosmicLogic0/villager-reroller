package dev.villagerreroller.game;

import dev.villagerreroller.VillagerRerollerClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** The mod's feedback sounds. Only you hear them (they are played on your client, not in the world). */
public final class Sounds {

    private Sounds() {
    }

    private static boolean enabled() {
        return VillagerRerollerClient.config == null || VillagerRerollerClient.config.sounds;
    }

    /** Rerolling started: a short, bright pling. */
    public static void started(Minecraft mc) {
        if (!enabled()) {
            return;
        }
        play(mc, () -> SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.6F));
    }

    /** Rerolling stopped without finding anything: a low bass note. */
    public static void stopped(Minecraft mc) {
        if (!enabled()) {
            return;
        }
        play(mc, () -> SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 0.7F));
    }

    /** A wanted book was found: the advancement "challenge complete" fanfare. */
    public static void found(Minecraft mc) {
        if (!enabled()) {
            return;
        }
        play(mc, () -> SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.7F));
    }

    private interface Maker {
        SimpleSoundInstance make();
    }

    private static void play(Minecraft mc, Maker maker) {
        try {
            mc.getSoundManager().play(maker.make());
        } catch (RuntimeException ignored) {
            // a sound must never break rerolling
        }
    }
}
