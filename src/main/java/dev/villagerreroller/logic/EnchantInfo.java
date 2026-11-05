package dev.villagerreroller.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu extras for each enchantment: the item used as its icon and a one-line description for the hover tooltip.
 * Plain Java (item ids as strings) so it can be unit tested; the menu turns the ids into real items and falls back
 * to an enchanted book for anything unknown (enchantments from other mods).
 */
public final class EnchantInfo {

    private EnchantInfo() {
    }

    public static final String FALLBACK_ICON = "minecraft:enchanted_book";

    private record Info(String icon, String description) {
    }

    private static final Map<String, Info> VANILLA = new HashMap<>();
    private static final Map<EnchantGroups.Group, String> GROUP_ICONS = new HashMap<>();

    private static void put(String id, String icon, String description) {
        VANILLA.put("minecraft:" + id, new Info("minecraft:" + icon, description));
    }

    static {
        // Universal
        put("mending", "experience_bottle", "Repairs the item with the XP orbs you pick up.");
        put("unbreaking", "anvil", "The item loses durability less often.");
        // Swords & axes
        put("sharpness", "diamond_sword", "Extra damage against every mob.");
        put("smite", "bone", "Extra damage against undead mobs like zombies and skeletons.");
        put("bane_of_arthropods", "spider_eye", "Extra damage against spiders, bees and other arthropods, and slows them.");
        put("knockback", "piston", "Knocks the target further back.");
        put("fire_aspect", "fire_charge", "Sets the target on fire.");
        put("looting", "gold_ingot", "Mobs drop more loot.");
        put("sweeping_edge", "iron_sword", "Sweep attacks deal more damage (swords only).");
        // Tools
        put("efficiency", "diamond_pickaxe", "Mines and chops faster.");
        put("silk_touch", "grass_block", "Blocks drop themselves, like ores, glass and grass blocks.");
        put("fortune", "diamond", "More drops from ores and crops.");
        // Armor
        put("protection", "iron_chestplate", "Less damage from almost everything.");
        put("fire_protection", "blaze_powder", "Less fire damage, and you burn for a shorter time.");
        put("blast_protection", "tnt", "Less damage and knockback from explosions.");
        put("projectile_protection", "shield", "Less damage from arrows, tridents and other projectiles.");
        put("thorns", "cactus", "Hurts mobs and players that attack you.");
        put("respiration", "turtle_helmet", "Breathe longer underwater.");
        put("aqua_affinity", "prismarine_shard", "Mine at normal speed underwater.");
        put("swift_sneak", "sculk_sensor", "Walk faster while sneaking.");
        put("feather_falling", "feather", "Less fall damage.");
        put("depth_strider", "nautilus_shell", "Move faster underwater.");
        put("frost_walker", "blue_ice", "Turns water into ice under your feet as you walk.");
        put("soul_speed", "soul_sand", "Run faster on soul sand and soul soil.");
        // Bow
        put("power", "bow", "Arrows deal more damage.");
        put("punch", "slime_ball", "Arrows knock the target back.");
        put("flame", "flint_and_steel", "Arrows set the target on fire.");
        put("infinity", "arrow", "Shoot without using up arrows (you need one in your inventory).");
        // Crossbow
        put("multishot", "firework_rocket", "Shoots three arrows at once.");
        put("piercing", "spectral_arrow", "Arrows pass through several mobs.");
        put("quick_charge", "sugar", "Loads the crossbow faster.");
        // Trident
        put("loyalty", "lead", "The trident flies back to you after you throw it.");
        put("riptide", "water_bucket", "Throwing the trident in water or rain launches you with it.");
        put("channeling", "lightning_rod", "Calls down lightning on the target during thunderstorms.");
        put("impaling", "trident", "Extra damage against mobs in water or rain.");
        // Mace
        put("density", "heavy_core", "Smash attacks hit harder the further you fall.");
        put("breach", "iron_bars", "Mace hits ignore part of the target's armor.");
        put("wind_burst", "wind_charge", "Smash attacks bounce you back up into the air.");
        // Spear
        put("lunge", "diamond_spear", "Spear jabs dash you forward.");
        // Fishing rod
        put("luck_of_the_sea", "rabbit_foot", "Better fishing loot, with more treasure.");
        put("lure", "cod", "Fish bite sooner.");
        // Curses
        put("binding_curse", "carved_pumpkin", "Can't be taken off once you wear it.");
        put("vanishing_curse", "glass_bottle", "The item disappears when you die.");

        GROUP_ICONS.put(EnchantGroups.Group.UNIVERSAL, "minecraft:experience_bottle");
        GROUP_ICONS.put(EnchantGroups.Group.MELEE, "minecraft:diamond_sword");
        GROUP_ICONS.put(EnchantGroups.Group.TOOLS, "minecraft:diamond_pickaxe");
        GROUP_ICONS.put(EnchantGroups.Group.ARMOR, "minecraft:diamond_chestplate");
        GROUP_ICONS.put(EnchantGroups.Group.HELMET, "minecraft:diamond_helmet");
        GROUP_ICONS.put(EnchantGroups.Group.LEGGINGS, "minecraft:diamond_leggings");
        GROUP_ICONS.put(EnchantGroups.Group.BOOTS, "minecraft:diamond_boots");
        GROUP_ICONS.put(EnchantGroups.Group.BOW, "minecraft:bow");
        GROUP_ICONS.put(EnchantGroups.Group.CROSSBOW, "minecraft:crossbow");
        GROUP_ICONS.put(EnchantGroups.Group.TRIDENT, "minecraft:trident");
        GROUP_ICONS.put(EnchantGroups.Group.MACE, "minecraft:mace");
        GROUP_ICONS.put(EnchantGroups.Group.SPEAR, "minecraft:iron_spear");
        GROUP_ICONS.put(EnchantGroups.Group.FISHING, "minecraft:fishing_rod");
        GROUP_ICONS.put(EnchantGroups.Group.CURSES, "minecraft:wither_rose");
        GROUP_ICONS.put(EnchantGroups.Group.OTHER, FALLBACK_ICON);
    }

    /** Item id used as the enchantment's icon. */
    public static String icon(String enchantId) {
        Info i = VANILLA.get(enchantId);
        return i != null ? i.icon() : FALLBACK_ICON;
    }

    public static String groupIcon(EnchantGroups.Group g) {
        return GROUP_ICONS.getOrDefault(g, FALLBACK_ICON);
    }

    /** One-line description, or a note naming the mod that adds it. */
    public static String description(String enchantId) {
        Info i = VANILLA.get(enchantId);
        if (i != null) {
            return i.description();
        }
        int colon = enchantId == null ? -1 : enchantId.indexOf(':');
        String mod = colon > 0 ? enchantId.substring(0, colon) : "another mod";
        return "Added by " + mod + ".";
    }

    /** I, II, III ... X; plain numbers above that. */
    public static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 1 && n <= 10 ? r[n] : String.valueOf(n);
    }

    /** Splits text into lines of at most {@code width} characters, breaking at spaces. */
    public static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }
}
