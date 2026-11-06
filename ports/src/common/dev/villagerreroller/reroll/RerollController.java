package dev.villagerreroller.reroll;

import dev.villagerreroller.game.GameActions;
import dev.villagerreroller.game.Msg;
import dev.villagerreroller.game.Sounds;
import dev.villagerreroller.port.V;
import dev.villagerreroller.game.VillagerReader;
import dev.villagerreroller.logic.BookOffer;
import dev.villagerreroller.logic.DayCycle;
import dev.villagerreroller.logic.OfferMatcher;
import dev.villagerreroller.logic.RerollerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * The reroll loop:
 *
 *   break lectern -> (pick it up) -> place lectern -> wait for the villager to take the job
 *   -> open trades -> read the book -> good enough? stop : repeat
 *
 * At night (villagers sleep instead of taking jobs) it pauses, sleeps in the calibrated bed if there is one,
 * walks back to where you were standing, and carries on.
 *
 * The camera is never moved: blocks and the villager are used straight from the positions recorded during
 * calibration, and walking is done with the movement keys relative to wherever you happen to be facing.
 */
public final class RerollController {

    private enum State {
        BREAK_PREP, BREAKING, PICKUP, PLACE_PREP, PLACE, PLACE_CONFIRM, CLAIM_WAIT, INTERACT, WAIT_SCREEN,
        NIGHT_WAIT, WALK_TO_BED, USE_BED, SLEEPING, WALK_BACK
    }

    private static final int BREAK_TIMEOUT_TICKS = 20 * 15;
    private static final int BREAK_CONFIRM_TICKS = 20;
    private static final int MAX_BREAK_RETRIES = 5;
    private static final int PICKUP_GRACE_TICKS = 15;
    private static final int PICKUP_TIMEOUT_TICKS = 20 * 5;
    private static final int PLACE_CONFIRM_TICKS = 10;
    private static final int MAX_PLACE_RETRIES = 5;
    private static final int SCREEN_WAIT_TICKS = 12;
    private static final int INTERACT_RETRY_TICKS = 20;
    private static final int MAX_CLAIM_FAILS = 5;
    private static final int SAME_OFFER_LIMIT = 6;
    private static final int NO_BOOK_LIMIT = 5;
    private static final int WALK_TIMEOUT_TICKS = 20 * 15;
    private static final int WALK_STUCK_TICKS = 20 * 3;
    private static final int SLEEP_CONFIRM_TICKS = 40;
    private static final int SLEEP_RETRY_TICKS = 100;
    private static final int MAX_SLEEP_TRIES = 6;
    private static final double ARRIVE_DISTANCE = 0.25;

    private final Calibration cal;
    private final RerollerConfigSupplier configSupplier;

    @FunctionalInterface
    public interface RerollerConfigSupplier {
        RerollerConfig get();
    }

    private boolean running;
    private State state = State.BREAK_PREP;
    private int cooldown;
    private int stateTicks;
    private long ticks;

    // breaking
    private boolean breakStarted;
    private boolean breakStopSent;
    private float breakProgress;
    private int breakRetries;
    private int lecternsBeforeBreak;

    private int placeRetries;
    private long claimStartTick;
    private int claimFails;
    private int rerolls;
    private int sameOfferStreak;
    private int noBookStreak;
    private String lastSignature = "";
    private String lastSummary = "";
    private long startedAtMs;

    // night
    private Vec3 returnPos;
    private State resumeState = State.INTERACT;
    private boolean movedForNight;
    private int sleepTries;
    private double walkBestDist;
    private int walkStuckTicks;

    public RerollController(Calibration cal, RerollerConfigSupplier configSupplier) {
        this.cal = cal;
        this.configSupplier = configSupplier;
    }

    public boolean isRunning() {
        return running;
    }

    public int rerolls() {
        return rerolls;
    }

    public String lastSummary() {
        return lastSummary;
    }

    public boolean isNightPaused() {
        return running && state.ordinal() >= State.NIGHT_WAIT.ordinal();
    }

    // ------------------------------------------------------------------ start / stop

    public boolean start(Minecraft mc) {
        RerollerConfig cfg = configSupplier.get();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            return false;
        }
        if (running) {
            Msg.info(mc, "Already running.");
            return false;
        }
        if (!cal.isDone()) {
            Msg.warn(mc, "Not calibrated yet. Open the menu (V) and press Calibrate, or use /vr calibrate.");
            return false;
        }
        if (!cfg.hasAnyTarget()) {
            Msg.warn(mc, "Nothing selected. Mark at least one enchant as WANT in the menu, or turn on \"accept any\".");
            return false;
        }
        Entity villager = villager(mc);
        if (villager == null) {
            Msg.warn(mc, "I can't find the calibrated villager (too far away or unloaded). Recalibrate.");
            return false;
        }
        cal.skipBed(mc); // starting means "no bed" if the bed step was still open

        boolean lecternPlaced = mc.level.getBlockState(cal.lecternPos).getBlock() == Blocks.LECTERN;
        if (lecternPlaced) {
            state = State.BREAK_PREP;
        } else if (GameActions.countHotbar(p, Items.LECTERN) > 0) {
            state = State.PLACE_PREP;
        } else {
            Msg.warn(mc, "No lectern in the world at the calibrated spot and none in your hotbar.");
            return false;
        }

        running = true;
        cooldown = 0;
        stateTicks = 0;
        breakRetries = 0;
        placeRetries = 0;
        claimFails = 0;
        rerolls = 0;
        sameOfferStreak = 0;
        noBookStreak = 0;
        lastSignature = "";
        lastSummary = "";
        returnPos = null;
        movedForNight = false;
        startedAtMs = System.currentTimeMillis();
        Msg.ok(mc, "Rerolling started. Press the toggle key (B) or use /vr stop to stop.");
        Sounds.started(mc);
        return true;
    }

    public void stop(Minecraft mc, String reason, boolean success) {
        if (!running) {
            return;
        }
        running = false;
        cleanup(mc);
        long secs = Math.max(1, (System.currentTimeMillis() - startedAtMs) / 1000);
        String stats = " (" + rerolls + " rerolls in " + secs + "s)";
        if (success) {
            Msg.ok(mc, reason + stats);
            Sounds.found(mc);
        } else {
            Msg.warn(mc, "Stopped: " + reason + stats);
            Sounds.stopped(mc);
        }
    }

    /** Used on disconnect, when there's no player left to message. */
    public void stopSilently() {
        running = false;
        GameActions.releaseMovement(Minecraft.getInstance());
    }

    private void cleanup(Minecraft mc) {
        GameActions.releaseMovement(mc);
        if (breakStarted && !breakStopSent && cal.lecternPos != null) {
            GameActions.abortBreak(mc, cal.lecternPos, cal.breakFace);
        }
        breakStarted = false;
        breakStopSent = false;
    }

    // ------------------------------------------------------------------ the loop

    public void tick(Minecraft mc) {
        if (!running) {
            return;
        }
        ticks++;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) {
            stopSilently();
            return;
        }
        boolean inBedPhase = state == State.USE_BED || state == State.SLEEPING;
        if (V.screen(mc) != null && !(V.screen(mc) instanceof MerchantScreen) && !inBedPhase) {
            stop(mc, "a menu was opened", false);
            return;
        }
        if (p.hurtTime > 0 && !inBedPhase) {
            stop(mc, "you took damage", false);
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        RerollerConfig cfg = configSupplier.get();
        if (cfg.handleNight && isSafeNightPoint() && DayCycle.villagersResting(GameActions.dayTime(mc))) {
            enterNight(mc, p);
            return;
        }

        switch (state) {
            case BREAK_PREP -> tickBreakPrep(mc, p);
            case BREAKING -> tickBreaking(mc, p);
            case PICKUP -> tickPickup(mc, p, cfg);
            case PLACE_PREP -> tickPlacePrep(mc, p);
            case PLACE -> tickPlace(mc, p);
            case PLACE_CONFIRM -> tickPlaceConfirm(mc, cfg);
            case CLAIM_WAIT -> tickClaimWait(mc);
            case INTERACT -> tickInteract(mc, p);
            case WAIT_SCREEN -> tickWaitScreen(mc, cfg);
            case NIGHT_WAIT -> tickNightWait(mc, p);
            case WALK_TO_BED -> tickWalkToBed(mc, p);
            case USE_BED -> tickUseBed(mc, p);
            case SLEEPING -> tickSleeping(mc, p);
            case WALK_BACK -> tickWalkBack(mc, p, cfg);
        }
    }

    private void go(State next, int cooldownTicks) {
        state = next;
        stateTicks = 0;
        cooldown = cooldownTicks;
    }

    private Entity villager(Minecraft mc) {
        Entity v = mc.level.getEntity(cal.villagerId);
        if (v == null || !v.getUUID().equals(cal.villagerUuid)) {
            return null;
        }
        return v;
    }

    // ---- breaking: a real "held" break, without turning the camera

    private void tickBreakPrep(Minecraft mc, LocalPlayer p) {
        BlockPos pos = cal.lecternPos;
        if (mc.level.getBlockState(pos).getBlock() != Blocks.LECTERN) {
            go(State.PICKUP, 0);
            return;
        }
        if (!GameActions.inBlockReach(p, new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))) {
            stop(mc, "the lectern is out of reach. Stay near where you calibrated.", false);
            return;
        }
        GameActions.selectSlot(p, cal.breakSlot);
        lecternsBeforeBreak = GameActions.countHotbar(p, Items.LECTERN);
        breakStarted = false;
        breakStopSent = false;
        breakProgress = 0;
        go(State.BREAKING, 1); // one tick so the server knows which tool we hold before we start
    }

    private void tickBreaking(Minecraft mc, LocalPlayer p) {
        BlockPos pos = cal.lecternPos;
        if (mc.level.getBlockState(pos).getBlock() != Blocks.LECTERN) {
            breakStarted = false;
            breakStopSent = false;
            breakRetries = 0;
            go(State.PICKUP, 0);
            return;
        }
        if (++stateTicks > BREAK_TIMEOUT_TICKS) {
            stop(mc, "couldn't break the lectern.", false);
            return;
        }
        GameActions.selectSlot(p, cal.breakSlot);
        if (!breakStarted) {
            GameActions.startBreak(mc, pos, cal.breakFace);
            breakStarted = true;
        }
        if (stateTicks % 4 == 1) {
            GameActions.swing(mc);
        }
        if (!breakStopSent) {
            if (breakProgress >= 1.0F) {
                // a full tick after reaching 100%, so the server's own count is surely past the line
                GameActions.finishBreak(mc, pos, cal.breakFace);
                breakStopSent = true;
                stateTicks = BREAK_TIMEOUT_TICKS - BREAK_CONFIRM_TICKS; // give the server a second to remove it
            } else {
                breakProgress += GameActions.destroyProgress(mc, p, pos);
            }
        } else if (stateTicks >= BREAK_TIMEOUT_TICKS) {
            // server refused (lag, tool swapped...): start over
            breakStarted = false;
            breakStopSent = false;
            if (++breakRetries > MAX_BREAK_RETRIES) {
                stop(mc, "the server keeps refusing to break the lectern.", false);
            } else {
                go(State.BREAK_PREP, 2);
            }
        }
    }

    // ---- placing

    private void tickPickup(Minecraft mc, LocalPlayer p, RerollerConfig cfg) {
        stateTicks++;
        int have = GameActions.countHotbar(p, Items.LECTERN);
        // give the dropped lectern a moment to fly back; if it got stuck somewhere, just use a spare one
        if (have > 0 && (have >= lecternsBeforeBreak + 1 || stateTicks >= PICKUP_GRACE_TICKS)) {
            go(State.PLACE_PREP, cfg.actionDelayTicks);
            return;
        }
        if (stateTicks > PICKUP_TIMEOUT_TICKS) {
            stop(mc, "out of lecterns: none in your hotbar. Keep a spare or two in the hotbar.", false);
        }
    }

    private void tickPlacePrep(Minecraft mc, LocalPlayer p) {
        int slot = GameActions.findHotbarSlot(p, Items.LECTERN);
        if (slot < 0) {
            go(State.PICKUP, 0);
            return;
        }
        GameActions.selectSlot(p, slot);
        go(State.PLACE, 1);
    }

    private void tickPlace(Minecraft mc, LocalPlayer p) {
        if (GameActions.findHotbarSlot(p, Items.LECTERN) != GameActions.selectedSlot(p)) {
            go(State.PLACE_PREP, 0);
            return;
        }
        if (!GameActions.inBlockReach(p, cal.placeHit.getLocation())) {
            stop(mc, "the lectern spot is out of reach. Stay near where you calibrated.", false);
            return;
        }
        GameActions.useBlock(mc, cal.placeHit);
        go(State.PLACE_CONFIRM, 0);
    }

    private void tickPlaceConfirm(Minecraft mc, RerollerConfig cfg) {
        if (mc.level.getBlockState(cal.lecternPos).getBlock() == Blocks.LECTERN) {
            placeRetries = 0;
            claimStartTick = ticks;
            go(State.CLAIM_WAIT, cfg.claimWaitTicks);
            return;
        }
        if (++stateTicks > PLACE_CONFIRM_TICKS) {
            if (++placeRetries > MAX_PLACE_RETRIES) {
                stop(mc, "couldn't place the lectern (is something in the way?).", false);
            } else {
                go(State.PLACE_PREP, cfg.actionDelayTicks);
            }
        }
    }

    // ---- waiting for the villager to take the job

    private void tickClaimWait(Minecraft mc) {
        if (mc.level.getBlockState(cal.lecternPos).getBlock() != Blocks.LECTERN) {
            go(State.PLACE_PREP, 2); // placement was rolled back by the server
            return;
        }
        go(State.INTERACT, 0);
    }

    private void tickInteract(Minecraft mc, LocalPlayer p) {
        Entity villager = villager(mc);
        if (villager == null) {
            stop(mc, "the villager disappeared or is too far away.", false);
            return;
        }
        if (!GameActions.inEntityReach(p, villager)) {
            stop(mc, "the villager is out of reach. Stay near where you calibrated.", false);
            return;
        }
        if (mc.level.getBlockState(cal.lecternPos).getBlock() != Blocks.LECTERN) {
            go(State.PLACE_PREP, 0);
            return;
        }
        GameActions.useEntity(mc, villager);
        go(State.WAIT_SCREEN, 0);
    }

    private void tickWaitScreen(Minecraft mc, RerollerConfig cfg) {
        VillagerReader.Scan scan = VillagerReader.scan(mc);
        if (scan != null) {
            claimFails = 0;
            handleOffers(mc, scan, cfg);
            return;
        }
        if (++stateTicks <= SCREEN_WAIT_TICKS) {
            return;
        }
        long waited = (ticks - claimStartTick) / 20;
        if (waited >= cfg.claimTimeoutSeconds) {
            if (++claimFails >= MAX_CLAIM_FAILS) {
                stop(mc, "the villager didn't take the job " + MAX_CLAIM_FAILS + " times in a row. Check it can reach the lectern"
                        + " and isn't a nitwit or already traded with.", false);
            } else {
                Msg.info(mc, "The villager hasn't taken the job after " + waited + "s. Placing the lectern again.");
                go(State.BREAK_PREP, cfg.actionDelayTicks);
            }
            return;
        }
        Msg.bar(mc, "Waiting for the villager to take the job... " + waited + "s / " + cfg.claimTimeoutSeconds + "s");
        go(State.INTERACT, INTERACT_RETRY_TICKS);
    }

    private void handleOffers(Minecraft mc, VillagerReader.Scan scan, RerollerConfig cfg) {
        rerolls++;
        List<BookOffer> books = scan.books();

        if (books.isEmpty()) {
            noBookStreak++;
            lastSummary = "no enchanted book offered";
            Msg.bar(mc, "Reroll #" + rerolls + ": no enchanted book");
            if (noBookStreak >= NO_BOOK_LIMIT) {
                stop(mc, "this villager keeps offering no enchanted book. Is it a librarian?", false);
                return;
            }
        } else {
            noBookStreak = 0;
        }

        Optional<OfferMatcher.MatchResult> hit = OfferMatcher.firstAccepted(books, cfg);
        if (hit.isPresent()) {
            OfferMatcher.MatchResult m = hit.get();
            lastSummary = describe(m);
            // The trade screen is deliberately left open so you can buy the book right away.
            stop(mc, "Found " + m.offer().displayName() + " for " + m.offer().price()
                    + " emeralds (" + m.percent() + "% of best, best = " + m.best() + "). "
                    + "The lectern is left in place.", true);
            return;
        }

        if (!books.isEmpty()) {
            OfferMatcher.MatchResult first = OfferMatcher.evaluate(books.get(0), cfg);
            lastSummary = describe(first);
            Msg.bar(mc, "Reroll #" + rerolls + ": " + describe(first));
        }

        if (scan.signature().equals(lastSignature)) {
            sameOfferStreak++;
            if (sameOfferStreak >= SAME_OFFER_LIMIT) {
                stop(mc, "the trades haven't changed " + SAME_OFFER_LIMIT
                        + " times in a row. The villager is probably locked in (already traded with).", false);
                return;
            }
        } else {
            sameOfferStreak = 1;
            lastSignature = scan.signature();
        }

        GameActions.closeScreenAndContainer(mc);
        go(State.BREAK_PREP, cfg.actionDelayTicks);
    }

    private static String describe(OfferMatcher.MatchResult m) {
        return m.offer().displayName() + " - " + m.offer().price() + " emeralds ("
                + m.percent() + "% of best " + m.best() + ")";
    }

    // ------------------------------------------------------------------ night

    /** Points in the loop where pausing is harmless (never in the middle of breaking or placing). */
    private boolean isSafeNightPoint() {
        return state == State.BREAK_PREP || state == State.PLACE_PREP || state == State.INTERACT
                || state == State.CLAIM_WAIT || (state == State.WAIT_SCREEN && stateTicks > SCREEN_WAIT_TICKS);
    }

    private void enterNight(Minecraft mc, LocalPlayer p) {
        if (V.screen(mc) instanceof MerchantScreen) {
            GameActions.closeScreenAndContainer(mc);
        }
        GameActions.releaseMovement(mc);
        boolean lecternPlaced = mc.level.getBlockState(cal.lecternPos).getBlock() == Blocks.LECTERN;
        resumeState = lecternPlaced ? State.INTERACT : State.PLACE_PREP;
        returnPos = p.position();
        movedForNight = false;
        sleepTries = 0;
        if (cal.hasBed()) {
            Msg.info(mc, "Night: villagers are going to bed. I'll sleep and carry on in the morning.");
            go(State.WALK_TO_BED, 0);
        } else {
            Msg.info(mc, "Night: villagers are going to bed. Waiting for morning (set a bed in the menu to sleep through it).");
            go(State.NIGHT_WAIT, 0);
        }
        walkBestDist = Double.MAX_VALUE;
        walkStuckTicks = 0;
    }

    private void tickNightWait(Minecraft mc, LocalPlayer p) {
        if (DayCycle.villagersResting(GameActions.dayTime(mc))) {
            if (++stateTicks % 100 == 1) {
                Msg.bar(mc, "Night: waiting for the villagers to wake up...");
            }
            return;
        }
        Msg.info(mc, "Morning! Back to rerolling.");
        startWalkBackOrResume(mc, p);
    }

    private void tickWalkToBed(Minecraft mc, LocalPlayer p) {
        if (!DayCycle.villagersResting(GameActions.dayTime(mc))) {
            startWalkBackOrResume(mc, p);
            return;
        }
        Vec3 bed = cal.bedHit.getLocation();
        Vec3 bedCenter = new Vec3(cal.bedPos.getX() + 0.5, cal.bedPos.getY() + 0.5, cal.bedPos.getZ() + 0.5);
        // vanilla only lets you sleep when the bed is close (about 3 blocks), not just within arm's reach
        boolean closeEnough = Math.hypot(bedCenter.x - p.getX(), bedCenter.z - p.getZ()) <= 2.0;
        if (closeEnough && GameActions.inBlockReach(p, bed)) {
            GameActions.releaseMovement(mc);
            if (!DayCycle.canSleep(GameActions.dayTime(mc), GameActions.thundering(mc))) {
                if (++stateTicks % 100 == 1) {
                    Msg.bar(mc, "Night: waiting until it's late enough to sleep...");
                }
                return;
            }
            go(State.USE_BED, 0);
            return;
        }
        movedForNight = true;
        if (!walkStep(mc, p, new Vec3(cal.bedPos.getX() + 0.5, cal.bedPos.getY() + 0.5, cal.bedPos.getZ() + 0.5))) {
            GameActions.releaseMovement(mc);
            Msg.warn(mc, "I can't reach the bed from here. Waiting for morning instead.");
            go(State.NIGHT_WAIT, 0);
        }
    }

    private void tickUseBed(Minecraft mc, LocalPlayer p) {
        if (GameActions.isSleeping(p)) {
            go(State.SLEEPING, 0);
            return;
        }
        if (stateTicks == 0) {
            sleepTries++;
            GameActions.useBlock(mc, cal.bedHit);
        }
        stateTicks++;
        if (stateTicks == SLEEP_CONFIRM_TICKS) {
            if (sleepTries >= MAX_SLEEP_TRIES) {
                Msg.warn(mc, "I couldn't sleep (monsters nearby, or the bed is blocked). Waiting for morning instead.");
                go(State.NIGHT_WAIT, 0);
            } else {
                go(State.USE_BED, SLEEP_RETRY_TICKS);
            }
        }
    }

    private void tickSleeping(Minecraft mc, LocalPlayer p) {
        if (GameActions.isSleeping(p)) {
            if (++stateTicks % 200 == 1) {
                Msg.bar(mc, "Sleeping until morning...");
            }
            return;
        }
        // woke up (morning, or someone left the bed)
        movedForNight = true;
        if (DayCycle.villagersResting(GameActions.dayTime(mc))) {
            go(State.NIGHT_WAIT, 20);
        } else {
            Msg.info(mc, "Good morning! Walking back to your spot.");
            beginWalk();
            go(State.WALK_BACK, 20);
        }
    }

    private void startWalkBackOrResume(Minecraft mc, LocalPlayer p) {
        if (returnPos != null && (movedForNight || p.position().distanceTo(returnPos) > 0.6)) {
            beginWalk();
            go(State.WALK_BACK, 0);
        } else {
            resume(mc);
        }
    }

    private void tickWalkBack(Minecraft mc, LocalPlayer p, RerollerConfig cfg) {
        if (returnPos == null) {
            resume(mc);
            return;
        }
        double d = Math.hypot(returnPos.x - p.getX(), returnPos.z - p.getZ());
        if (d <= ARRIVE_DISTANCE) {
            GameActions.releaseMovement(mc);
            if (cfg.handleNight && DayCycle.villagersResting(GameActions.dayTime(mc))) {
                go(State.NIGHT_WAIT, 0);
            } else {
                resume(mc);
            }
            return;
        }
        if (!walkStep(mc, p, returnPos)) {
            GameActions.releaseMovement(mc);
            stop(mc, "I couldn't walk back to your spot (something in the way?). Move back and press B.", false);
        }
    }

    private void resume(Minecraft mc) {
        GameActions.releaseMovement(mc);
        movedForNight = false;
        returnPos = null;
        claimStartTick = ticks;
        go(resumeState, 10);
    }

    private void beginWalk() {
        walkBestDist = Double.MAX_VALUE;
        walkStuckTicks = 0;
        stateTicks = 0;
    }

    /** Holds the right movement keys for one tick. Returns false when stuck or out of time. */
    private boolean walkStep(Minecraft mc, LocalPlayer p, Vec3 target) {
        stateTicks++;
        double d = Math.hypot(target.x - p.getX(), target.z - p.getZ());
        if (d < walkBestDist - 0.05) {
            walkBestDist = d;
            walkStuckTicks = 0;
        } else if (++walkStuckTicks > WALK_STUCK_TICKS) {
            return false;
        }
        if (stateTicks > WALK_TIMEOUT_TICKS) {
            return false;
        }
        GameActions.walkTowards(mc, p, target);
        return true;
    }
}
