package dev.dreamstar.thunder.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.dreamstar.thunder.ThunderSlashEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class ThunderSlashRenderer extends EntityRenderer<ThunderSlashEntity> {
    private static final ResourceLocation TARGET_TEXTURE = new ResourceLocation("dreamstar", "textures/effect/thunder_phantom_blade.png");
    private static final ResourceLocation OPENING_TEXTURE = new ResourceLocation("dreamstar", "textures/effect/thunder_cast_slash.png");

    public ThunderSlashRenderer(EntityRendererProvider.Context c){ super(c); }

    @Override
    public ResourceLocation getTextureLocation(ThunderSlashEntity e){
        return e.mode() == ThunderSlashEntity.MODE_OPENING_SLASH ? OPENING_TEXTURE : TARGET_TEXTURE;
    }

    @Override
    public void render(ThunderSlashEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight){
        long age = e.animationAge();
        if(age < 0L || age >= e.animationDuration()) return;

        int frames = e.animationFrames();
        int frame = e.mode() == ThunderSlashEntity.MODE_OPENING_SLASH
                ? e.openingFrame(partialTick)
                : Math.min(frames - 1, Math.max(0, (int)(age / 2L)));
        float v0 = frame / (float)frames;
        float v1 = (frame + 1) / (float)frames;
        float scale = e.visualScale();
        ResourceLocation texture = getTextureLocation(e);

        pose.pushPose();
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.scale(scale, scale, scale);

        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(texture));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        vertex(vc,m,n,-.5F,-.5F,0.0F,v1);
        vertex(vc,m,n,.5F,-.5F,1.0F,v1);
        vertex(vc,m,n,.5F,.5F,1.0F,v0);
        vertex(vc,m,n,-.5F,.5F,0.0F,v0);
        pose.popPose();

        super.render(e,yaw,partialTick,pose,buffers,LightTexture.FULL_BRIGHT);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float v){
        vc.vertex(m,x,y,0.0F).color(255,255,255,255).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n,0,0,1).endVertex();
    }
}
