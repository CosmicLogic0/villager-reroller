package dev.villagerreroller.port;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//#if MC >= 1.20
import net.minecraft.client.gui.GuiGraphics;
//#elif MC >= 1.16
import com.mojang.blaze3d.vertex.PoseStack;
//#endif
//#if MC >= 1.21.9
import net.minecraft.client.input.MouseButtonEvent;
//#endif

/**
 * The menu's screen, in one shape for every Minecraft version: the menu draws its panel in {@link #drawPanel},
 * everything on top in {@link #drawContent}, and gets clicks and scrolls with plain numbers.
 */
public abstract class VrScreen extends Screen {

    protected VrScreen(Component title) {
        super(title);
    }

    /** The panel behind everything (drawn after the dimmed world, before the search box). */
    protected abstract void drawPanel(G g, int mouseX, int mouseY);

    /** Everything drawn on top of the widgets. */
    protected abstract void drawContent(G g, int mouseX, int mouseY);

    /** @return true if the click was used */
    protected abstract boolean click(double x, double y, int button);

    /** @return true if the scroll was used; amount > 0 is up */
    protected abstract boolean scroll(double x, double y, double amount);

    protected void addBox(EditBox box) {
        //#if MC >= 1.17
        addRenderableWidget(box);
        //#else
        addButton(box);
        //#endif
    }

    protected void dropFocus() {
        //#if MC >= 1.19.4
        clearFocus();
        //#else
        setFocused(null);
        //#endif
    }

    // ------------------------------------------------------------------ rendering

    //#if MC >= 1.20.2
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderBackground(g, mouseX, mouseY, delta);
        drawPanel(new G(g), mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        drawContent(new G(g), mouseX, mouseY);
    }
    //#elif MC >= 1.20
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g);
        G gg = new G(g);
        drawPanel(gg, mouseX, mouseY);
        super.render(g, mouseX, mouseY, delta);
        drawContent(gg, mouseX, mouseY);
    }
    //#elif MC >= 1.16
    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float delta) {
        renderBackground(pose);
        G gg = new G(pose, this);
        drawPanel(gg, mouseX, mouseY);
        super.render(pose, mouseX, mouseY, delta);
        drawContent(gg, mouseX, mouseY);
    }
    //#else
    @Override
    public void render(int mouseX, int mouseY, float delta) {
        renderBackground();
        G gg = new G(this);
        drawPanel(gg, mouseX, mouseY);
        super.render(mouseX, mouseY, delta);
        drawContent(gg, mouseX, mouseY);
    }
    //#endif

    // ------------------------------------------------------------------ input

    //#if MC >= 1.21.9
    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (click(e.x(), e.y(), e.button())) {
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }
    //#else
    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (click(x, y, button)) {
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    //#endif

    //#if MC >= 1.20.2
    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (scroll(x, y, sy)) {
            return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }
    //#else
    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        if (scroll(x, y, amount)) {
            return true;
        }
        return super.mouseScrolled(x, y, amount);
    }
    //#endif
}
