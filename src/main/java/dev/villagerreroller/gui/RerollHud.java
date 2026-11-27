package dev.villagerreroller.gui;

import dev.villagerreroller.VillagerRerollerClient;
import dev.villagerreroller.logic.RerollStats;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.List;

/** The small panel in the top-left corner while rerolling: rerolls, time, villager, found books and the last few books. */
public final class RerollHud {
    private RerollHud() {
    }

    public static void init() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(VillagerRerollerClient.MOD_ID, "panel"), (g, delta) -> {
                    try {
                        draw(g);
                    } catch (RuntimeException e) {
                        // the panel must never break the game's HUD
                    }
                });
    }

    private static void draw(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        RerollStats st = VillagerRerollerClient.CONTROLLER.stats;
        long now = System.currentTimeMillis();
        if (mc.player == null || !VillagerRerollerClient.config.showHud || !st.visible(now)) {
            return;
        }
        List<RerollStats.Line> lines = st.lines(now);
        int w = 0;
        for (RerollStats.Line l : lines) {
            w = Math.max(w, mc.font.width(l.text));
        }
        int x = 4;
        int y = 4;
        int h = lines.size() * 10 + 6;
        g.fill(x, y, x + w + 10, y + h, 0xB0101418);
        g.fill(x, y, x + 2, y + h, 0xFFFFC94A);
        for (int i = 0; i < lines.size(); i++) {
            RerollStats.Line l = lines.get(i);
            g.text(mc.font, l.text, x + 6, y + 4 + i * 10, l.color, true);
        }
    }
}
