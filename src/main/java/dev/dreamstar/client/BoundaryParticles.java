package dev.dreamstar.client;

import dev.dreamstar.Dreamstar;
import dev.dreamstar.domain.DomainEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

final class BoundaryParticles {
    private static final Direction[] FACES = Direction.values();
    // Radius 40 has 4x the shell area of radius 20. Keep the boundary visibly traced
    // without scanning chunks or forcing chunk loads.
    private static final int MAX_ATTEMPTS = 768;
    private static final int MAX_PARTICLES_PER_TICK = 32;
    private BoundaryParticles() {}

    static void tick(ClientLevel level, DomainEntity domain) {
        var random = level.random;
        int emitted = 0;
        // Bounded rejection sampling of the thin spherical shell; no chunk-wide scan or forced loading.
        for (int attempt = 0; attempt < MAX_ATTEMPTS && emitted < MAX_PARTICLES_PER_TICK; attempt++) {
            double y = random.nextDouble() * 2 - 1;
            double angle = random.nextDouble() * Math.PI * 2;
            double horizontal = Math.sqrt(1 - y * y);
            double radius = domain.radius() + (random.nextDouble() - .5) * 1.8;
            var candidate = domain.position().add(horizontal * Math.cos(angle) * radius,
                    y * radius, horizontal * Math.sin(angle) * radius);
            BlockPos pos = BlockPos.containing(candidate);
            if (!level.hasChunkAt(pos)) continue;
            var state = level.getBlockState(pos);
            if (state.isAir()) continue;
            var shape = state.getShape(level, pos);
            if (shape.isEmpty()) continue;
            // Prefer top surfaces for the ground ring, but also sample walls/ceilings on spherical edges.
            Direction face = random.nextBoolean() ? Direction.UP : FACES[random.nextInt(FACES.length)];
            double x = .1 + random.nextDouble() * .8;
            double v = .1 + random.nextDouble() * .8;
            double z = .1 + random.nextDouble() * .8;
            switch (face.getAxis()) {
                case X -> x = face.getStepX() > 0 ? 1.05 : -.05;
                case Y -> v = face.getStepY() > 0 ? 1.05 : -.05;
                case Z -> z = face.getStepZ() > 0 ? 1.05 : -.05;
            }
            Vec3 from = Vec3.atLowerCornerOf(pos).add(x, v, z);
            BlockPos outside = BlockPos.containing(from);
            if (!level.hasChunkAt(outside) || level.getBlockState(outside).canOcclude()) continue;
            Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
            var hit = shape.clip(from, from.subtract(normal.scale(1.1)), pos);
            if (hit == null || Math.abs(hit.getLocation().distanceTo(domain.position()) - domain.radius()) > 1.0) continue;
            var actualNormal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
            var point = hit.getLocation().add(actualNormal.scale(.035));
            level.addParticle(Dreamstar.BOUNDARY_STAR.get(), point.x, point.y, point.z,
                    actualNormal.x * .004, .006 + actualNormal.y * .004, actualNormal.z * .004);
            emitted++;

            // Add a nearby second sparkle on many valid block/sphere intersections.
            // The offset stays tangent to the hit face, so it reads as a denser boundary
            // rather than particles floating away from the shell.
            if (emitted < MAX_PARTICLES_PER_TICK && random.nextFloat() < .65f) {
                double j1 = (random.nextDouble() - .5) * .24;
                double j2 = (random.nextDouble() - .5) * .24;
                Vec3 tangent = switch (hit.getDirection().getAxis()) {
                    case X -> new Vec3(0, j1, j2);
                    case Y -> new Vec3(j1, 0, j2);
                    case Z -> new Vec3(j1, j2, 0);
                };
                var extra = point.add(tangent);
                level.addParticle(Dreamstar.BOUNDARY_STAR.get(), extra.x, extra.y, extra.z,
                        actualNormal.x * .003, .004 + actualNormal.y * .003, actualNormal.z * .003);
                emitted++;
            }
        }
    }
}
