package dev.dreamstar.cannon;

import dev.dreamstar.whale.WhaleAllies;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.*;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;

import java.util.*;

public final class HoverCannonEntity extends Entity {
    public static final int MODE_RANGED=0,MODE_MELEE=1;
    public static final int MELEE_IDLE=0,MELEE_DASH=1,MELEE_ATTACK=2,MELEE_RETURN=3;
    private static final EntityDataAccessor<Integer> OWNER=SynchedEntityData.defineId(HoverCannonEntity.class,EntityDataSerializers.INT),COUNT=SynchedEntityData.defineId(HoverCannonEntity.class,EntityDataSerializers.INT),SLOT=SynchedEntityData.defineId(HoverCannonEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(HoverCannonEntity.class,EntityDataSerializers.LONG),DISMISS=SynchedEntityData.defineId(HoverCannonEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> REVERSE=SynchedEntityData.defineId(HoverCannonEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> PAUSE=SynchedEntityData.defineId(HoverCannonEntity.class,EntityDataSerializers.LONG),RETURN=SynchedEntityData.defineId(HoverCannonEntity.class,EntityDataSerializers.LONG),EXPIRY=SynchedEntityData.defineId(HoverCannonEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> PAUSE_TRANSITION=SynchedEntityData.defineId(HoverCannonEntity.class,EntityDataSerializers.INT),RETURN_TRANSITION=SynchedEntityData.defineId(HoverCannonEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> SHOT=SynchedEntityData.defineId(HoverCannonEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Integer> MODE=SynchedEntityData.defineId(HoverCannonEntity.class,EntityDataSerializers.INT),SWITCH_TARGET=SynchedEntityData.defineId(HoverCannonEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> SWITCH_START=SynchedEntityData.defineId(HoverCannonEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> MELEE_STATE=SynchedEntityData.defineId(HoverCannonEntity.class,EntityDataSerializers.INT),MELEE_TARGET=SynchedEntityData.defineId(HoverCannonEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> MELEE_STATE_START=SynchedEntityData.defineId(HoverCannonEntity.class,EntityDataSerializers.LONG);

    private UUID ownerId,family,preferred;
    private long preferredUntil,nextShot;
    private int spellLevel=1;
    private boolean dealt;
    private Vec3 followAnchor;

    public HoverCannonEntity(EntityType<? extends HoverCannonEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    @Override protected void defineSynchedData(){
        entityData.define(OWNER,-1);entityData.define(COUNT,2);entityData.define(SLOT,0);entityData.define(START,0L);entityData.define(DISMISS,-1L);entityData.define(REVERSE,4.2f);entityData.define(SHOT,new CompoundTag());entityData.define(PAUSE,-1L);entityData.define(RETURN,-1L);entityData.define(EXPIRY,0L);
        entityData.define(MODE,MODE_RANGED);entityData.define(SWITCH_TARGET,MODE_RANGED);entityData.define(SWITCH_START,-1L);entityData.define(MELEE_STATE,MELEE_IDLE);entityData.define(MELEE_TARGET,-1);entityData.define(MELEE_STATE_START,-1L);entityData.define(PAUSE_TRANSITION,60);entityData.define(RETURN_TRANSITION,60);
    }

    public boolean suspended(){return entityData.get(PAUSE)>=0;}
    public float pauseAge(float partial){return suspended()?level().getGameTime()-entityData.get(PAUSE)+partial:1000;}
    public int pauseTransitionTicks(){return Math.max(1,entityData.get(PAUSE_TRANSITION));}
    public int returnTransitionTicks(){return Math.max(1,entityData.get(RETURN_TRANSITION));}
    public void suspend(){suspendFor(mode()==MODE_MELEE?CannonFormation.SWITCH_PHASE_TICKS:60);}
    public void suspendQuick(){suspendFor(8);}
    private void suspendFor(int ticks){if(!suspended()&&!dismissing()){entityData.set(PAUSE_TRANSITION,Math.max(1,ticks));entityData.set(REVERSE,appearanceTime(0));entityData.set(PAUSE,level().getGameTime());entityData.set(SHOT,new CompoundTag());clearMeleeAttack();}}
    public void resume(int bonus){resumeFor(bonus,mode()==MODE_MELEE?CannonFormation.SWITCH_PHASE_TICKS:60);}
    public void resumeQuick(int bonus){resumeFor(bonus,8);}
    private void resumeFor(int bonus,int ticks){if(suspended()){entityData.set(START,entityData.get(START)+level().getGameTime()-entityData.get(PAUSE));entityData.set(EXPIRY,entityData.get(EXPIRY)+bonus);entityData.set(PAUSE,-1L);entityData.set(RETURN_TRANSITION,Math.max(1,ticks));entityData.set(RETURN,level().getGameTime());nextShot=level().getGameTime()+Math.max(1,ticks)+slot()*2;}}
    public boolean returning(){if(entityData.get(RETURN)<0)return false;long age=level().getGameTime()-entityData.get(RETURN);return age<returnTransitionTicks();}
    public float returnAge(float partial){return entityData.get(RETURN)<0?1000:level().getGameTime()-entityData.get(RETURN)+partial;}
    public float visualAppearance(float partial){if(suspended()){float progress=(float)((level().getGameTime()-entityData.get(PAUSE)+partial)/pauseTransitionTicks());return Math.max(0,entityData.get(REVERSE)*(1-Mth.clamp(progress,0,1)));}if(returning()){float progress=Mth.clamp(returnAge(partial)/returnTransitionTicks(),0,1);return 4.2f*progress;}return appearanceTime(partial);}
    public long remainingLifetime(){return Math.max(0,CannonFormation.DURATION+entityData.get(EXPIRY)-(long)(suspended()?entityData.get(PAUSE)-entityData.get(START):age(0)));}
    public void extendLifetime(int ticks){if(ticks>0)entityData.set(EXPIRY,entityData.get(EXPIRY)+ticks);}

    public void initialize(LivingEntity owner,int count,int slot,int level){ownerId=owner.getUUID();family=WhaleAllies.family(owner);spellLevel=level;entityData.set(OWNER,owner.getId());entityData.set(COUNT,count);entityData.set(SLOT,slot);entityData.set(START,level().getGameTime());setPos(CannonFormation.position(owner,count,slot));nextShot=level().getGameTime()+CannonFormation.TRANSITION+slot*6;}
    public LivingEntity owner(){var e=level().getEntity(entityData.get(OWNER));return e instanceof LivingEntity l&&l.getUUID().equals(ownerIdOrClient(l))?l:null;}
    private UUID ownerIdOrClient(LivingEntity e){return level().isClientSide?e.getUUID():ownerId;}
    public UUID ownerId(){return ownerId;}
    public int slot(){return entityData.get(SLOT);}public int count(){return entityData.get(COUNT);}public int spellLevel(){return spellLevel;}
    public float age(float partial){return level().getGameTime()-entityData.get(START)+partial;}
    public boolean dismissing(){return entityData.get(DISMISS)>=0;}
    public float dismissAge(float partial){return dismissing()?level().getGameTime()-entityData.get(DISMISS)+partial:1000;}
    public boolean switching(){return entityData.get(SWITCH_START)>=0;}
    public float switchAge(float partial){return switching()?level().getGameTime()-entityData.get(SWITCH_START)+partial:1000;}
    public int mode(){return entityData.get(MODE);}
    public void forceMode(int mode){entityData.set(MODE,mode==MODE_MELEE?MODE_MELEE:MODE_RANGED);entityData.set(SWITCH_TARGET,entityData.get(MODE));entityData.set(SWITCH_START,-1L);entityData.set(RETURN,-1L);clearMeleeAttack();}
    public int switchTargetMode(){return entityData.get(SWITCH_TARGET);}
    public boolean meleeOrSwitchingToMelee(){return mode()==MODE_MELEE||switching()&&switchTargetMode()==MODE_MELEE;}
    public boolean active(){return !dismissing()&&!suspended()&&!returning()&&!switching()&&age(0)>=CannonFormation.TRANSITION&&remainingLifetime()>0;}
    public boolean rangedActive(){return active()&&mode()==MODE_RANGED;}
    public boolean meleeReady(){return active()&&mode()==MODE_MELEE&&slot()<2&&entityData.get(MELEE_STATE)==MELEE_IDLE;}
    public float appearanceTime(float partial){return dismissing()?Math.max(0,entityData.get(REVERSE)-(level().getGameTime()-entityData.get(DISMISS)+partial)*.07f):CannonFormation.appearTime(age(partial));}
    public float shotAge(float partial){return entityData.get(SHOT).contains("time")?level().getGameTime()-entityData.get(SHOT).getLong("time")+partial:1000;}
    public Vec3 shotEnd(){var t=entityData.get(SHOT);return new Vec3(t.getDouble("x"),t.getDouble("y"),t.getDouble("z"));}

    public int meleeState(){return entityData.get(MELEE_STATE);}
    public float meleeStateAge(float partial){return entityData.get(MELEE_STATE_START)<0?1000:level().getGameTime()-entityData.get(MELEE_STATE_START)+partial;}
    public LivingEntity meleeTarget(){var e=level().getEntity(entityData.get(MELEE_TARGET));return e instanceof LivingEntity l?l:null;}
    public int meleeTargetId(){return entityData.get(MELEE_TARGET);}

    public void beginModeSwitch(boolean melee){
        if(dismissing())return;int target=melee?MODE_MELEE:MODE_RANGED;if(!switching()&&mode()==target)return;
        entityData.set(SHOT,new CompoundTag());clearMeleeAttack();entityData.set(SWITCH_TARGET,target);entityData.set(SWITCH_START,level().getGameTime());
    }
    private void finishSwitchIfDue(){
        if(!switching()||switchAge(0)<CannonFormation.SWITCH_PHASE_TICKS*2)return;
        int target=switchTargetMode();entityData.set(MODE,target);entityData.set(SWITCH_START,-1L);entityData.set(RETURN,-1L);clearMeleeAttack();
        if(target==MODE_RANGED)nextShot=level().getGameTime()+CannonFormation.INTERVAL;
    }

    public void dismiss(){
        if(dismissing())return;
        if(switching()){
            if(switchAge(0)>=CannonFormation.SWITCH_PHASE_TICKS)entityData.set(MODE,switchTargetMode());
            entityData.set(SWITCH_START,-1L);
        }
        entityData.set(REVERSE,CannonFormation.appearTime(age(0)));entityData.set(DISMISS,level().getGameTime());entityData.set(SHOT,new CompoundTag());clearMeleeAttack();
    }
    public void prefer(LivingEntity target){preferred=target.getUUID();preferredUntil=level().getGameTime()+CannonFormation.DURATION;}
    public static List<HoverCannonEntity> owned(ServerLevel world,UUID owner){var result=new ArrayList<HoverCannonEntity>();for(var e:world.getAllEntities())if(e instanceof HoverCannonEntity c&&Objects.equals(c.ownerId,owner)&&!c.isRemoved())result.add(c);return result;}
    public static void dismissAll(ServerLevel world,UUID owner){for(var c:owned(world,owner))c.dismiss();}

    public static void cooperativeMeleeAttack(ServerLevel world,LivingEntity caster,LivingEntity target){
        if(target==caster||!target.isAlive()||target.distanceToSqr(caster)>CannonFormation.MELEE_RANGE*CannonFormation.MELEE_RANGE)return;
        var cannons=owned(world,caster.getUUID()).stream().filter(c->c.slot()<2&&c.mode()==MODE_MELEE&&!c.switching()&&!c.dismissing()&&!c.suspended()&&!c.returning()).sorted(Comparator.comparingInt(HoverCannonEntity::slot)).toList();
        if(cannons.isEmpty())return;long now=world.getGameTime();var ready=cannons.stream().filter(HoverCannonEntity::meleeReady).toList();
        if(ready.size()>=2){ready.get(0).startMeleeAttack(target);ready.get(1).startMeleeAttack(target);return;}
        if(ready.size()==1){ready.get(0).startMeleeAttack(target);return;}
        if(cannons.size()>=2){var left=cannons.get(0);var right=cannons.get(1);if(left.entityData.get(MELEE_STATE)==MELEE_DASH&&right.entityData.get(MELEE_STATE)==MELEE_DASH&&left.entityData.get(MELEE_STATE_START)==now&&right.entityData.get(MELEE_STATE_START)==now+CannonFormation.MELEE_STAGGER_TICKS&&left.meleeTargetId()==right.meleeTargetId()&&left.meleeTargetId()!=target.getId())right.reassignQueuedMelee(target);}
    }

    private void startMeleeAttack(LivingEntity target){if(!meleeReady())return;entityData.set(MELEE_TARGET,target.getId());entityData.set(MELEE_STATE,MELEE_DASH);entityData.set(MELEE_STATE_START,level().getGameTime()+(slot()==1?CannonFormation.MELEE_STAGGER_TICKS:0));dealt=false;}
    private void reassignQueuedMelee(LivingEntity target){if(entityData.get(MELEE_STATE)==MELEE_DASH&&entityData.get(MELEE_STATE_START)>level().getGameTime()){entityData.set(MELEE_TARGET,target.getId());dealt=false;}}
    private void clearMeleeAttack(){entityData.set(MELEE_STATE,MELEE_IDLE);entityData.set(MELEE_STATE_START,-1L);entityData.set(MELEE_TARGET,-1);dealt=false;}
    private void beginReturn(){if(entityData.get(MELEE_STATE)!=MELEE_RETURN){entityData.set(MELEE_STATE,MELEE_RETURN);entityData.set(MELEE_STATE_START,level().getGameTime());}}

    private boolean friendly(LivingEntity target,LivingEntity owner){return !WhaleAllies.hostile(target,ownerId,family,owner);}
    private boolean eligible(LivingEntity t,LivingEntity owner){return !friendly(t,owner)&&t.distanceToSqr(owner)<=900&&(t instanceof Enemy||Objects.equals(t.getUUID(),preferred)&&level().getGameTime()<preferredUntil||t==owner.getLastHurtMob()&&owner.tickCount-owner.getLastHurtMobTimestamp()<1200||t==owner.getLastHurtByMob()&&owner.tickCount-owner.getLastHurtByMobTimestamp()<1200);}

    private Vec3 idlePosition(LivingEntity owner,boolean melee){
        Vec3 base=followAnchor==null?owner.position():followAnchor;
        Vec3 offset=melee?CannonFormation.meleeOffset(Math.min(1,slot()),owner.getBbWidth(),owner.getBbHeight(),owner.yBodyRot):CannonFormation.offset(count(),slot(),owner.getBbWidth(),owner.getBbHeight(),owner.yBodyRot);
        return base.add(offset).add(0,CannonFormation.bob(level().getGameTime(),slot()),0);
    }
    private Vec3 meleeServerPosition(LivingEntity owner,Vec3 shoulder){
        int state=meleeState();var target=meleeTarget();if(state==MELEE_IDLE)return shoulder;
        if(target==null||!target.isAlive()){beginReturn();state=MELEE_RETURN;}
        Vec3 attack=target!=null?CannonFormation.meleeTargetPosition(owner,target,slot()):position();
        if(state==MELEE_DASH)return meleeStateAge(0)<CannonFormation.MELEE_DASH_TICKS?shoulder:attack;
        if(state==MELEE_ATTACK)return attack;
        return attack;
    }

    @Override public void tick(){
        super.tick();if(level().isClientSide)return;
        var owner=owner();if(owner==null||!owner.isAlive()){discard();return;}
        finishSwitchIfDue();
        followAnchor=CannonFormation.follow(followAnchor,owner.position(),owner.isSprinting(),owner.yBodyRot);
        boolean meleeVisual=mode()==MODE_MELEE&&!switching();Vec3 shoulder=idlePosition(owner,meleeVisual);
        setPos(meleeVisual&&slot()<2?meleeServerPosition(owner,shoulder):shoulder);
        if(suspended())return;
        if(remainingLifetime()<=0)dismiss();
        if(dismissing()){
            boolean done=mode()==MODE_MELEE?dismissAge(0)>=CannonFormation.SWITCH_PHASE_TICKS:appearanceTime(0)<=0;
            if(done)discard();return;
        }
        if(dev.dreamstar.point.PointStrikeEntity.forCaster(owner)!=null||dev.dreamstar.lockstrike.LockedStrikeEntity.forCaster(owner)!=null){if(!entityData.get(SHOT).isEmpty())entityData.set(SHOT,new CompoundTag());return;}
        if(switching())return;
        if(mode()==MODE_MELEE){tickMelee(owner);return;}
        if(!rangedActive())return;
        float shotAge=shotAge(0);
        if(shotAge<CannonFormation.SHOT_TICKS){
            var shot=entityData.get(SHOT);Vec3 aim=new Vec3(shot.getDouble("aimx"),shot.getDouble("aimy"),shot.getDouble("aimz"));Vec3 direction=aim.subtract(position()).normalize();var collision=trace(owner,position().add(direction.scale(CannonFormation.MUZZLE)),aim);
            updateEnd(collision.getLocation());if(!dealt&&shotAge>=CannonFormation.SHOT_DELAY){dealt=true;impact(owner,collision);}return;
        }
        if(level().getGameTime()<nextShot)return;nextShot=level().getGameTime()+5;
        var candidates=level().getEntitiesOfClass(LivingEntity.class,owner.getBoundingBox().inflate(30),e->eligible(e,owner));
        candidates.sort(Comparator.<LivingEntity>comparingInt(e->Objects.equals(e.getUUID(),preferred)?0:1).thenComparingDouble(e->e.distanceToSqr(owner)));
        for(var target:candidates){Vec3 aim=target.getBoundingBox().getCenter(),direction=aim.subtract(position()).normalize();var collision=trace(owner,position().add(direction.scale(CannonFormation.MUZZLE)),aim);
            if(!(collision instanceof EntityHitResult eh)||!(eh.getEntity() instanceof LivingEntity hit)||friendly(hit,owner))continue;
            var tag=new CompoundTag();tag.putLong("time",level().getGameTime());tag.putDouble("aimx",aim.x);tag.putDouble("aimy",aim.y);tag.putDouble("aimz",aim.z);tag.putDouble("x",collision.getLocation().x);tag.putDouble("y",collision.getLocation().y);tag.putDouble("z",collision.getLocation().z);entityData.set(SHOT,tag);dealt=false;nextShot=level().getGameTime()+CannonFormation.INTERVAL;break;
        }
    }

    private void tickMelee(LivingEntity owner){
        if(slot()>=2||!active())return;int state=meleeState();if(state==MELEE_IDLE)return;var target=meleeTarget();
        if(target==null||!target.isAlive()){beginReturn();state=MELEE_RETURN;}
        float age=meleeStateAge(0);
        if(state==MELEE_DASH&&age>=CannonFormation.MELEE_DASH_TICKS){entityData.set(MELEE_STATE,MELEE_ATTACK);entityData.set(MELEE_STATE_START,level().getGameTime());dealt=false;return;}
        if(state==MELEE_ATTACK){
            if(!dealt&&age>=CannonFormation.MELEE_DAMAGE_TICK&&target!=null&&target.isAlive()){dealt=true;target.invulnerableTime=0;applyListedSpellDamage(target,CannonRegistry.SWITCH_SPELL.meleeDamage(spellLevel,owner),SpellDamageSource.source(this,owner,CannonRegistry.SWITCH_SPELL));}
            if(age>=CannonFormation.MELEE_ATTACK_TICKS)beginReturn();return;
        }
        if(state==MELEE_RETURN&&age>=CannonFormation.MELEE_RETURN_TICKS)clearMeleeAttack();
    }

    private void updateEnd(Vec3 p){var t=entityData.get(SHOT).copy();t.putDouble("x",p.x);t.putDouble("y",p.y);t.putDouble("z",p.z);entityData.set(SHOT,t);}
    public HitResult trace(LivingEntity owner,Vec3 start,Vec3 end){
        var block=level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));Vec3 limit=block.getType()==HitResult.Type.MISS?end:block.getLocation();double best=start.distanceToSqr(limit);EntityHitResult nearest=null;
        for(var e:level().getEntitiesOfClass(LivingEntity.class,new AABB(start,limit).inflate(.25),e->e!=owner&&e.isAlive()&&!e.isSpectator())){var box=e.getBoundingBox().inflate(.03);var point=box.contains(start)?Optional.of(start):box.clip(start,limit);if(point.isPresent()&&point.get().distanceToSqr(start)<=best){best=point.get().distanceToSqr(start);nearest=new EntityHitResult(e,point.get());}}
        return nearest!=null?nearest:block;
    }
    private void impact(LivingEntity owner,HitResult hit){
        if(hit instanceof EntityHitResult entity&&entity.getEntity() instanceof LivingEntity target&&!friendly(target,owner)){target.invulnerableTime=0;applyListedSpellDamage(target,CannonRegistry.SPELL.damage(spellLevel,owner),SpellDamageSource.source(this,owner,CannonRegistry.SPELL));}
        var p=hit.getLocation();MagicManager.spawnParticles(level(),ParticleHelper.ICY_FOG,p.x,p.y,p.z,4,0,0,0,.3,true);MagicManager.spawnParticles(level(),ParticleHelper.SNOWFLAKE,p.x,p.y,p.z,20,0,0,0,.2,false);level().playSound(null,getX(),getY(),getZ(),io.redspace.ironsspellbooks.registries.SoundRegistry.RAY_OF_FROST.get(),SoundSource.PLAYERS,.28f,1.3f);
    }
    @Override public boolean shouldBeSaved(){return false;}
    @Override protected void readAdditionalSaveData(CompoundTag t){}
    @Override protected void addAdditionalSaveData(CompoundTag t){}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override public AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(32);}
    private static boolean applyListedSpellDamage(net.minecraft.world.entity.Entity target,float amount,net.minecraft.world.damagesource.DamageSource source){
        if(target instanceof LivingEntity living && source instanceof SpellDamageSource spellSource){
            float resist=DamageSources.getResist(living,spellSource.spell().getSchoolType());
            amount/=Math.max(.001f,resist);
        }
        return DamageSources.applyDamage(target,amount,source);
    }

}
