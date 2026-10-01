/*
 * Decompiled with CFR 0.152.
 */
package dev.dreamstar.shard;

import dev.dreamstar.shard.StarShardPlacement;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public final class StarShardAttackEntity
extends Entity {
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(StarShardAttackEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(StarShardAttackEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START_TICK = SynchedEntityData.defineId(StarShardAttackEntity.class, (EntityDataSerializer)EntityDataSerializers.LONG);
    public static final int[] SPAWN_TICKS = new int[]{1, 6, 11};
    public static final int FIRE_TICK = 16;
    public static final int BEAM_DURATION = 4;
    public static final int END_TICK = 28;
    public static final float DAMAGE_PER_BEAM = 4.0f;
    private UUID ownerUUID;

    public StarShardAttackEntity(EntityType<? extends StarShardAttackEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void initialize(LivingEntity target, LivingEntity owner, int seed) {
        this.entityData.set(TARGET_ID, target.getId());
        this.entityData.set(SEED, seed);
        this.entityData.set(START_TICK, this.level().getGameTime());
        this.ownerUUID = owner.getUUID();
        Vec3 aim = StarShardPlacement.aimPoint(target);
        this.setPos(aim.x, aim.y, aim.z);
    }

    public int targetId() {
        return (Integer)this.entityData.get(TARGET_ID);
    }

    public int seed() {
        return (Integer)this.entityData.get(SEED);
    }

    public long startTick() {
        return (Long)this.entityData.get(START_TICK);
    }

    public float sequenceAge(float partialTick) {
        return (float)this.level().getGameTime() + partialTick - (float)this.startTick();
    }

    public LivingEntity target() {
        LivingEntity living;
        Entity entity = this.level().getEntity(this.targetId());
        return entity instanceof LivingEntity ? (living = (LivingEntity)entity) : null;
    }

    public boolean matches(UUID owner, int targetEntityId) {
        return this.ownerUUID != null && this.ownerUUID.equals(owner) && this.targetId() == targetEntityId;
    }

    public float shardAlpha(int index, float partialTick) {
        float age = this.sequenceAge(partialTick);
        int spawn = SPAWN_TICKS[index];
        if (age < (float)spawn) {
            return 0.0f;
        }
        float appear = Math.min(1.0f, (age - (float)spawn + 1.0f) / 2.0f);
        if (age <= 16.0f) {
            return appear;
        }
        float fade = 1.0f - (age - 16.0f) / 12.0f;
        return Math.max(0.0f, Math.min(1.0f, fade));
    }

    public float beamAlpha(float partialTick) {
        float age = this.sequenceAge(partialTick);
        if (age < 16.0f || age > 20.0f) {
            return 0.0f;
        }
        float t = (age - 16.0f) / 4.0f;
        return Math.max(0.0f, 1.0f - t * 0.65f);
    }

    protected void defineSynchedData() {
        this.entityData.define(TARGET_ID, -1);
        this.entityData.define(SEED, 0);
        this.entityData.define(START_TICK, 0L);
    }

    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        LivingEntity target = this.target();
        if (target == null || target.isRemoved() || !target.isAlive()) {
            this.discard();
            return;
        }
        Vec3 aim = StarShardPlacement.aimPoint(target);
        this.setPos(aim.x, aim.y, aim.z);
        int age = (int)(this.level().getGameTime() - this.startTick());
        for (int i = 0; i < SPAWN_TICKS.length; ++i) {
            if (age != SPAWN_TICKS[i]) continue;
            Vec3 pos = StarShardPlacement.shardPosition(target, this.seed(), i);
            this.level().playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.PLAYERS, 0.95f, 0.96f + (float)i * 0.04f);
        }
        if (age == 16) {
            this.fire(target);
        }
        if (age >= 28) {
            this.discard();
        }
    }

    private void fire(LivingEntity target) {
        ServerLevel server;
        block6: {
            block5: {
                Level level = this.level();
                if (!(level instanceof ServerLevel)) break block5;
                server = (ServerLevel)level;
                if (this.ownerUUID != null) break block6;
            }
            return;
        }
        Entity ownerEntity = server.getEntity(this.ownerUUID);
        if (!(ownerEntity instanceof LivingEntity owner) || owner.level() != this.level() || !owner.isAlive()) {
            return;
        }
        for (int i = 0; i < 3 && target.isAlive(); ++i) {
            target.invulnerableTime = 0;
            target.hurt(this.level().damageSources().indirectMagic(this, owner), 4.0f);
        }
    }

    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(TARGET_ID, tag.getInt("Target"));
        this.entityData.set(SEED, tag.getInt("Seed"));
        this.entityData.set(START_TICK, tag.getLong("Start"));
        this.ownerUUID = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Target", this.targetId());
        tag.putInt("Seed", this.seed());
        tag.putLong("Start", this.startTick());
        if (this.ownerUUID != null) {
            tag.putUUID("Owner", this.ownerUUID);
        }
    }

    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
