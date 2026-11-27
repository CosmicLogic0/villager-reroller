package net.fabricmc.fabric.api.client.rendering.v1.hud;

import net.minecraft.resources.Identifier;

/** Stand-in for Fabric's names of the vanilla HUD parts (only the one the mod uses). */
public final class VanillaHudElements {
    private VanillaHudElements() {
    }

    //#if FORGE
    public static final Identifier CHAT = net.minecraftforge.client.gui.overlay.ForgeLayeredDraw.CHAT_OVERLAY;
    //#else
    public static final Identifier CHAT = net.neoforged.neoforge.client.gui.VanillaGuiLayers.CHAT;
    //#endif
}
