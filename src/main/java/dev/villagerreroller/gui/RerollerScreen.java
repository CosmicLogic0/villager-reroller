package dev.villagerreroller.gui;

import dev.villagerreroller.VillagerRerollerClient;
import dev.villagerreroller.config.ConfigIO;
import dev.villagerreroller.game.EnchantCatalog;
import dev.villagerreroller.logic.EnchantGroups;
import dev.villagerreroller.logic.EnchantInfo;
import dev.villagerreroller.logic.EnchantRule;
import dev.villagerreroller.logic.PriceCalc;
import dev.villagerreroller.logic.RerollerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The settings menu: one dark panel, drawn by hand so it can look modern.
 *
 *   [book] Villager Reroller                         [Start] [x]
 *          * Calibrated. Ready.
 *   [Calibrate] [Bed] [Night] [Sounds] [Accept any]
 *   [search......] [Show: All] [Want all] [Avoid all] [Clear]
 *   v [sword] Swords & Axes                    2 wanted   7
 *       [icon] Sharpness     [Want] [Max V]  - 150% [emerald] 25 +
 *   > [pick]  Tools                                        3
 *   ...
 *   40 enchants . 2 wanted . 1 avoided           Expand all . Collapse all
 *
 * Everything is drawn each frame and the clickable areas are remembered as "zones"; only the search
 * box is a real vanilla widget. Hovering an enchant shows what it does and its prices.
 */
public final class RerollerScreen extends Screen {

    // ---- palette (ARGB)
    private static final int SHADOW = 0x66000000;
    private static final int PANEL = 0xF2111419;
    private static final int EDGE = 0xFF2B3340;
    private static final int HEADER = 0xFF161B22;
    private static final int BTN = 0xFF232A34;
    private static final int BTN_HOVER = 0xFF2F3846;
    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFF8B96A5;
    private static final int DIM = 0xFF5D6774;
    private static final int GOLD = 0xFFFFC857;
    private static final int GREEN = 0xFF4ADE80;
    private static final int GREEN_BG = 0xFF1B4630;
    private static final int GREEN_BG_HOVER = 0xFF23583C;
    private static final int RED = 0xFFF87171;
    private static final int RED_BG = 0xFF4A1C21;
    private static final int RED_BG_HOVER = 0xFF5C242A;
    private static final int YELLOW = 0xFFFFE27A;
    private static final int ORANGE = 0xFFFFB050;
    private static final int ROW_HOVER = 0x1FFFFFFF;
    private static final int GROUP_BG = 0xFF19202A;
    private static final int GROUP_BG_HOVER = 0xFF222A36;
    private static final int TRACK = 0xFF1A2029;
    private static final int THUMB = 0xFF465263;

    private static final int ROW_H = 20;

    /** Which sections are open. Remembered while the game is running. */
    private static final EnumSet<EnchantGroups.Group> EXPANDED = EnumSet.noneOf(EnchantGroups.Group.class);

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

    private enum Style { NORMAL, PRIMARY, DANGER, WANT, AVOID }

    private interface Click {
        void run(int button);
    }

    /** A clickable and/or hoverable rectangle from the last frame. */
    private record Zone(int x0, int y0, int x1, int y1, Click click, List<Component> tooltip) {
        boolean hit(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    /** One line of the list: a section heading or an enchantment. */
    private record Line(EnchantGroups.Group header, EnchantCatalog.Entry entry) {
    }

    private final List<Zone> zones = new ArrayList<>();
    private final Map<String, ItemStack> icons = new HashMap<>();
    private List<EnchantCatalog.Entry> catalog = List.of();
    private EditBox searchBox;
    private String search = "";
    private Filter filter = Filter.ALL;
    private int scroll;

    // panel geometry, set by layout()
    private int px, py, pw, ph, ix, iw, toolbarY, filterY, listTop, listBottom;

    /** CI screenshots only. */
    public static void expandForTest(EnchantGroups.Group... groups) {
        EXPANDED.addAll(List.of(groups));
    }

    public RerollerScreen() {
        super(Component.literal("Villager Reroller"));
    }

    private static RerollerConfig cfg() {
        return VillagerRerollerClient.config;
    }

    // ------------------------------------------------------------------ setup

    private void layout() {
        pw = Math.min(400, this.width - 12);
        ph = this.height - 12;
        px = (this.width - pw) / 2;
        py = 6;
        ix = px + 8;
        iw = pw - 16;
        toolbarY = py + 37;
        filterY = py + 59;
        listTop = py + 83;
        listBottom = py + ph - 18;
    }

    @Override
    protected void init() {
        catalog = EnchantCatalog.tradeable(Minecraft.getInstance());
        layout();
        int searchW = Math.max(60, iw - (62 + 58 + 58 + 48 + 4 * 6));
        searchBox = new EditBox(this.font, ix, filterY, searchW, 18, Component.literal("Search"));
        searchBox.setMaxLength(40);
        searchBox.setHint(Component.literal("Search enchants...").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setValue(search);
        searchBox.setResponder(s -> {
            if (!s.equals(search)) {
                search = s;
                scroll = 0;
            }
        });
        addRenderableWidget(searchBox);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        layout();
        g.fill(px + 2, py + 3, px + pw + 2, py + ph + 3, SHADOW);
        g.fill(px, py, px + pw, py + ph, PANEL);
        g.fill(px, py, px + pw, py + 31, HEADER);
        g.fill(px, py + 31, px + pw, py + 32, EDGE);
        g.fill(px, listBottom + 1, px + pw, listBottom + 2, EDGE);
        g.fill(px, py, px + pw, py + 1, EDGE);
        g.fill(px, py + ph - 1, px + pw, py + ph, EDGE);
        g.fill(px, py, px + 1, py + ph, EDGE);
        g.fill(px + pw - 1, py, px + pw, py + ph, EDGE);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        zones.clear();
        layout();
        super.extractRenderState(g, mouseX, mouseY, delta);
        RerollerConfig cfg = cfg();

        drawHeader(g, mouseX, mouseY);
        drawToolbar(g, mouseX, mouseY, cfg);
        List<EnchantCatalog.Entry> shown = shownEntries(cfg);
        drawFilterRow(g, mouseX, mouseY, cfg, shown);
        drawList(g, mouseX, mouseY, cfg, shown);
        drawFooter(g, mouseX, mouseY, cfg);

        for (int i = zones.size() - 1; i >= 0; i--) {
            Zone z = zones.get(i);
            if (z.hit(mouseX, mouseY)) {
                if (z.tooltip() != null) {
                    g.setComponentTooltipForNextFrame(this.font, z.tooltip(), mouseX, mouseY);
                }
                break;
            }
        }
    }

    private void drawHeader(GuiGraphicsExtractor g, int mx, int my) {
        g.item(icon(EnchantInfo.FALLBACK_ICON), px + 8, py + 7);
        g.text(this.font, "Villager Reroller", px + 30, py + 6, GOLD, true);
        int sc = statusColor();
        g.fill(px + 30, py + 20, px + 34, py + 24, sc);
        g.text(this.font, fit(statusText(), pw - 140), px + 38, py + 18, MUTED, false);

        // close
        int cx = px + pw - 26;
        int cy = py + 6;
        boolean hoverClose = mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18;
        g.fill(cx, cy, cx + 18, cy + 18, hoverClose ? RED_BG_HOVER : BTN);
        for (int i = 0; i < 7; i++) {
            g.fill(cx + 5 + i, cy + 5 + i, cx + 6 + i, cy + 6 + i, TEXT);
            g.fill(cx + 11 - i, cy + 5 + i, cx + 12 - i, cy + 6 + i, TEXT);
        }
        zones.add(new Zone(cx, cy, cx + 18, cy + 18, b -> onClose(), List.of(Component.literal("Save and close (Esc)"))));

        // start / stop
        Minecraft mc = Minecraft.getInstance();
        boolean running = VillagerRerollerClient.CONTROLLER.isRunning();
        button(g, mx, my, cx - 70, cy, 64, 18, running ? "Stop" : "Start", running ? Style.DANGER : Style.PRIMARY, true,
                b -> {
                    if (VillagerRerollerClient.CONTROLLER.isRunning()) {
                        VillagerRerollerClient.CONTROLLER.stop(mc, "stopped from the menu.", false);
                    } else {
                        saveAndClose();
                        VillagerRerollerClient.CONTROLLER.start(mc);
                    }
                },
                tip(running ? "Stop rerolling" : "Start rerolling", "Shortcut: the toggle key (B) or /vr start"));
    }

    private void drawToolbar(GuiGraphicsExtractor g, int mx, int my, RerollerConfig cfg) {
        Minecraft mc = Minecraft.getInstance();
        var cal = VillagerRerollerClient.CALIBRATION;
        int bw = (iw - 4 * 6) / 5;
        int x = ix;
        int y = toolbarY;

        button(g, mx, my, x, y, bw, 18, "Calibrate", Style.NORMAL, true, b -> {
            saveAndClose();
            cal.begin(mc);
        }, tip("Calibrate", "Do the routine once by hand (place the lectern,", "click the villager, break the lectern, click", "your bed) and the mod copies it."));
        x += bw + 6;

        toggle(g, mx, my, x, y, bw, cal.hasBed() ? "Bed set" : "Set bed", cal.hasBed(), cal.isDone(), b -> {
            saveAndClose();
            cal.askForBed(mc);
        }, cal.isDone()
                ? tip(cal.hasBed() ? "Change bed" : "Set bed", "Right-click the bed to sleep in at night.", "Keep it a few blocks from where you stand.")
                : tip("Set bed", "Calibrate first."));
        x += bw + 6;

        toggle(g, mx, my, x, y, bw, "Night", cfg.handleNight, true, b -> cfg.handleNight = !cfg.handleNight,
                tip("Night handling: " + (cfg.handleNight ? "ON" : "OFF"), "Villagers sleep instead of taking jobs at night.",
                        "When on: sleep in your bed (or wait for morning),", "then walk back to your spot and carry on."));
        x += bw + 6;

        toggle(g, mx, my, x, y, bw, "Sounds", cfg.sounds, true, b -> cfg.sounds = !cfg.sounds,
                tip("Sounds: " + (cfg.sounds ? "ON" : "OFF"), "A chime when rerolling starts, a low note when it", "stops, and a fanfare when your book is found."));
        x += bw + 6;

        toggle(g, mx, my, x, y, iw - (x - ix), "Accept any", cfg.acceptAnyNotAvoided, true,
                b -> cfg.acceptAnyNotAvoided = !cfg.acceptAnyNotAvoided,
                tip("Accept any: " + (cfg.acceptAnyNotAvoided ? "ON" : "OFF"), "Stop on any enchant you haven't marked Avoid,",
                        "as long as it's within that enchant's own price limit."));
    }

    private void drawFilterRow(GuiGraphicsExtractor g, int mx, int my, RerollerConfig cfg, List<EnchantCatalog.Entry> shown) {
        int x = ix + searchBox.getWidth() + 6;
        int y = filterY;
        List<String> shownIds = ids(shown);

        button(g, mx, my, x, y, 62, 18, "Show: " + filter.label, Style.NORMAL, true, b -> {
            filter = b == 1 ? Filter.values()[(filter.ordinal() + 2) % 3] : filter.next();
            scroll = 0;
        }, tip("Show", "All, only Wanted, or only Avoided enchants."));
        x += 68;
        button(g, mx, my, x, y, 58, 18, "Want all", Style.WANT, !shownIds.isEmpty(),
                b -> EnchantGroups.setAll(cfg, shownIds, EnchantRule.Mode.WANT),
                tip("Want all", "Sets every enchant in the list to Want.", "Works with the search and Show filter."));
        x += 64;
        button(g, mx, my, x, y, 58, 18, "Avoid all", Style.AVOID, !shownIds.isEmpty(),
                b -> EnchantGroups.setAll(cfg, shownIds, EnchantRule.Mode.AVOID),
                tip("Avoid all", "Sets every enchant in the list to Avoid.", "Works with the search and Show filter."));
        x += 64;
        button(g, mx, my, x, y, ix + iw - x, 18, "Clear", Style.NORMAL, !shownIds.isEmpty(),
                b -> EnchantGroups.setAll(cfg, shownIds, EnchantRule.Mode.IGNORE),
                tip("Clear", "Sets every enchant in the list back to Ignore."));
    }

    private void drawList(GuiGraphicsExtractor g, int mx, int my, RerollerConfig cfg, List<EnchantCatalog.Entry> shown) {
        if (catalog.isEmpty()) {
            centered(g, "Join a world to load the enchantment list.", listTop + 12, ORANGE);
            return;
        }
        if (shown.isEmpty()) {
            centered(g, "Nothing matches.", listTop + 12, MUTED);
            return;
        }

        Map<EnchantGroups.Group, List<EnchantCatalog.Entry>> byGroup = new EnumMap<>(EnchantGroups.Group.class);
        for (EnchantCatalog.Entry e : shown) {
            byGroup.computeIfAbsent(EnchantGroups.of(e.id()), k -> new ArrayList<>()).add(e);
        }
        boolean filtering = !search.isBlank() || filter != Filter.ALL; // open every section while searching
        List<Line> lines = new ArrayList<>();
        for (Map.Entry<EnchantGroups.Group, List<EnchantCatalog.Entry>> grp : byGroup.entrySet()) {
            lines.add(new Line(grp.getKey(), null));
            if (filtering || EXPANDED.contains(grp.getKey())) {
                for (EnchantCatalog.Entry e : grp.getValue()) {
                    lines.add(new Line(null, e));
                }
            }
        }

        int visible = Math.max(1, (listBottom - listTop) / ROW_H);
        int maxScroll = Math.max(0, lines.size() - visible);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int right = ix + iw - 8;

        for (int i = scroll; i < Math.min(lines.size(), scroll + visible); i++) {
            int y = listTop + (i - scroll) * ROW_H;
            Line line = lines.get(i);
            if (line.header() != null) {
                drawHeaderLine(g, mx, my, y, right, line.header(), byGroup.get(line.header()), cfg, filtering);
            } else {
                drawEntry(g, mx, my, y, right, line.entry(), cfg);
            }
        }

        // scrollbar
        int tx = ix + iw - 4;
        g.fill(tx, listTop, tx + 4, listTop + visible * ROW_H, TRACK);
        if (maxScroll > 0) {
            int trackH = visible * ROW_H;
            int thumbH = Math.max(14, trackH * visible / lines.size());
            int thumbY = listTop + (trackH - thumbH) * scroll / maxScroll;
            g.fill(tx, thumbY, tx + 4, thumbY + thumbH, THUMB);
        }
    }

    private void drawHeaderLine(GuiGraphicsExtractor g, int mx, int my, int y, int right, EnchantGroups.Group group,
                                List<EnchantCatalog.Entry> entries, RerollerConfig cfg, boolean filtering) {
        boolean open = filtering || EXPANDED.contains(group);
        boolean hover = mx >= ix && mx < right && my >= y && my < y + ROW_H;
        g.fill(ix, y + 1, right, y + ROW_H - 1, hover ? GROUP_BG_HOVER : GROUP_BG);
        chevron(g, ix + 5, y + 7, open, hover ? TEXT : MUTED);
        g.item(icon(EnchantInfo.groupIcon(group)), ix + 16, y + 2);
        g.text(this.font, group.title, ix + 37, y + 6, TEXT, true);

        int wanted = 0;
        int avoided = 0;
        for (EnchantCatalog.Entry e : entries) {
            EnchantRule.Mode m = mode(cfg, e.id());
            if (m == EnchantRule.Mode.WANT) {
                wanted++;
            } else if (m == EnchantRule.Mode.AVOID) {
                avoided++;
            }
        }
        int x = right - 6;
        String total = String.valueOf(entries.size());
        x -= this.font.width(total);
        g.text(this.font, total, x, y + 6, DIM, false);
        if (avoided > 0) {
            String s = avoided + " avoided";
            x -= this.font.width(s) + 10;
            g.text(this.font, s, x, y + 6, RED, false);
        }
        if (wanted > 0) {
            String s = wanted + " wanted";
            x -= this.font.width(s) + 10;
            g.text(this.font, s, x, y + 6, GREEN, false);
        }
        zones.add(new Zone(ix, y, right, y + ROW_H, b -> {
            if (EXPANDED.contains(group)) {
                EXPANDED.remove(group);
            } else {
                EXPANDED.add(group);
            }
        }, filtering ? null : List.of(Component.literal(open ? "Click to collapse" : "Click to expand").withStyle(ChatFormatting.GRAY))));
    }

    private void drawEntry(GuiGraphicsExtractor g, int mx, int my, int y, int right, EnchantCatalog.Entry e, RerollerConfig cfg) {
        EnchantRule existing = cfg.ruleOrNull(e.id());
        EnchantRule rule = existing != null ? existing : new EnchantRule();
        EnchantRule.Mode mode = rule.mode;
        boolean priceActive = mode == EnchantRule.Mode.WANT || (cfg.acceptAnyNotAvoided && mode == EnchantRule.Mode.IGNORE);
        int level = rule.requiredLevel(e.maxLevel());
        int best = PriceCalc.bestPrice(cfg.priceModel, level, e.doublePrice());
        int limit = PriceCalc.priceLimit(best, rule.maxPercent);

        boolean hover = mx >= ix && mx < right && my >= y && my < y + ROW_H;
        if (hover) {
            g.fill(ix, y, right, y + ROW_H, ROW_HOVER);
        }
        if (mode != EnchantRule.Mode.IGNORE) {
            g.fill(ix + 12, y + 3, ix + 14, y + ROW_H - 3, mode == EnchantRule.Mode.WANT ? GREEN : RED);
        }
        g.item(icon(EnchantInfo.icon(e.id())), ix + 18, y + 2);

        // the whole row shows the enchant's details; controls added after it win the hover
        zones.add(new Zone(ix, y, right, y + ROW_H, null, entryTooltip(e, rule, priceActive, best, limit, level)));

        int r = right - 4;
        int modeX = r - 182;
        int nameColor = switch (mode) {
            case WANT -> GREEN;
            case AVOID -> RED;
            case IGNORE -> TEXT;
        };
        g.text(this.font, fit(e.name().getString(), modeX - 6 - (ix + 39)), ix + 39, y + 6, nameColor, true);

        // mode chip: left click forward, right click back
        Style ms = mode == EnchantRule.Mode.WANT ? Style.WANT : mode == EnchantRule.Mode.AVOID ? Style.AVOID : Style.NORMAL;
        String ml = mode == EnchantRule.Mode.WANT ? "Want" : mode == EnchantRule.Mode.AVOID ? "Avoid" : "Ignore";
        button(g, mx, my, modeX, y + 2, 42, 16, ml, ms, true, b -> {
            EnchantRule rr = cfg.rule(e.id());
            rr.mode = b == 1 ? EnchantRule.Mode.values()[(rr.mode.ordinal() + 2) % 3] : rr.mode.next();
        }, tip("Ignore / Want / Avoid", "Want: stop when it's offered.", "Avoid: never accept a villager offering it.", "Click to change (right-click goes back)."));

        // level chip
        boolean levelActive = mode == EnchantRule.Mode.WANT && e.maxLevel() > 1;
        String lvl = e.maxLevel() <= 1 ? "I" : rule.minLevel <= 0 ? "Max " + EnchantInfo.roman(e.maxLevel())
                : EnchantInfo.roman(level) + "+";
        button(g, mx, my, r - 136, y + 2, 36, 16, lvl, Style.NORMAL, levelActive, b -> {
            EnchantRule rr = cfg.rule(e.id());
            rr.minLevel = b == 1 ? prevMinLevel(rr.minLevel, e.maxLevel()) : nextMinLevel(rr.minLevel, e.maxLevel());
        }, e.maxLevel() <= 1 ? tip("Level", "This enchant only has one level.")
                : tip("Lowest level you'll accept", "Max " + EnchantInfo.roman(e.maxLevel()) + " = only the best level.", "Click to change (right-click goes back)."));

        // price: [-] 150% [emerald] 25 [+]
        g.item(icon("minecraft:emerald"), r - 52, y + 2);
        if (priceActive) {
            button(g, mx, my, r - 96, y + 3, 14, 14, "-", Style.NORMAL, rule.maxPercent > 100, b -> {
                EnchantRule rr = cfg.rule(e.id());
                rr.maxPercent = stepDown(rr.maxPercent);
            }, null);
            String pct = rule.maxPercent + "%";
            g.text(this.font, pct, r - 67 - this.font.width(pct) / 2, y + 6, YELLOW, false);
            g.text(this.font, String.valueOf(limit), r - 34, y + 6, GREEN, true);
            button(g, mx, my, r - 14, y + 3, 14, 14, "+", Style.NORMAL, rule.maxPercent < 1000, b -> {
                EnchantRule rr = cfg.rule(e.id());
                rr.maxPercent = stepUp(rr.maxPercent);
            }, null);
            zones.add(new Zone(r - 82, y, r - 16, y + ROW_H, null,
                    tip("Price limit: " + limit + " emeralds", rule.maxPercent + "% of the cheapest possible price (" + best + ").",
                            "100% = only the cheapest roll. Use - and + to change.")));
        } else {
            g.text(this.font, "best", r - 67 - this.font.width("best") / 2, y + 6, DIM, false);
            g.text(this.font, String.valueOf(best), r - 34, y + 6, MUTED, false);
            zones.add(new Zone(r - 82, y, r, y + ROW_H, null,
                    tip("Cheapest possible: " + best + " emeralds", "Set this enchant to Want to choose your price limit.")));
        }
    }

    private void drawFooter(GuiGraphicsExtractor g, int mx, int my, RerollerConfig cfg) {
        int wanted = 0;
        int avoided = 0;
        for (EnchantCatalog.Entry e : catalog) {
            EnchantRule.Mode m = mode(cfg, e.id());
            if (m == EnchantRule.Mode.WANT) {
                wanted++;
            } else if (m == EnchantRule.Mode.AVOID) {
                avoided++;
            }
        }
        int y = listBottom + 6;
        g.text(this.font, catalog.size() + " enchants  ·  ", ix, y, MUTED, false);
        int x = ix + this.font.width(catalog.size() + " enchants  ·  ");
        g.text(this.font, wanted + " wanted", x, y, wanted > 0 ? GREEN : MUTED, false);
        x += this.font.width(wanted + " wanted  ·  ");
        g.text(this.font, "  ·  ", x - this.font.width("  ·  "), y, MUTED, false);
        g.text(this.font, avoided + " avoided", x, y, avoided > 0 ? RED : MUTED, false);

        int rx = ix + iw;
        rx = link(g, mx, my, rx, y, "Collapse all", b -> EXPANDED.clear());
        rx -= this.font.width("  ·  ");
        g.text(this.font, "  ·  ", rx, y, DIM, false);
        link(g, mx, my, rx, y, "Expand all", b -> EXPANDED.addAll(EnumSet.allOf(EnchantGroups.Group.class)));
    }

    // ------------------------------------------------------------------ small drawing helpers

    private void button(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String label, Style style,
                        boolean enabled, Click click, List<Component> tooltip) {
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        int bg;
        int fg;
        switch (style) {
            case PRIMARY -> {
                bg = hover ? 0xFF2FB765 : 0xFF22A055;
                fg = 0xFFFFFFFF;
            }
            case DANGER -> {
                bg = hover ? 0xFFE0464E : 0xFFC9363E;
                fg = 0xFFFFFFFF;
            }
            case WANT -> {
                bg = hover ? GREEN_BG_HOVER : GREEN_BG;
                fg = GREEN;
            }
            case AVOID -> {
                bg = hover ? RED_BG_HOVER : RED_BG;
                fg = RED;
            }
            default -> {
                bg = hover ? BTN_HOVER : BTN;
                fg = TEXT;
            }
        }
        if (!enabled) {
            bg = BTN;
            fg = DIM;
        }
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + w, y + 1, 0x14FFFFFF);
        String s = fit(label, w - 4);
        g.text(this.font, s, x + (w - this.font.width(s)) / 2, y + (h - 8) / 2, fg, style == Style.PRIMARY || style == Style.DANGER);
        zones.add(new Zone(x, y, x + w, y + h, enabled ? click : null, tooltip));
    }

    /** A button with a little on/off light on the left. */
    private void toggle(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String label, boolean on,
                        boolean enabled, Click click, List<Component> tooltip) {
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + 18;
        int bg = !enabled ? BTN : on ? (hover ? GREEN_BG_HOVER : GREEN_BG) : (hover ? BTN_HOVER : BTN);
        g.fill(x, y, x + w, y + 18, bg);
        g.fill(x, y, x + w, y + 1, 0x14FFFFFF);
        g.fill(x + 6, y + 7, x + 10, y + 11, !enabled ? DIM : on ? GREEN : DIM);
        String s = fit(label, w - 18);
        g.text(this.font, s, x + 14 + (w - 18 - this.font.width(s)) / 2, y + 5, !enabled ? DIM : on ? GREEN : TEXT, false);
        zones.add(new Zone(x, y, x + w, y + 18, enabled ? click : null, tooltip));
    }

    /** Right-aligned clickable text ending at {@code right}; returns its left edge. */
    private int link(GuiGraphicsExtractor g, int mx, int my, int right, int y, String text, Click click) {
        int w = this.font.width(text);
        int x = right - w;
        boolean hover = mx >= x && mx < right && my >= y - 2 && my < y + 10;
        g.text(this.font, text, x, y, hover ? TEXT : MUTED, false);
        if (hover) {
            g.fill(x, y + 9, right, y + 10, TEXT);
        }
        zones.add(new Zone(x, y - 2, right, y + 10, click, null));
        return x;
    }

    /** A small triangle: pointing right (closed) or down (open). */
    private static void chevron(GuiGraphicsExtractor g, int x, int y, boolean open, int color) {
        if (open) {
            for (int i = 0; i < 4; i++) {
                g.fill(x + i, y + 1 + i, x + 7 - i, y + 2 + i, color);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                g.fill(x + 1 + i, y + i, x + 2 + i, y + 7 - i, color);
            }
        }
    }

    private void centered(GuiGraphicsExtractor g, String s, int y, int color) {
        g.text(this.font, s, px + (pw - this.font.width(s)) / 2, y, color, false);
    }

    private ItemStack icon(String itemId) {
        return icons.computeIfAbsent(itemId, k -> {
            Identifier id = Identifier.tryParse(k);
            Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
            if (item == null || item == Items.AIR) {
                item = Items.ENCHANTED_BOOK;
            }
            return new ItemStack(item);
        });
    }

    private String fit(String s, int w) {
        if (w <= 0) {
            return "";
        }
        if (this.font.width(s) <= w) {
            return s;
        }
        return this.font.plainSubstrByWidth(s, Math.max(0, w - this.font.width(".."))) + "..";
    }

    private static List<Component> tip(String title, String... lines) {
        List<Component> out = new ArrayList<>();
        out.add(Component.literal(title).withStyle(ChatFormatting.WHITE));
        for (String l : lines) {
            out.add(Component.literal(l).withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    private static List<Component> entryTooltip(EnchantCatalog.Entry e, EnchantRule rule, boolean priceActive,
                                                int best, int limit, int level) {
        List<Component> out = new ArrayList<>();
        out.add(Component.literal(e.name().getString() + (e.maxLevel() > 1 ? " " + EnchantInfo.roman(e.maxLevel()) : ""))
                .withStyle(ChatFormatting.GOLD));
        for (String l : EnchantInfo.wrap(EnchantInfo.description(e.id()), 42)) {
            out.add(Component.literal(l).withStyle(ChatFormatting.GRAY));
        }
        out.add(Component.empty());
        out.add(Component.literal("Works on: " + EnchantGroups.of(e.id()).title).withStyle(ChatFormatting.DARK_AQUA));
        out.add(Component.literal("Max level: " + EnchantInfo.roman(e.maxLevel())).withStyle(ChatFormatting.DARK_AQUA));
        out.add(Component.literal("Cheapest possible" + (e.maxLevel() > 1 ? " (level " + EnchantInfo.roman(level) + ")" : "")
                + ": " + best + " emeralds").withStyle(ChatFormatting.GREEN));
        if (priceActive) {
            out.add(Component.literal("Your limit: " + limit + " emeralds (" + rule.maxPercent + "%)").withStyle(ChatFormatting.YELLOW));
        }
        if (e.doublePrice()) {
            out.add(Component.literal("Treasure enchant: books cost double").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        out.add(Component.literal(switch (rule.mode) {
            case WANT -> "Wanted: rerolling stops when it's offered.";
            case AVOID -> "Avoided: villagers offering it are skipped.";
            case IGNORE -> "Ignored.";
        }).withStyle(ChatFormatting.DARK_GRAY));
        return out;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int button = event.button();
        if (button == 0 || button == 1) {
            for (int i = zones.size() - 1; i >= 0; i--) {
                Zone z = zones.get(i);
                if (z.hit(event.x(), event.y())) {
                    if (z.click() == null) {
                        break;
                    }
                    clearFocus();
                    AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
                    z.click().run(button);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= px && mouseX < px + pw && mouseY >= listTop && mouseY < listBottom) {
            if (scrollY > 0) {
                scroll = Math.max(0, scroll - 2);
            } else if (scrollY < 0) {
                scroll += 2; // clamped when drawn
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ------------------------------------------------------------------ data helpers

    private List<EnchantCatalog.Entry> shownEntries(RerollerConfig cfg) {
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<EnchantCatalog.Entry> out = new ArrayList<>();
        for (EnchantCatalog.Entry e : catalog) {
            EnchantRule.Mode m = mode(cfg, e.id());
            if (filter == Filter.WANTED && m != EnchantRule.Mode.WANT) {
                continue;
            }
            if (filter == Filter.AVOIDED && m != EnchantRule.Mode.AVOID) {
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

    private static EnchantRule.Mode mode(RerollerConfig cfg, String id) {
        EnchantRule r = cfg.ruleOrNull(id);
        return r == null ? EnchantRule.Mode.IGNORE : r.mode;
    }

    private static List<String> ids(List<EnchantCatalog.Entry> entries) {
        List<String> out = new ArrayList<>();
        for (EnchantCatalog.Entry e : entries) {
            out.add(e.id());
        }
        return out;
    }

    private static String statusText() {
        var controller = VillagerRerollerClient.CONTROLLER;
        var cal = VillagerRerollerClient.CALIBRATION;
        String bed = cal.hasBed() ? "  ·  bed set" : "  ·  no bed";
        if (controller.isRunning()) {
            return (controller.isNightPaused() ? "Night pause" : "Running") + "  ·  reroll #" + controller.rerolls() + bed;
        }
        if (cal.isDone()) {
            return "Calibrated and ready" + bed;
        }
        if (cal.isActive()) {
            return "Calibrating... follow the chat instructions";
        }
        return "Not calibrated: press Calibrate first";
    }

    private static int statusColor() {
        var cal = VillagerRerollerClient.CALIBRATION;
        if (VillagerRerollerClient.CONTROLLER.isRunning() || cal.isDone()) {
            return GREEN;
        }
        return cal.isActive() ? YELLOW : ORANGE;
    }

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

    /** The reverse of {@link #nextMinLevel}. */
    static int prevMinLevel(int current, int maxLevel) {
        if (maxLevel <= 1) {
            return 0;
        }
        if (current <= 0) {
            return maxLevel - 1;
        }
        return current - 1 <= 0 ? 0 : current - 1;
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
