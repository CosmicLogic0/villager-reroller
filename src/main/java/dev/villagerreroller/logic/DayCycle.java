package dev.villagerreroller.logic;

/** Vanilla day/night rules, from the time of day in ticks (0-23999). */
public final class DayCycle {

    private DayCycle() {
    }

    /** Villagers switch to their "rest" schedule (go to bed, stop taking jobs) from 12000 until morning. */
    public static final int VILLAGER_REST_START = 12000;
    /** Earliest time a player may sleep in clear weather. */
    public static final int SLEEP_START = 12542;
    /** Latest time a player may get into bed in clear weather. */
    public static final int SLEEP_END = 23459;

    public static int timeOfDay(long dayTime) {
        return (int) Math.floorMod(dayTime, 24000L);
    }

    public static boolean villagersResting(long dayTime) {
        return timeOfDay(dayTime) >= VILLAGER_REST_START;
    }

    public static boolean canSleep(long dayTime, boolean thundering) {
        int t = timeOfDay(dayTime);
        return thundering || (t >= SLEEP_START && t <= SLEEP_END);
    }
}
