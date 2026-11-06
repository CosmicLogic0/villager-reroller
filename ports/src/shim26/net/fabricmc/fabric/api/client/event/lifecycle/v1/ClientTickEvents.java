package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
//#if FORGE
import net.minecraftforge.event.TickEvent;
//#else
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
//#endif

/** Stand-in for Fabric's client tick event, backed by the loader's own. */
public final class ClientTickEvents {
    private ClientTickEvents() {
    }

    public interface EndTick {
        void onEndTick(Minecraft mc);
    }

    public static final class Event {
        private final List<EndTick> list = new ArrayList<>();

        public void register(EndTick cb) {
            hook();
            list.add(cb);
        }
    }

    public static final Event END_CLIENT_TICK = new Event();
    private static boolean hooked;

    private static void hook() {
        if (hooked) {
            return;
        }
        hooked = true;
        //#if FORGE
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> fire());
        //#else
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> fire());
        //#endif
    }

    private static void fire() {
        Minecraft mc = Minecraft.getInstance();
        for (EndTick t : END_CLIENT_TICK.list) {
            t.onEndTick(mc);
        }
    }
}
