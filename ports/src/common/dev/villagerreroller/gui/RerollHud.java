package dev.villagerreroller.gui;

import dev.villagerreroller.VillagerRerollerClient;
import dev.villagerreroller.logic.RerollStats;
import dev.villagerreroller.port.G;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * The small panel in the top-left corner while rerolling: rerolls, time, villager, found books and the last few
 * books. Each loader's entry class calls {@link #draw} from its own HUD event.
 */
public final class RerollHud {
    private RerollHud() {
    }

    public static void draw(G g) {
        try {
            Minecraft mc = Minecraft.getInstance();
            RerollStats st = VillagerRerollerClient.CONTROLLER.stats;
            long now = System.currentTimeMillis();
            if (mc.player == null || mc.options.hideGui || !VillagerRerollerClient.config.showHud || !st.visible(now)) {
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
        } catch (RuntimeException e) {
            // the panel must never break the game's HUD
        }
    }
}
