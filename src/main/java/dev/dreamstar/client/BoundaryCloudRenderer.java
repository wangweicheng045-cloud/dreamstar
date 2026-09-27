package dev.dreamstar.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.dreamstar.Dreamstar;
import dev.dreamstar.domain.DomainEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.List;

/**
 * Layered pixel-cloud shell for the Dream Star boundary.
 *
 * Three translucent sprite layers are distributed over the spherical surface. Their longitude
 * continuously decreases, which is counter-clockwise when the sphere is viewed from above.
 * The texture itself is left-heavy and trails to the right; because the shell moves toward the
 * sprite's left side, the thin right side naturally reads as a dragged cloud tail.
 */
final class BoundaryCloudRenderer {
    private static final ResourceLocation[] TEXTURES = {
            new ResourceLocation(Dreamstar.ID, "textures/effect/domain_cloud_light.png"),
            new ResourceLocation(Dreamstar.ID, "textures/effect/domain_cloud_deep.png"),
            new ResourceLocation(Dreamstar.ID, "textures/effect/domain_cloud_purple.png")
    };

    private static final int LAYERS = 3;
    private static final int PATCHES_PER_LAYER = 60;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    // About one full revolution over a normal 30-second domain.
    private static final double ROTATION_SPEED = 0.19D;

    // Intentionally translucent; overlapping patches build up the visible shell.
    private static final float BASE_ALPHA = 0.23F;

    private BoundaryCloudRenderer() {}

    static void render(RenderLevelStageEvent event, List<DomainEntity> domains) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || domains.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean writeDepth = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int srcRGB = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRGB = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        ShaderInstance previous = RenderSystem.getShader();

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();
        double worldTime = (mc.level.getGameTime() + partialTick) / 20.0D;

        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

            poseStack.pushPose();
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            Matrix4f pose = poseStack.last().pose();

            // Batch by texture to avoid changing texture state for every individual cloud.
            for (int textureIndex = 0; textureIndex < TEXTURES.length; textureIndex++) {
                RenderSystem.setShaderTexture(0, TEXTURES[textureIndex]);

                BufferBuilder buffer = Tesselator.getInstance().getBuilder();
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                int patchCount = 0;

                for (DomainEntity domain : domains) {
                    float domainOpacity = domain.opacity(partialTick);
                    if (domainOpacity <= 0.01F) continue;

                    for (int layer = 0; layer < LAYERS; layer++) {
                        for (int i = 0; i < PATCHES_PER_LAYER; i++) {
                            if ((i + layer * 2) % TEXTURES.length != textureIndex) continue;

                            appendPatch(
                                    buffer,
                                    pose,
                                    domain,
                                    layer,
                                    i,
                                    worldTime,
                                    domainOpacity
                            );
                            patchCount++;
                        }
                    }
                }

                if (patchCount > 0) {
                    BufferUploader.drawWithShader(buffer.end());
                } else {
                    buffer.end().release();
                }
            }

            poseStack.popPose();
        } finally {
            RenderSystem.setShader(() -> previous);
            RenderSystem.depthMask(writeDepth);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        }
    }

    private static void appendPatch(
            BufferBuilder buffer,
            Matrix4f pose,
            DomainEntity domain,
            int layer,
            int index,
            double worldTime,
            float domainOpacity
    ) {
        // Fibonacci sphere keeps the sprites evenly spread instead of forming latitude bands.
        double y = 1.0D - 2.0D * (index + 0.5D) / PATCHES_PER_LAYER;
        double horizontal = Math.sqrt(Math.max(0.0D, 1.0D - y * y));

        double phase = layer * 0.93D;
        double theta = index * GOLDEN_ANGLE + phase - worldTime * ROTATION_SPEED;

        double cos = Math.cos(theta);
        double sin = Math.sin(theta);

        double nx = cos * horizontal;
        double ny = y;
        double nz = sin * horizontal;

        // Three very close shells hide gaps without making the boundary look like one flat wall.
        double shellScale = 0.986D + layer * 0.014D;
        double shellRadius = domain.radius() * shellScale;

        double cx = domain.getX() + nx * shellRadius;
        double cy = domain.getY() + ny * shellRadius;
        double cz = domain.getZ() + nz * shellRadius;

        // Tangent basis on the sphere. "right" follows increasing longitude.
        double rx = -sin;
        double ry = 0.0D;
        double rz = cos;

        // up = right x normal; already unit length.
        double ux = -cos * y;
        double uy = horizontal;
        double uz = -sin * y;

        // A small deterministic roll keeps the shell organic rather than tiled.
        double roll = (hash01(index + layer * 101) - 0.5D) * 0.50D;
        double cr = Math.cos(roll);
        double sr = Math.sin(roll);

        double r2x = rx * cr + ux * sr;
        double r2y = ry * cr + uy * sr;
        double r2z = rz * cr + uz * sr;

        double u2x = -rx * sr + ux * cr;
        double u2y = -ry * sr + uy * cr;
        double u2z = -rz * sr + uz * cr;

        float variation = (float) hash01(index * 17 + layer * 137 + 11);
        double width = domain.radius() * (0.42D + variation * 0.08D);
        double height = width / 3.0D;

        double halfW = width * 0.5D;
        double halfH = height * 0.5D;

        float layerAlpha = switch (layer) {
            case 0 -> 1.00F;
            case 1 -> 0.86F;
            default -> 0.72F;
        };
        int alpha = Math.max(0, Math.min(255, (int) (255.0F * BASE_ALPHA * layerAlpha * domainOpacity)));

        vertex(buffer, pose, cx - r2x * halfW - u2x * halfH, cy - r2y * halfW - u2y * halfH,
                cz - r2z * halfW - u2z * halfH, 0.0F, 1.0F, alpha);
        vertex(buffer, pose, cx + r2x * halfW - u2x * halfH, cy + r2y * halfW - u2y * halfH,
                cz + r2z * halfW - u2z * halfH, 1.0F, 1.0F, alpha);
        vertex(buffer, pose, cx + r2x * halfW + u2x * halfH, cy + r2y * halfW + u2y * halfH,
                cz + r2z * halfW + u2z * halfH, 1.0F, 0.0F, alpha);
        vertex(buffer, pose, cx - r2x * halfW + u2x * halfH, cy - r2y * halfW + u2y * halfH,
                cz - r2z * halfW + u2z * halfH, 0.0F, 0.0F, alpha);
    }

    private static void vertex(
            BufferBuilder buffer,
            Matrix4f pose,
            double x,
            double y,
            double z,
            float u,
            float v,
            int alpha
    ) {
        buffer.vertex(pose, (float) x, (float) y, (float) z)
                .uv(u, v)
                .color(255, 255, 255, alpha)
                .endVertex();
    }

    private static double hash01(int seed) {
        int x = seed;
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return (x & 0x7fffffff) / (double) Integer.MAX_VALUE;
    }
}
