package dev.dreamstar.shard;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/** Server-authoritative timeline for the three Dream Star shards. */
public final class StarShardAttackEntity extends Entity {
    private static final EntityDataAccessor<Integer> TARGET_ID =
            SynchedEntityData.defineId(StarShardAttackEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEED =
            SynchedEntityData.defineId(StarShardAttackEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START_TICK =
            SynchedEntityData.defineId(StarShardAttackEntity.class, EntityDataSerializers.LONG);

    public static final int[] SPAWN_TICKS = {1, 6, 11};
    public static final int FIRE_TICK = 16;
    public static final int BEAM_DURATION = 4;
    public static final int END_TICK = 28;
    public static final float DAMAGE_PER_BEAM = 4.0F;

    private UUID ownerUUID;

    public StarShardAttackEntity(EntityType<? extends StarShardAttackEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public void initialize(LivingEntity target, ServerPlayer owner, int seed) {
        entityData.set(TARGET_ID, target.getId());
        entityData.set(SEED, seed);
        entityData.set(START_TICK, level().getGameTime());
        ownerUUID = owner.getUUID();
        Vec3 aim = StarShardPlacement.aimPoint(target);
        setPos(aim.x, aim.y, aim.z);
    }

    public int targetId() {
        return entityData.get(TARGET_ID);
    }

    public int seed() {
        return entityData.get(SEED);
    }

    public long startTick() {
        return entityData.get(START_TICK);
    }

    public float sequenceAge(float partialTick) {
        return level().getGameTime() + partialTick - startTick();
    }

    public LivingEntity target() {
        Entity entity = level().getEntity(targetId());
        return entity instanceof LivingEntity living ? living : null;
    }

    public boolean matches(UUID owner, int targetEntityId) {
        return ownerUUID != null && ownerUUID.equals(owner) && targetId() == targetEntityId;
    }

    public float shardAlpha(int index, float partialTick) {
        float age = sequenceAge(partialTick);
        int spawn = SPAWN_TICKS[index];
        if (age < spawn) return 0.0F;

        float appear = Math.min(1.0F, (age - spawn + 1.0F) / 2.0F);
        if (age <= FIRE_TICK) return appear;

        float fade = 1.0F - (age - FIRE_TICK) / (END_TICK - FIRE_TICK);
        return Math.max(0.0F, Math.min(1.0F, fade));
    }

    public float beamAlpha(float partialTick) {
        float age = sequenceAge(partialTick);
        if (age < FIRE_TICK || age > FIRE_TICK + BEAM_DURATION) return 0.0F;
        float t = (age - FIRE_TICK) / BEAM_DURATION;
        return Math.max(0.0F, 1.0F - t * 0.65F);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TARGET_ID, -1);
        entityData.define(SEED, 0);
        entityData.define(START_TICK, 0L);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        LivingEntity target = target();
        if (target == null || target.isRemoved() || !target.isAlive()) {
            discard();
            return;
        }

        Vec3 aim = StarShardPlacement.aimPoint(target);
        setPos(aim.x, aim.y, aim.z);

        int age = (int) (level().getGameTime() - startTick());
        for (int i = 0; i < SPAWN_TICKS.length; i++) {
            if (age == SPAWN_TICKS[i]) {
                Vec3 pos = StarShardPlacement.shardPosition(target, seed(), i);
                // Use the actual vanilla sound played when an amethyst block is placed.
                level().playSound(
                        null,
                        pos.x, pos.y, pos.z,
                        SoundEvents.AMETHYST_BLOCK_PLACE,
                        SoundSource.PLAYERS,
                        0.95F,
                        0.96F + i * 0.04F
                );
            }
        }

        if (age == FIRE_TICK) {
            fire(target);
        }

        if (age >= END_TICK) {
            discard();
        }
    }

    private void fire(LivingEntity target) {
        if (!(level() instanceof ServerLevel server) || ownerUUID == null) return;
        ServerPlayer owner = server.getServer().getPlayerList().getPlayer(ownerUUID);
        if (owner == null || owner.level() != level()) return;

        // Three independent four-point magic hits. Reset only the target's brief hurt cooldown
        // between the simultaneous beams so every beam contributes its requested damage.
        for (int i = 0; i < 3 && target.isAlive(); i++) {
            target.invulnerableTime = 0;
            target.hurt(level().damageSources().indirectMagic(this, owner), DAMAGE_PER_BEAM);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(TARGET_ID, tag.getInt("Target"));
        entityData.set(SEED, tag.getInt("Seed"));
        entityData.set(START_TICK, tag.getLong("Start"));
        ownerUUID = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Target", targetId());
        tag.putInt("Seed", seed());
        tag.putLong("Start", startTick());
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}