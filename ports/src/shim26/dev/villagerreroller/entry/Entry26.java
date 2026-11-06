package dev.villagerreroller.entry;

import dev.villagerreroller.VillagerRerollerClient;
//#if FORGE
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
//#else
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
//#endif

/** The loader's way in (Minecraft 26.x). Client-side only. */
//#if FORGE
@Mod("villagerreroller")
public class Entry26 {
    public Entry26(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Client.start();
        }
    }

    static final class Client {   // kept in its own class so a server never loads client code
        static void start() {
            new VillagerRerollerClient().onInitializeClient();
        }
    }
}
//#else
@Mod(value = "villagerreroller", dist = Dist.CLIENT)
public class Entry26 {
    public Entry26(IEventBus bus) {
        NeoEntry.modBus = bus;
        new VillagerRerollerClient().onInitializeClient();
    }
}
//#endif
