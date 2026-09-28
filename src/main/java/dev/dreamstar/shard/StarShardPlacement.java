package dev.dreamstar.shard;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Deterministic shard placement derived from the attacked entity's current bounds. */
public final class StarShardPlacement {
    private StarShardPlacement() {}

    public static Vec3 aimPoint(LivingEntity target) {
        AABB box = target.getBoundingBox();
        return new Vec3(
                (box.minX + box.maxX) * 0.5D,
                box.minY + box.getYsize() * 0.68D,
                (box.minZ + box.maxZ) * 0.5D
        );
    }

    public static Vec3 shardPosition(LivingEntity target, int seed, int index) {
        AABB box = target.getBoundingBox();
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        double width = Math.max(0.25D, Math.max(box.getXsize(), box.getZsize()));
        double height = Math.max(0.25D, box.getYsize());

        /*
         * The shard must sit diagonally above the attacked model, not merely somewhere in its
         * upper half. Start at the real bounding-box surface, then add a size-aware sideways
         * and upward gap. The extra 3D distance from the model surface stays below two blocks.
         */
        double surfaceRadius = width * 0.5D;

        double sideGap = 0.48D
                + hash01(seed ^ (index * 0x45d9f3b)) * 0.92D;

        double upGap = 0.34D
                + hash01(seed ^ (index * 0x27d4eb2d) ^ 0x510e527f)
                * Math.min(0.86D, 0.28D + height * 0.18D);

        // Keep the actual outside-the-model offset comfortably below the requested two blocks.
        double gapLength = Math.sqrt(sideGap * sideGap + upGap * upGap);
        if (gapLength > 1.82D) {
            double scale = 1.82D / gapLength;
            sideGap *= scale;
            upGap *= scale;
        }

        double radialDistance = surfaceRadius + sideGap;

        double baseAngle = hash01(seed ^ 0x6a09e667) * Math.PI * 2.0D;
        double jitter = (hash01(seed ^ (index * 0x9e3779b9) ^ 0x3c6ef372) - 0.5D) * 0.46D;
        double angle = baseAngle + index * (Math.PI * 2.0D / 3.0D) + jitter;

        // Every shard is above the target's actual top, giving an unmistakable diagonal-up layout.
        double y = box.maxY + upGap;

        return new Vec3(
                centerX + Math.cos(angle) * radialDistance,
                y,
                centerZ + Math.sin(angle) * radialDistance
        );
    }

    public static float rollRadians(int seed, int index) {
        return (float) ((hash01(seed ^ (index * 0x165667b1) ^ 0xbb67ae85) - 0.5D) * 0.62D);
    }

    private static double hash01(int input) {
        int x = input;
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return (x & 0x7fffffff) / (double) Integer.MAX_VALUE;
    }
}