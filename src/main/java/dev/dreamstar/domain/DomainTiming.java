package dev.dreamstar.domain;

/** Tick-based, deterministic on server and client; survives chunk unload/reload. */
public final class DomainTiming {
    public static final int DURATION = 30 * 20;
    private DomainTiming() {}

    public static boolean expired(long now, long start) {
        return now - start >= DURATION;
    }

    public static float opacity(double now, long start) {
        double age = now - start;
        return (float) Math.max(0, Math.min(1, Math.min(age / 12.0, (DURATION - age) / 20.0)));
    }
}
