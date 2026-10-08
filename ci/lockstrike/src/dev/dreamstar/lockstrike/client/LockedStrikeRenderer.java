package dev.dreamstar.lockstrike.client;

import com.mojang.blaze3d.vertex.*;
import dev.dreamstar.cannon.*;
import dev.dreamstar.cannon.client.*;
import dev.dreamstar.lockstrike.LockedStrikeEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class LockedStrikeRenderer extends EntityRenderer<LockedStrikeEntity> {
    private static final ResourceLocation TEX=new ResourceLocation("dreamstar","textures/entity/hover_cannon/fire_0.png");
    private static CannonModel appear,fire;private static MeleeCannonModel melee;private static int idle=-1,attack=-1,reveal=-1;
    public LockedStrikeRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=0;}
    public static void reload(){appear=null;fire=null;melee=null;idle=attack=reveal=-1;}
    private static void ensure(){if(appear==null){appear=CannonModel.load("appear");fire=CannonModel.load("fire");}if(melee==null){melee=MeleeCannonModel.load();idle=melee.clip("悬浮待机");attack=melee.clip("蓄力出拳");reveal=melee.clip("数字故障显现");}}
    @Override public ResourceLocation getTextureLocation(LockedStrikeEntity e){return TEX;}
    @Override public void render(LockedStrikeEntity e,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light){ensure();float age=e.age(partial);if(age<LockedStrikeEntity.VANISH){super.render(e,yaw,partial,stack,buffers,light);return;}if(e.mode()==HoverCannonEntity.MODE_MELEE)renderMeleeScene(e,age,partial,stack,buffers,light);else renderRangedScene(e,age,stack,buffers,light);super.render(e,yaw,partial,stack,buffers,light);}
    private static void renderRangedScene(LockedStrikeEntity e,float age,PoseStack stack,MultiBufferSource buffers,int light){
        float local=age-LockedStrikeEntity.VANISH;Vec3 anchor=e.anchor();Vec3 aim=anchor.add(0,e.targetHeight()*.55,0);int count=e.cannonCount();double radius=Math.max(2.0,e.targetWidth()*.5+2.0);
        if(local<LockedStrikeEntity.APPEAR){float v=clamp(local/LockedStrikeEntity.APPEAR);for(int i=0;i<count;i++){Vec3 p=orbit(anchor,e.targetHeight(),radius,i,count,0);renderRemote(p,e.position(),aim.subtract(p),true,4.2f*v,0,stack,buffers,light);}return;}
        float attackLocal=local-LockedStrikeEntity.APPEAR;
        if(attackLocal<LockedStrikeEntity.RANGED_BEAM){for(int i=0;i<count;i++){Vec3 p=orbit(anchor,e.targetHeight(),radius,i,count,attackLocal);renderRemote(p,e.position(),aim.subtract(p),false,(attackLocal%16)/20f,p.distanceTo(aim),stack,buffers,light);}return;}
        float finalLocal=attackLocal-LockedStrikeEntity.RANGED_BEAM;renderVerticalBeam(e,finalLocal,stack,buffers);
        double frozen=LockedStrikeEntity.RANGED_BEAM;for(int i=0;i<count;i++){Vec3 base=orbit(anchor,e.targetHeight(),radius,i,count,frozen);double rise=Math.min(2.4,finalLocal*.11);Vec3 p=base.add(0,rise,0);if(finalLocal<LockedStrikeEntity.FINAL_BEAM){renderRemote(p,e.position(),aim.subtract(p),false,0,1,stack,buffers,light);}else{float gone=clamp((finalLocal-LockedStrikeEntity.FINAL_BEAM)/LockedStrikeEntity.FINISH_VANISH);renderRemote(p,e.position(),aim.subtract(p),true,4.2f*(1-gone),0,stack,buffers,light);}}
    }
    private static Vec3 orbit(Vec3 a,float h,double r,int i,int count,double ticks){double angle=(Math.PI*2*i/Math.max(1,count))+ticks*.32;double y=a.y+h*.55+Math.sin(angle*1.7+i)*.18;return new Vec3(a.x+Math.cos(angle)*r,y,a.z+Math.sin(angle)*r);}
    private static void renderRemote(Vec3 center,Vec3 entityPos,Vec3 direction,boolean transition,float anim,double distance,PoseStack stack,MultiBufferSource buffers,int light){if(direction.lengthSqr()<1e-6)direction=new Vec3(0,0,1);stack.pushPose();try{Vec3 o=center.subtract(entityPos);stack.translate(o.x,o.y,o.z);stack.mulPose(CannonFormation.orientation(direction));stack.scale(CannonFormation.SCALE,CannonFormation.SCALE,CannonFormation.SCALE);stack.translate(0,-20,0);if(transition)appear.render(stack,buffers,0,anim,1,light);else{float stretch=(float)Math.max(.001,(distance-CannonFormation.MUZZLE)/(156*CannonFormation.SCALE));fire.render(stack,buffers,1,anim,stretch,light);}}finally{stack.popPose();}}
    private static void renderVerticalBeam(LockedStrikeEntity e,float t,PoseStack stack,MultiBufferSource buffers){if(t<0||t>LockedStrikeEntity.FINAL_BEAM)return;float w=.5f*(1-clamp(t/LockedStrikeEntity.FINAL_BEAM));if(w<=.01)return;Vec3 base=e.anchor().subtract(e.position());float y0=(float)base.y,y1=y0+28;float x=(float)base.x,z=(float)base.z;var out=buffers.getBuffer(RenderType.lightning());var m=stack.last().pose();quad(out,m,x-w,y0,z,x-w,y1,z,x+w,y1,z,x+w,y0,z);quad(out,m,x,y0,z-w,x,y0,z+w,x,y1,z+w,x,y1,z-w);}
    private static void quad(VertexConsumer out,org.joml.Matrix4f m,float x0,float y0,float z0,float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3){v(out,m,x0,y0,z0);v(out,m,x1,y1,z1);v(out,m,x2,y2,z2);v(out,m,x3,y3,z3);}
    private static void v(VertexConsumer out,org.joml.Matrix4f m,float x,float y,float z){out.vertex(m,x,y,z).color(0.65f,0.9f,1f,.88f).endVertex();}
    private static void renderMeleeScene(LockedStrikeEntity e,float age,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        float local=age-LockedStrikeEntity.VANISH;Vec3 a=e.anchor();Vec3 front=front(e),side=new Vec3(front.z,0,-front.x);double contact=e.targetWidth()*.5+.25,base=e.targetWidth()*.5+1.05;double y=a.y+Math.min(1.0,e.targetHeight()*.45);
        if(local<LockedStrikeEntity.APPEAR){float vis=clamp(local/LockedStrikeEntity.APPEAR);for(int slot=0;slot<2;slot++){Vec3 p=new Vec3(a.x,y,a.z).add(front.scale(base)).add(side.scale(slot==0?-.85:.85));renderMelee(e,p,e.position(),a.add(0,e.targetHeight()*.5,0).subtract(p),slot,reveal,melee.clipLength(reveal)*vis,stack,buffers,light);}return;}
        float attackLocal=local-LockedStrikeEntity.APPEAR;Vec3 target=new Vec3(a.x,y,a.z);
        if(attackLocal<LockedStrikeEntity.MELEE_COMBO){int hit=(int)(attackLocal/LockedStrikeEntity.MELEE_HIT_INTERVAL);int active=hit%2;float phase=(attackLocal%LockedStrikeEntity.MELEE_HIT_INTERVAL)/LockedStrikeEntity.MELEE_HIT_INTERVAL;for(int slot=0;slot<2;slot++){double s=slot==0?-.85:.85;Vec3 ready=target.add(front.scale(base)).add(side.scale(s));Vec3 p=ready;int clip=idle;float time=(age/20f)%Math.max(.01f,melee.clipLength(idle));if(slot==active){double push;if(phase<.30)push=Mth.lerp(phase/.30,0,1.0);else if(phase<.78)push=Mth.lerp((phase-.30)/.48,1.0,-(base-contact));else push=Mth.lerp((phase-.78)/.22,-(base-contact),0);p=ready.add(front.scale(push));clip=attack;time=melee.clipLength(attack)*clamp(phase);}renderMelee(e,p,e.position(),a.add(0,e.targetHeight()*.48,0).subtract(p),slot,clip,time,stack,buffers,light);}return;}
        float fin=attackLocal-LockedStrikeEntity.MELEE_COMBO;Vec3[] vpos=new Vec3[2];for(int slot=0;slot<2;slot++){double s=slot==0?-1.45:1.45;vpos[slot]=target.add(front.scale(e.targetWidth()*.5+2.25)).add(side.scale(s));}
        if(fin<LockedStrikeEntity.V_PAUSE){for(int slot=0;slot<2;slot++)renderMelee(e,vpos[slot],e.position(),a.add(0,e.targetHeight()*.5,0).subtract(vpos[slot]),slot,idle,(age/20f)%Math.max(.01f,melee.clipLength(idle)),stack,buffers,light);return;}
        float rush=fin-LockedStrikeEntity.V_PAUSE;if(rush<LockedStrikeEntity.FINAL_RUSH){float t=clamp(rush/LockedStrikeEntity.FINAL_RUSH);for(int slot=0;slot<2;slot++){Vec3 end=target.add(front.scale(contact)).add(side.scale(slot==0?-.35:.35));Vec3 p=vpos[slot].lerp(end,smooth(t));renderMelee(e,p,e.position(),a.add(0,e.targetHeight()*.5,0).subtract(p),slot,attack,melee.clipLength(attack)*t,stack,buffers,light);}return;}
        float gone=clamp((rush-LockedStrikeEntity.FINAL_RUSH)/LockedStrikeEntity.FINISH_VANISH);for(int slot=0;slot<2;slot++){Vec3 p=target.add(front.scale(contact)).add(side.scale(slot==0?-.35:.35));renderMelee(e,p,e.position(),a.add(0,e.targetHeight()*.5,0).subtract(p),slot,reveal,melee.clipLength(reveal)*(1-gone),stack,buffers,light);}
    }
    private static Vec3 front(LockedStrikeEntity e){var owner=e.owner();Vec3 d=owner==null?new Vec3(0,0,1):owner.position().subtract(e.anchor());d=new Vec3(d.x,0,d.z);return d.lengthSqr()<1e-6?new Vec3(0,0,1):d.normalize();}
    private static void renderMelee(LockedStrikeEntity e,Vec3 center,Vec3 entityPos,Vec3 dir,int slot,int clip,float time,PoseStack stack,MultiBufferSource buffers,int light){if(dir.lengthSqr()<1e-6)dir=new Vec3(0,0,1);stack.pushPose();try{Vec3 o=center.subtract(entityPos);stack.translate(o.x,o.y,o.z);stack.mulPose(CannonFormation.orientation(dir));float mirror=slot==0?-1f:1f;stack.scale(CannonFormation.MELEE_SCALE*mirror,CannonFormation.MELEE_SCALE,CannonFormation.MELEE_SCALE);stack.translate(0,-24,0);melee.render(stack,buffers,clip,time,light);}finally{stack.popPose();}}
    private static float smooth(float t){t=clamp(t);return t*t*(3-2*t);}private static float clamp(double v){return (float)Mth.clamp(v,0,1);}
}
