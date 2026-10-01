package dev.dreamstar.domain;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

public final class DomainCastSignalEntity extends Entity {
    private static final EntityDataAccessor<Long> START_TICK = SynchedEntityData.defineId(DomainCastSignalEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> CASTER_ID = SynchedEntityData.defineId(DomainCastSignalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> COMPLETED = SynchedEntityData.defineId(DomainCastSignalEntity.class, EntityDataSerializers.BOOLEAN);
    private long lastTouchedTick;

    public DomainCastSignalEntity(EntityType<? extends DomainCastSignalEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(START_TICK, 0L);
        entityData.define(CASTER_ID, -1);
        entityData.define(COMPLETED, false);
    }

    public void initialize(LivingEntity caster) {
        entityData.set(START_TICK, level().getGameTime());
        entityData.set(CASTER_ID, caster.getId());
        entityData.set(COMPLETED, false);
        touch(caster);
    }

    public void touch(LivingEntity caster) {
        lastTouchedTick = level().getGameTime();
        setPos(caster.getX(), caster.getY(), caster.getZ());
    }

    public void complete(LivingEntity caster) {
        touch(caster);
        entityData.set(COMPLETED, true);
    }

    public long startTick() {
        return entityData.get(START_TICK);
    }

    public long castAge() {
        return level().getGameTime() - startTick();
    }

    public int casterId() {
        return entityData.get(CASTER_ID);
    }

    public boolean completed() {
        return entityData.get(COMPLETED);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        long now = level().getGameTime();
        if (completed()) {
            if (now - startTick() >= 108L) discard();
        } else if (now - lastTouchedTick > 3L) {
            discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(START_TICK, tag.getLong("Start"));
        entityData.set(CASTER_ID, tag.getInt("CasterId"));
        entityData.set(COMPLETED, tag.getBoolean("Completed"));
        lastTouchedTick = tag.getLong("LastTouched");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("Start", startTick());
        tag.putInt("CasterId", casterId());
        tag.putBoolean("Completed", completed());
        tag.putLong("LastTouched", lastTouchedTick);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
