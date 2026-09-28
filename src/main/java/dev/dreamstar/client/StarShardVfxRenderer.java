package dev.dreamstar.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.dreamstar.Dreamstar;
import dev.dreamstar.shard.StarShardAttackEntity;
import dev.dreamstar.shard.StarShardPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.util.List;

/** Renders the uploaded Blockbench shard mesh and its three white attack beams. */
final class StarShardVfxRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Dreamstar.ID, "textures/effect/star_shard.png");
    private static final float MODEL_SCALE = 0.78F;
    private static final float MODEL_THICKNESS = 0.046875F;

    // Converted directly from the front faces of 星空碎片.bbmodel.
    private static final Part[] PARTS = {
            new Part(new V[]{
                    new V(0.062500f, 0.781250f, -0.023438f, 0.218750f, 0.015625f),
                    new V(0.343750f, 0.218750f, -0.023438f, 0.359375f, 0.296875f),
                    new V(-0.093750f, -0.781250f, -0.023438f, 0.140625f, 0.796875f),
                    new V(-0.343750f, 0.000000f, -0.023438f, 0.015625f, 0.406250f)
            }),
            new Part(new V[]{
                    new V(-0.271875f, 0.625000f, -0.018437f, 0.465625f, 0.015625f),
                    new V(-0.171875f, 0.531250f, -0.018437f, 0.515625f, 0.062500f),
                    new V(-0.237500f, 0.356250f, -0.018437f, 0.482812f, 0.150000f),
                    new V(-0.328125f, 0.465625f, -0.018437f, 0.437500f, 0.095312f)
            }),
            new Part(new V[]{
                    new V(0.331250f, 0.706250f, -0.023438f, 0.679688f, 0.015625f),
                    new V(0.393750f, 0.615625f, -0.023438f, 0.710938f, 0.060937f),
                    new V(0.256250f, 0.528125f, -0.023438f, 0.642188f, 0.104688f),
                    new V(0.190625f, 0.643750f, -0.023438f, 0.609375f, 0.046875f)
            }),
            new Part(new V[]{
                    new V(-0.387500f, -0.087500f, -0.018437f, 0.468750f, 0.250000f),
                    new V(-0.343750f, -0.178125f, -0.018437f, 0.490625f, 0.295312f),
                    new V(-0.406250f, -0.362500f, -0.018437f, 0.459375f, 0.387500f),
                    new V(-0.450000f, -0.231250f, -0.018437f, 0.437500f, 0.321875f)
            }),
            new Part(new V[]{
                    new V(0.234375f, -0.309375f, -0.026562f, 0.642188f, 0.250000f),
                    new V(0.281250f, -0.356250f, -0.026562f, 0.665625f, 0.273438f),
                    new V(0.193750f, -0.443750f, -0.026562f, 0.621875f, 0.317188f),
                    new V(0.168750f, -0.384375f, -0.026562f, 0.609375f, 0.287500f)
            }),
            new Part(new V[]{
                    new V(-0.394792f, 0.671875f, -0.026562f, 0.642188f, 0.250000f),
                    new V(-0.368750f, 0.638081f, -0.026562f, 0.665625f, 0.273438f),
                    new V(-0.417361f, 0.575000f, -0.026562f, 0.621875f, 0.317188f),
                    new V(-0.431250f, 0.617805f, -0.026562f, 0.609375f, 0.287500f)
            })
    };

    private StarShardVfxRenderer() {}

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<StarShardAttackEntity> sequences = new ArrayList<>();
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof StarShardAttackEntity sequence && !sequence.isRemoved()) {
                sequences.add(sequence);
            }
        }
        if (sequences.isEmpty()) return;

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

        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            poseStack.pushPose();
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            Matrix4f pose = poseStack.last().pose();

            renderShards(sequences, partialTick, pose);
            renderBeams(sequences, partialTick, pose);

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

    private static void renderShards(List<StarShardAttackEntity> sequences, float partialTick, Matrix4f pose) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEXTURE);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int vertices = 0;

        for (StarShardAttackEntity sequence : sequences) {
            LivingEntity target = sequence.target();
            if (target == null) continue;
            Vec3 aim = StarShardPlacement.aimPoint(target);

            for (int i = 0; i < 3; i++) {
                float alpha = sequence.shardAlpha(i, partialTick);
                if (alpha <= 0.001F) continue;
                Vec3 position = StarShardPlacement.shardPosition(target, sequence.seed(), i);
                appendModel(buffer, pose, position, aim,
                        StarShardPlacement.rollRadians(sequence.seed(), i), alpha);
                vertices += PARTS.length * 8;
            }
        }

        if (vertices > 0) BufferUploader.drawWithShader(buffer.end());
        else buffer.end().release();
    }

    private static void renderBeams(List<StarShardAttackEntity> sequences, float partialTick, Matrix4f pose) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int vertices = 0;

        for (StarShardAttackEntity sequence : sequences) {
            float alpha = sequence.beamAlpha(partialTick);
            if (alpha <= 0.001F) continue;
            LivingEntity target = sequence.target();
            if (target == null) continue;

            Vec3 to = StarShardPlacement.aimPoint(target);
            for (int i = 0; i < 3; i++) {
                Vec3 from = StarShardPlacement.shardPosition(target, sequence.seed(), i);
                appendBeam(buffer, pose, from, to, 0.070F, alpha * 0.25F);
                appendBeam(buffer, pose, from, to, 0.025F, alpha);
                vertices += 16;
            }
        }

        if (vertices > 0) BufferUploader.drawWithShader(buffer.end());
        else buffer.end().release();
    }

    private static void appendModel(
            BufferBuilder buffer,
            Matrix4f pose,
            Vec3 position,
            Vec3 target,
            float roll,
            float alpha
    ) {
        Vec3 normal = target.subtract(position);
        if (normal.lengthSqr() < 1.0E-6D) normal = new Vec3(0, 0, 1);
        else normal = normal.normalize();

        Vec3 referenceUp = Math.abs(normal.y) > 0.96D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = referenceUp.cross(normal).normalize();
        Vec3 up = normal.cross(right).normalize();

        double cos = Math.cos(roll);
        double sin = Math.sin(roll);
        Vec3 rolledRight = right.scale(cos).add(up.scale(sin));
        Vec3 rolledUp = up.scale(cos).subtract(right.scale(sin));

        int a = Math.max(0, Math.min(255, (int) (alpha * 255.0F)));
        for (Part part : PARTS) {
            V[] v = part.vertices;
            for (int i = 0; i < 4; i++) {
                appendModelVertex(buffer, pose, position, rolledRight, rolledUp, normal, v[i], 0.0F, a);
            }
            for (int i = 3; i >= 0; i--) {
                appendModelVertex(buffer, pose, position, rolledRight, rolledUp, normal, v[i], MODEL_THICKNESS, a);
            }
        }
    }

    private static void appendModelVertex(
            BufferBuilder buffer,
            Matrix4f pose,
            Vec3 center,
            Vec3 right,
            Vec3 up,
            Vec3 normal,
            V vertex,
            float zOffset,
            int alpha
    ) {
        double lx = vertex.x * MODEL_SCALE;
        double ly = vertex.y * MODEL_SCALE;
        double lz = (vertex.z + zOffset) * MODEL_SCALE;
        Vec3 world = center
                .add(right.scale(lx))
                .add(up.scale(ly))
                .add(normal.scale(lz));

        buffer.vertex(pose, (float) world.x, (float) world.y, (float) world.z)
                .uv(vertex.u, vertex.v)
                .color(255, 255, 255, alpha)
                .endVertex();
    }

    private static void appendBeam(
            BufferBuilder buffer,
            Matrix4f pose,
            Vec3 from,
            Vec3 to,
            float width,
            float alpha
    ) {
        Vec3 direction = to.subtract(from);
        if (direction.lengthSqr() < 1.0E-6D) return;
        direction = direction.normalize();

        Vec3 axis = Math.abs(direction.y) > 0.95D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 sideA = direction.cross(axis).normalize().scale(width);
        Vec3 sideB = direction.cross(sideA).normalize().scale(width);
        int a = Math.max(0, Math.min(255, (int) (alpha * 255.0F)));

        appendBeamRibbon(buffer, pose, from, to, sideA, a);
        appendBeamRibbon(buffer, pose, from, to, sideB, a);
    }

    private static void appendBeamRibbon(
            BufferBuilder buffer,
            Matrix4f pose,
            Vec3 from,
            Vec3 to,
            Vec3 side,
            int alpha
    ) {
        beamVertex(buffer, pose, from.add(side), alpha);
        beamVertex(buffer, pose, from.subtract(side), alpha);
        beamVertex(buffer, pose, to.subtract(side), alpha);
        beamVertex(buffer, pose, to.add(side), alpha);
    }

    private static void beamVertex(BufferBuilder buffer, Matrix4f pose, Vec3 p, int alpha) {
        buffer.vertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .color(255, 255, 255, alpha)
                .endVertex();
    }

    private record V(float x, float y, float z, float u, float v) {}
    private record Part(V[] vertices) {}
}