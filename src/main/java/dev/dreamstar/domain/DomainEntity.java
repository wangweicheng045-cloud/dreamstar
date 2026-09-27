package dev.dreamstar.domain;

import dev.dreamstar.Dreamstar;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/** A stationary server-authoritative anchor. Vanilla tracking handles join/rejoin/dimensions. */
public final class DomainEntity extends Entity {
    private static final EntityDataAccessor<Long> START =
            SynchedEntityData.defineId(DomainEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(DomainEntity.class, EntityDataSerializers.FLOAT);

    private UUID ownerUUID;

    public DomainEntity(EntityType<? extends DomainEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** Kept for debug/preview callers that intentionally have no owning caster. */
    public void initialize(float radius) {
        initialize(radius, null);
    }

    public void initialize(float radius, UUID ownerUUID) {
        entityData.set(START, level().getGameTime());
        entityData.set(RADIUS, radius);
        this.ownerUUID = ownerUUID;
    }

    public long startTick() {
        return entityData.get(START);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public UUID ownerUUID() {
        return ownerUUID;
    }

    public float opacity(float partialTick) {
        return DomainTiming.opacity(level().getGameTime() + partialTick, startTick());
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(START, 0L);
        entityData.define(RADIUS, 40f);
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide) {
            if (DomainTiming.expired(level().getGameTime(), startTick())) {
                discard();
                return;
            }
            tickOwnerBlessing();
        }
    }

    private void tickOwnerBlessing() {
        if (ownerUUID == null) return;

        Player owner = level().getPlayerByUUID(ownerUUID);
        if (owner == null || !owner.isAlive() || owner.isSpectator()) return;

        double radius = radius();
        if (owner.position().distanceToSqr(position()) > radius * radius) return;

        // Refresh a one-second effect every server tick while the caster remains inside THEIR
        // own domain. Leaving it stops refresh, so the blessing naturally expires within one second.
        owner.addEffect(new MobEffectInstance(
                Dreamstar.DREAM_STAR_DOMAIN_EFFECT.get(),
                20,
                0,
                false,
                false,
                true
        ));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(START, tag.getLong("Start"));
        entityData.set(RADIUS, Math.max(4, Math.min(64, tag.getFloat("Radius"))));
        ownerUUID = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("Start", startTick());
        tag.putFloat("Radius", radius());
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
