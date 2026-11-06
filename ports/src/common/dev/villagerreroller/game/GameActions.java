package dev.villagerreroller.game;

import dev.villagerreroller.logic.WalkMath;
import dev.villagerreroller.port.V;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Every call that makes the player DO something goes through this class (checked against the real
 * Minecraft 26.3 signatures with the CI "API probe" workflow).
 *
 * Nothing in here turns the camera: blocks and entities are used directly from their recorded positions,
 * and walking holds movement keys relative to wherever the player is facing.
 */
public final class GameActions {

    private GameActions() {
    }

    public static int selectedSlot(Player p) {
        return V.selectedSlot(p);
    }

    public static void selectSlot(LocalPlayer p, int slot) {
        if (slot >= 0 && slot <= 8 && V.selectedSlot(p) != slot) {
            V.setSelectedSlot(p, slot);
        }
    }

    /** Hotbar slot (0-8) holding {@code item}, or -1. Prefers the slot already selected. */
    public static int findHotbarSlot(LocalPlayer p, Item item) {
        int sel = V.selectedSlot(p);
        if (sel >= 0 && sel < 9 && V.hotbar(p, sel).getItem() == item) {
            return sel;
        }
        for (int i = 0; i < 9; i++) {
            if (V.hotbar(p, i).getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    /** Total number of {@code item} in the hotbar. */
    public static int countHotbar(LocalPlayer p, Item item) {
        int n = 0;
        for (int i = 0; i < 9; i++) {
            if (V.hotbar(p, i).getItem() == item) {
                n += V.hotbar(p, i).getCount();
            }
        }
        return n;
    }

    public static boolean inBlockReach(LocalPlayer p, Vec3 target) {
        return p.getEyePosition(1.0F).distanceTo(target) <= V.blockReach(Minecraft.getInstance(), p) + 0.25;
    }

    public static boolean inEntityReach(LocalPlayer p, Entity e) {
        return p.getEyePosition(1.0F).distanceTo(e.getBoundingBox().getCenter()) <= V.entityReach(Minecraft.getInstance(), p) + 0.25;
    }

    // ------------------------------------------------------------------ breaking (a real held break)

    private static void sendAction(Minecraft mc, ServerboundPlayerActionPacket.Action action, BlockPos pos, Direction face) {
        ClientPacketListener c = mc.getConnection();
        if (c != null) {
            c.send(new ServerboundPlayerActionPacket(action, pos, face));
        }
    }

    /** Starts breaking (like pressing the attack button on the block). */
    public static void startBreak(Minecraft mc, BlockPos pos, Direction face) {
        sendAction(mc, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, face);
    }

    /** Finishes breaking (like the break completing while the button is held). */
    public static void finishBreak(Minecraft mc, BlockPos pos, Direction face) {
        sendAction(mc, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, face);
    }

    public static void abortBreak(Minecraft mc, BlockPos pos, Direction face) {
        sendAction(mc, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, pos, face);
    }

    /** How much of the block one tick of mining removes with what's in hand (1.0 = instant). */
    public static float destroyProgress(Minecraft mc, LocalPlayer p, BlockPos pos) {
        return mc.level.getBlockState(pos).getDestroyProgress(p, mc.level, pos);
    }

    /** Arm swing is only cosmetic; 26.3 has no plain swing packet, so this does nothing for now. */
    public static void swing(Minecraft mc) {
    }

    // ------------------------------------------------------------------ using things

    /** Right-click a block face using the recorded hit (placing the lectern, using the bed). */
    public static void useBlock(Minecraft mc, BlockHitResult hit) {
        V.useItemOn(mc, hit);
    }

    /** Right-click a villager (opens its trade screen). */
    public static void useEntity(Minecraft mc, Entity entity) {
        V.interact(mc, entity);
    }

    public static void closeScreenAndContainer(Minecraft mc) {
        if (mc.player != null) {
            mc.player.closeContainer();
        }
    }

    // ------------------------------------------------------------------ time & sleep

    /** Overworld time of day in ticks (26.x replaced getDayTime with world clocks). */
    public static long dayTime(Minecraft mc) {
        return V.dayTime(mc);
    }

    public static boolean thundering(Minecraft mc) {
        return mc.level != null && mc.level.isThundering();
    }

    public static boolean isSleeping(LocalPlayer p) {
        return p.isSleeping();
    }

    // ------------------------------------------------------------------ walking without turning

    /** Holds the movement keys that move the player towards {@code target}, keeping the current facing. */
    public static void walkTowards(Minecraft mc, LocalPlayer p, Vec3 target) {
        WalkMath.Keys k = WalkMath.keysTowards(p.getX(), p.getZ(), target.x, target.z, V.yaw(p), 0.12, 0.9);
        mc.options.keyUp.setDown(k.forward());
        mc.options.keyDown.setDown(k.back());
        mc.options.keyLeft.setDown(k.left());
        mc.options.keyRight.setDown(k.right());
    }

    public static void releaseMovement(Minecraft mc) {
        if (mc.options == null) {
            return;
        }
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
    }
}
