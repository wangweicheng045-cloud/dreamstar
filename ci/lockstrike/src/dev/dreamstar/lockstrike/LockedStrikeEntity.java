package dev.dreamstar.lockstrike;

import dev.dreamstar.cannon.*;
import dev.dreamstar.point.PausedCannonRecast;
import dev.dreamstar.whale.WhaleAllies;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.damage.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;
import java.util.*;

public final class LockedStrikeEntity extends Entity {
    public static final int VANISH=8, APPEAR=8, RANGED_BEAM=60, FINAL_BEAM=20;
    public static final int MELEE_HIT_INTERVAL=4, MELEE_HITS=20, MELEE_COMBO=MELEE_HIT_INTERVAL*MELEE_HITS, V_PAUSE=10, FINAL_RUSH=6, FINISH_VANISH=8;
    private static final EntityDataAccessor<Integer> OWNER=SynchedEntityData.defineId(LockedStrikeEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET=SynchedEntityData.defineId(LockedStrikeEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MODE=SynchedEntityData.defineId(LockedStrikeEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LEVEL=SynchedEntityData.defineId(LockedStrikeEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COUNT=SynchedEntityData.defineId(LockedStrikeEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(LockedStrikeEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<CompoundTag> ANCHOR=SynchedEntityData.defineId(LockedStrikeEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private UUID ownerId,family,targetId;
    private LivingEntity casterReference;
    private final List<HoverCannonEntity> suspended=new ArrayList<>();
    private PausedCannonRecast pausedRecast;
    private boolean resumed,rangedDamageDone,finalDamageDone;
    private int meleeDamageIndex=-1;

    public LockedStrikeEntity(EntityType<? extends LockedStrikeEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    @Override protected void defineSynchedData(){entityData.define(OWNER,-1);entityData.define(TARGET,-1);entityData.define(MODE,HoverCannonEntity.MODE_RANGED);entityData.define(LEVEL,1);entityData.define(COUNT,2);entityData.define(START,0L);entityData.define(ANCHOR,new CompoundTag());}
    public static LockedStrikeEntity forCaster(LivingEntity caster){if(caster.level() instanceof ServerLevel world)for(var e:world.getAllEntities())if(e instanceof LockedStrikeEntity s&&!s.isRemoved()&&Objects.equals(s.ownerId,caster.getUUID()))return s;return null;}
    public static LockedStrikeEntity begin(LivingEntity caster,LivingEntity target,int level){if(!(caster.level() instanceof ServerLevel world))return null;var e=LockedStrikeRegistry.ENTITY.create(world);if(e==null)return null;e.initialize(caster,target,level);world.addFreshEntity(e);return e;}
    public static LivingEntity acquireTarget(LivingEntity caster,double range){
        Vec3 start=caster.getEyePosition(),end=start.add(caster.getLookAngle().normalize().scale(range));LivingEntity best=null;double bestDist=range*range;
        for(var e:caster.level().getEntitiesOfClass(LivingEntity.class,new AABB(start,end).inflate(1.0),t->t!=caster&&t.isAlive()&&!t.isSpectator())){
            if(!WhaleAllies.hostile(e,caster.getUUID(),WhaleAllies.family(caster),caster))continue;
            var hit=e.getBoundingBox().inflate(.35).clip(start,end);if(hit.isPresent()){double d=start.distanceToSqr(hit.get());if(d<bestDist){best=e;bestDist=d;}}
        }return best;
    }
    private void initialize(LivingEntity caster,LivingEntity target,int spellLevel){
        casterReference=caster;ownerId=caster.getUUID();family=WhaleAllies.family(caster);targetId=target.getUUID();entityData.set(OWNER,caster.getId());entityData.set(TARGET,target.getId());entityData.set(LEVEL,Math.max(1,Math.min(2,spellLevel)));entityData.set(START,level().getGameTime());
        var cannons=HoverCannonEntity.owned((ServerLevel)level(),ownerId).stream().filter(c->!c.dismissing()).sorted(Comparator.comparingInt(HoverCannonEntity::slot)).toList();
        int mode=cannons.stream().anyMatch(HoverCannonEntity::meleeOrSwitchingToMelee)?HoverCannonEntity.MODE_MELEE:HoverCannonEntity.MODE_RANGED;entityData.set(MODE,mode);
        int count=mode==HoverCannonEntity.MODE_MELEE?2:Math.max(1,cannons.size());entityData.set(COUNT,count);updateAnchor(target);
        if(mode==HoverCannonEntity.MODE_MELEE){
            int synergy=cannons.isEmpty()?1:cannons.get(0).spellLevel();
            for(int slot=0;slot<2;slot++){final int wanted=slot;var c=cannons.stream().filter(x->x.slot()==wanted).findFirst().orElse(null);if(c==null){c=CannonRegistry.ENTITY.create(level());if(c!=null){c.initialize(caster,2,slot,synergy);c.forceMode(HoverCannonEntity.MODE_MELEE);level().addFreshEntity(c);}}}
            cannons=HoverCannonEntity.owned((ServerLevel)level(),ownerId).stream().filter(c->!c.dismissing()).sorted(Comparator.comparingInt(HoverCannonEntity::slot)).toList();
        }
        for(var c:cannons){c.suspendQuick();suspended.add(c);}
        pauseRecast(caster);
    }
    private void pauseRecast(LivingEntity caster){if(caster instanceof ServerPlayer){var recasts=MagicData.getPlayerMagicData(caster).getPlayerRecasts();var original=recasts.getRecastInstance(CannonRegistry.SPELL.getSpellId());if(original!=null){pausedRecast=new PausedCannonRecast(original);recasts.forceAddRecast(pausedRecast);recasts.syncToPlayer(pausedRecast);}}}
    private void resumeCannons(){if(resumed||level().isClientSide)return;resumed=true;for(var c:suspended)if(!c.isRemoved()&&!c.dismissing())c.resumeQuick(200);if(casterReference instanceof ServerPlayer){var recasts=MagicData.getPlayerMagicData(casterReference).getPlayerRecasts();if(pausedRecast!=null&&recasts.getRecastInstance(CannonRegistry.SPELL.getSpellId())==pausedRecast){var restored=pausedRecast.restored(200);recasts.forceAddRecast(restored);recasts.syncToPlayer(restored);}}}
    public LivingEntity owner(){var e=level().getEntity(entityData.get(OWNER));return e instanceof LivingEntity l?l:null;}
    public LivingEntity target(){var e=level().getEntity(entityData.get(TARGET));return e instanceof LivingEntity l?l:null;}
    public int mode(){return entityData.get(MODE);}public int spellLevel(){return entityData.get(LEVEL);}public int cannonCount(){return entityData.get(COUNT);}public float age(float partial){return level().getGameTime()-entityData.get(START)+partial;}
    public Vec3 anchor(){var t=entityData.get(ANCHOR);return new Vec3(t.getDouble("x"),t.getDouble("y"),t.getDouble("z"));}
    public float targetWidth(){var t=entityData.get(ANCHOR);return Math.max(.2f,t.getFloat("w"));}public float targetHeight(){var t=entityData.get(ANCHOR);return Math.max(.2f,t.getFloat("h"));}
    private void updateAnchor(LivingEntity target){var t=new CompoundTag();Vec3 p=target.position();t.putDouble("x",p.x);t.putDouble("y",p.y);t.putDouble("z",p.z);t.putFloat("w",target.getBbWidth());t.putFloat("h",target.getBbHeight());entityData.set(ANCHOR,t);setPos(p);}
    public int attackStart(){return VANISH+APPEAR;}
    public int endTick(){return mode()==HoverCannonEntity.MODE_RANGED?attackStart()+RANGED_BEAM+FINAL_BEAM+FINISH_VANISH:attackStart()+MELEE_COMBO+V_PAUSE+FINAL_RUSH+FINISH_VANISH;}
    private boolean validTarget(LivingEntity t){return t!=null&&t.isAlive()&&Objects.equals(t.getUUID(),targetId);}
    private void exactDamage(LivingEntity target,float amount){if(target==null||!target.isAlive())return;target.invulnerableTime=0;DamageSources.ignoreNextKnockback(target);float resist=Math.max(.001f,DamageSources.getResist(target,LockedStrikeRegistry.SPELL.getSchoolType()));DamageSources.applyDamage(target,amount/resist,SpellDamageSource.source(this,owner()==null?this:owner(),LockedStrikeRegistry.SPELL));target.setDeltaMovement(target.getDeltaMovement());}
    private void finalKnockback(LivingEntity t){var owner=owner();if(t==null||!t.isAlive())return;Vec3 away=t.position().subtract(owner==null?anchor():owner.position());away=new Vec3(away.x,0,away.z);if(away.lengthSqr()<1e-6)away=new Vec3(0,0,1);away=away.normalize();t.setDeltaMovement(away.x*1.45,Math.max(.18,t.getDeltaMovement().y),away.z*1.45);t.hurtMarked=true;}
    @Override public void tick(){
        super.tick();if(level().isClientSide)return;var owner=owner();if(owner==null||!owner.isAlive()){resumeCannons();discard();return;}
        var target=target();if(validTarget(target))updateAnchor(target);int a=(int)age(0);if(a<attackStart())return;
        if(mode()==HoverCannonEntity.MODE_RANGED){
            int local=a-attackStart();if(local>=0&&local<RANGED_BEAM&&!rangedDamageDone){rangedDamageDone=true;if(validTarget(target))for(int i=0;i<cannonCount();i++)exactDamage(target,LockedStrikeRegistry.SPELL.rangedDamage(spellLevel()));}
            if(local>=RANGED_BEAM&&!finalDamageDone){finalDamageDone=true;if(validTarget(target))exactDamage(target,LockedStrikeRegistry.SPELL.finisherBeamDamage(spellLevel()));}
        }else{
            int local=a-attackStart();if(local>=0&&local<MELEE_COMBO){int index=local/MELEE_HIT_INTERVAL;if(index!=meleeDamageIndex&&local%MELEE_HIT_INTERVAL==2){meleeDamageIndex=index;if(validTarget(target))exactDamage(target,LockedStrikeRegistry.SPELL.meleeDamage(spellLevel()));}}
            int finalStart=MELEE_COMBO+V_PAUSE;if(local>=finalStart+FINAL_RUSH/2&&!finalDamageDone){finalDamageDone=true;if(validTarget(target)){exactDamage(target,10);exactDamage(target,10);finalKnockback(target);}}
        }
        if(a>=endTick()-FINISH_VANISH)resumeCannons();if(a>=endTick())discard();
    }
    @Override public void remove(RemovalReason reason){if(!level().isClientSide)resumeCannons();super.remove(reason);}
    @Override public boolean shouldBeSaved(){return false;}@Override protected void readAdditionalSaveData(CompoundTag t){}@Override protected void addAdditionalSaveData(CompoundTag t){}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}@Override public AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(48);}
}
