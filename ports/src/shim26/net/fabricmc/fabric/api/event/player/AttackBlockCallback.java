package net.fabricmc.fabric.api.event.player;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
//#if FORGE
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
//#else
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
//#endif

/** Stand-in for Fabric's block left-click callback (the result is ignored: the mod never cancels these). */
public interface AttackBlockCallback {
    InteractionResult interact(Player player, Level level, InteractionHand hand, BlockPos pos, Direction face);

    Holder EVENT = new Holder();

    final class Holder {
        private final List<AttackBlockCallback> list = new ArrayList<>();
        private boolean hooked;

        public void register(AttackBlockCallback cb) {
            if (!hooked) {
                hooked = true;
                //#if FORGE
                PlayerInteractEvent.LeftClickBlock.BUS.addListener(e -> {
                    fire(e.getEntity(), e.getLevel(), e.getHand(), e.getPos(), e.getFace());
                });
                //#else
                NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock e) -> fire(e.getEntity(), e.getLevel(), e.getHand(), e.getPos(), e.getFace()));
                //#endif
            }
            list.add(cb);
        }

        private void fire(Player player, Level level, InteractionHand hand, BlockPos pos, Direction face) {
            for (AttackBlockCallback c : list) {
                c.interact(player, level, hand, pos, face == null ? Direction.UP : face);
            }
        }
    }
}
