package dev.villagerreroller.gui;

import dev.villagerreroller.VillagerRerollerClient;
import dev.villagerreroller.config.ConfigIO;
import dev.villagerreroller.game.EnchantCatalog;
import dev.villagerreroller.logic.EnchantGroups;
import dev.villagerreroller.logic.EnchantRule;
import dev.villagerreroller.logic.PriceCalc;
import dev.villagerreroller.logic.RerollerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The settings menu.
 *
 *   [Calibrate] [Set bed] [Night: ON] [Start] [Done]
 *   [Accept any: OFF] [-] Max 150% [+]   [Max rerolls: 500]
 *   [search........] [Show: All] [Want all] [Avoid all] [Clear]     <- act on everything listed
 *   SWORDS & AXES                 [Want all] [Avoid all] [Clear]     <- act on that group
 *     Sharpness  [Want]  [Max lvl]  [-] 150% (<=25) [+]
 *     ...
 *
 * Text is drawn with GuiGraphicsExtractor.text (the 26.x way); the list is paged by hand.
 */
public final class RerollerScreen extends Screen {

    private static final int ROW_H = 22;
    private static final int[] MAX_REROLL_CHOICES = {100, 250, 500, 1000, 5000, 0};

    private static final int WHITE = 0xFFFFFFFF;
    private static final int GOLD = 0xFFFFD75A;
    private static final int GREEN = 0xFF55E055;
    private static final int RED = 0xFFFF6060;
    private static final int GRAY = 0xFF9A9A9A;
    private static final int ORANGE = 0xFFFFB050;
    private static final int YELLOW = 0xFFFFE066;
    private static final int AQUA = 0xFF6FD3FF;

    private enum Filter {
        ALL("All"), WANTED("Wanted"), AVOIDED("Avoided");
        final String label;

        Filter(String label) {
            this.label = label;
        }

        Filter next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** A piece of text to draw after the widgets: position, width to fit into, colour, centred or left. */
    private record Label(int x, int y, int w, String text, int color, boolean center) {
    }

    /** One line of the list: either a group heading or an enchantment. */
    private record Line(EnchantGroups.Group header, EnchantCatalog.Entry entry) {
    }

    private final List<Label> labels = new ArrayList<>();
    private List<EnchantCatalog.Entry> catalog = List.of();
    private int page;
    private Filter filter = Filter.ALL;
    private String search = "";
    private boolean searchFocused;
    private boolean rebuildPending;

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
        label(left, 6, 360, "Villager Reroller", GOLD, true);
        label(left, 18, 360, statusText(), statusColor(), true);

        // ---- row 1: main buttons
        int y = 30;
        addRenderableWidget(Button.builder(Component.literal("Calibrate"), b -> {
            saveAndClose();
            VillagerRerollerClient.CALIBRATION.begin(mc);
        }).bounds(left, y, 68, 20).build());

        Button bed = Button.builder(Component.literal(VillagerRerollerClient.CALIBRATION.hasBed() ? "Bed: set" : "Set bed"), b -> {
            saveAndClose();
            VillagerRerollerClient.CALIBRATION.askForBed(mc);
        }).bounds(left + 73, y, 68, 20).build();
        bed.active = VillagerRerollerClient.CALIBRATION.isDone();
        addRenderableWidget(bed);

        addRenderableWidget(Button.builder(
                Component.literal("Night: " + (cfg.handleNight ? "ON" : "OFF")),
                b -> {
                    cfg.handleNight = !cfg.handleNight;
                    rebuildWidgets();
                }).bounds(left + 146, y, 68, 20).build());

        boolean running = VillagerRerollerClient.CONTROLLER.isRunning();
        addRenderableWidget(Button.builder(
                Component.literal(running ? "Stop" : "Start").withStyle(running ? ChatFormatting.RED : ChatFormatting.GREEN),
                b -> {
                    if (VillagerRerollerClient.CONTROLLER.isRunning()) {
                        VillagerRerollerClient.CONTROLLER.stop(mc, "stopped from the menu.", false);
                        rebuildWidgets();
                    } else {
                        saveAndClose();
                        VillagerRerollerClient.CONTROLLER.start(mc);
                    }
                }).bounds(left + 219, y, 68, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(left + 292, y, 68, 20).build());

        // ---- row 2: global options
        y = 54;
        addRenderableWidget(Button.builder(
                Component.literal("Accept any: " + (cfg.acceptAnyNotAvoided ? "ON" : "OFF")),
                b -> {
                    cfg.acceptAnyNotAvoided = !cfg.acceptAnyNotAvoided;
                    rebuildWidgets();
                }).bounds(left, y, 140, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            cfg.anyMaxPercent = stepDown(cfg.anyMaxPercent);
            rebuildWidgets();
        }).bounds(left + 144, y, 20, 20).build());
        label(left + 166, y + 6, 52, "Max " + cfg.anyMaxPercent + "%", WHITE, true);
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            cfg.anyMaxPercent = stepUp(cfg.anyMaxPercent);
            rebuildWidgets();
        }).bounds(left + 220, y, 20, 20).build());
        addRenderableWidget(Button.builder(
                Component.literal("Max rerolls: " + (cfg.maxRerolls == 0 ? "no limit" : cfg.maxRerolls)),
                b -> {
                    cfg.maxRerolls = nextRerollLimit(cfg.maxRerolls);
                    rebuildWidgets();
                }).bounds(left + 244, y, 116, 20).build());

        // ---- row 3: search, filter and "everything listed" shortcuts
        y = 78;
        EditBox box = new EditBox(this.font, left, y, 140, 20, Component.literal("Search"));
        box.setMaxLength(40);
        box.setHint(Component.literal("Search enchants...").withStyle(ChatFormatting.DARK_GRAY));
        box.setValue(search);
        box.setResponder(s -> {
            if (!s.equals(search)) {
                search = s;
                page = 0;
                searchFocused = true;
                rebuildPending = true;
            }
        });
        addRenderableWidget(box);
        if (searchFocused) {
            this.setFocused(box);
            box.setFocused(true);
        }

        addRenderableWidget(Button.builder(Component.literal("Show: " + filter.label), b -> {
            filter = filter.next();
            page = 0;
            searchFocused = false;
            rebuildWidgets();
        }).bounds(left + 144, y, 70, 20).build());

        List<EnchantCatalog.Entry> shown = shownEntries(cfg);
        List<String> shownIds = ids(shown);
        addRenderableWidget(Button.builder(Component.literal("Want all").withStyle(ChatFormatting.GREEN), b -> {
            EnchantGroups.setAll(cfg, shownIds, EnchantRule.Mode.WANT);
            rebuildWidgets();
        }).bounds(left + 218, y, 46, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Avoid all").withStyle(ChatFormatting.RED), b -> {
            EnchantGroups.setAll(cfg, shownIds, EnchantRule.Mode.AVOID);
            rebuildWidgets();
        }).bounds(left + 268, y, 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> {
            EnchantGroups.setAll(cfg, shownIds, EnchantRule.Mode.IGNORE);
            rebuildWidgets();
        }).bounds(left + 320, y, 40, 20).build());

        // ---- the grouped list
        Map<EnchantGroups.Group, List<EnchantCatalog.Entry>> byGroup = new EnumMap<>(EnchantGroups.Group.class);
        for (EnchantCatalog.Entry e : shown) {
            byGroup.computeIfAbsent(EnchantGroups.of(e.id()), g -> new ArrayList<>()).add(e);
        }
        List<Line> lines = new ArrayList<>();
        for (Map.Entry<EnchantGroups.Group, List<EnchantCatalog.Entry>> g : byGroup.entrySet()) {
            lines.add(new Line(g.getKey(), null));
            for (EnchantCatalog.Entry e : g.getValue()) {
                lines.add(new Line(null, e));
            }
        }

        int top = 104;
        int rowsPerPage = Math.max(3, (this.height - top - 30) / ROW_H);
        int pages = Math.max(1, (lines.size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, pages - 1));

        if (catalog.isEmpty()) {
            label(left, top + 8, 360, "Join a world to load the enchantment list.", ORANGE, true);
        } else if (lines.isEmpty()) {
            label(left, top + 8, 360, "Nothing matches.", GRAY, true);
        }

        int from = page * rowsPerPage;
        int to = Math.min(lines.size(), from + rowsPerPage);
        for (int i = from; i < to; i++) {
            int rowY = top + (i - from) * ROW_H;
            Line line = lines.get(i);
            if (line.header() != null) {
                addHeader(left, rowY, line.header(), ids(byGroup.get(line.header())), cfg);
            } else {
                addRow(left, rowY, line.entry(), cfg);
            }
        }

        // ---- paging
        int by = this.height - 24;
        Button prev = Button.builder(Component.literal("< Prev"), b -> {
            page--;
            searchFocused = false;
            rebuildWidgets();
        }).bounds(left, by, 80, 20).build();
        prev.active = page > 0;
        addRenderableWidget(prev);

        label(left + 80, by + 6, 200, "Page " + (page + 1) + " / " + pages + "   (" + shown.size() + " enchants)", WHITE, true);

        Button next = Button.builder(Component.literal("Next >"), b -> {
            page++;
            searchFocused = false;
            rebuildWidgets();
        }).bounds(left + 280, by, 80, 20).build();
        next.active = page < pages - 1;
        addRenderableWidget(next);
    }

    private List<EnchantCatalog.Entry> shownEntries(RerollerConfig cfg) {
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<EnchantCatalog.Entry> out = new ArrayList<>();
        for (EnchantCatalog.Entry e : catalog) {
            EnchantRule r = cfg.ruleOrNull(e.id());
            EnchantRule.Mode mode = r == null ? EnchantRule.Mode.IGNORE : r.mode;
            if (filter == Filter.WANTED && mode != EnchantRule.Mode.WANT) {
                continue;
            }
            if (filter == Filter.AVOIDED && mode != EnchantRule.Mode.AVOID) {
                continue;
            }
            if (!q.isEmpty()) {
                String hay = (e.name().getString() + " " + e.id() + " " + EnchantGroups.of(e.id()).title).toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) {
                    continue;
                }
            }
            out.add(e);
        }
        return out;
    }

    private static List<String> ids(List<EnchantCatalog.Entry> entries) {
        List<String> out = new ArrayList<>();
        if (entries != null) {
            for (EnchantCatalog.Entry e : entries) {
                out.add(e.id());
            }
        }
        return out;
    }

    private void addHeader(int left, int y, EnchantGroups.Group group, List<String> groupIds, RerollerConfig cfg) {
        int wanted = 0;
        for (String id : groupIds) {
            EnchantRule r = cfg.ruleOrNull(id);
            if (r != null && r.mode == EnchantRule.Mode.WANT) {
                wanted++;
            }
        }
        String title = group.title.toUpperCase(Locale.ROOT) + (wanted > 0 ? "  (" + wanted + " wanted)" : "");
        label(left, y + 7, 214, title, AQUA, false);
        addRenderableWidget(Button.builder(Component.literal("Want all").withStyle(ChatFormatting.GREEN), b -> {
            EnchantGroups.setAll(cfg, groupIds, EnchantRule.Mode.WANT);
            searchFocused = false;
            rebuildWidgets();
        }).bounds(left + 218, y + 1, 46, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Avoid all").withStyle(ChatFormatting.RED), b -> {
            EnchantGroups.setAll(cfg, groupIds, EnchantRule.Mode.AVOID);
            searchFocused = false;
            rebuildWidgets();
        }).bounds(left + 268, y + 1, 48, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> {
            EnchantGroups.setAll(cfg, groupIds, EnchantRule.Mode.IGNORE);
            searchFocused = false;
            rebuildWidgets();
        }).bounds(left + 320, y + 1, 40, 18).build());
    }

    private void addRow(int left, int y, EnchantCatalog.Entry e, RerollerConfig cfg) {
        EnchantRule existing = cfg.ruleOrNull(e.id());
        EnchantRule.Mode mode = existing == null ? EnchantRule.Mode.IGNORE : existing.mode;
        boolean active = mode == EnchantRule.Mode.WANT;

        int nameColor = switch (mode) {
            case WANT -> GREEN;
            case AVOID -> RED;
            case IGNORE -> WHITE;
        };
        label(left + 8, y + 6, 124, e.name().getString(), nameColor, false);

        addRenderableWidget(Button.builder(modeLabel(mode), b -> {
            EnchantRule r = cfg.rule(e.id());
            r.mode = r.mode.next();
            searchFocused = false;
            rebuildWidgets();
        }).bounds(left + 134, y, 58, 20).build());

        EnchantRule rule = existing != null ? existing : new EnchantRule();
        int effectiveLevel = rule.requiredLevel(e.maxLevel());

        Button lvl = Button.builder(
                Component.literal(rule.minLevel <= 0 ? "Max lvl" : ">= " + rule.minLevel),
                b -> {
                    EnchantRule r = cfg.rule(e.id());
                    r.minLevel = nextMinLevel(r.minLevel, e.maxLevel());
                    searchFocused = false;
                    rebuildWidgets();
                }).bounds(left + 196, y, 48, 20).build();
        lvl.active = active && e.maxLevel() > 1;
        addRenderableWidget(lvl);

        Button minus = Button.builder(Component.literal("-"), b -> {
            EnchantRule r = cfg.rule(e.id());
            r.maxPercent = stepDown(r.maxPercent);
            searchFocused = false;
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
            EnchantRule r = cfg.rule(e.id());
            r.maxPercent = stepUp(r.maxPercent);
            searchFocused = false;
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
        String bed = cal.hasBed() ? "  |  bed set" : "  |  no bed";
        if (controller.isRunning()) {
            return (controller.isNightPaused() ? "Night pause" : "Running") + " - reroll #" + controller.rerolls() + bed;
        }
        if (cal.isDone()) {
            return "Calibrated. Ready." + bed;
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
        if (rebuildPending) {
            // typing in the search box: rebuild outside the key event, keep focus in the box
            rebuildPending = false;
            rebuildWidgets();
        }
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
