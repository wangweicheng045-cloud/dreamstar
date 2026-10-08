package dev.dreamstar.cannon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.dreamstar.cannon.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class CannonRenderer extends EntityRenderer<HoverCannonEntity> {
    private static CannonModel appear,fire;
    private static MeleeCannonModel melee;
    private static int idleClip=-1,attackClip=-1,revealClip=-1;
    private static final java.util.Map<HoverCannonEntity,CannonVisualMotion> MOTION=new java.util.WeakHashMap<>();
    public CannonRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    public static void reload(){appear=fire=null;melee=null;idleClip=attackClip=revealClip=-1;MOTION.clear();}
    @Override public boolean shouldRender(HoverCannonEntity e,Frustum f,double x,double y,double z){return e.distanceToSqr(x,y,z)<128*128;}
    @Override public ResourceLocation getTextureLocation(HoverCannonEntity e){return new ResourceLocation("dreamstar","textures/entity/hover_cannon/fire_0.png");}

    private static void ensureModels(){
        if(appear==null){appear=CannonModel.load("appear");fire=CannonModel.load("fire");}
        if(melee==null){melee=MeleeCannonModel.load();idleClip=melee.clip("悬浮待机");attackClip=melee.clip("蓄力出拳");revealClip=melee.clip("数字故障显现");}
    }

    @Override public void render(HoverCannonEntity entity,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        var owner=entity.owner();if(owner==null)return;ensureModels();
        float bodyYaw=Mth.rotLerp(partial,owner.yBodyRotO,owner.yBodyRot);
        var rendered=interpolated(entity,partial);var ownerPos=interpolated(owner,partial);double tickTime=entity.level().getGameTime()+partial;
        var motion=MOTION.computeIfAbsent(entity,e->new CannonVisualMotion());var anchor=motion.sample(ownerPos,bodyYaw,owner.isSprinting(),tickTime);
        var rangedCenter=anchor.add(CannonFormation.offset(entity.count(),entity.slot(),owner.getBbWidth(),owner.getBbHeight(),bodyYaw)).add(0,CannonFormation.bob(tickTime,entity.slot()),0);
        var meleeCenter=anchor.add(CannonFormation.meleeOffset(Math.min(1,entity.slot()),owner.getBbWidth(),owner.getBbHeight(),bodyYaw)).add(0,CannonFormation.bob(tickTime,entity.slot()),0);

        if(entity.suspended()){
            if(entity.mode()==HoverCannonEntity.MODE_MELEE){if(entity.slot()<2)renderMeleeReveal(entity,meleeCenter,rendered,bodyYaw,1-clamp(entity.pauseAge(partial)/entity.pauseTransitionTicks()),stack,buffers,light);}
            else renderRemoteTransition(entity,rangedCenter,rendered,bodyYaw,entity.visualAppearance(partial),stack,buffers,light);
            return;
        }
        if(entity.dismissing()){
            if(entity.mode()==HoverCannonEntity.MODE_MELEE){if(entity.slot()<2)renderMeleeReveal(entity,meleeCenter,rendered,bodyYaw,1-clamp(entity.dismissAge(partial)/CannonFormation.SWITCH_PHASE_TICKS),stack,buffers,light);}
            else renderRemoteTransition(entity,rangedCenter,rendered,bodyYaw,entity.visualAppearance(partial),stack,buffers,light);
            return;
        }
        if(entity.switching()){
            float age=entity.switchAge(partial);int from=entity.mode(),to=entity.switchTargetMode();
            if(age<CannonFormation.SWITCH_PHASE_TICKS){float visible=1-clamp(age/CannonFormation.SWITCH_PHASE_TICKS);renderModeReveal(entity,from,visible,rangedCenter,meleeCenter,rendered,bodyYaw,stack,buffers,light);}
            else {float visible=clamp((age-CannonFormation.SWITCH_PHASE_TICKS)/CannonFormation.SWITCH_PHASE_TICKS);renderModeReveal(entity,to,visible,rangedCenter,meleeCenter,rendered,bodyYaw,stack,buffers,light);}
            return;
        }
        if(entity.returning()){
            if(entity.mode()==HoverCannonEntity.MODE_MELEE){if(entity.slot()<2)renderMeleeReveal(entity,meleeCenter,rendered,bodyYaw,clamp(entity.returnAge(partial)/entity.returnTransitionTicks()),stack,buffers,light);}
            else renderRemoteTransition(entity,rangedCenter,rendered,bodyYaw,entity.visualAppearance(partial),stack,buffers,light);
            return;
        }
        if(entity.mode()==HoverCannonEntity.MODE_MELEE){
            if(entity.slot()>=2)return;renderMeleeActive(entity,owner,ownerPos,meleeCenter,rendered,bodyYaw,partial,tickTime,stack,buffers,light);return;
        }

        boolean initial=entity.age(partial)<CannonFormation.TRANSITION;float shotAge=entity.shotAge(partial);boolean shooting=!initial&&shotAge<CannonFormation.SHOT_TICKS;
        Vec3 direction=shooting?entity.shotEnd().subtract(rangedCenter).normalize():CannonFormation.forward(bodyYaw);
        if(motion.trailDue(!initial&&!net.minecraft.client.Minecraft.getInstance().isPaused(),tickTime)){
            var random=entity.level().random;Vec3 tail=rangedCenter.subtract(direction.scale(.50)).add((random.nextDouble()-.5)*.08,(random.nextDouble()-.5)*.08,(random.nextDouble()-.5)*.08);Vec3 drift=motion.velocity().normalize().scale(-.025).subtract(direction.scale(.015));
            entity.level().addParticle(CannonRegistry.WAKE,tail.x,tail.y,tail.z,drift.x,drift.y+.006,drift.z);
        }
        stack.pushPose();try{
            var offset=rangedCenter.subtract(rendered);stack.translate(offset.x,offset.y,offset.z);stack.mulPose(CannonFormation.orientation(direction));stack.scale(CannonFormation.SCALE,CannonFormation.SCALE,CannonFormation.SCALE);stack.translate(0,-20,0);
            if(initial)appear.render(stack,buffers,0,entity.visualAppearance(partial),1,light);
            else {float stretch=shooting?(float)Math.max(.001,(rangedCenter.distanceTo(entity.shotEnd())-CannonFormation.MUZZLE)/(156*CannonFormation.SCALE)):1;fire.render(stack,buffers,1,shooting?shotAge/20f:0,stretch,light);}
        }finally{stack.popPose();}
        super.render(entity,yaw,partial,stack,buffers,light);
    }

    private static void renderModeReveal(HoverCannonEntity entity,int mode,float visible,Vec3 rangedCenter,Vec3 meleeCenter,Vec3 rendered,float bodyYaw,PoseStack stack,MultiBufferSource buffers,int light){
        if(mode==HoverCannonEntity.MODE_MELEE){if(entity.slot()<2)renderMeleeReveal(entity,meleeCenter,rendered,bodyYaw,visible,stack,buffers,light);}
        else renderRemoteTransition(entity,rangedCenter,rendered,bodyYaw,4.2f*visible,stack,buffers,light);
    }

    private static void renderRemoteTransition(HoverCannonEntity entity,Vec3 center,Vec3 rendered,float bodyYaw,float time,PoseStack stack,MultiBufferSource buffers,int light){
        Vec3 direction=CannonFormation.forward(bodyYaw);stack.pushPose();try{var offset=center.subtract(rendered);stack.translate(offset.x,offset.y,offset.z);stack.mulPose(CannonFormation.orientation(direction));stack.scale(CannonFormation.SCALE,CannonFormation.SCALE,CannonFormation.SCALE);stack.translate(0,-20,0);appear.render(stack,buffers,0,time,1,light);}finally{stack.popPose();}
    }

    private static void renderMeleeReveal(HoverCannonEntity entity,Vec3 center,Vec3 rendered,float bodyYaw,float visible,PoseStack stack,MultiBufferSource buffers,int light){
        float time=melee.clipLength(revealClip)*clamp(visible);renderMelee(entity,center,rendered,CannonFormation.forward(bodyYaw),revealClip,time,stack,buffers,light);
    }

    private static void renderMeleeActive(HoverCannonEntity entity,LivingEntity owner,Vec3 ownerPos,Vec3 shoulder,Vec3 rendered,float bodyYaw,float partial,double tickTime,PoseStack stack,MultiBufferSource buffers,int light){
        int state=entity.meleeState();LivingEntity target=entity.meleeTarget();Vec3 attack=target==null?rendered:meleeAttackPosition(owner,ownerPos,target,entity.slot(),partial,bodyYaw);float age=entity.meleeStateAge(partial);
        Vec3 shoulderDirection=CannonFormation.forward(bodyYaw);
        Vec3 aim=target==null?attack.add(shoulderDirection):interpolated(target,partial).add(0,target.getBbHeight()*.62,0);Vec3 attackDirection=aim.subtract(attack);if(attackDirection.lengthSqr()<1e-6)attackDirection=shoulderDirection;else attackDirection=attackDirection.normalize();
        float idleTime=(float)((tickTime/20.0)%melee.clipLength(idleClip));
        if(state==HoverCannonEntity.MELEE_IDLE||state==HoverCannonEntity.MELEE_DASH&&age<0){renderMelee(entity,shoulder,rendered,shoulderDirection,idleClip,idleTime,stack,buffers,light);return;}
        if(state==HoverCannonEntity.MELEE_DASH){float t=clamp(age/CannonFormation.MELEE_DASH_TICKS);renderMeleeRevealDirected(entity,shoulder,rendered,shoulderDirection,1-t,stack,buffers,light);renderMeleeRevealDirected(entity,attack,rendered,attackDirection,t,stack,buffers,light);return;}
        if(state==HoverCannonEntity.MELEE_ATTACK){float time=melee.clipLength(attackClip)*clamp(age/CannonFormation.MELEE_ATTACK_TICKS);renderMelee(entity,attack,rendered,attackDirection,attackClip,time,stack,buffers,light);return;}
        float t=clamp(age/CannonFormation.MELEE_RETURN_TICKS);renderMeleeRevealDirected(entity,attack,rendered,attackDirection,1-t,stack,buffers,light);renderMeleeRevealDirected(entity,shoulder,rendered,shoulderDirection,t,stack,buffers,light);
    }

    private static Vec3 meleeAttackPosition(LivingEntity owner,Vec3 ownerPos,LivingEntity target,int slot,float partial,float bodyYaw){
        Vec3 targetPos=interpolated(target,partial),toward=ownerPos.subtract(targetPos);toward=new Vec3(toward.x,0,toward.z);if(toward.lengthSqr()<1e-6)toward=CannonFormation.forward(bodyYaw).scale(-1);else toward=toward.normalize();Vec3 side=new Vec3(toward.z,0,-toward.x);double diagonal=Math.max(.8,target.getBbWidth()*.5+.55);Vec3 upper=targetPos.add(0,target.getBbHeight()*.70+diagonal,0);return upper.add(toward.scale(diagonal)).add(side.scale(slot==0?-.80:.80));
    }

    private static void renderMeleeRevealDirected(HoverCannonEntity entity,Vec3 center,Vec3 rendered,Vec3 direction,float visible,PoseStack stack,MultiBufferSource buffers,int light){
        float time=melee.clipLength(revealClip)*clamp(visible);renderMelee(entity,center,rendered,direction,revealClip,time,stack,buffers,light);
    }

    private static void renderMelee(HoverCannonEntity entity,Vec3 center,Vec3 rendered,Vec3 direction,int clip,float time,PoseStack stack,MultiBufferSource buffers,int light){
        stack.pushPose();try{var offset=center.subtract(rendered);stack.translate(offset.x,offset.y,offset.z);stack.mulPose(CannonFormation.orientation(direction));float mirror=entity.slot()==0?-1f:1f;stack.scale(CannonFormation.MELEE_SCALE*mirror,CannonFormation.MELEE_SCALE,CannonFormation.MELEE_SCALE);stack.translate(0,-24,0);melee.render(stack,buffers,clip,time,light);}finally{stack.popPose();}
    }

    private static Vec3 interpolated(net.minecraft.world.entity.Entity e,float partial){return new Vec3(Mth.lerp(partial,e.xOld,e.getX()),Mth.lerp(partial,e.yOld,e.getY()),Mth.lerp(partial,e.zOld,e.getZ()));}
    private static float clamp(double value){return (float)Mth.clamp(value,0,1);}
}
