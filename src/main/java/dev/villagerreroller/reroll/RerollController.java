package dev.villagerreroller.reroll;

import dev.villagerreroller.game.GameActions;
import dev.villagerreroller.game.Msg;
import dev.villagerreroller.game.VillagerReader;
import dev.villagerreroller.logic.BookOffer;
import dev.villagerreroller.logic.OfferMatcher;
import dev.villagerreroller.logic.RerollerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * The reroll loop:
 *
 *   break lectern -> pick it up -> place lectern -> wait for the villager to claim it
 *   -> open trades -> read the book -> good enough? stop : repeat
 *
 * One small step per tick (with short cooldowns) so it behaves like a player and never floods the server.
 */
public final class RerollController {

    private enum State {
        BREAK, PICKUP, PLACE, PLACE_CONFIRM, CLAIM_WAIT, INTERACT, WAIT_SCREEN
    }

    private static final int BREAK_TIMEOUT_TICKS = 20 * 15;
    private static final int PICKUP_TIMEOUT_TICKS = 20 * 5;
    private static final int PLACE_CONFIRM_TICKS = 10;
    private static final int MAX_PLACE_RETRIES = 5;
    private static final int SCREEN_WAIT_TICKS = 12;
    private static final int MAX_INTERACT_ATTEMPTS = 12;
    private static final int SAME_OFFER_LIMIT = 6;
    private static final int NO_BOOK_LIMIT = 5;

    private final Calibration cal;
    private final RerollerConfigSupplier configSupplier;

    @FunctionalInterface
    public interface RerollerConfigSupplier {
        RerollerConfig get();
    }

    private boolean running;
    private State state = State.BREAK;
    private int cooldown;
    private int stateTicks;
    private boolean aimed;
    private int placeRetries;
    private int interactAttempts;
    private int rerolls;
    private int sameOfferStreak;
    private int noBookStreak;
    private String lastSignature = "";
    private String lastSummary = "";
    private long startedAtMs;

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
        Entity villager = mc.level.getEntity(cal.villagerId);
        if (villager == null || !villager.getUUID().equals(cal.villagerUuid)) {
            Msg.warn(mc, "I can't find the calibrated villager (too far away or unloaded). Recalibrate.");
            return false;
        }

        boolean lecternPlaced = mc.level.getBlockState(cal.lecternPos).is(Blocks.LECTERN);
        if (lecternPlaced) {
            state = State.BREAK;
        } else if (GameActions.findHotbarSlot(p, Items.LECTERN) >= 0) {
            state = State.PLACE;
        } else {
            Msg.warn(mc, "No lectern in the world at the calibrated spot and none in your hotbar.");
            return false;
        }

        running = true;
        cooldown = 0;
        stateTicks = 0;
        aimed = false;
        placeRetries = 0;
        interactAttempts = 0;
        rerolls = 0;
        sameOfferStreak = 0;
        noBookStreak = 0;
        lastSignature = "";
        lastSummary = "";
        startedAtMs = System.currentTimeMillis();
        Msg.ok(mc, "Rerolling started. Press the toggle key or use /vr stop to stop.");
        return true;
    }

    public void stop(Minecraft mc, String reason, boolean success) {
        if (!running) {
            return;
        }
        running = false;
        if (mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
        }
        long secs = Math.max(1, (System.currentTimeMillis() - startedAtMs) / 1000);
        String stats = " (" + rerolls + " rerolls in " + secs + "s)";
        if (success) {
            Msg.ok(mc, reason + stats);
            try {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F, 0.8F));
            } catch (RuntimeException ignored) {
                // a missing sound must never break the stop
            }
        } else {
            Msg.warn(mc, "Stopped: " + reason + stats);
        }
    }

    /** Used on disconnect, when there's no player left to message. */
    public void stopSilently() {
        running = false;
    }

    // ------------------------------------------------------------------ the loop

    public void tick(Minecraft mc) {
        if (!running) {
            return;
        }
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) {
            stopSilently();
            return;
        }
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof MerchantScreen)) {
            stop(mc, "a menu was opened", false);
            return;
        }
        if (p.hurtTime > 0) {
            stop(mc, "you took damage", false);
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        RerollerConfig cfg = configSupplier.get();
        switch (state) {
            case BREAK -> tickBreak(mc, p, cfg);
            case PICKUP -> tickPickup(mc, p, cfg);
            case PLACE -> tickPlace(mc, p);
            case PLACE_CONFIRM -> tickPlaceConfirm(mc, cfg);
            case CLAIM_WAIT -> tickClaimWait(mc);
            case INTERACT -> tickInteract(mc, p);
            case WAIT_SCREEN -> tickWaitScreen(mc, cfg);
        }
    }

    private void go(State next, int cooldownTicks) {
        state = next;
        stateTicks = 0;
        aimed = false;
        cooldown = cooldownTicks;
    }

    private void tickBreak(Minecraft mc, LocalPlayer p, RerollerConfig cfg) {
        BlockPos pos = cal.lecternPos;
        if (!mc.level.getBlockState(pos).is(Blocks.LECTERN)) {
            mc.gameMode.stopDestroyBlock();
            go(State.PICKUP, cfg.actionDelayTicks);
            return;
        }
        Vec3 center = Vec3.atCenterOf(pos);
        if (!GameActions.inBlockReach(p, center)) {
            stop(mc, "the lectern is out of reach. Stand where you calibrated.", false);
            return;
        }
        if (++stateTicks > BREAK_TIMEOUT_TICKS) {
            stop(mc, "couldn't break the lectern.", false);
            return;
        }
        GameActions.selectSlot(p, cal.breakSlot);
        GameActions.lookAt(p, center);
        GameActions.mineTick(mc, pos, cal.breakFace);
    }

    private void tickPickup(Minecraft mc, LocalPlayer p, RerollerConfig cfg) {
        if (GameActions.findHotbarSlot(p, Items.LECTERN) >= 0) {
            go(State.PLACE, cfg.actionDelayTicks);
            return;
        }
        if (++stateTicks > PICKUP_TIMEOUT_TICKS) {
            stop(mc, "the lectern item never came back to your hotbar. Stand closer to it and keep a hotbar slot free.", false);
        }
    }

    private void tickPlace(Minecraft mc, LocalPlayer p) {
        int slot = GameActions.findHotbarSlot(p, Items.LECTERN);
        if (slot < 0) {
            go(State.PICKUP, 0);
            return;
        }
        Vec3 aim = cal.placeHit.getLocation();
        if (!aimed) {
            GameActions.selectSlot(p, slot);
            GameActions.lookAt(p, aim);
            aimed = true; // act on the next tick so the new rotation has been sent first
            return;
        }
        GameActions.useBlock(mc, cal.placeHit);
        go(State.PLACE_CONFIRM, 0);
    }

    private void tickPlaceConfirm(Minecraft mc, RerollerConfig cfg) {
        if (mc.level.getBlockState(cal.lecternPos).is(Blocks.LECTERN)) {
            placeRetries = 0;
            go(State.CLAIM_WAIT, cfg.claimWaitTicks);
            return;
        }
        if (++stateTicks > PLACE_CONFIRM_TICKS) {
            if (++placeRetries > MAX_PLACE_RETRIES) {
                stop(mc, "couldn't place the lectern.", false);
            } else {
                go(State.PLACE, cfg.actionDelayTicks);
            }
        }
    }

    private void tickClaimWait(Minecraft mc) {
        if (!mc.level.getBlockState(cal.lecternPos).is(Blocks.LECTERN)) {
            go(State.PLACE, 2); // placement was rolled back by the server
            return;
        }
        interactAttempts = 0;
        go(State.INTERACT, 0);
    }

    private void tickInteract(Minecraft mc, LocalPlayer p) {
        Entity villager = mc.level.getEntity(cal.villagerId);
        if (villager == null || !villager.getUUID().equals(cal.villagerUuid)) {
            stop(mc, "the villager disappeared or is too far away.", false);
            return;
        }
        if (!GameActions.inEntityReach(p, villager)) {
            stop(mc, "the villager is out of reach. Stand where you calibrated.", false);
            return;
        }
        if (!aimed) {
            GameActions.lookAt(p, villager.getBoundingBox().getCenter());
            aimed = true;
            return;
        }
        interactAttempts++;
        GameActions.useEntity(mc, villager);
        go(State.WAIT_SCREEN, 0);
    }

    private void tickWaitScreen(Minecraft mc, RerollerConfig cfg) {
        VillagerReader.Scan scan = VillagerReader.scan(mc);
        if (scan != null) {
            handleOffers(mc, scan, cfg);
            return;
        }
        if (++stateTicks > SCREEN_WAIT_TICKS) {
            if (interactAttempts >= MAX_INTERACT_ATTEMPTS) {
                stop(mc, "the villager never opened its trades. It may not have taken the lectern as a job.", false);
            } else {
                go(State.INTERACT, 8); // villager probably hasn't claimed the job yet, try again shortly
            }
        }
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

        if (cfg.maxRerolls > 0 && rerolls >= cfg.maxRerolls) {
            GameActions.closeScreenAndContainer(mc);
            stop(mc, "reached the reroll limit.", false);
            return;
        }

        GameActions.closeScreenAndContainer(mc);
        go(State.BREAK, cfg.actionDelayTicks);
    }

    private static String describe(OfferMatcher.MatchResult m) {
        return m.offer().displayName() + " - " + m.offer().price() + " emeralds ("
                + m.percent() + "% of best " + m.best() + ")";
    }
}
