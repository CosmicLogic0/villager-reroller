package dev.villagerreroller.game;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Every call that makes the player DO something goes through this class.
 *
 * If a Minecraft/Fabric update changes a method signature, this and VillagerReader / EnchantCatalog
 * are the only places that should need touching.
 */
public final class GameActions {

    private GameActions() {
    }

    /** Turn the player to face a point. The rotation reaches the server with the next movement packet. */
    public static void lookAt(LocalPlayer p, Vec3 target) {
        p.lookAt(EntityAnchorArgument.Anchor.EYES, target);
    }

    public static int selectedSlot(Player p) {
        return p.getInventory().getSelectedSlot();
    }

    public static void selectSlot(LocalPlayer p, int slot) {
        if (slot >= 0 && slot <= 8) {
            p.getInventory().setSelectedSlot(slot);
        }
    }

    /** Hotbar slot (0-8) holding {@code item}, or -1. */
    public static int findHotbarSlot(LocalPlayer p, Item item) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean inBlockReach(LocalPlayer p, Vec3 target) {
        return p.getEyePosition().distanceTo(target) <= p.blockInteractionRange() + 0.25;
    }

    public static boolean inEntityReach(LocalPlayer p, Entity e) {
        return p.getEyePosition().distanceTo(e.getBoundingBox().getCenter()) <= p.entityInteractionRange() + 0.25;
    }

    /** One tick of mining. Call every tick until the block is gone. */
    public static void mineTick(Minecraft mc, BlockPos pos, Direction face) {
        MultiPlayerGameMode gm = mc.gameMode;
        if (gm == null || mc.player == null) {
            return;
        }
        if (gm.isDestroying()) {
            gm.continueDestroyBlock(pos, face);
        } else {
            gm.startDestroyBlock(pos, face);
        }
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    /** Right-click a block face using the recorded hit (this is how the lectern gets placed). */
    public static void useBlock(Minecraft mc, BlockHitResult hit) {
        if (mc.gameMode == null || mc.player == null) {
            return;
        }
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    /** Right-click a villager (opens its trade screen). */
    public static void useEntity(Minecraft mc, Entity entity) {
        if (mc.gameMode == null || mc.player == null) {
            return;
        }
        mc.gameMode.interact(mc.player, entity, InteractionHand.MAIN_HAND);
    }

    public static void closeScreenAndContainer(Minecraft mc) {
        if (mc.player != null) {
            mc.player.closeContainer();
        }
    }
}
