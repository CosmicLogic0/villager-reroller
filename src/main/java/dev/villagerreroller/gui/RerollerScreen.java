package dev.villagerreroller.gui;

import dev.villagerreroller.VillagerRerollerClient;
import dev.villagerreroller.config.ConfigIO;
import dev.villagerreroller.game.EnchantCatalog;
import dev.villagerreroller.logic.EnchantRule;
import dev.villagerreroller.logic.PriceCalc;
import dev.villagerreroller.logic.RerollerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings menu. Buttons are stock widgets; text is drawn with GuiGraphicsExtractor.text (the 26.x way),
 * and the enchant list is paged by hand so it doesn't depend on any list-widget internals.
 *
 * Per enchant row:  [name]  [Ignore/Want/Avoid]  [Level]  [-] [price %] [+]
 */
public final class RerollerScreen extends Screen {

    private static final int ROW_H = 22;
    private static final int[] MAX_REROLL_CHOICES = {100, 250, 500, 1000, 5000, 0};

    private static final int WHITE = 0xFFFFFFFF;
    private static final int GOLD = 0xFFFFD75A;
    private static final int GREEN = 0xFF55E055;
    private static final int GRAY = 0xFF9A9A9A;
    private static final int ORANGE = 0xFFFFB050;
    private static final int YELLOW = 0xFFFFE066;

    /** A piece of text to draw after the widgets: position, width to fit into, colour, centred or left. */
    private record Label(int x, int y, int w, String text, int color, boolean center) {
    }

    private final List<Label> labels = new ArrayList<>();
    private List<EnchantCatalog.Entry> catalog = List.of();
    private int page;
    private boolean onlySelected;

    public RerollerScreen() {
        super(Component.literal("Villager Reroller"));
    }

    private static RerollerConfig cfg() {
        return VillagerRerollerClient.config;
    }

    @Override
    protected void init() {
        labels.clear();
        Minecraft mc = Minecraft.getInstance();
        RerollerConfig cfg = cfg();
        catalog = EnchantCatalog.tradeable(mc);
        int left = this.width / 2 - 180;

        // ---- title + status
        label(left, 8, 360, "Villager Reroller", GOLD, true);
        label(left, 22, 360, statusText(), statusColor(), true);

        // ---- main buttons
        int y = 38;
        addRenderableWidget(Button.builder(Component.literal("Calibrate"), b -> {
            saveAndClose();
            VillagerRerollerClient.CALIBRATION.begin(mc);
        }).bounds(left, y, 112, 20).build());

        boolean running = VillagerRerollerClient.CONTROLLER.isRunning();
        addRenderableWidget(Button.builder(
                Component.literal(running ? "Stop" : "Start rerolling").withStyle(running ? ChatFormatting.RED : ChatFormatting.GREEN),
                b -> {
                    if (VillagerRerollerClient.CONTROLLER.isRunning()) {
                        VillagerRerollerClient.CONTROLLER.stop(mc, "stopped from the menu.", false);
                        rebuildWidgets();
                    } else {
                        saveAndClose();
                        VillagerRerollerClient.CONTROLLER.start(mc);
                    }
                }).bounds(left + 124, y, 112, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(left + 248, y, 112, 20).build());

        // ---- global options
        y = 62;
        addRenderableWidget(Button.builder(
                Component.literal("Accept any non-avoided: " + (cfg.acceptAnyNotAvoided ? "ON" : "OFF")),
                b -> {
                    cfg.acceptAnyNotAvoided = !cfg.acceptAnyNotAvoided;
                    rebuildWidgets();
                }).bounds(left, y, 218, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            cfg.anyMaxPercent = stepDown(cfg.anyMaxPercent);
            rebuildWidgets();
        }).bounds(left + 222, y, 20, 20).build());
        label(left + 244, y + 6, 72, "Max " + cfg.anyMaxPercent + "%", WHITE, true);
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            cfg.anyMaxPercent = stepUp(cfg.anyMaxPercent);
            rebuildWidgets();
        }).bounds(left + 318, y, 20, 20).build());

        y = 86;
        addRenderableWidget(Button.builder(
                Component.literal("Max rerolls: " + (cfg.maxRerolls == 0 ? "unlimited" : cfg.maxRerolls)),
                b -> {
                    cfg.maxRerolls = nextRerollLimit(cfg.maxRerolls);
                    rebuildWidgets();
                }).bounds(left, y, 176, 20).build());
        addRenderableWidget(Button.builder(
                Component.literal("Show: " + (onlySelected ? "selected only" : "all enchants")),
                b -> {
                    onlySelected = !onlySelected;
                    page = 0;
                    rebuildWidgets();
                }).bounds(left + 184, y, 176, 20).build());

        // ---- enchant rows
        List<EnchantCatalog.Entry> shown = new ArrayList<>();
        for (EnchantCatalog.Entry e : catalog) {
            EnchantRule r = cfg.ruleOrNull(e.id());
            if (!onlySelected || (r != null && r.mode != EnchantRule.Mode.IGNORE)) {
                shown.add(e);
            }
        }

        int top = 114;
        int rowsPerPage = Math.max(2, (this.height - top - 34) / ROW_H);
        int pages = Math.max(1, (shown.size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, pages - 1));

        if (catalog.isEmpty()) {
            label(left, top + 8, 360, "Join a world to load the enchantment list.", ORANGE, true);
        }

        int from = page * rowsPerPage;
        int to = Math.min(shown.size(), from + rowsPerPage);
        for (int i = from; i < to; i++) {
            addRow(left, top + (i - from) * ROW_H, shown.get(i), cfg);
        }

        // ---- paging
        int by = this.height - 24;
        Button prev = Button.builder(Component.literal("< Prev"), b -> {
            page--;
            rebuildWidgets();
        }).bounds(left, by, 80, 20).build();
        prev.active = page > 0;
        addRenderableWidget(prev);

        label(left + 80, by + 6, 200, "Page " + (page + 1) + " / " + pages, WHITE, true);

        Button next = Button.builder(Component.literal("Next >"), b -> {
            page++;
            rebuildWidgets();
        }).bounds(left + 280, by, 80, 20).build();
        next.active = page < pages - 1;
        addRenderableWidget(next);
    }

    private void addRow(int left, int y, EnchantCatalog.Entry e, RerollerConfig cfg) {
        EnchantRule rule = cfg.rule(e.id());
        boolean active = rule.mode == EnchantRule.Mode.WANT;

        int nameColor = switch (rule.mode) {
            case WANT -> GREEN;
            case AVOID -> 0xFFFF6060;
            case IGNORE -> WHITE;
        };
        label(left, y + 6, 130, e.name().getString(), nameColor, false);

        addRenderableWidget(Button.builder(modeLabel(rule.mode), b -> {
            rule.mode = rule.mode.next();
            rebuildWidgets();
        }).bounds(left + 134, y, 58, 20).build());

        int effectiveLevel = rule.requiredLevel(e.maxLevel());

        Button lvl = Button.builder(
                Component.literal(rule.minLevel <= 0 ? "Max lvl" : ">= " + rule.minLevel),
                b -> {
                    rule.minLevel = nextMinLevel(rule.minLevel, e.maxLevel());
                    rebuildWidgets();
                }).bounds(left + 196, y, 48, 20).build();
        lvl.active = active && e.maxLevel() > 1;
        addRenderableWidget(lvl);

        Button minus = Button.builder(Component.literal("-"), b -> {
            rule.maxPercent = stepDown(rule.maxPercent);
            rebuildWidgets();
        }).bounds(left + 248, y, 20, 20).build();
        minus.active = active;
        addRenderableWidget(minus);

        int best = PriceCalc.bestPrice(cfg.priceModel, effectiveLevel, e.doublePrice());
        if (active) {
            int limit = PriceCalc.priceLimit(best, rule.maxPercent);
            label(left + 270, y + 6, 68, rule.maxPercent + "% (<=" + limit + ")", YELLOW, true);
        } else {
            label(left + 270, y + 6, 68, "best " + best, GRAY, true);
        }

        Button plus = Button.builder(Component.literal("+"), b -> {
            rule.maxPercent = stepUp(rule.maxPercent);
            rebuildWidgets();
        }).bounds(left + 340, y, 20, 20).build();
        plus.active = active;
        addRenderableWidget(plus);
    }

    private static Component modeLabel(EnchantRule.Mode mode) {
        return switch (mode) {
            case IGNORE -> Component.literal("Ignore").withStyle(ChatFormatting.GRAY);
            case WANT -> Component.literal("Want").withStyle(ChatFormatting.GREEN);
            case AVOID -> Component.literal("Avoid").withStyle(ChatFormatting.RED);
        };
    }

    private static String statusText() {
        var controller = VillagerRerollerClient.CONTROLLER;
        var cal = VillagerRerollerClient.CALIBRATION;
        if (controller.isRunning()) {
            return "Running - reroll #" + controller.rerolls();
        }
        if (cal.isDone()) {
            return "Calibrated. Ready.";
        }
        if (cal.isActive()) {
            return "Calibrating... follow the chat instructions.";
        }
        return "Not calibrated - press Calibrate first.";
    }

    private static int statusColor() {
        var cal = VillagerRerollerClient.CALIBRATION;
        if (VillagerRerollerClient.CONTROLLER.isRunning() || cal.isDone()) {
            return GREEN;
        }
        return cal.isActive() ? YELLOW : ORANGE;
    }

    private void label(int x, int y, int w, String text, int color, boolean center) {
        labels.add(new Label(x, y, w, text, color, center));
    }

    private String fit(String s, int w) {
        if (this.font.width(s) <= w) {
            return s;
        }
        return this.font.plainSubstrByWidth(s, Math.max(0, w - this.font.width(".."))) + "..";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        for (Label l : labels) {
            String s = fit(l.text(), l.w());
            int x = l.center() ? l.x() + l.w() / 2 - this.font.width(s) / 2 : l.x();
            g.text(this.font, s, x, l.y(), l.color(), true);
        }
    }

    // ------------------------------------------------------------------ value helpers

    private static int stepUp(int pct) {
        int step = pct < 200 ? 10 : pct < 400 ? 25 : 100;
        return Math.min(1000, pct + step);
    }

    private static int stepDown(int pct) {
        int step = pct <= 200 ? 10 : pct <= 400 ? 25 : 100;
        return Math.max(100, pct - step);
    }

    /** Cycles: max only -> 1 -> 2 -> ... -> (max-1) -> max only. */
    static int nextMinLevel(int current, int maxLevel) {
        if (maxLevel <= 1) {
            return 0;
        }
        if (current <= 0) {
            return 1;
        }
        int n = current + 1;
        return n >= maxLevel ? 0 : n;
    }

    private static int nextRerollLimit(int current) {
        for (int i = 0; i < MAX_REROLL_CHOICES.length; i++) {
            if (MAX_REROLL_CHOICES[i] == current) {
                return MAX_REROLL_CHOICES[(i + 1) % MAX_REROLL_CHOICES.length];
            }
        }
        return MAX_REROLL_CHOICES[0];
    }

    private void saveAndClose() {
        cfg().sanitize();
        ConfigIO.save(cfg());
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public void onClose() {
        saveAndClose();
    }
}
