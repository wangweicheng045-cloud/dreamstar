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
import net.minecraft.world.phys.Vec3;
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

        if (e.mode() == ThunderSlashEntity.MODE_OPENING_SLASH) {
            renderHorizontalOpeningSlash(pose, buffers, e, partialTick);
        } else {
            pose.pushPose();
            pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            renderBillboardSheetQuad(pose, buffers, TARGET_TEXTURE, e.visualScale() * 3.0F,
                    e.targetFrame(partialTick), ThunderSlashEntity.TARGET_FRAME_COUNT, 255);
            pose.popPose();
        }

        super.render(e, yaw, partialTick, pose, buffers, LightTexture.FULL_BRIGHT);
    }

    private static void renderHorizontalOpeningSlash(PoseStack pose, MultiBufferSource buffers, ThunderSlashEntity e, float partialTick) {
        int frame = e.openingFrame(partialTick);
        float frames = ThunderSlashEntity.OPENING_FRAME_COUNT;
        float v0 = frame / frames;
        float v1 = (frame + 1.0F) / frames;
        float half = e.visualScale() * 0.5F;

        Vec3 forward = Vec3.directionFromRotation(0.0F, e.facingYaw());
        forward = new Vec3(forward.x, 0.0D, forward.z).normalize();
        Vec3 right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();

        Vec3 p0 = forward.scale(-half).add(right.scale(-half));
        Vec3 p1 = forward.scale(-half).add(right.scale( half));
        Vec3 p2 = forward.scale( half).add(right.scale( half));
        Vec3 p3 = forward.scale( half).add(right.scale(-half));

        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(OPENING_TEXTURE));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();

        vertex(vc,m,n,p0,0.0F,v1,255,0.0F,1.0F,0.0F);
        vertex(vc,m,n,p1,0.0F,v0,255,0.0F,1.0F,0.0F);
        vertex(vc,m,n,p2,1.0F,v0,255,0.0F,1.0F,0.0F);
        vertex(vc,m,n,p3,1.0F,v1,255,0.0F,1.0F,0.0F);

        vertex(vc,m,n,p3,1.0F,v1,255,0.0F,-1.0F,0.0F);
        vertex(vc,m,n,p2,1.0F,v0,255,0.0F,-1.0F,0.0F);
        vertex(vc,m,n,p1,0.0F,v0,255,0.0F,-1.0F,0.0F);
        vertex(vc,m,n,p0,0.0F,v1,255,0.0F,-1.0F,0.0F);
    }

    private static void renderBillboardSheetQuad(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, float scale, int frame, int frames, int alpha){
        pose.pushPose();
        pose.scale(scale, scale, scale);
        float v0 = frame / (float)frames;
        float v1 = (frame + 1) / (float)frames;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(texture));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        vertex(vc,m,n,new Vec3(-.5F,-.5F,0.0F),0F,v1,alpha,0F,0F,1F);
        vertex(vc,m,n,new Vec3(.5F,-.5F,0.0F),1F,v1,alpha,0F,0F,1F);
        vertex(vc,m,n,new Vec3(.5F,.5F,0.0F),1F,v0,alpha,0F,0F,1F);
        vertex(vc,m,n,new Vec3(-.5F,.5F,0.0F),0F,v0,alpha,0F,0F,1F);
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, float u, float v, int alpha, float nx, float ny, float nz){
        vc.vertex(m, (float)p.x, (float)p.y, (float)p.z)
                .color(255,255,255,alpha)
                .uv(u,v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(n,nx,ny,nz)
                .endVertex();
    }
}
