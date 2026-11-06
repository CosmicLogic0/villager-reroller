package net.fabricmc.loader.api;

import java.nio.file.Path;
//#if FORGE
import net.minecraftforge.fml.loading.FMLPaths;
//#else
import net.neoforged.fml.loading.FMLPaths;
//#endif

/** Stand-in for the bits of Fabric Loader the mod uses. */
public final class FabricLoader {
    private static final FabricLoader I = new FabricLoader();

    public static FabricLoader getInstance() {
        return I;
    }

    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }
}
