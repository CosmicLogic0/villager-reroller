package dev.villagerreroller.entry;

import dev.villagerreroller.VillagerRerollerClient;
import dev.villagerreroller.config.ConfigIO;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
//#if MC >= 1.20.5
import net.neoforged.neoforge.client.event.ClientTickEvent;
//#else
import net.neoforged.neoforge.event.TickEvent;
//#endif
//#if MC < 1.20.6
import net.neoforged.fml.loading.FMLEnvironment;
//#endif

/** NeoForge's way in. Client-side only. */
//#if MC >= 1.20.6
@Mod(value = "villagerreroller", dist = Dist.CLIENT)
//#else
@Mod("villagerreroller")
//#endif
public class NeoEntry {

    public NeoEntry(IEventBus modBus) {
        //#if MC >= 1.20.6
        Client.start(modBus);
        //#else
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Client.start(modBus);
        }
        //#endif
    }

    /** Kept in its own class so a server never loads client code. */
    static final class Client {

        static void start(IEventBus modBus) {
            ConfigIO.configDir = FMLPaths.CONFIGDIR.get();
            VillagerRerollerClient.init();
            modBus.addListener((RegisterKeyMappingsEvent e) -> {
                for (KeyMapping k : VillagerRerollerClient.keys()) {
                    e.register(k);
                }
            });

            //#if MC >= 1.20.5
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> VillagerRerollerClient.tick(Minecraft.getInstance()));
            //#else
            NeoForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
                if (e.phase == TickEvent.Phase.END) {
                    VillagerRerollerClient.tick(Minecraft.getInstance());
                }
            });
            //#endif
            NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e) -> {
                if (VillagerRerollerClient.onUseBlock(e.getEntity(), e.getLevel(), e.getHand(), e.getHitVec())) {
                    e.setCanceled(true);
                    e.setCancellationResult(InteractionResult.FAIL);
                }
            });
            NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract e) ->
                    VillagerRerollerClient.onUseEntity(e.getEntity(), e.getTarget()));
            NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock e) ->
                    VillagerRerollerClient.onAttackBlock(e.getEntity(), e.getPos(), e.getFace()));
            NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) ->
                    e.getDispatcher().register(VillagerRerollerClient.<CommandSourceStack>command()));
        }
    }
}
