package net.fabricmc.api;

/** Stand-in for Fabric's entry interface (Forge / NeoForge builds). */
public interface ClientModInitializer {
    void onInitializeClient();
}
