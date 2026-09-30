package dev.dreamstar.thunder.client;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.dreamstar.thunder.ThunderSlashEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.*;
public final class ThunderSlashRenderer extends EntityRenderer<ThunderSlashEntity>{
    private static final ResourceLocation TEXTURE=new ResourceLocation("dreamstar","textures/effect/thunder_phantom_blade.png");
    public ThunderSlashRenderer(EntityRendererProvider.Context c){super(c);}
    @Override public ResourceLocation getTextureLocation(ThunderSlashEntity e){return TEXTURE;}
    @Override public void render(ThunderSlashEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int packedLight){
        LivingEntity target=e.target();if(target==null)return;float modelSize=Math.max(target.getBbHeight(),target.getBbWidth());float scale=Math.max(1.15F,modelSize*1.35F);int frame=Math.min(8,Math.max(0,e.tickCount/2));float v0=frame/9F,v1=(frame+1)/9F;
        pose.pushPose();pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());pose.mulPose(Axis.YP.rotationDegrees(180));pose.scale(scale,scale,scale);
        VertexConsumer vc=buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));Matrix4f m=pose.last().pose();Matrix3f n=pose.last().normal();
        v(vc,m,n,-.5f,-.5f,0,v1);v(vc,m,n,.5f,-.5f,1,v1);v(vc,m,n,.5f,.5f,1,v0);v(vc,m,n,-.5f,.5f,0,v0);pose.popPose();super.render(e,yaw,partial,pose,buffers,LightTexture.FULL_BRIGHT);
    }
    private static void v(VertexConsumer vc,Matrix4f m,Matrix3f n,float x,float y,float u,float v){vc.vertex(m,x,y,0).color(255,255,255,255).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n,0,0,1).endVertex();}
}