package dev.villagerreroller.logic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Sorts enchantments into the groups shown in the menu. Plain Java so it can be unit tested. */
public final class EnchantGroups {

    private EnchantGroups() {
    }

    public enum Group {
        UNIVERSAL("Universal"),
        MELEE("Swords & Axes"),
        TOOLS("Tools"),
        ARMOR("Armor (any piece)"),
        HELMET("Helmet"),
        LEGGINGS("Leggings"),
        BOOTS("Boots"),
        BOW("Bow"),
        CROSSBOW("Crossbow"),
        TRIDENT("Trident"),
        MACE("Mace"),
        SPEAR("Spear"),
        FISHING("Fishing Rod"),
        CURSES("Curses"),
        OTHER("Other / Modded");

        public final String title;

        Group(String title) {
            this.title = title;
        }
    }

    private static final Map<String, Group> VANILLA = new HashMap<>();

    private static void put(Group g, String... paths) {
        for (String p : paths) {
            VANILLA.put("minecraft:" + p, g);
        }
    }

    static {
        put(Group.UNIVERSAL, "mending", "unbreaking");
        put(Group.MELEE, "sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect", "looting", "sweeping_edge");
        put(Group.TOOLS, "efficiency", "silk_touch", "fortune");
        put(Group.ARMOR, "protection", "fire_protection", "blast_protection", "projectile_protection", "thorns");
        put(Group.HELMET, "respiration", "aqua_affinity");
        put(Group.LEGGINGS, "swift_sneak");
        put(Group.BOOTS, "feather_falling", "depth_strider", "frost_walker", "soul_speed");
        put(Group.BOW, "power", "punch", "flame", "infinity");
        put(Group.CROSSBOW, "multishot", "piercing", "quick_charge");
        put(Group.TRIDENT, "loyalty", "riptide", "channeling", "impaling");
        put(Group.MACE, "density", "breach", "wind_burst");
        put(Group.SPEAR, "lunge");
        put(Group.FISHING, "luck_of_the_sea", "lure");
        put(Group.CURSES, "binding_curse", "vanishing_curse");
    }

    public static Group of(String enchantId) {
        Group g = VANILLA.get(enchantId);
        if (g != null) {
            return g;
        }
        if (enchantId != null && enchantId.contains("curse")) {
            return Group.CURSES;
        }
        return Group.OTHER;
    }

    /** Sets every enchantment in {@code ids} to {@code mode}. IGNORE removes the rule entirely. */
    public static void setAll(RerollerConfig cfg, List<String> ids, EnchantRule.Mode mode) {
        for (String id : ids) {
            if (mode == EnchantRule.Mode.IGNORE) {
                EnchantRule r = cfg.ruleOrNull(id);
                if (r != null) {
                    r.mode = EnchantRule.Mode.IGNORE;
                }
            } else {
                cfg.rule(id).mode = mode;
            }
        }
    }
}
