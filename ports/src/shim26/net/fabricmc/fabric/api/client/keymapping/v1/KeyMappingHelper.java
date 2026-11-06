package net.fabricmc.fabric.api.client.keymapping.v1;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
//#if FORGE
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
//#else
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
//#endif

/** Stand-in for Fabric's key mapping helper, backed by the loader's RegisterKeyMappingsEvent. */
public final class KeyMappingHelper {
    private KeyMappingHelper() {
    }

    private static final List<KeyMapping> KEYS = new ArrayList<>();
    private static boolean hooked;

    public static KeyMapping registerKeyMapping(KeyMapping k) {
        if (!hooked) {
            hooked = true;
            //#if FORGE
            RegisterKeyMappingsEvent.BUS.addListener(e -> {
                for (KeyMapping m : KEYS) {
                    e.register(m);
                }
            });
            //#else
            dev.villagerreroller.entry.NeoEntry.modBus.addListener((RegisterKeyMappingsEvent e) -> {
                for (KeyMapping m : KEYS) {
                    e.register(m);
                }
            });
            //#endif
        }
        KEYS.add(k);
        return k;
    }
}
