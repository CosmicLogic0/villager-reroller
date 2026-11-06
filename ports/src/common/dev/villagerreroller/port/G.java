package dev.villagerreroller.port;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
//#if MC >= 1.20
import net.minecraft.client.gui.GuiGraphics;
//#elif MC >= 1.16
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
//#else
import net.minecraft.client.gui.GuiComponent;
//#endif

/** The few drawing calls the menu uses, for whatever this Minecraft version draws menus with. */
public final class G {

    //#if MC >= 1.20
    public final GuiGraphics g;
    public G(GuiGraphics g) {
        this.g = g;
    }
    //#elif MC >= 1.16
    public final PoseStack pose;
    private final Screen screen;
    public G(PoseStack pose, Screen screen) {
        this.pose = pose;
        this.screen = screen;
    }
    //#else
    private final Screen screen;
    public G(Screen screen) {
        this.screen = screen;
    }
    //#endif

    public void fill(int x0, int y0, int x1, int y1, int argb) {
        //#if MC >= 1.20
        g.fill(x0, y0, x1, y1, argb);
        //#elif MC >= 1.16
        GuiComponent.fill(pose, x0, y0, x1, y1, argb);
        //#else
        GuiComponent.fill(x0, y0, x1, y1, argb);
        //#endif
    }

    public void text(Font f, String s, int x, int y, int argb, boolean shadow) {
        //#if MC >= 1.20
        g.drawString(f, s, x, y, argb, shadow);
        //#elif MC >= 1.16
        if (shadow) {
            f.drawShadow(pose, s, x, y, argb);
        } else {
            f.draw(pose, s, x, y, argb);
        }
        //#else
        if (shadow) {
            f.drawShadow(s, x, y, argb);
        } else {
            f.draw(s, x, y, argb);
        }
        //#endif
    }

    public void item(ItemStack st, int x, int y) {
        //#if MC >= 1.20
        g.renderItem(st, x, y);
        //#elif MC >= 1.19.4
        Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(pose, st, x, y);
        //#else
        Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(st, x, y);
        //#endif
    }

    public void tooltip(Font f, List<Component> lines, int x, int y) {
        //#if MC >= 1.21.6
        g.setComponentTooltipForNextFrame(f, lines, x, y);
        //#elif MC >= 1.20
        g.renderComponentTooltip(f, lines, x, y);
        //#elif MC >= 1.16.2
        screen.renderComponentTooltip(pose, lines, x, y);
        //#elif MC >= 1.16
        screen.renderTooltip(pose, lines, x, y);
        //#else
        java.util.List<String> plain = new java.util.ArrayList<>();
        for (Component c : lines) {
            plain.add(c.getColoredString());
        }
        screen.renderTooltip(plain, x, y);
        //#endif
    }
}
