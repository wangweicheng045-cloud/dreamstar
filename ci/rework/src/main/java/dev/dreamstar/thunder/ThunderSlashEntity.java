package dev.dreamstar.thunder;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
public final class ThunderSlashEntity extends Entity {
    private static final EntityDataAccessor<Integer> TARGET_ID=SynchedEntityData.defineId(ThunderSlashEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE=SynchedEntityData.defineId(ThunderSlashEntity.class,EntityDataSerializers.FLOAT);
    private UUID ownerUuid;
    public ThunderSlashEntity(EntityType<? extends ThunderSlashEntity> type,Level level){super(type,level);noPhysics=true;}
    @Override protected void defineSynchedData(){entityData.define(TARGET_ID,-1);entityData.define(DAMAGE,0F);}
    public void initialize(LivingEntity target,LivingEntity owner,float damage){entityData.set(TARGET_ID,target.getId());entityData.set(DAMAGE,damage);ownerUuid=owner.getUUID();follow(target);}
    public int targetId(){return entityData.get(TARGET_ID);}
    public LivingEntity target(){Entity e=level().getEntity(targetId());return e instanceof LivingEntity l?l:null;}
    private LivingEntity owner(){if(ownerUuid==null||!(level() instanceof ServerLevel s))return null;Entity e=s.getEntity(ownerUuid);return e instanceof LivingEntity l?l:null;}
    public boolean matchesTarget(int id){return targetId()==id&&!isRemoved();}
    private void follow(LivingEntity t){Vec3 c=t.getBoundingBox().getCenter();setPos(c.x,c.y,c.z);}
    @Override public void tick(){super.tick();LivingEntity t=target();if(t==null||!t.isAlive()||t.isRemoved()){discard();return;}follow(t);if(!level().isClientSide&&tickCount==6){LivingEntity o=owner();if(o!=null&&o.isAlive()){DamageSources.applyDamage(t,entityData.get(DAMAGE),ThunderRegistry.SPELL.getDamageSource(this,o));t.invulnerableTime=0;}}if(tickCount>=18)discard();}
    @Override protected void readAdditionalSaveData(CompoundTag tag){if(tag.hasUUID("Owner"))ownerUuid=tag.getUUID("Owner");entityData.set(TARGET_ID,tag.getInt("Target"));entityData.set(DAMAGE,tag.getFloat("Damage"));}
    @Override protected void addAdditionalSaveData(CompoundTag tag){if(ownerUuid!=null)tag.putUUID("Owner",ownerUuid);tag.putInt("Target",targetId());tag.putFloat("Damage",entityData.get(DAMAGE));}
}