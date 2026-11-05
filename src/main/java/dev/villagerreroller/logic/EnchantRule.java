package dev.villagerreroller.logic;

/**
 * What the player wants for one enchantment. Plain fields so Gson can read/write it directly.
 */
public final class EnchantRule {

    public enum Mode {
        /** No opinion. The enchant never stops the reroller. */
        IGNORE,
        /** Stop rerolling when this enchant shows up (subject to level and price limits). */
        WANT,
        /** Never accept a villager that offers this enchant. */
        AVOID;

        public Mode next() {
            Mode[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    /** Price limit used when nothing was changed. */
    public static final int DEFAULT_MAX_PERCENT = 150;

    public Mode mode = Mode.IGNORE;

    /** Minimum acceptable level. 0 (or less) means "only the maximum level". */
    public int minLevel = 0;

    /**
     * Highest acceptable price as a percentage of the best possible price for the offered level.
     * 100 = only the cheapest possible roll, 150 = up to 1.5x the cheapest possible roll.
     */
    public int maxPercent = DEFAULT_MAX_PERCENT;

    /** The level actually required for an enchant whose maximum level is {@code maxLevel}. */
    public int requiredLevel(int maxLevel) {
        if (minLevel <= 0) {
            return maxLevel;
        }
        return Math.min(minLevel, maxLevel);
    }
}
