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
    private static final ResourceLocation TEXTURE=new ResourceLocation("dreamstar","textures/effect/thunder_phantom_blade.png");

    public ThunderSlashRenderer(EntityRendererProvider.Context c){super(c);}

    private static int frameIndex(ThunderSlashEntity e){
        return java.lang.Math.min(8,java.lang.Math.max(0,(int)(e.animationAge()/2L)));
    }

    @Override
    public ResourceLocation getTextureLocation(ThunderSlashEntity e){return TEXTURE;}

    @Override
    public void render(ThunderSlashEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int packedLight){
        long age=e.animationAge();
        if(age<0L||age>=18L)return;

        int frame=frameIndex(e);
        float v0=frame/9.0F;
        float v1=(frame+1)/9.0F;
        float scale=e.visualScale();

        pose.pushPose();
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.scale(scale,scale,scale);

        VertexConsumer vc=buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        Matrix4f m=pose.last().pose();
        Matrix3f n=pose.last().normal();
        vertex(vc,m,n,-.5F,-.5F,0.0F,v1);
        vertex(vc,m,n,.5F,-.5F,1.0F,v1);
        vertex(vc,m,n,.5F,.5F,1.0F,v0);
        vertex(vc,m,n,-.5F,.5F,0.0F,v0);
        pose.popPose();

        super.render(e,yaw,partial,pose,buffers,LightTexture.FULL_BRIGHT);
    }

    private static void vertex(VertexConsumer vc,Matrix4f m,Matrix3f n,float x,float y,float u,float v){
        vc.vertex(m,x,y,0.0F).color(255,255,255,255).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n,0,0,1).endVertex();
    }
}
