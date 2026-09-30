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
    private static final ResourceLocation[] FRAMES=new ResourceLocation[9];
    static {
        for(int i=0;i<FRAMES.length;i++) FRAMES[i]=new ResourceLocation("dreamstar",String.format("textures/effect/thunder_phantom_blade/frame_%02d.png",i+1));
    }
    public ThunderSlashRenderer(EntityRendererProvider.Context c){ super(c); }
    private static int frameIndex(ThunderSlashEntity e){ return java.lang.Math.min(8,java.lang.Math.max(0,(e.tickCount-1)/2)); }
    @Override public ResourceLocation getTextureLocation(ThunderSlashEntity e){ return FRAMES[frameIndex(e)]; }
    @Override public void render(ThunderSlashEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int packedLight){
        float scale=e.visualScale();
        ResourceLocation texture=getTextureLocation(e);
        pose.pushPose();
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.scale(scale,scale,scale);
        VertexConsumer vc=buffers.getBuffer(RenderType.entityTranslucent(texture));
        Matrix4f m=pose.last().pose(); Matrix3f n=pose.last().normal();
        v(vc,m,n,-.5f,-.5f,0,1); v(vc,m,n,.5f,-.5f,1,1); v(vc,m,n,.5f,.5f,1,0); v(vc,m,n,-.5f,.5f,0,0);
        pose.popPose();
        super.render(e,yaw,partial,pose,buffers,LightTexture.FULL_BRIGHT);
    }
    private static void v(VertexConsumer vc,Matrix4f m,Matrix3f n,float x,float y,float u,float v){ vc.vertex(m,x,y,0).color(255,255,255,255).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n,0,0,1).endVertex(); }
}
