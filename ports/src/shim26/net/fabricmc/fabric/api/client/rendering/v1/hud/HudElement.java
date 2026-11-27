package net.fabricmc.fabric.api.client.rendering.v1.hud;

/** Stand-in for Fabric's HUD element: the same shape as the loader's own HUD layer. */
//#if FORGE
public interface HudElement extends net.minecraftforge.client.gui.overlay.ForgeLayer {
}
//#else
public interface HudElement extends net.neoforged.neoforge.client.gui.GuiLayer {
}
//#endif
