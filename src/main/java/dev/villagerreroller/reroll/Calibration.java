package dev.villagerreroller.reroll;

import dev.villagerreroller.game.GameActions;
import dev.villagerreroller.game.Msg;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

import java.util.UUID;

/**
 * Walks the player through doing the routine once by hand so the mod can copy it:
 *   1. place the lectern  2. right-click the villager  3. break the lectern
 *
 * Everything recorded here lives in memory only (positions are specific to one world) and is
 * forgotten when you leave the world.
 */
public final class Calibration {

    public enum Step {
        IDLE, WAIT_PLACE, WAIT_PLACE_CONFIRM, WAIT_VILLAGER, WAIT_BREAK, WAIT_BREAK_CONFIRM, DONE
    }

    private static final int CONFIRM_TIMEOUT_TICKS = 40;
    private static final int BREAK_TIMEOUT_TICKS = 20 * 30;

    private Step step = Step.IDLE;
    private int timer;
    private BlockPos candidateA;
    private BlockPos candidateB;

    // ---- recorded data, read by RerollController ----
    public BlockPos lecternPos;
    public BlockHitResult placeHit;
    public int lecternSlot = -1;
    public int villagerId = -1;
    public UUID villagerUuid;
    public Direction breakFace = Direction.UP;
    public int breakSlot = 0;

    public Step step() {
        return step;
    }

    public boolean isDone() {
        return step == Step.DONE;
    }

    public boolean isActive() {
        return step != Step.IDLE && step != Step.DONE;
    }

    public void reset() {
        step = Step.IDLE;
        timer = 0;
        candidateA = null;
        candidateB = null;
        lecternPos = null;
        placeHit = null;
        lecternSlot = -1;
        villagerId = -1;
        villagerUuid = null;
        breakFace = Direction.UP;
        breakSlot = 0;
    }

    public void begin(Minecraft mc) {
        reset();
        step = Step.WAIT_PLACE;
        Msg.ok(mc, "Calibration started. You will do the routine once by hand and I'll copy it.");
        Msg.info(mc, "Stand where you want to stay while rerolling and don't move afterwards. Picking the lectern back up needs you to be close to it.");
        Msg.step(mc, "Step 1/3: hold a lectern and place it next to the villager (the villager must not have been traded with yet).");
    }

    public void cancel(Minecraft mc) {
        reset();
        Msg.info(mc, "Calibration cancelled.");
    }

    // ------------------------------------------------------------------ input hooks

    /** Called from Fabric's UseBlockCallback. */
    public void onUseBlock(Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(player instanceof LocalPlayer) || step != Step.WAIT_PLACE) {
            return;
        }
        if (!player.getItemInHand(hand).is(Items.LECTERN)) {
            return;
        }
        placeHit = hit;
        lecternSlot = GameActions.selectedSlot(player);
        candidateA = hit.getBlockPos().relative(hit.getDirection());
        candidateB = hit.getBlockPos(); // in case the clicked block (grass, snow...) got replaced
        timer = 0;
        step = Step.WAIT_PLACE_CONFIRM;
    }

    /** Called from Fabric's UseEntityCallback. */
    public void onUseEntity(Player player, Entity entity) {
        if (!(player instanceof LocalPlayer) || step != Step.WAIT_VILLAGER) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!"villager".equals(EntityType.getKey(entity.getType()).getPath())) {
            Msg.warn(mc, "That's not a villager. Right-click the villager that should get the new job.");
            return;
        }
        villagerId = entity.getId();
        villagerUuid = entity.getUUID();
        step = Step.WAIT_BREAK;
        Msg.ok(mc, "Villager recorded.");
        Msg.step(mc, "Step 3/3: break the lectern with whatever tool you want me to use. Then make sure the lectern item ends up in your hotbar.");
    }

    /** Called from Fabric's AttackBlockCallback. */
    public void onAttackBlock(Player player, BlockPos pos, Direction face) {
        if (!(player instanceof LocalPlayer) || step != Step.WAIT_BREAK || !pos.equals(lecternPos)) {
            return;
        }
        breakFace = face;
        breakSlot = GameActions.selectedSlot(player);
        timer = 0;
        step = Step.WAIT_BREAK_CONFIRM;
    }

    // ------------------------------------------------------------------ per tick

    public void tick(Minecraft mc) {
        if (!isActive() || mc.level == null || mc.player == null) {
            return;
        }
        switch (step) {
            case WAIT_PLACE_CONFIRM -> {
                if (mc.level.getBlockState(candidateA).is(Blocks.LECTERN)) {
                    lecternPos = candidateA;
                } else if (mc.level.getBlockState(candidateB).is(Blocks.LECTERN)) {
                    lecternPos = candidateB;
                }
                if (lecternPos != null) {
                    step = Step.WAIT_VILLAGER;
                    Msg.ok(mc, "Lectern recorded.");
                    Msg.step(mc, "Step 2/3: right-click the villager so I know which one to talk to.");
                } else if (++timer > CONFIRM_TIMEOUT_TICKS) {
                    step = Step.WAIT_PLACE;
                    Msg.warn(mc, "The lectern didn't appear. Try placing it again.");
                }
            }
            case WAIT_BREAK_CONFIRM -> {
                if (!mc.level.getBlockState(lecternPos).is(Blocks.LECTERN)) {
                    step = Step.DONE;
                    Msg.ok(mc, "Calibration complete!");
                    Msg.info(mc, "Pick the lectern item back up, then open the menu (V) and press Start, or use /vr start.");
                } else if (++timer > BREAK_TIMEOUT_TICKS) {
                    step = Step.WAIT_BREAK;
                    Msg.warn(mc, "The lectern is still there. Break it fully to finish calibration.");
                }
            }
            default -> {
            }
        }
    }
}
