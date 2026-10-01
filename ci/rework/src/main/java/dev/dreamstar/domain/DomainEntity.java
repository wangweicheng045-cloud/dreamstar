/*
 * Decompiled with CFR 0.152.
 */
package dev.dreamstar.domain;

import dev.dreamstar.Dreamstar;
import dev.dreamstar.domain.DomainTiming;
import dev.dreamstar.whale.StarWhaleEntity;
import dev.dreamstar.whale.WhaleAllies;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

public final class DomainEntity
extends Entity
implements DomainCasterAccess {
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(DomainEntity.class, (EntityDataSerializer)EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(DomainEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    private UUID ownerUUID;
    private UUID casterUUID;
    private UUID whaleUUID;

    public void initialize(float radius, LivingEntity caster) {
        this.initialize(radius, WhaleAllies.family(caster));
        this.casterUUID = caster.getUUID();
    }

    public UUID casterUUID() {
        return this.casterUUID == null ? this.ownerUUID : this.casterUUID;
    }

    public boolean whaleActive() {
        return this.level().getGameTime() - this.startTick() < 1180L;
    }

    public boolean ownsWhale(UUID id) {
        return id.equals(this.whaleUUID);
    }

    public DomainEntity(EntityType<? extends DomainEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void initialize(float radius) {
        this.initialize(radius, (UUID)null);
    }

    public void initialize(float radius, UUID ownerUUID) {
        this.entityData.set(START, this.level().getGameTime());
        this.entityData.set(RADIUS, Float.valueOf(radius));
        this.ownerUUID = ownerUUID;
    }

    public long startTick() {
        return (Long)this.entityData.get(START);
    }

    public float radius() {
        return ((Float)this.entityData.get(RADIUS)).floatValue();
    }

    public UUID ownerUUID() {
        return this.ownerUUID;
    }

    public float opacity(float partialTick) {
        return DomainTiming.opacity((float)this.level().getGameTime() + partialTick, this.startTick());
    }

    protected void defineSynchedData() {
        this.entityData.define(START, 0L);
        this.entityData.define(RADIUS, Float.valueOf(40.0f));
    }

    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            Level level;
            if (DomainTiming.expired(this.level().getGameTime(), this.startTick())) {
                this.discard();
                return;
            }
            this.tickCasterBlessing();
            if (this.ownerUUID != null && this.whaleActive() && (level = this.level()) instanceof ServerLevel) {
                StarWhaleEntity whale;
                Entity current;
                ServerLevel server = (ServerLevel)level;
                Entity entity = current = this.whaleUUID == null ? null : server.getEntity(this.whaleUUID);
                if ((current == null || current.isRemoved()) && (whale = (StarWhaleEntity)Dreamstar.STAR_WHALE.get().create(this.level())) != null) {
                    whale.initialize(this);
                    if (server.addFreshEntity(whale)) {
                        this.whaleUUID = whale.getUUID();
                    }
                }
            }
        }
    }

    private void tickCasterBlessing() {
        Level level = this.level();
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        LivingEntity caster = this.resolveDomainCaster(server);
        if (caster == null || !caster.isAlive() || caster.isSpectator()) {
            return;
        }
        double radius = this.radius();
        if (caster.position().distanceToSqr(this.position()) > radius * radius) {
            return;
        }
        caster.addEffect(new MobEffectInstance(Dreamstar.DREAM_STAR_DOMAIN_EFFECT.get(), 20, 0, false, false, true));
    }

    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(START, tag.getLong("Start"));
        this.entityData.set(RADIUS, Float.valueOf(Math.max(4.0f, Math.min(64.0f, tag.getFloat("Radius")))));
        this.ownerUUID = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.casterUUID = tag.hasUUID("Caster") ? tag.getUUID("Caster") : this.ownerUUID;
    }

    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("Start", this.startTick());
        tag.putFloat("Radius", this.radius());
        if (this.casterUUID != null) {
            tag.putUUID("Caster", this.casterUUID);
        }
        if (this.ownerUUID != null) {
            tag.putUUID("Owner", this.ownerUUID);
        }
    }

    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
