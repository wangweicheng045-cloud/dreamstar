package dev.dreamstar.domain;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/** A stationary server-authoritative anchor. Vanilla tracking handles join/rejoin/dimensions. */
public final class DomainEntity extends Entity {
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(DomainEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(DomainEntity.class, EntityDataSerializers.FLOAT);

    public DomainEntity(EntityType<? extends DomainEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public void initialize(float radius) {
        entityData.set(START, level().getGameTime());
        entityData.set(RADIUS, radius);
    }

    public long startTick() { return entityData.get(START); }
    public float radius() { return entityData.get(RADIUS); }
    public float opacity(float partialTick) {
        return DomainTiming.opacity(level().getGameTime() + partialTick, startTick());
    }

    @Override protected void defineSynchedData() {
        entityData.define(START, 0L);
        entityData.define(RADIUS, 40f);
    }

    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && DomainTiming.expired(level().getGameTime(), startTick())) discard();
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(START, tag.getLong("Start"));
        entityData.set(RADIUS, Math.max(4, Math.min(64, tag.getFloat("Radius"))));
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("Start", startTick());
        tag.putFloat("Radius", radius());
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
