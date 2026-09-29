package dev.dreamstar.time;

import dev.dreamstar.domain.DomainEntity;
import dev.dreamstar.whale.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import java.util.UUID;

/** Per-whale, server-authoritative combo. The original swimming code runs unchanged when idle. */
public final class TimeDomainCombo {
    private UUID targetId;
    private UUID fractureId;
    private int age;
    private boolean jumping,struck;
    private long fractureAt=-1,releaseAt=-1;
    private Vec3 fracturePos;
    private Vec3 origin,forward;
    private double ground,top;
    private BreachRoute route;
    private double travelled;
    public static DomainEntity containing(LivingEntity target){
        return target.level().getEntitiesOfClass(DomainEntity.class,target.getBoundingBox().inflate(64),
            d->!d.isRemoved()&&target.level().getGameTime()-d.startTick()<600
                &&target.position().distanceToSqr(d.position())<=d.radius()*d.radius())
            .stream().min(java.util.Comparator.comparingDouble(d->d.distanceToSqr(target))).orElse(null);
    }
    private boolean eligible(StarWhaleEntity whale,DomainEntity domain,LivingEntity target){
        if(target==null||!target.getPersistentData().contains(TimeLockState.KEY)
                ||target.position().distanceToSqr(domain.position())>domain.radius()*domain.radius())return false;
        var caster=WhaleAllies.caster((ServerLevel)whale.level(),domain.casterUUID(),domain.ownerUUID());
        if(!WhaleAllies.hostile(target,domain.casterUUID(),domain.ownerUUID(),caster))return false;
        var n=target.getPersistentData().getCompound(TimeLockState.KEY);
        if(n.contains("whaleTargetAt")&&whale.level().getGameTime()<n.getLong("whaleTargetAt"))return false;
        return !n.hasUUID("whaleClaim")||n.getUUID("whaleClaim").equals(whale.getUUID())
            ||((ServerLevel)whale.level()).getEntity(n.getUUID("whaleClaim"))==null;
    }
    public boolean active(){return targetId!=null;}
    public void reset(StarWhaleEntity whale){
        fadeFracture(whale);
        if(targetId!=null&&((ServerLevel)whale.level()).getEntity(targetId) instanceof LivingEntity target){
            var n=target.getPersistentData().getCompound(TimeLockState.KEY);
            if(n.hasUUID("whaleClaim")&&n.getUUID("whaleClaim").equals(whale.getUUID()))n.remove("whaleClaim");
        }
        targetId=null;fractureId=null;jumping=false;struck=false;fractureAt=releaseAt=-1;fracturePos=null;age=0;route=null;travelled=0;
    }
    private void fadeFracture(StarWhaleEntity whale){if(fractureId!=null&&((ServerLevel)whale.level()).getEntity(fractureId) instanceof TimeFracture crack)crack.fade();}
    private void spawnFracture(ServerLevel world,DomainEntity domain,StarWhaleEntity whale){
        if(fracturePos==null)return;
        var crack=TimeRegistry.FRACTURE.create(world);
        if(crack!=null){
            crack.setPos(fracturePos.x,fracturePos.y,fracturePos.z);crack.initialize(whale.getUUID());
            if(world.addFreshEntity(crack)){
                fractureId=crack.getUUID();
                for(var player:world.players())if(player.position().distanceToSqr(domain.position())<=domain.radius()*domain.radius())
                    player.playNotifySound(TimeRegistry.WHALE_FRACTURE_SOUND,SoundSource.PLAYERS,1f,1f);
            }
        }
        fractureAt=-1;fracturePos=null;
    }
    private Vec3 move(StarWhaleEntity whale,Vec3 goal,double speed){
        var previous=whale.trail(1).frame(0).forward();var desired=goal.subtract(whale.position());
        if(desired.lengthSqr()<1e-10)return whale.position();
        var step=StableWhaleFrames.steer(previous,new org.joml.Vector3d(desired.x,desired.y,desired.z).normalize().mul(speed),speed<.7?45:12);
        return whale.position().add(step.x,step.y,step.z);
    }
    /** Null means resume the base swimming AI. Otherwise this is the next head position. */
    public Vec3 next(StarWhaleEntity whale){
        var domain=whale.domain();
        if(domain==null||!domain.whaleActive()||!domain.ownsWhale(whale.getUUID())){reset(whale);return null;}
        var world=(ServerLevel)whale.level();
        LivingEntity target=targetId==null?null:world.getEntity(targetId) instanceof LivingEntity l?l:null;
        if(!jumping&&!eligible(whale,domain,target)){
            reset(whale);
            target=world.getEntitiesOfClass(LivingEntity.class,domain.getBoundingBox().inflate(domain.radius()),t->eligible(whale,domain,t))
                .stream().min(java.util.Comparator.comparingDouble(t->t.distanceToSqr(whale))).orElse(null);
            if(target==null)return null;
            targetId=target.getUUID();target.getPersistentData().getCompound(TimeLockState.KEY).putUUID("whaleClaim",whale.getUUID());
        }
        if(!jumping){
            ground=surface(world,target.position());
            var below=new Vec3(target.getX(),ground-7,target.getZ());
            Vec3 step=below.subtract(whale.position());
            if(step.length()>.85)return move(whale,below,.65);
            origin=whale.position();top=Math.max(ground+12,target.getY()+5);
            // Belly side after pitching vertically is UP cross the transported right vector.
            var right=whale.trail(1).frame(0).right();forward=new Vec3(right.z,0,-right.x);
            if(forward.lengthSqr()<.01){double yaw=Math.toRadians(whale.getYRot());forward=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));}else forward=forward.normalize();
            route=new BreachRoute(new org.joml.Vector3d(origin.x,origin.y,origin.z),new org.joml.Vector3d(forward.x,0,forward.z),top,ground);
            jumping=true;age=0;travelled=0;whale.triggerBreach();
        }
        ++age;
        travelled+=BreachRoute.SPEED;
        var point=route.point(Math.min(travelled,route.length()));
        var desired=travelled<=route.length()?new Vec3(point.x,point.y,point.z):domain.position().add(0,-5,0);
        Vec3 goal=move(whale,desired,BreachRoute.SPEED);
        // Test the moved nose against the actual local ground, not an animation timer.
        double floor=surface(world,new Vec3(goal.x,Math.max(ground,goal.y),goal.z));
        if(!struck&&goal.y>=floor&&goal.y>=ground-.5){
            struck=true;fractureAt=world.getGameTime()+20;fracturePos=new Vec3(goal.x,floor,goal.z);
        }
        if(fractureAt>=0&&world.getGameTime()>=fractureAt&&fractureId==null)spawnFracture(world,domain,whale);
        if(travelled>=route.descentStart()&&goal.y<=floor&&goal.y<whale.getY())fadeFracture(whale);
        if(releaseAt<0&&struck&&goal.y>whale.getY()&&travelled<route.descentStart()
                &&target!=null&&eligible(whale,domain,target)&&headTouches(target,whale.position(),goal))releaseAt=world.getGameTime()+20;
        if(releaseAt>=0&&world.getGameTime()>=releaseAt){
            if(target!=null&&eligible(whale,domain,target))TimeLockState.release(target,3);
            releaseAt=-1;
        }
        if(travelled>route.length()&&goal.distanceToSqr(domain.position())<Math.pow(Math.max(2,domain.radius()-11),2)){reset(whale);return null;}
        return goal;
    }
    public static boolean headTouches(LivingEntity target,Vec3 previous,Vec3 next){
        // Swept nose volume avoids skipping a target between two server ticks. Height alone is not a hit.
        var box=target.getBoundingBox().inflate(1.5);
        return box.contains(previous)||box.contains(next)||box.clip(previous,next).isPresent();
    }
    public static double surface(ServerLevel world,Vec3 point){
        var p=new BlockPos.MutableBlockPos();
        for(int y=Mth.floor(point.y+1);y>=Math.max(world.getMinBuildHeight(),point.y-24);y--){
            p.set(Mth.floor(point.x),y,Mth.floor(point.z));if(!world.hasChunkAt(p))break;
            var shape=world.getBlockState(p).getCollisionShape(world,p);
            if(!shape.isEmpty())return y+shape.max(net.minecraft.core.Direction.Axis.Y);
        }
        return point.y;
    }
}
