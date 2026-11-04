package dev.villagerreroller.logic;

public final class PriceCalc {

    private PriceCalc() {
    }

    /** Cheapest price a librarian can possibly charge for a book of this level. */
    public static int bestPrice(RerollerConfig.PriceModel m, int level, boolean doublePrice) {
        int p = m.base + m.perLevel * Math.max(1, level);
        if (doublePrice) {
            p *= m.doublePriceMultiplier;
        }
        return Math.max(1, Math.min(m.cap, p));
    }

    /** Price as a whole-number percentage of the best price (100 = best possible). */
    public static int percentOf(int price, int best) {
        if (best <= 0) {
            return 0;
        }
        return (int) Math.round(price * 100.0 / best);
    }

    /** True if {@code price} is at most {@code maxPercent} percent of {@code best}. Integer math, no rounding. */
    public static boolean withinPercent(int price, int best, int maxPercent) {
        return (long) price * 100L <= (long) best * (long) maxPercent;
    }

    /** Highest whole-emerald price that still passes {@code maxPercent}. */
    public static int priceLimit(int best, int maxPercent) {
        return (int) (((long) best * maxPercent) / 100L);
    }
}
