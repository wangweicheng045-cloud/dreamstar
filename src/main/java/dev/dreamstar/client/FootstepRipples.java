package dev.dreamstar.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.dreamstar.domain.DomainEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * White, flat water-like ripples under moving living entities inside Dream Star domains.
 *
 * This class is driven by DomainRenderer instead of subscribing independently to Forge events.
 * That keeps it on the same known-good client render/tick path as the domain itself.
 */
final class FootstepRipples {
    private static final int LIFETIME = 14;
    // 5, 5, 6 tick cadence averages 5.33 ticks, i.e. about 75% of the old 4-tick frequency.
    private static final int[] SPAWN_INTERVALS = {5, 5, 6};
    private static final int MAX_RIPPLES = 192;
    private static final int SEGMENTS = 40;

    private static final double MOVE_STEP = 0.20;
    private static final float START_RADIUS = 0.07f;
    private static final float MAX_RADIUS = 0.49f;

    private static final List<Ripple> RIPPLES = new ArrayList<>();
    private static final Map<Integer, MoveState> MOVE_STATES = new HashMap<>();
    private static final VertexBuffer VERTEX_BUFFER = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);

    private FootstepRipples() {}

    static void tick(ClientLevel level, List<DomainEntity> domains) {
        tickRipples();

        long gameTime = level.getGameTime();
        Set<Integer> processed = new HashSet<>();

        for (DomainEntity domain : domains) {
            if (domain.isRemoved() || domain.opacity(0) <= 0.05f) continue;

            Vec3 center = domain.position();
            double radius = domain.radius();
            AABB bounds = new AABB(
                    center.x - radius, center.y - radius, center.z - radius,
                    center.x + radius, center.y + radius, center.z + radius
            );

            for (LivingEntity living : level.getEntitiesOfClass(
                    LivingEntity.class,
                    bounds,
                    entity -> entity.isAlive() && !entity.isSpectator()
            )) {
                if (!processed.add(living.getId())) continue;
                if (living.position().distanceToSqr(center) > radius * radius) continue;

                MoveState state = MOVE_STATES.computeIfAbsent(
                        living.getId(),
                        ignored -> new MoveState(living.getX(), living.getZ(), gameTime)
                );
                state.lastSeenTick = gameTime;

                if (!living.onGround()) {
                    state.lastRippleX = living.getX();
                    state.lastRippleZ = living.getZ();
                    continue;
                }

                double dx = living.getX() - state.lastRippleX;
                double dz = living.getZ() - state.lastRippleZ;
                boolean movedEnough = dx * dx + dz * dz >= MOVE_STEP * MOVE_STEP;
                boolean cooldownReady = gameTime - state.lastSpawnTick >= state.currentInterval();

                if (movedEnough && cooldownReady) {
                    // Feet are at entity Y. Lift a few centimetres to avoid z-fighting with blocks.
                    spawn(living.getX(), living.getY() + 0.035, living.getZ());
                    state.lastRippleX = living.getX();
                    state.lastRippleZ = living.getZ();
                    state.lastSpawnTick = gameTime;
                    state.advanceInterval();
                }
            }
        }

        MOVE_STATES.entrySet().removeIf(entry -> gameTime - entry.getValue().lastSeenTick > 20);
    }

    private static void tickRipples() {
        Iterator<Ripple> iterator = RIPPLES.iterator();
        while (iterator.hasNext()) {
            Ripple ripple = iterator.next();
            if (++ripple.age >= LIFETIME) iterator.remove();
        }
    }

    private static void spawn(double x, double y, double z) {
        if (RIPPLES.size() >= MAX_RIPPLES) RIPPLES.remove(0);
        RIPPLES.add(new Ripple(x, y, z));
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || RIPPLES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        float partialTick = event.getPartialTick();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        int quads = 0;
        for (Ripple ripple : RIPPLES) {
            float t = Mth.clamp((ripple.age + partialTick) / (float) LIFETIME, 0f, 1f);
            float expand = 1f - (float) Math.pow(1f - t, 3);
            float radius = Mth.lerp(expand, START_RADIUS, MAX_RADIUS);

            float fadeIn = Mth.clamp(t / 0.08f, 0f, 1f);
            float fadeOut = 1f - t;
            float alpha = fadeIn * fadeOut * fadeOut;

            // Slightly thicker than before so the ripple reads clearly against the star field.
            float width = Mth.lerp(radius / MAX_RADIUS, 0.016f, 0.039f);

            // Soft outside glow.
            quads += appendRing(buffer, ripple.x, ripple.y, ripple.z,
                    radius - width * 1.65f, radius + width * 1.65f, alpha * 0.18f);

            // Mid glow.
            quads += appendRing(buffer, ripple.x, ripple.y + 0.0008, ripple.z,
                    radius - width * 0.80f, radius + width * 0.80f, alpha * 0.58f);

            // Bright white core.
            quads += appendRing(buffer, ripple.x, ripple.y + 0.0016, ripple.z,
                    radius - width * 0.30f, radius + width * 0.30f, alpha);
        }

        if (quads == 0) {
            buffer.end().release();
            return;
        }

        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean writeDepth = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int srcRGB = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRGB = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

        PoseStack poseStack = event.getPoseStack();
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            // Upload world-space vertices, then use the render event's view + projection matrices.
            // This is the important difference from the old invisible implementation.
            VERTEX_BUFFER.bind();
            VERTEX_BUFFER.upload(buffer.end());

            poseStack.pushPose();
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            VERTEX_BUFFER.drawWithShader(
                    poseStack.last().pose(),
                    event.getProjectionMatrix(),
                    GameRenderer.getPositionColorShader()
            );
            poseStack.popPose();
            VertexBuffer.unbind();
        } finally {
            RenderSystem.depthMask(writeDepth);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        }
    }

    private static int appendRing(
            BufferBuilder buffer,
            double centerX,
            double y,
            double centerZ,
            float innerRadius,
            float outerRadius,
            float alpha
    ) {
        if (outerRadius <= 0f || alpha <= 0.001f) return 0;

        innerRadius = Math.max(0f, innerRadius);
        int a = Mth.clamp((int) (alpha * 255f), 0, 255);

        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2.0 * i / SEGMENTS;
            double a1 = Math.PI * 2.0 * (i + 1) / SEGMENTS;
            double c0 = Math.cos(a0), s0 = Math.sin(a0);
            double c1 = Math.cos(a1), s1 = Math.sin(a1);

            buffer.vertex(centerX + c0 * outerRadius, y, centerZ + s0 * outerRadius)
                    .color(255, 255, 255, a).endVertex();
            buffer.vertex(centerX + c0 * innerRadius, y, centerZ + s0 * innerRadius)
                    .color(255, 255, 255, a).endVertex();
            buffer.vertex(centerX + c1 * innerRadius, y, centerZ + s1 * innerRadius)
                    .color(255, 255, 255, a).endVertex();
            buffer.vertex(centerX + c1 * outerRadius, y, centerZ + s1 * outerRadius)
                    .color(255, 255, 255, a).endVertex();
        }
        return SEGMENTS;
    }

    static void clear() {
        RIPPLES.clear();
        MOVE_STATES.clear();
    }

    private static final class Ripple {
        final double x;
        final double y;
        final double z;
        int age;

        Ripple(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class MoveState {
        double lastRippleX;
        double lastRippleZ;
        long lastSpawnTick;
        long lastSeenTick;
        int intervalIndex;

        MoveState(double x, double z, long gameTime) {
            lastRippleX = x;
            lastRippleZ = z;
            // Permit a ripple immediately once the entity has actually moved MOVE_STEP.
            lastSpawnTick = gameTime - SPAWN_INTERVALS[0];
            lastSeenTick = gameTime;
        }

        int currentInterval() {
            return SPAWN_INTERVALS[intervalIndex];
        }

        void advanceInterval() {
            intervalIndex = (intervalIndex + 1) % SPAWN_INTERVALS.length;
        }
    }
}
