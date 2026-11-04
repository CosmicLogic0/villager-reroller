package dev.villagerreroller.game;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Small helpers for talking to the player (Minecraft 26.3 names: sendSystemMessage / sendOverlayMessage). */
public final class Msg {

    private static final MutableComponent PREFIX =
            Component.literal("[Villager Reroller] ").withStyle(ChatFormatting.AQUA);

    private Msg() {
    }

    public static void info(Minecraft mc, String text) {
        send(mc, Component.literal(text).withStyle(ChatFormatting.WHITE));
    }

    public static void ok(Minecraft mc, String text) {
        send(mc, Component.literal(text).withStyle(ChatFormatting.GREEN));
    }

    public static void warn(Minecraft mc, String text) {
        send(mc, Component.literal(text).withStyle(ChatFormatting.RED));
    }

    public static void step(Minecraft mc, String text) {
        send(mc, Component.literal(text).withStyle(ChatFormatting.YELLOW));
    }

    /** Short status line above the hotbar. */
    public static void bar(Minecraft mc, String text) {
        if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.literal(text));
        }
    }

    private static void send(Minecraft mc, Component body) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(PREFIX.copy().append(body));
        }
    }
}
