package dev.villagerreroller.game;

import dev.villagerreroller.port.V;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Chat messages from the mod, with a coloured prefix so they're easy to spot. */
public final class Msg {

    private Msg() {
    }

    public static void info(Minecraft mc, String text) {
        send(mc, V.text(text, ChatFormatting.WHITE));
    }

    public static void ok(Minecraft mc, String text) {
        send(mc, V.text(text, ChatFormatting.GREEN));
    }

    public static void warn(Minecraft mc, String text) {
        send(mc, V.text(text, ChatFormatting.RED));
    }

    public static void step(Minecraft mc, String text) {
        send(mc, V.text(text, ChatFormatting.YELLOW));
    }

    /** Short status shown above the hotbar instead of in chat. */
    public static void bar(Minecraft mc, String text) {
        V.actionBar(mc, V.text(text));
    }

    private static void send(Minecraft mc, Component body) {
        V.chat(mc, V.join(V.text("[Villager Reroller] ", ChatFormatting.AQUA), body));
    }
}
