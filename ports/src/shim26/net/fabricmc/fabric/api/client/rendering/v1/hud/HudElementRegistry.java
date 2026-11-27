package net.fabricmc.fabric.api.client.rendering.v1.hud;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;

/** Stand-in for Fabric's HUD registry, backed by the loader's own HUD layers. */
public final class HudElementRegistry {
    private HudElementRegistry() {
    }

    private static final class Entry {
        final Identifier before;
        final Identifier id;
        final HudElement element;

        Entry(Identifier before, Identifier id, HudElement element) {
            this.before = before;
            this.id = id;
            this.element = element;
        }
    }

    private static final List<Entry> LIST = new ArrayList<>();
    private static boolean hooked;

    public static void attachElementBefore(Identifier before, Identifier id, HudElement element) {
        if (!hooked) {
            hooked = true;
            //#if FORGE
            net.minecraftforge.client.event.AddGuiOverlayLayersEvent.BUS.addListener(e -> {
                for (Entry x : LIST) {
                    e.getLayeredDraw().addBelow(net.minecraftforge.client.gui.overlay.ForgeLayeredDraw.POST_SLEEP_STACK, x.id, x.before, x.element);
                }
            });
            //#else
            dev.villagerreroller.entry.NeoEntry.modBus.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent e) -> {
                for (Entry x : LIST) {
                    e.registerBelow(x.before, x.id, x.element);
                }
            });
            //#endif
        }
        LIST.add(new Entry(before, id, element));
    }
}
