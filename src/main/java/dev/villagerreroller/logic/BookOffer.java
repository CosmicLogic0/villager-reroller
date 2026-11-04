package dev.villagerreroller.logic;

/**
 * One enchantment on one enchanted-book trade, reduced to plain data.
 *
 * @param enchantId   registry name, e.g. "minecraft:sharpness"
 * @param displayName human readable, e.g. "Sharpness V"
 * @param level       level on the book
 * @param maxLevel    the enchantment's maximum level
 * @param doublePrice true for enchants whose book price is doubled (mending etc.)
 * @param price       emerald price currently shown (includes any discounts/demand)
 */
public record BookOffer(String enchantId, String displayName, int level, int maxLevel, boolean doublePrice, int price) {
}
