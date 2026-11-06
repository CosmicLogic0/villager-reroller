package net.fabricmc.fabric.api.event.player;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
//#if FORGE
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
//#else
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
//#endif

/** Stand-in for Fabric's block right-click callback; anything but PASS cancels the click. */
public interface UseBlockCallback {
    InteractionResult interact(Player player, Level level, InteractionHand hand, BlockHitResult hit);

    Holder EVENT = new Holder();

    final class Holder {
        private final List<UseBlockCallback> list = new ArrayList<>();
        private boolean hooked;

        public void register(UseBlockCallback cb) {
            if (!hooked) {
                hooked = true;
                //#if FORGE
                PlayerInteractEvent.RightClickBlock.BUS.addListener(e -> {
                    return fire(e.getEntity(), e.getLevel(), e.getHand(), e.getHitVec()) != InteractionResult.PASS;
                });
                //#else
                NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e) -> {
                    InteractionResult r = fire(e.getEntity(), e.getLevel(), e.getHand(), e.getHitVec());
                    if (r != InteractionResult.PASS) {
                        e.setCanceled(true);
                        e.setCancellationResult(r);
                    }
                });
                //#endif
            }
            list.add(cb);
        }

        private InteractionResult fire(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
            for (UseBlockCallback c : list) {
                InteractionResult r = c.interact(player, level, hand, hit);
                if (r != InteractionResult.PASS) {
                    return r;
                }
            }
            return InteractionResult.PASS;
        }
    }
}
