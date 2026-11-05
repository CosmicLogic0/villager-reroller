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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

import java.util.UUID;

/**
 * Walks the player through doing the routine once by hand so the mod can copy it:
 *   1. place the lectern  2. right-click the villager  3. break the lectern  4. (optional) right-click a bed
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

    // ---- optional bed, used at night ----
    public BlockPos bedPos;
    public BlockHitResult bedHit;
    private boolean awaitingBed;

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
        bedPos = null;
        bedHit = null;
        awaitingBed = false;
    }

    public boolean hasBed() {
        return bedPos != null && bedHit != null;
    }

    public boolean isAwaitingBed() {
        return awaitingBed;
    }

    /** Ask the player to right-click the bed to use at night (can be done any time after calibration). */
    public void askForBed(Minecraft mc) {
        awaitingBed = true;
        Msg.step(mc, "Right-click the bed you want me to sleep in at night (keep it a few blocks from where you stand). "
                + "/vr skipbed to skip.");
    }

    public void skipBed(Minecraft mc) {
        if (awaitingBed) {
            awaitingBed = false;
            Msg.info(mc, "No bed set. At night I'll just wait for morning instead of sleeping.");
        }
    }

    public void begin(Minecraft mc) {
        BlockPos keepBedPos = bedPos;
        BlockHitResult keepBedHit = bedHit;
        reset();
        bedPos = keepBedPos; // recalibrating keeps the bed you already set
        bedHit = keepBedHit;
        step = Step.WAIT_PLACE;
        Msg.ok(mc, "Calibration started. You will do the routine once by hand and I'll copy it.");
        Msg.info(mc, "Stand roughly where you will stay. You can look around and shuffle a little later: I remember the spots, so your camera never moves.");
        Msg.step(mc, "Step 1/4: hold a lectern and place it next to the villager (the villager must not have been traded with yet).");
    }

    public void cancel(Minecraft mc) {
        reset();
        Msg.info(mc, "Calibration cancelled.");
    }

    // ------------------------------------------------------------------ input hooks

    /**
     * Called from Fabric's UseBlockCallback.
     *
     * @return true if the click was a bed being recorded (the caller then cancels it so you don't
     *         get "You can only sleep at night" during the day)
     */
    public boolean onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (!(player instanceof LocalPlayer)) {
            return false;
        }
        if (awaitingBed && level.getBlockState(hit.getBlockPos()).getBlock() instanceof BedBlock) {
            bedPos = hit.getBlockPos();
            bedHit = hit;
            awaitingBed = false;
            Msg.ok(Minecraft.getInstance(), "Bed recorded. At night I'll sleep there, then walk you back to your spot.");
            return true;
        }
        if (step != Step.WAIT_PLACE) {
            return false;
        }
        if (!player.getItemInHand(hand).is(Items.LECTERN)) {
            return false;
        }
        placeHit = hit;
        lecternSlot = GameActions.selectedSlot(player);
        candidateA = hit.getBlockPos().relative(hit.getDirection());
        candidateB = hit.getBlockPos(); // in case the clicked block (grass, snow...) got replaced
        timer = 0;
        step = Step.WAIT_PLACE_CONFIRM;
        return false;
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
        Msg.step(mc, "Step 3/4: break the lectern with whatever tool you want me to use. Then make sure the lectern item ends up in your hotbar.");
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
                    Msg.step(mc, "Step 2/4: right-click the villager so I know which one to talk to.");
                } else if (++timer > CONFIRM_TIMEOUT_TICKS) {
                    step = Step.WAIT_PLACE;
                    Msg.warn(mc, "The lectern didn't appear. Try placing it again.");
                }
            }
            case WAIT_BREAK_CONFIRM -> {
                if (!mc.level.getBlockState(lecternPos).is(Blocks.LECTERN)) {
                    step = Step.DONE;
                    Msg.ok(mc, "Calibration complete! Pick the lectern back up (spare lecterns in your hotbar are fine too).");
                    awaitingBed = !hasBed();
                    if (hasBed()) {
                        Msg.info(mc, "Using the bed you set before (/vr setbed to change it). Press B or /vr start.");
                    } else {
                        Msg.step(mc, "Step 4/4 (optional): right-click a bed near you so I can sleep through the night. "
                                + "Or type /vr skipbed. Then press B or /vr start.");
                    }
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
