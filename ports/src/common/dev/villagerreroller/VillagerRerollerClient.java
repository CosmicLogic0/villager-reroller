package dev.villagerreroller;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.villagerreroller.config.ConfigIO;
import dev.villagerreroller.game.Msg;
import dev.villagerreroller.gui.RerollerScreen;
import dev.villagerreroller.logic.RerollerConfig;
import dev.villagerreroller.port.V;
import dev.villagerreroller.reroll.Calibration;
import dev.villagerreroller.reroll.RerollController;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

/**
 * The mod itself, the same for Fabric, Forge and NeoForge. Each loader's entry class (dev.villagerreroller.entry)
 * calls {@link #init}, registers {@link #keys}, and forwards ticks, clicks and the /vr command here.
 * Client-side only: nothing needs to be installed on the server you join.
 */
public final class VillagerRerollerClient {

    public static final String MOD_ID = "villagerreroller";

    public static RerollerConfig config = new RerollerConfig();
    public static final Calibration CALIBRATION = new Calibration();
    public static final RerollController CONTROLLER = new RerollController(CALIBRATION, () -> config);

    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    /** CI only: run with -Dvillagerreroller.selftest=true to open the menu in a world and log VRTEST lines. */
    private static final boolean SELFTEST = "true".equals(System.getProperty("villagerreroller.selftest"));
    private static int selftestTicks = -1;

    private static boolean openMenuNextTick;
    private static boolean wasInWorld;
    private static KeyMapping menuKey;
    private static KeyMapping toggleKey;

    private VillagerRerollerClient() {
    }

    public static void init() {
        config = ConfigIO.load();
        menuKey = V.key("key.villagerreroller.menu", GLFW.GLFW_KEY_V);
        toggleKey = V.key("key.villagerreroller.toggle", GLFW.GLFW_KEY_B);
        LOGGER.info("VRTEST loaded (Villager Reroller initialised, {} saved rules)", config.rules.size());
    }

    /** The key bindings, for the loader to register. */
    public static KeyMapping[] keys() {
        return new KeyMapping[] {menuKey, toggleKey};
    }

    /** At the end of every client tick. */
    public static void tick(Minecraft mc) {
        boolean inWorld = mc.level != null && mc.player != null;
        if (wasInWorld && !inWorld) {   // left the world or server
            CONTROLLER.stopSilently();
            CALIBRATION.reset();
        }
        wasInWorld = inWorld;
        while (menuKey.consumeClick()) {
            openMenuNextTick = true;
        }
        while (toggleKey.consumeClick()) {
            toggle(mc);
        }
        if (openMenuNextTick && mc.player != null && V.screen(mc) == null) {
            openMenuNextTick = false;
            V.setScreen(mc, new RerollerScreen());
        }
        CALIBRATION.tick(mc);
        CONTROLLER.tick(mc);
        if (SELFTEST) {
            selftestTick(mc);
        }
    }

    /** A right-click on a block. @return true to cancel it (recording your bed during the day shouldn't try to sleep). */
    public static boolean onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        return CALIBRATION.onUseBlock(player, level, hand, hit);
    }

    public static void onUseEntity(Player player, Entity entity) {
        CALIBRATION.onUseEntity(player, entity);
    }

    public static void onAttackBlock(Player player, BlockPos pos, Direction face) {
        CALIBRATION.onAttackBlock(player, pos, face == null ? Direction.UP : face);
    }

    /** The /vr command, for any command source type. */
    public static <S> LiteralArgumentBuilder<S> command() {
        return LiteralArgumentBuilder.<S>literal("vr")
                .executes(ctx -> {
                    openMenuNextTick = true;
                    return 1;
                })
                .then(LiteralArgumentBuilder.<S>literal("calibrate")
                        .executes(ctx -> {
                            CALIBRATION.begin(Minecraft.getInstance());
                            return 1;
                        }))
                .then(LiteralArgumentBuilder.<S>literal("setbed")
                        .executes(ctx -> {
                            CALIBRATION.askForBed(Minecraft.getInstance());
                            return 1;
                        }))
                .then(LiteralArgumentBuilder.<S>literal("skipbed")
                        .executes(ctx -> {
                            CALIBRATION.skipBed(Minecraft.getInstance());
                            return 1;
                        }))
                .then(LiteralArgumentBuilder.<S>literal("start")
                        .executes(ctx -> {
                            CONTROLLER.start(Minecraft.getInstance());
                            return 1;
                        }))
                .then(LiteralArgumentBuilder.<S>literal("stop")
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (CONTROLLER.isRunning()) {
                                CONTROLLER.stop(mc, "stopped by command.", false);
                            } else if (CALIBRATION.isActive()) {
                                CALIBRATION.cancel(mc);
                            } else {
                                Msg.info(mc, "Nothing is running.");
                            }
                            return 1;
                        }))
                .then(LiteralArgumentBuilder.<S>literal("status")
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            Msg.info(mc, "Calibrated: " + CALIBRATION.isDone()
                                    + " | bed: " + (CALIBRATION.hasBed() ? "set" : "not set")
                                    + " | running: " + CONTROLLER.isRunning()
                                    + " | rerolls: " + CONTROLLER.rerolls()
                                    + (CONTROLLER.lastSummary().isEmpty() ? "" : " | last: " + CONTROLLER.lastSummary()));
                            return 1;
                        }));
    }

    /** Runs only in CI (see .github/workflows/ports.yml). Every step logs a VRTEST line the workflow checks for. */
    private static void selftestTick(Minecraft mc) {
        if (mc.player == null || mc.level == null || selftestTicks >= 1000) {
            return;
        }
        selftestTicks++;
        try {
            if (selftestTicks == 1) {
                LOGGER.info("VRTEST in world");
            } else if (selftestTicks == 60) {
                V.setScreen(mc, new RerollerScreen());
            } else if (selftestTicks == 70) {
                config.rule("minecraft:mending").mode = dev.villagerreroller.logic.EnchantRule.Mode.WANT;
                config.rule("minecraft:unbreaking").mode = dev.villagerreroller.logic.EnchantRule.Mode.WANT;
                config.rule("minecraft:binding_curse").mode = dev.villagerreroller.logic.EnchantRule.Mode.AVOID;
                RerollerScreen.expandForTest(dev.villagerreroller.logic.EnchantGroups.Group.UNIVERSAL,
                        dev.villagerreroller.logic.EnchantGroups.Group.MELEE);
                LOGGER.info("VRTEST menu shot");
            } else if (selftestTicks == 200) {
                boolean open = V.screen(mc) instanceof RerollerScreen;
                int enchants = dev.villagerreroller.game.EnchantCatalog.tradeable(mc).size();
                LOGGER.info("VRTEST menu open={} enchants={}", open, enchants);
                config.rules.clear();
                long t = dev.villagerreroller.game.GameActions.dayTime(mc);
                LOGGER.info("VRTEST clock time={} resting={} sleeping={}", t,
                        dev.villagerreroller.logic.DayCycle.villagersResting(t),
                        dev.villagerreroller.game.GameActions.isSleeping(mc.player));
                dev.villagerreroller.game.GameActions.releaseMovement(mc);
                float prog = dev.villagerreroller.game.GameActions.destroyProgress(mc, mc.player, V.feet(mc.player).below());
                LOGGER.info("VRTEST break progress/tick={}", prog);
                V.setScreen(mc, null);
            } else if (selftestTicks == 210) {
                CALIBRATION.begin(mc);
                LOGGER.info("VRTEST calibration step={}", CALIBRATION.step());
                CALIBRATION.cancel(mc);
                config.rule("minecraft:mending").mode = dev.villagerreroller.logic.EnchantRule.Mode.WANT;
                boolean started = CONTROLLER.start(mc); // must refuse: not calibrated
                LOGGER.info("VRTEST start-without-calibration refused={}", !started);
                config.rules.remove("minecraft:mending");
            } else if (selftestTicks == 220) {
                LOGGER.info("VRTEST done");
                selftestTicks = 1000;
            }
        } catch (Throwable e) {
            LOGGER.error("VRTEST failed at tick " + selftestTicks, e);
            selftestTicks = 1000;
        }
    }

    private static void toggle(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        if (CONTROLLER.isRunning()) {
            CONTROLLER.stop(mc, "stopped with the toggle key.", false);
        } else {
            CONTROLLER.start(mc);
        }
    }
}
