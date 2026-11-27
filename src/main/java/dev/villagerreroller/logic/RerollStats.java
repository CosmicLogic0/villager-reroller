package dev.villagerreroller.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * What the on-screen panel shows while rerolling: how many rerolls, how long, which villager of how many,
 * the last few books seen, and the books found so far. Plain Java, so every Minecraft version shares it.
 */
public final class RerollStats {

    public static final int SHOW_AFTER_STOP_MS = 15000;
    private static final int RECENT = 6;

    /** One line of the panel. */
    public static final class Line {
        public final String text;
        public final int color;

        Line(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }

    public static final int WHITE = 0xFFFFFFFF;
    public static final int GOLD = 0xFFFFC94A;
    public static final int GREEN = 0xFF7CE37C;
    public static final int GRAY = 0xFFB8B8B8;
    public static final int DARK = 0xFF8A8A8A;
    public static final int RED = 0xFFFF8080;

    private boolean running;
    private long startMs;
    private long stopMs;
    private int rerolls;
    private int station;
    private int stations = 1;
    private String stopReason = "";
    private final Deque<Line> recent = new ArrayDeque<>();
    private final List<String> found = new ArrayList<>();

    public synchronized void start(int stationCount) {
        running = true;
        startMs = System.currentTimeMillis();
        stopMs = 0;
        rerolls = 0;
        station = 0;
        stations = Math.max(1, stationCount);
        stopReason = "";
        recent.clear();
        found.clear();
    }

    public synchronized void station(int index) {
        station = index;
    }

    /** One look at the trades. book = null when the villager offered no enchanted book this time. */
    public synchronized void reroll(String book) {
        rerolls++;
        add(book == null ? new Line("#" + rerolls + "  no book", DARK) : new Line("#" + rerolls + "  " + book, GRAY));
    }

    public synchronized void found(String book) {
        found.add(stations > 1 ? "Villager " + (station + 1) + ": " + book : book);
        add(new Line("#" + rerolls + "  " + book, GREEN));
    }

    public synchronized void stop(String reason, boolean success) {
        if (!running) {
            return;
        }
        running = false;
        stopMs = System.currentTimeMillis();
        stopReason = success ? "" : reason;
    }

    public synchronized void clear() {
        running = false;
        stopMs = 0;
    }

    private void add(Line l) {
        recent.addFirst(l);
        while (recent.size() > RECENT) {
            recent.removeLast();
        }
    }

    public synchronized int rerolls() {
        return rerolls;
    }

    public synchronized List<String> foundList() {
        return new ArrayList<>(found);
    }

    /** Should the panel be on screen right now? (while rerolling, and for a little while after it stops) */
    public synchronized boolean visible(long now) {
        return running || (stopMs > 0 && now - stopMs < SHOW_AFTER_STOP_MS);
    }

    public static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        return s >= 3600 ? (s / 3600) + "h " + (s / 60 % 60) + "m" : s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s";
    }

    public synchronized List<Line> lines(long now) {
        List<Line> out = new ArrayList<>();
        long elapsed = (running ? now : stopMs) - startMs;
        out.add(new Line(running ? "Rerolling" : stopReason.isEmpty() ? "Done" : "Stopped", running ? GOLD : stopReason.isEmpty() ? GREEN : RED));
        out.add(new Line(rerolls + (rerolls == 1 ? " reroll" : " rerolls") + "  ·  " + time(elapsed)
                + (stations > 1 ? "  ·  villager " + Math.min(station + 1, stations) + "/" + stations : ""), WHITE));
        if (!stopReason.isEmpty()) {
            out.add(new Line(stopReason, RED));
        }
        if (!found.isEmpty()) {
            out.add(new Line("Found:", GREEN));
            for (String f : found) {
                out.add(new Line("  " + f, GREEN));
            }
        }
        if (!recent.isEmpty()) {
            out.add(new Line("Last books:", GOLD));
            for (Line l : recent) {
                out.add(new Line("  " + l.text, l.color));
            }
        }
        return out;
    }
}
