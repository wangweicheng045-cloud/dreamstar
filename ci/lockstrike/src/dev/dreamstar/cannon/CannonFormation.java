package dev.dreamstar.cannon;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class CannonFormation {
    public static final int DURATION=1200, TRANSITION=60, SHOT_TICKS=16, SHOT_DELAY=2, INTERVAL=30;
    public static final float SCALE=.90f/36.74f;
    public static final float MELEE_SCALE=.018f;
    public static final int SWITCH_PHASE_TICKS=10, MELEE_DASH_TICKS=4, MELEE_ATTACK_TICKS=12, MELEE_RETURN_TICKS=4, MELEE_STAGGER_TICKS=12;
    public static final int MELEE_DAMAGE_TICK=Math.round(MELEE_ATTACK_TICKS*(.8f/1.9f));
    public static final double MELEE_RANGE=8;
    public static final double MUZZLE=25.34*SCALE, RANGE=30;
    public static final double MAX_LAG=.20, MAX_SIDE_LAG=.06;
    public static double followAlpha(boolean sprinting){return sprinting?.60:.72;}
    public static Vec3 limitLag(Vec3 delta,float yaw){
        Vec3 forward=forward(yaw),side=new Vec3(forward.z,0,-forward.x);
        return forward.scale(net.minecraft.util.Mth.clamp(delta.dot(forward),-MAX_LAG,MAX_LAG)).add(side.scale(net.minecraft.util.Mth.clamp(delta.dot(side),-MAX_SIDE_LAG,MAX_SIDE_LAG)));
    }
    public static int count(int level){return Math.max(2,Math.min(4,level+1));}
    public static Vec3 offset(int count,int slot,double width,double height,float yaw){
        double side,heightOffset,back=width*.5+.43;
        if(slot<2){side=(slot==0?-1:1)*(width*.5+.82);heightOffset=height*.68;back=0;}
        else if(count==3){side=0;heightOffset=height+.65;}
        else{side=(slot==2?-1:1)*(width*.5+.20);heightOffset=height+.72;}
        double a=Math.toRadians(yaw);
        return new Vec3(Math.cos(a)*side+Math.sin(a)*back,heightOffset,Math.sin(a)*side-Math.cos(a)*back);
    }
    public static Vec3 position(LivingEntity owner,int count,int slot){return owner.position().add(offset(count,slot,owner.getBbWidth(),owner.getBbHeight(),owner.yBodyRot));}
    public static Vec3 meleeOffset(int slot,double width,double height,float yaw){
        double side=(slot==0?-1:1)*(width*.5+.55),back=.08;
        double a=Math.toRadians(yaw);
        return new Vec3(Math.cos(a)*side+Math.sin(a)*back,height*.72,Math.sin(a)*side-Math.cos(a)*back);
    }
    public static Vec3 meleePosition(LivingEntity owner,int slot){return owner.position().add(meleeOffset(slot,owner.getBbWidth(),owner.getBbHeight(),owner.yBodyRot));}
    public static Vec3 meleeTargetPosition(LivingEntity owner,LivingEntity target,int slot){
        Vec3 targetPos=target.position();
        Vec3 toward=owner.position().subtract(targetPos);toward=new Vec3(toward.x,0,toward.z);
        if(toward.lengthSqr()<1.0e-6)toward=forward(owner.yBodyRot).scale(-1);else toward=toward.normalize();
        Vec3 side=new Vec3(toward.z,0,-toward.x);
        double diagonal=Math.max(.8,target.getBbWidth()*.5+.55);
        Vec3 upper=targetPos.add(0,target.getBbHeight()*.70+diagonal,0);
        return upper.add(toward.scale(diagonal)).add(side.scale(slot==0?-.80:.80));
    }
    public static double smooth(double t){t=net.minecraft.util.Mth.clamp(t,0,1);return t*t*(3-2*t);}
    public static Vec3 follow(Vec3 previous,Vec3 owner,boolean sprinting,float yaw){
        if(previous==null||previous.distanceToSqr(owner)>16)return owner;
        return owner.add(limitLag(previous.lerp(owner,followAlpha(sprinting)).subtract(owner),yaw));
    }
    public static double bob(double ticks,int slot){return .055*Math.sin(ticks*Math.PI/35+slot*1.9);}
    public static Vec3 forward(float yaw){double a=Math.toRadians(yaw);return new Vec3(-Math.sin(a),0,Math.cos(a));}
    public static org.joml.Quaternionf orientation(Vec3 direction){
        var d=direction.normalize();float yaw=(float)Math.atan2(-d.x,d.z),elevation=(float)Math.asin(Math.max(-1,Math.min(1,d.y)));
        return new org.joml.Quaternionf().rotationY((float)Math.PI-yaw).rotateX(elevation);
    }
    public static float appearTime(float ticks){return Math.max(0,Math.min(4.2f,ticks/20f*1.4f));}
}
