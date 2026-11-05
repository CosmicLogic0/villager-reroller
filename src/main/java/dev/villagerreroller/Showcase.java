package dev.villagerreroller;

import dev.villagerreroller.game.GameActions;
import dev.villagerreroller.gui.RerollerScreen;
import dev.villagerreroller.logic.EnchantGroups;
import dev.villagerreroller.logic.EnchantRule;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CI only (run with -Dvillagerreroller.showcase=true): records the screenshots and video for the mod page.
 * The CI script writes one command at a time to {@code <game dir>/showcase/cmd}; this runs it, deletes the
 * file and logs "VRSHOW did ...". Nothing here is active in a normal game.
 */
final class Showcase {

    private static final Logger LOGGER = LoggerFactory.getLogger("villagerreroller-showcase");

    private static int ticks;
    private static boolean wasRunning;
    private static boolean readyLogged;

    private Showcase() {
    }

    static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!readyLogged) {
            readyLogged = true;
            LOGGER.info("VRSHOW ready");
        }
        boolean running = VillagerRerollerClient.CONTROLLER.isRunning();
        if (wasRunning && !running) {
            LOGGER.info("VRSHOW stopped: {}", VillagerRerollerClient.CONTROLLER.lastSummary());
        }
        wasRunning = running;

        if (++ticks % 5 != 0) {
            return;
        }
        Path file = FabricLoader.getInstance().getGameDir().resolve("showcase").resolve("cmd");
        if (!Files.exists(file)) {
            return;
        }
        String cmd;
        try {
            cmd = Files.readString(file).trim();
            Files.delete(file);
        } catch (Exception e) {
            return;
        }
        try {
            run(mc, cmd);
            LOGGER.info("VRSHOW did {}", cmd);
        } catch (RuntimeException e) {
            LOGGER.error("VRSHOW failed {}", cmd, e);
        }
    }

    private static void run(Minecraft mc, String cmd) {
        var cfg = VillagerRerollerClient.config;
        String[] a = cmd.split(" ");
        switch (a[0]) {
            case "rules" -> {
                cfg.rules.clear();
                cfg.rule("minecraft:mending").mode = EnchantRule.Mode.WANT;
                cfg.rule("minecraft:unbreaking").mode = EnchantRule.Mode.WANT;
                cfg.rule("minecraft:sharpness").mode = EnchantRule.Mode.WANT;
                cfg.rule("minecraft:efficiency").mode = EnchantRule.Mode.WANT;
                cfg.rule("minecraft:fortune").mode = EnchantRule.Mode.WANT;
                cfg.rule("minecraft:protection").mode = EnchantRule.Mode.WANT;
                cfg.rule("minecraft:binding_curse").mode = EnchantRule.Mode.AVOID;
                cfg.rule("minecraft:vanishing_curse").mode = EnchantRule.Mode.AVOID;
                cfg.handleNight = false;
                cfg.acceptAnyNotAvoided = a.length > 1 && a[1].equals("any");
            }
            case "menu" -> {
                RerollerScreen.expandForTest(EnchantGroups.Group.UNIVERSAL, EnchantGroups.Group.MELEE, EnchantGroups.Group.TOOLS);
                mc.gui.setScreen(new RerollerScreen());
            }
            case "close" -> mc.gui.setScreen(null);
            case "hud" -> mc.options.hideGui = a.length > 1 && a[1].equals("off");
            case "demo" -> {
                // the live run: popular enchants at any level, generous price, so the video ends on a find
                for (String id : List.of("mending", "unbreaking", "sharpness", "efficiency", "fortune", "protection",
                        "feather_falling", "silk_touch", "looting", "power")) {
                    EnchantRule r = cfg.rule("minecraft:" + id);
                    r.mode = EnchantRule.Mode.WANT;
                    r.minLevel = 1;
                    r.maxPercent = 300;
                }
            }
            case "calibrate" -> VillagerRerollerClient.CALIBRATION.begin(mc);
            case "preset" -> {
                // preset <lecternX> <lecternY> <lecternZ>: what calibration would record, without the clicks
                BlockPos lectern = new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
                BlockPos below = lectern.below();
                BlockHitResult place = new BlockHitResult(new Vec3(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5),
                        Direction.UP, below, false);
                Entity villager = nearestVillager(mc);
                if (villager == null) {
                    throw new IllegalStateException("no villager nearby");
                }
                int lecternSlot = GameActions.findHotbarSlot(mc.player, Items.LECTERN);
                int axeSlot = GameActions.findHotbarSlot(mc.player, Items.IRON_AXE);
                VillagerRerollerClient.CALIBRATION.preset(lectern, place, Math.max(0, lecternSlot), villager, Direction.UP,
                        axeSlot >= 0 ? axeSlot : Math.max(0, lecternSlot));
            }
            case "start" -> VillagerRerollerClient.CONTROLLER.start(mc);
            case "stop" -> VillagerRerollerClient.CONTROLLER.stop(mc, "stopped.", false);
            default -> LOGGER.warn("VRSHOW unknown command {}", cmd);
        }
    }

    private static Entity nearestVillager(Minecraft mc) {
        List<Entity> all = mc.level.getEntitiesOfClass(Entity.class, new AABB(mc.player.blockPosition()).inflate(12));
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : all) {
            if ("villager".equals(EntityType.getKey(e.getType()).getPath())) {
                double d = e.distanceToSqr(mc.player);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
        }
        return best;
    }
}
