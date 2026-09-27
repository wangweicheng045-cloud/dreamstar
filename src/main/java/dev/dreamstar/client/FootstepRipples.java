package dev.dreamstar.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.dreamstar.Dreamstar;
import dev.dreamstar.domain.DomainEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Small white ground ripples for living entities walking inside an active Dream Star Domain.
 *
 * The effect is deliberately geometry-only: no texture is required. Each ripple remains at the
 * footstep position, expands to just under one block in diameter, then fades away.
 */
@Mod.EventBusSubscriber(modid = Dreamstar.ID, value = Dist.CLIENT)
public final class FootstepRipples {
    private static final int LIFETIME = 14;
    private static final int MIN_SPAWN_INTERVAL = 4;
    private static final int MAX_RIPPLES = 192;
    private static final int SEGMENTS = 36;

    private static final double MOVE_STEP = 0.22;
    private static final float START_RADIUS = 0.08f;
    private static final float MAX_RADIUS = 0.49f;

    private static final List<Ripple> RIPPLES = new ArrayList<>();
    private static final Map<Integer, MoveState> MOVE_STATES = new HashMap<>();
    private static ClientLevel trackedLevel;

    private FootstepRipples() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            clear();
            return;
        }

        ClientLevel level = mc.level;
        if (trackedLevel != level) {
            clear();
            trackedLevel = level;
        }
        if (mc.isPaused()) return;

        tickRipples();
        tickWalkers(level);
        cleanupMoveStates(level.getGameTime());
    }

    private static void tickRipples() {
        Iterator<Ripple> iterator = RIPPLES.iterator();
        while (iterator.hasNext()) {
            Ripple ripple = iterator.next();
            if (++ripple.age >= LIFETIME) iterator.remove();
        }
    }

    private static void tickWalkers(ClientLevel level) {
        List<DomainEntity> domains = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof DomainEntity domain
                    && !domain.isRemoved()
                    && domain.opacity(0) > 0.05f) {
                domains.add(domain);
            }
        }
        if (domains.isEmpty()) return;

        long gameTime = level.getGameTime();

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)
                    || !living.isAlive()
                    || living.isSpectator()) {
                continue;
            }

            if (!insideAnyDomain(living.position(), domains)) continue;

            MoveState state = MOVE_STATES.computeIfAbsent(
                    living.getId(),
                    ignored -> new MoveState(living.getX(), living.getZ(), gameTime)
            );
            state.lastSeenTick = gameTime;

            // No ripple while airborne. Reset the distance anchor so landing after a jump does not
            // create a false long-distance footstep.
            if (!living.onGround()) {
                state.lastRippleX = living.getX();
                state.lastRippleZ = living.getZ();
                continue;
            }

            double dx = living.getX() - state.lastRippleX;
            double dz = living.getZ() - state.lastRippleZ;
            boolean movedEnough = dx * dx + dz * dz >= MOVE_STEP * MOVE_STEP;
            boolean cooldownReady = gameTime - state.lastSpawnTick >= MIN_SPAWN_INTERVAL;

            if (movedEnough && cooldownReady) {
                spawn(living.getX(), living.getY() + 0.018, living.getZ());
                state.lastRippleX = living.getX();
                state.lastRippleZ = living.getZ();
                state.lastSpawnTick = gameTime;
            }
        }
    }

    private static boolean insideAnyDomain(Vec3 position, List<DomainEntity> domains) {
        for (DomainEntity domain : domains) {
            double radius = domain.radius();
            if (position.distanceToSqr(domain.position()) <= radius * radius) return true;
        }
        return false;
    }

    private static void cleanupMoveStates(long gameTime) {
        MOVE_STATES.entrySet().removeIf(entry -> gameTime - entry.getValue().lastSeenTick > 20);
    }

    private static void spawn(double x, double y, double z) {
        if (RIPPLES.size() >= MAX_RIPPLES) RIPPLES.remove(0);
        RIPPLES.add(new Ripple(x, y, z));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || RIPPLES.isEmpty()) return;

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();

        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean writeDepth = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int srcRGB = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRGB = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            float partialTick = event.getPartialTick();
            for (Ripple ripple : RIPPLES) {
                float t = Mth.clamp((ripple.age + partialTick) / (float) LIFETIME, 0f, 1f);

                // Fast initial expansion that eases out near the one-block limit.
                float expand = 1f - (float) Math.pow(1f - t, 3);
                float radius = Mth.lerp(expand, START_RADIUS, MAX_RADIUS);

                // Tiny fade-in removes popping; quadratic fade-out keeps the trail soft.
                float fadeIn = Mth.clamp(t / 0.08f, 0f, 1f);
                float fadeOut = 1f - t;
                float alpha = 0.82f * fadeIn * fadeOut * fadeOut;

                drawRipple(
                        ripple.x - cameraPos.x,
                        ripple.y - cameraPos.y,
                        ripple.z - cameraPos.z,
                        radius,
                        alpha
                );
            }
        } finally {
            RenderSystem.depthMask(writeDepth);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        }
    }

    private static void drawRipple(double x, double y, double z, float radius, float alpha) {
        float width = Mth.lerp(radius / MAX_RADIUS, 0.010f, 0.024f);

        // A faint halo plus a crisp white core gives a watery ripple without requiring a texture.
        drawRing(x, y, z, radius - width * 1.8f, radius + width * 1.8f, alpha * 0.14f);
        drawRing(x, y + 0.0007, z, radius - width * 0.75f, radius + width * 0.75f, alpha * 0.44f);
        drawRing(x, y + 0.0014, z, radius - width * 0.25f, radius + width * 0.25f, alpha);
    }

    private static void drawRing(
            double centerX,
            double y,
            double centerZ,
            float innerRadius,
            float outerRadius,
            float alpha
    ) {
        if (outerRadius <= 0f || alpha <= 0.001f) return;

        innerRadius = Math.max(0f, innerRadius);
        int a = Mth.clamp((int) (alpha * 255f), 0, 255);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

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

        BufferUploader.drawWithShader(buffer.end());
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void clear() {
        RIPPLES.clear();
        MOVE_STATES.clear();
        trackedLevel = null;
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

        MoveState(double x, double z, long gameTime) {
            lastRippleX = x;
            lastRippleZ = z;
            lastSpawnTick = gameTime;
            lastSeenTick = gameTime;
        }
    }
}
