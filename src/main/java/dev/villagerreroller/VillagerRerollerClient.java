package dev.villagerreroller;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.villagerreroller.config.ConfigIO;
import dev.villagerreroller.game.Msg;
import dev.villagerreroller.gui.RerollerScreen;
import dev.villagerreroller.logic.RerollerConfig;
import dev.villagerreroller.reroll.Calibration;
import dev.villagerreroller.reroll.RerollController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only entrypoint. Nothing here runs on a dedicated server, and nothing needs to be
 * installed on the server you join.
 */
public final class VillagerRerollerClient implements ClientModInitializer {

    public static final String MOD_ID = "villagerreroller";

    public static RerollerConfig config = new RerollerConfig();
    public static final Calibration CALIBRATION = new Calibration();
    public static final RerollController CONTROLLER = new RerollController(CALIBRATION, () -> config);

    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** CI only: run with -Dvillagerreroller.selftest=true to open the menu in a world and log VRTEST lines. */
    private static final boolean SELFTEST = "true".equals(System.getProperty("villagerreroller.selftest"));
    private static int selftestTicks = -1;

    private static boolean openMenuNextTick;

    @Override
    public void onInitializeClient() {
        config = ConfigIO.load();

        // 26.x key binding setup: a registered category and KeyMappingHelper (checked against a mod that builds on 26.3).
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        KeyMapping menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.villagerreroller.menu", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, category));
        KeyMapping toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.villagerreroller.toggle", InputConstants.Type.KEYBOARD, InputConstants.KEY_B, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menuKey.consumeClick()) {
                openMenuNextTick = true;
            }
            while (toggleKey.consumeClick()) {
                toggle(mc);
            }
            if (openMenuNextTick && mc.player != null && mc.gui.screen() == null) {
                openMenuNextTick = false;
                mc.gui.setScreen(new RerollerScreen());
            }
            CALIBRATION.tick(mc);
            CONTROLLER.tick(mc);
            if (SELFTEST) {
                selftestTick(mc);
            }
        });

        // These Fabric events fire for both the client and (in singleplayer) the integrated server.
        // Calibration ignores everything that isn't the local player.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            // FAIL = swallow the click (so recording your bed during the day doesn't try to sleep)
            return CALIBRATION.onUseBlock(player, level, hand, hit) ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            CALIBRATION.onUseEntity(player, entity);
            return InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, face) -> {
            CALIBRATION.onAttackBlock(player, pos, face);
            return InteractionResult.PASS;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            CONTROLLER.stopSilently();
            CALIBRATION.reset();
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("vr")
                        .executes(ctx -> {
                            openMenuNextTick = true;
                            return 1;
                        })
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("calibrate")
                                .executes(ctx -> {
                                    CALIBRATION.begin(Minecraft.getInstance());
                                    return 1;
                                }))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("setbed")
                                .executes(ctx -> {
                                    CALIBRATION.askForBed(Minecraft.getInstance());
                                    return 1;
                                }))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("skipbed")
                                .executes(ctx -> {
                                    CALIBRATION.skipBed(Minecraft.getInstance());
                                    return 1;
                                }))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("start")
                                .executes(ctx -> {
                                    CONTROLLER.start(Minecraft.getInstance());
                                    return 1;
                                }))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stop")
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
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status")
                                .executes(ctx -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    Msg.info(mc, "Calibrated: " + CALIBRATION.isDone()
                                            + " | bed: " + (CALIBRATION.hasBed() ? "set" : "not set")
                                            + " | running: " + CONTROLLER.isRunning()
                                            + " | rerolls: " + CONTROLLER.rerolls()
                                            + (CONTROLLER.lastSummary().isEmpty() ? "" : " | last: " + CONTROLLER.lastSummary()));
                                    return 1;
                                }))));

        LOGGER.info("VRTEST loaded (Villager Reroller initialised, {} saved rules)", config.rules.size());
    }

    /** Runs only in CI (see .github/workflows/launch-test.yml). Every step logs a VRTEST line the workflow checks for. */
    private static void selftestTick(Minecraft mc) {
        if (mc.player == null || mc.level == null || selftestTicks >= 1000) {
            return;
        }
        selftestTicks++;
        try {
            if (selftestTicks == 1) {
                LOGGER.info("VRTEST in world");
            } else if (selftestTicks == 60) {
                mc.gui.setScreen(new RerollerScreen());
            } else if (selftestTicks == 90) {
                boolean open = mc.gui.screen() instanceof RerollerScreen;
                int enchants = dev.villagerreroller.game.EnchantCatalog.tradeable(mc).size();
                LOGGER.info("VRTEST menu open={} enchants={}", open, enchants);
                long t = dev.villagerreroller.game.GameActions.dayTime(mc);
                LOGGER.info("VRTEST clock time={} resting={} sleeping={}", t,
                        dev.villagerreroller.logic.DayCycle.villagersResting(t),
                        dev.villagerreroller.game.GameActions.isSleeping(mc.player));
                dev.villagerreroller.game.GameActions.releaseMovement(mc);
                float prog = dev.villagerreroller.game.GameActions.destroyProgress(mc, mc.player, mc.player.blockPosition().below());
                LOGGER.info("VRTEST break progress/tick={}", prog);
                mc.gui.setScreen(null);
            } else if (selftestTicks == 100) {
                CALIBRATION.begin(mc);
                LOGGER.info("VRTEST calibration step={}", CALIBRATION.step());
                CALIBRATION.cancel(mc);
                config.rule("minecraft:mending").mode = dev.villagerreroller.logic.EnchantRule.Mode.WANT;
                boolean started = CONTROLLER.start(mc); // must refuse: not calibrated
                LOGGER.info("VRTEST start-without-calibration refused={}", !started);
                config.rules.remove("minecraft:mending");
            } else if (selftestTicks == 110) {
                LOGGER.info("VRTEST done");
                selftestTicks = 1000;
            }
        } catch (RuntimeException e) {
            LOGGER.error("VRTEST failed at tick {}", selftestTicks, e);
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
