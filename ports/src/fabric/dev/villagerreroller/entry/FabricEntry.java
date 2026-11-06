package dev.villagerreroller.entry;

import dev.villagerreroller.VillagerRerollerClient;
import dev.villagerreroller.config.ConfigIO;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.InteractionResult;
//#if MC >= 1.19
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
//#elif MC >= 1.16
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
//#endif

/** Fabric's way in. Client-side only. */
public final class FabricEntry implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ConfigIO.configDir = FabricLoader.getInstance().getConfigDir();
        VillagerRerollerClient.init();
        for (KeyMapping k : VillagerRerollerClient.keys()) {
            KeyBindingHelper.registerKeyBinding(k);
        }
        ClientTickEvents.END_CLIENT_TICK.register(VillagerRerollerClient::tick);

        // These fire for both the client and (in singleplayer) the integrated server; the mod ignores everything
        // that isn't the local player.
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                VillagerRerollerClient.onUseBlock(player, level, hand, hit) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            VillagerRerollerClient.onUseEntity(player, entity);
            return InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, face) -> {
            VillagerRerollerClient.onAttackBlock(player, pos, face);
            return InteractionResult.PASS;
        });

        //#if MC >= 1.19
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(VillagerRerollerClient.<FabricClientCommandSource>command()));
        //#elif MC >= 1.16
        // Fabric API's client commands arrived late in 1.16; older Fabric API builds don't have them
        if (FabricLoader.getInstance().isModLoaded("fabric-command-api-v1")) {
            try {
                Commands16.register();
            } catch (Throwable t) {
                VillagerRerollerClient.LOGGER.warn("Villager Reroller: this Fabric API has no client commands; use the V key menu");
            }
        }
        //#endif
        // Minecraft 1.15 has no client commands in Fabric: the V key menu does everything.
    }

    //#if MC >= 1.16 && MC < 1.19
    /** In its own class so a Fabric API without client commands never loads it. */
    private static final class Commands16 {
        static void register() {
            ClientCommandManager.DISPATCHER.register(VillagerRerollerClient.<FabricClientCommandSource>command());
        }
    }
    //#endif
}
