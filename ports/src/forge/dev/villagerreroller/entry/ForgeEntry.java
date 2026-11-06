package dev.villagerreroller.entry;

import dev.villagerreroller.VillagerRerollerClient;
import dev.villagerreroller.config.ConfigIO;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
//#if MC < 1.21.6
import net.minecraftforge.common.MinecraftForge;
//#endif
//#if MC >= 1.19.1
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
//#else
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
//#endif
//#if MC >= 1.18.2
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
//#endif

/** Forge's way in. Villager Reroller is client-side only, so it does nothing on a server. */
@Mod("villagerreroller")
public class ForgeEntry {

    public ForgeEntry() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Client.start();
        }
    }

    /** Kept in its own class so a server never loads client code. */
    static final class Client {

        static void start() {
            ConfigIO.configDir = FMLPaths.CONFIGDIR.get();
            VillagerRerollerClient.init();
            registerKeys();

            //#if MC >= 1.21.6
            TickEvent.ClientTickEvent.Post.BUS.addListener(e -> VillagerRerollerClient.tick(Minecraft.getInstance()));
            // returning true cancels the click
            PlayerInteractEvent.RightClickBlock.BUS.addListener(e -> {
                return VillagerRerollerClient.onUseBlock(e.getEntity(), e.getLevel(), e.getHand(), e.getHitVec());
            });
            PlayerInteractEvent.EntityInteract.BUS.addListener(e -> {
                VillagerRerollerClient.onUseEntity(e.getEntity(), e.getTarget());
            });
            PlayerInteractEvent.LeftClickBlock.BUS.addListener(e -> {
                VillagerRerollerClient.onAttackBlock(e.getEntity(), e.getPos(), e.getFace());
            });
            RegisterClientCommandsEvent.BUS.addListener(e ->
                    e.getDispatcher().register(VillagerRerollerClient.<CommandSourceStack>command()));
            //#else
            MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
                if (e.phase == TickEvent.Phase.END) {
                    VillagerRerollerClient.tick(Minecraft.getInstance());
                }
            });
            MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e) -> {
                //#if MC >= 1.19
                boolean cancel = VillagerRerollerClient.onUseBlock(e.getEntity(), e.getLevel(), e.getHand(), e.getHitVec());
                //#else
                boolean cancel = VillagerRerollerClient.onUseBlock(e.getPlayer(), e.getWorld(), e.getHand(), e.getHitVec());
                //#endif
                if (cancel) {
                    e.setCanceled(true);
                    e.setCancellationResult(InteractionResult.FAIL);
                }
            });
            MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract e) -> {
                //#if MC >= 1.19
                VillagerRerollerClient.onUseEntity(e.getEntity(), e.getTarget());
                //#else
                VillagerRerollerClient.onUseEntity(e.getPlayer(), e.getTarget());
                //#endif
            });
            MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock e) -> {
                //#if MC >= 1.19
                VillagerRerollerClient.onAttackBlock(e.getEntity(), e.getPos(), e.getFace());
                //#else
                VillagerRerollerClient.onAttackBlock(e.getPlayer(), e.getPos(), e.getFace());
                //#endif
            });
            //#if MC >= 1.18.2
            MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) ->
                    e.getDispatcher().register(VillagerRerollerClient.<CommandSourceStack>command()));
            //#endif
            //#endif
        }

        private static void registerKeys() {
            //#if MC >= 1.21.6
            RegisterKeyMappingsEvent.getBus(FMLJavaModLoadingContext.get().getModBusGroup()).addListener(e -> {
                for (KeyMapping k : VillagerRerollerClient.keys()) {
                    e.register(k);
                }
            });
            //#elif MC >= 1.19.1
            FMLJavaModLoadingContext.get().getModEventBus().addListener((RegisterKeyMappingsEvent e) -> {
                for (KeyMapping k : VillagerRerollerClient.keys()) {
                    e.register(k);
                }
            });
            //#else
            FMLJavaModLoadingContext.get().getModEventBus().addListener((FMLClientSetupEvent e) -> {
                Runnable r = () -> {
                    for (KeyMapping k : VillagerRerollerClient.keys()) {
                        //#if MC >= 1.18
                        net.minecraftforge.client.ClientRegistry.registerKeyBinding(k);
                        //#elif MC >= 1.17
                        net.minecraftforge.fmlclient.registry.ClientRegistry.registerKeyBinding(k);
                        //#else
                        net.minecraftforge.fml.client.registry.ClientRegistry.registerKeyBinding(k);
                        //#endif
                    }
                };
                //#if MC >= 1.17
                e.enqueueWork(r);
                //#else
                try {
                    e.getClass().getMethod("enqueueWork", Runnable.class).invoke(e, r);   // Forge for 1.16.3+
                } catch (Throwable old) {                                                 // older: no enqueueWork yet
                    try {
                        Class.forName("net.minecraftforge.fml.DeferredWorkQueue").getMethod("runLater", Runnable.class).invoke(null, r);
                    } catch (Throwable t) {
                        r.run();
                    }
                }
                //#endif
            });
            //#endif
        }
    }
}
