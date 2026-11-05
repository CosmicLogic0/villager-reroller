package dev.villagerreroller.logic;

import java.util.List;
import java.util.Optional;

/** Decides whether an offered book is good enough to stop rerolling. No Minecraft types in here. */
public final class OfferMatcher {

    private OfferMatcher() {
    }

    public enum Verdict {
        WANTED_MATCH,
        ANY_MATCH,
        AVOIDED,
        LEVEL_TOO_LOW,
        TOO_EXPENSIVE,
        NOT_WANTED
    }

    /**
     * @param best    cheapest possible price for the offered level
     * @param percent actual price as a percentage of {@code best}
     */
    public record MatchResult(BookOffer offer, Verdict verdict, int best, int percent) {
        public boolean accepted() {
            return verdict == Verdict.WANTED_MATCH || verdict == Verdict.ANY_MATCH;
        }
    }

    public static MatchResult evaluate(BookOffer o, RerollerConfig cfg) {
        int best = PriceCalc.bestPrice(cfg.priceModel, o.level(), o.doublePrice());
        int percent = PriceCalc.percentOf(o.price(), best);
        EnchantRule r = cfg.ruleOrNull(o.enchantId());

        if (r != null && r.mode == EnchantRule.Mode.AVOID) {
            return new MatchResult(o, Verdict.AVOIDED, best, percent);
        }
        if (r != null && r.mode == EnchantRule.Mode.WANT) {
            if (o.level() < r.requiredLevel(o.maxLevel())) {
                return new MatchResult(o, Verdict.LEVEL_TOO_LOW, best, percent);
            }
            if (!PriceCalc.withinPercent(o.price(), best, r.maxPercent)) {
                return new MatchResult(o, Verdict.TOO_EXPENSIVE, best, percent);
            }
            return new MatchResult(o, Verdict.WANTED_MATCH, best, percent);
        }
        if (cfg.acceptAnyNotAvoided) {
            int limit = r != null ? r.maxPercent : EnchantRule.DEFAULT_MAX_PERCENT;
            if (!PriceCalc.withinPercent(o.price(), best, limit)) {
                return new MatchResult(o, Verdict.TOO_EXPENSIVE, best, percent);
            }
            return new MatchResult(o, Verdict.ANY_MATCH, best, percent);
        }
        return new MatchResult(o, Verdict.NOT_WANTED, best, percent);
    }

    /**
     * Looks at every book the villager offers. The villager is rejected outright if any book is AVOIDED;
     * otherwise the first accepted book wins.
     */
    public static Optional<MatchResult> firstAccepted(List<BookOffer> offers, RerollerConfig cfg) {
        MatchResult found = null;
        for (BookOffer o : offers) {
            MatchResult m = evaluate(o, cfg);
            if (m.verdict() == Verdict.AVOIDED) {
                return Optional.empty();
            }
            if (found == null && m.accepted()) {
                found = m;
            }
        }
        return Optional.ofNullable(found);
    }
}
