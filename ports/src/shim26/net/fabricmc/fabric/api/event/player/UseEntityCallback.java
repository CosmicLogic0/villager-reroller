package net.fabricmc.fabric.api.event.player;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
//#if FORGE
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
//#else
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
//#endif

/** Stand-in for Fabric's entity right-click callback (the result is ignored: the mod never cancels these). */
public interface UseEntityCallback {
    InteractionResult interact(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hit);

    Holder EVENT = new Holder();

    final class Holder {
        private final List<UseEntityCallback> list = new ArrayList<>();
        private boolean hooked;

        public void register(UseEntityCallback cb) {
            if (!hooked) {
                hooked = true;
                //#if FORGE
                PlayerInteractEvent.EntityInteractSpecific.BUS.addListener(e -> {
                    fire(e.getEntity(), e.getLevel(), e.getHand(), e.getTarget());
                });
                //#else
                NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract e) -> fire(e.getEntity(), e.getLevel(), e.getHand(), e.getTarget()));
                //#endif
            }
            list.add(cb);
        }

        private void fire(Player player, Level level, InteractionHand hand, Entity target) {
            for (UseEntityCallback c : list) {
                c.interact(player, level, hand, target, null);
            }
        }
    }
}
