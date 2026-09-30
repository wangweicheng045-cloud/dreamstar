package dev.dreamstar.thunder;

import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class ThunderSlashEntity extends Entity {
    private static final EntityDataAccessor<Integer> TARGET_ID=SynchedEntityData.defineId(ThunderSlashEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE=SynchedEntityData.defineId(ThunderSlashEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> VISUAL_SCALE=SynchedEntityData.defineId(ThunderSlashEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> ANIMATION_START_TICK=SynchedEntityData.defineId(ThunderSlashEntity.class,EntityDataSerializers.LONG);
    private UUID ownerUuid;
    private boolean damageApplied;
    private boolean frozen;

    public ThunderSlashEntity(EntityType<? extends ThunderSlashEntity> type, Level level){
        super(type,level);
        noPhysics=true;
    }

    @Override
    protected void defineSynchedData(){
        entityData.define(TARGET_ID,-1);
        entityData.define(DAMAGE,0F);
        entityData.define(VISUAL_SCALE,1.15F);
        entityData.define(ANIMATION_START_TICK,0L);
    }

    public void initialize(LivingEntity target,LivingEntity owner,float damage){
        entityData.set(TARGET_ID,target.getId());
        entityData.set(DAMAGE,damage);
        entityData.set(ANIMATION_START_TICK,level().getGameTime()+20L);
        ownerUuid=owner.getUUID();
        follow(target);
    }

    public int targetId(){return entityData.get(TARGET_ID);}
    public LivingEntity target(){Entity e=level().getEntity(targetId());return e instanceof LivingEntity l?l:null;}
    public float visualScale(){return entityData.get(VISUAL_SCALE);}
    public long animationAge(){return level().getGameTime()-entityData.get(ANIMATION_START_TICK);}

    private LivingEntity owner(){
        if(ownerUuid==null||!(level() instanceof ServerLevel s))return null;
        Entity e=s.getEntity(ownerUuid);
        return e instanceof LivingEntity l?l:null;
    }

    public boolean matchesTarget(int id){return targetId()==id&&!isRemoved();}

    private void follow(LivingEntity target){
        Vec3 c=target.getBoundingBox().getCenter();
        setPos(c.x,c.y,c.z);
        entityData.set(VISUAL_SCALE,java.lang.Math.max(1.15F,java.lang.Math.max(target.getBbHeight(),target.getBbWidth())*1.35F));
    }

    @Override
    public void tick(){
        super.tick();
        long age=animationAge();
        LivingEntity t=target();

        if(!level().isClientSide && age<0L){
            if(t==null||!t.isAlive()||t.isRemoved()){discard();return;}
            follow(t);
            return;
        }

        if(!level().isClientSide && !frozen){
            if(t==null||!t.isAlive()||t.isRemoved()){discard();return;}
            follow(t);
            frozen=true;
        }

        if(!level().isClientSide&&!damageApplied&&age>=6L){
            damageApplied=true;
            if(t!=null&&t.isAlive()&&!t.isRemoved()){
                LivingEntity o=owner();
                if(o!=null&&o.isAlive()){
                    DamageSources.applyDamage(t,entityData.get(DAMAGE),ThunderRegistry.SPELL.getDamageSource(this,o));
                    t.invulnerableTime=0;
                }
            }
        }

        if(age>=18L)discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag){
        if(tag.hasUUID("Owner"))ownerUuid=tag.getUUID("Owner");
        entityData.set(TARGET_ID,tag.getInt("Target"));
        entityData.set(DAMAGE,tag.getFloat("Damage"));
        entityData.set(VISUAL_SCALE,tag.contains("VisualScale")?tag.getFloat("VisualScale"):1.15F);
        entityData.set(ANIMATION_START_TICK,tag.getLong("AnimationStartTick"));
        damageApplied=tag.getBoolean("DamageApplied");
        frozen=tag.getBoolean("Frozen");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag){
        if(ownerUuid!=null)tag.putUUID("Owner",ownerUuid);
        tag.putInt("Target",targetId());
        tag.putFloat("Damage",entityData.get(DAMAGE));
        tag.putFloat("VisualScale",visualScale());
        tag.putLong("AnimationStartTick",entityData.get(ANIMATION_START_TICK));
        tag.putBoolean("DamageApplied",damageApplied);
        tag.putBoolean("Frozen",frozen);
    }
}
