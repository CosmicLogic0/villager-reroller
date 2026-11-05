package dev.villagerreroller.logic;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All user settings. Serialised to config/villagerreroller.json by ConfigIO.
 * Keys of {@link #rules} are registry names such as "minecraft:mending".
 */
public final class RerollerConfig {

    /**
     * How a librarian prices an enchanted book (in emeralds). Vanilla: 2 + random(5 + 10*level) + 3*level,
     * doubled for "double trade price" enchants (mending, frost walker...), capped at 64.
     * The cheapest possible roll therefore is base + perLevel*level. Editable in case a game update changes it.
     */
    public static final class PriceModel {
        public int base = 2;
        public int perLevel = 3;
        public int doublePriceMultiplier = 2;
        public int cap = 64;
    }

    public Map<String, EnchantRule> rules = new LinkedHashMap<>();

    /** If true, any enchant that is not marked AVOID is accepted when its price is within {@link #anyMaxPercent}. */
    public boolean acceptAnyNotAvoided = false;
    public int anyMaxPercent = 150;

    /** Safety stop. 0 = unlimited. */
    public int maxRerolls = 500;

    /** Ticks to wait between actions (20 ticks = 1 second). */
    public int actionDelayTicks = 2;

    /** Ticks to give the villager to claim the freshly placed lectern before we talk to it. */
    public int claimWaitTicks = 20;

    /**
     * How long to keep waiting for the villager to take the job before the lectern is broken and placed again.
     * Villagers can take a while to notice a new lectern.
     */
    public int claimTimeoutSeconds = 60;

    /** At night villagers go to bed instead of taking jobs: pause, sleep in the calibrated bed, then carry on. */
    public boolean handleNight = true;

    public PriceModel priceModel = new PriceModel();

    public EnchantRule rule(String id) {
        return rules.computeIfAbsent(id, k -> new EnchantRule());
    }

    public EnchantRule ruleOrNull(String id) {
        return rules.get(id);
    }

    public boolean hasAnyTarget() {
        if (acceptAnyNotAvoided) {
            return true;
        }
        for (EnchantRule r : rules.values()) {
            if (r != null && r.mode == EnchantRule.Mode.WANT) {
                return true;
            }
        }
        return false;
    }

    /** Clamp everything into sane ranges (the file may have been hand edited). */
    public void sanitize() {
        if (rules == null) {
            rules = new LinkedHashMap<>();
        }
        rules.values().removeIf(r -> r == null);
        for (EnchantRule r : rules.values()) {
            if (r.mode == null) {
                r.mode = EnchantRule.Mode.IGNORE;
            }
            r.maxPercent = clamp(r.maxPercent, 100, 1000);
            r.minLevel = clamp(r.minLevel, 0, 10);
        }
        anyMaxPercent = clamp(anyMaxPercent, 100, 1000);
        maxRerolls = clamp(maxRerolls, 0, 1_000_000);
        actionDelayTicks = clamp(actionDelayTicks, 1, 40);
        claimWaitTicks = clamp(claimWaitTicks, 5, 200);
        claimTimeoutSeconds = clamp(claimTimeoutSeconds, 10, 600);
        if (priceModel == null) {
            priceModel = new PriceModel();
        }
        priceModel.base = clamp(priceModel.base, 0, 64);
        priceModel.perLevel = clamp(priceModel.perLevel, 0, 64);
        priceModel.doublePriceMultiplier = clamp(priceModel.doublePriceMultiplier, 1, 8);
        priceModel.cap = clamp(priceModel.cap, 1, 64);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
