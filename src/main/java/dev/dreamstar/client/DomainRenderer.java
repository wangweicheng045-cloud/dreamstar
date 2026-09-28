package dev.dreamstar.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.dreamstar.Dreamstar;
import dev.dreamstar.domain.DomainEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Separate sky and terrain passes. Both preserve depth so later terrain/entities occlude them. */
@Mod.EventBusSubscriber(modid = Dreamstar.ID, value = Dist.CLIENT)
public final class DomainRenderer {
    static ShaderInstance shader;
    private static TextureTarget depthCopy;
    private static final DustParticleOptions ICE_DUST = new DustParticleOptions(new Vector3f(.38f, .7f, 1f), .65f);
    private static final int MAX_VISIBLE_DOMAINS = 8;
    private static final int DOMAIN_AMBIENT_SAMPLES = 8;
    private static final int LOCAL_WHITE_SPARKS = 2;
    private static final SkyTransition SKY = new SkyTransition();
    private static ClientLevel trackedLevel;
    private static Vec3 skyCenter = Vec3.ZERO;
    private static long skyStart;

    private static void resetSky() {
        SKY.reset();
        trackedLevel = null;
        skyCenter = Vec3.ZERO;
        skyStart = 0;
    }

    private static List<DomainEntity> nearbyDomains() {
        Minecraft mc = Minecraft.getInstance();
        List<DomainEntity> result = new ArrayList<>();
        if (mc.level == null) return result;
        var camera = mc.gameRenderer.getMainCamera().getPosition();
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof DomainEntity domain && !domain.isRemoved()
                    && domain.position().distanceToSqr(camera) < Math.pow(domain.radius() + 96, 2)) result.add(domain);
        }
        result.sort(Comparator.comparingDouble(domain -> domain.position().distanceToSqr(camera)));
        return result.size() > MAX_VISIBLE_DOMAINS ? result.subList(0, MAX_VISIBLE_DOMAINS) : result;
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            var cloudDomains = nearbyDomains();
            BoundaryCloudRenderer.render(event, cloudDomains);
            StarShardVfxRenderer.render(event);
            FootstepRipples.render(event);
            return;
        }
        boolean skyPass = event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY;
        if ((!skyPass && event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) || shader == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (trackedLevel != mc.level) resetSky();
        var domains = skyPass ? List.<DomainEntity>of() : nearbyDomains();
        float skyOpacity = SKY.value(event.getPartialTick());
        if (skyPass ? skyOpacity <= .001f : domains.isEmpty()) return;
        var target = mc.getMainRenderTarget();
        if (target.width <= 0 || target.height <= 0) return;
        if (depthCopy == null) depthCopy = new TextureTarget(target.width, target.height, true, Minecraft.ON_OSX);
        if (target.isStencilEnabled() && !depthCopy.isStencilEnabled()) depthCopy.enableStencil();
        if (depthCopy.width != target.width || depthCopy.height != target.height)
            depthCopy.resize(target.width, target.height, Minecraft.ON_OSX);
        // Sampling the main depth attachment while writing to that framebuffer is undefined.
        // Keep a separate depth snapshot, and retain the original terrain depth for later entities.
        if (!skyPass) depthCopy.copyDepthFrom(target);
        target.bindWrite(true);

        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean writeDepth = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int srcRGB = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRGB = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        ShaderInstance previous = RenderSystem.getShader();
        try {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(() -> shader);
            shader.setSampler("DepthSampler", depthCopy.getDepthTextureId());
            shader.safeGetUniform("ScreenSize").set((float) target.width, (float) target.height);
            shader.safeGetUniform("InverseProjection").set(new Matrix4f(event.getProjectionMatrix()).invert());
            shader.safeGetUniform("InverseView").set(new Matrix4f(event.getPoseStack().last().pose()).invert());
            shader.safeGetUniform("SkyPass").set(skyPass ? 1f : 0f);
            var camera = event.getCamera().getPosition();
            if (skyPass) {
                draw(camera.subtract(skyCenter), Dreamstar.DOMAIN_RADIUS, skyOpacity,
                        (mc.level.getGameTime() - skyStart + event.getPartialTick()) / 20f);
            }
            for (var domain : domains) {
                float opacity = domain.opacity(event.getPartialTick());
                if (opacity <= 0) continue;
                var relative = camera.subtract(domain.position());
                draw(relative, domain.radius(), opacity,
                        (mc.level.getGameTime() - domain.startTick() + event.getPartialTick()) / 20f);
            }
        } finally {
            RenderSystem.setShader(() -> previous);
            RenderSystem.depthMask(writeDepth);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        }
    }

    private static void draw(Vec3 relative, float radius, float opacity, float time) {
        shader.safeGetUniform("CameraRelative").set((float) relative.x, (float) relative.y, (float) relative.z);
        shader.safeGetUniform("Radius").set(radius);
        shader.safeGetUniform("Opacity").set(opacity);
        shader.safeGetUniform("Time").set(time);
        var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.vertex(-1, -1, 0).endVertex();
        buffer.vertex(1, -1, 0).endVertex();
        buffer.vertex(1, 1, 0).endVertex();
        buffer.vertex(-1, 1, 0).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }

    @SubscribeEvent public static void particles(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END) return;
        if (mc.level == null || mc.player == null) {
            resetSky();
            FootstepRipples.clear();
            return;
        }
        if (trackedLevel != mc.level) {
            resetSky();
            FootstepRipples.clear();
            trackedLevel = mc.level;
        }
        if (mc.isPaused()) return;
        var domains = nearbyDomains();
        FootstepRipples.tick(mc.level, domains);
        float skyTarget = 0;
        for (var domain : domains) {
            float target = SkyTransition.target(mc.player.position().distanceTo(domain.position()), domain.radius(), domain.opacity(0));
            if (target > skyTarget) {
                skyTarget = target;
                skyCenter = domain.position();
                skyStart = domain.startTick();
            }
        }
        // Keep the last center while fading out, even after the anchor stops tracking/expires.
        SKY.tick(skyTarget);
        for (var domain : domains) {
            if (domain.opacity(0) <= .05f) continue;
            BoundaryParticles.tick(mc.level, domain);
            var random = mc.level.random;
            for (int i = 0; i < DOMAIN_AMBIENT_SAMPLES; i++) {
                double x = (random.nextDouble() * 2 - 1) * domain.radius();
                double y = (random.nextDouble() * 2 - 1) * domain.radius();
                double z = (random.nextDouble() * 2 - 1) * domain.radius();
                if (x*x + y*y + z*z > domain.radius()*domain.radius()) continue;
                var point = domain.position().add(x, y, z);
                BlockPos pointPos = BlockPos.containing(point);
                if (!mc.level.hasChunkAt(pointPos) || !mc.level.getBlockState(pointPos).isAir()) continue;

                // More white flashes than before: roughly three END_ROD particles per tick per domain.
                mc.level.addParticle(i % 3 == 0 ? ParticleTypes.END_ROD : ICE_DUST,
                        point.x, point.y, point.z, 0, .006, 0);
            }

            // The domain is huge, so purely uniform random particles can all appear far away.
            // When the local player is actually inside, guarantee a few nearby white flashes so
            // the ambience is continuously readable in normal gameplay.
            if (mc.player.position().distanceToSqr(domain.position())
                    <= domain.radius() * domain.radius()) {
                for (int spark = 0; spark < LOCAL_WHITE_SPARKS; spark++) {
                    for (int attempt = 0; attempt < 6; attempt++) {
                        double x = (random.nextDouble() * 2 - 1) * 8.0;
                        double y = (random.nextDouble() * 2 - 1) * 3.5 + 1.0;
                        double z = (random.nextDouble() * 2 - 1) * 8.0;
                        var point = mc.player.position().add(x, y, z);
                        if (point.distanceToSqr(domain.position())
                                > domain.radius() * domain.radius()) continue;

                        BlockPos pointPos = BlockPos.containing(point);
                        if (!mc.level.hasChunkAt(pointPos)
                                || !mc.level.getBlockState(pointPos).isAir()) continue;

                        mc.level.addParticle(ParticleTypes.END_ROD,
                                point.x, point.y, point.z,
                                0, .004 + random.nextDouble() * .004, 0);
                        break;
                    }
                }
            }
        }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetSky();
        FootstepRipples.clear();
        if (depthCopy != null) {
            depthCopy.destroyBuffers();
            depthCopy = null;
        }
    }
}
