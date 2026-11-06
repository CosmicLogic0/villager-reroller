package net.fabricmc.fabric.api.client.networking.v1;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** Stand-in for Fabric's disconnect event: fires on the first tick after leaving a world or server. */
public final class ClientPlayConnectionEvents {
    private ClientPlayConnectionEvents() {
    }

    public interface Disconnect {
        void onPlayDisconnect(ClientPacketListener handler, Minecraft client);
    }

    public static final class Event {
        private final List<Disconnect> list = new ArrayList<>();

        public void register(Disconnect cb) {
            hook();
            list.add(cb);
        }
    }

    public static final Event DISCONNECT = new Event();
    private static boolean hooked;
    private static boolean wasInWorld;

    private static void hook() {
        if (hooked) {
            return;
        }
        hooked = true;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean in = mc.level != null;
            if (wasInWorld && !in) {
                for (Disconnect d : DISCONNECT.list) {
                    d.onPlayDisconnect(null, mc);
                }
            }
            wasInWorld = in;
        });
    }
}
