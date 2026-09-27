package dev.dreamstar.client;

/** Tick-based smoothing, independent of FPS. World changes must explicitly reset it. */
public final class SkyTransition {
    private float previous;
    private float current;

    public static float target(double distance, double radius, float lifetimeOpacity) {
        // Start only inside the sphere, reach full strength two blocks beyond its edge.
        double depth = Math.max(0, Math.min(1, (radius - distance) / 2.0));
        return (float)(depth * depth * (3 - 2 * depth)) * lifetimeOpacity;
    }

    public void tick(float target) {
        previous = current;
        current += Math.max(-1f / 30, Math.min(1f / 30, target - current));
        current = Math.max(0, Math.min(1, current));
    }

    public float value(float partial) { return previous + (current - previous) * partial; }
    public void reset() { previous = current = 0; }
}
