package dev.dreamstar.time;

import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class TimeCrystal extends AbstractMagicProjectile {
    private static final EntityDataAccessor<Boolean> LAUNCHED = SynchedEntityData.defineId(TimeCrystal.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ORBIT_INDEX = SynchedEntityData.defineId(TimeCrystal.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ORBIT_COUNT = SynchedEntityData.defineId(TimeCrystal.class, EntityDataSerializers.INT);
    private UUID homingTargetUUID;
    private Entity cachedHomingTarget;
    private int launchedTicks;

    public TimeCrystal(EntityType<? extends TimeCrystal> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(LAUNCHED, false);
        entityData.define(ORBIT_INDEX, 0);
        entityData.define(ORBIT_COUNT, 1);
    }

    public boolean isLaunched() { return entityData.get(LAUNCHED); }
    public int getOrbitIndex() { return entityData.get(ORBIT_INDEX); }
    public int getOrbitCount() { return entityData.get(ORBIT_COUNT); }

    public void setOrbitSlot(int index, int count) {
        entityData.set(ORBIT_INDEX, Math.max(0, index));
        entityData.set(ORBIT_COUNT, Math.max(1, count));
    }

    public void launchForward(Vec3 forward) {
        if (isLaunched()) return;
        entityData.set(LAUNCHED, true);
        noPhysics = false;
        homingTargetUUID = null;
        cachedHomingTarget = null;
        launchedTicks = 0;
        if (forward.lengthSqr() < 1.0E-6D) forward = new Vec3(0, 0, 1);
        setDeltaMovement(forward.normalize().scale(getSpeed()));
        if (!level().isClientSide) {
            level().playSound(null, getX(), getY(), getZ(), TimeRegistry.TIME_CRYSTAL_LAUNCH_SOUND,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    @Override
    public void handleHitDetection() {
        if (isLaunched()) super.handleHitDetection();
    }

    @Override
    public void travel() {
        if (isLaunched()) {
            super.travel();
            return;
        }
        Entity owner = getOwner();
        if (!(owner instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()) {
            discard();
            return;
        }
        noPhysics = true;
        setDeltaMovement(Vec3.ZERO);

        Vec3 forward = living.getLookAngle();
        forward = new Vec3(forward.x, 0.0D, forward.z);
        if (forward.lengthSqr() < 1.0E-6D) {
            float yaw = living.getYRot() * ((float)Math.PI / 180F);
            forward = new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
        }
        forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 center = living.getEyePosition().subtract(forward.scale(1.15D)).add(0.0D, 0.05D, 0.0D);
        int count = Math.max(1, getOrbitCount());
        double angle = level().getGameTime() * 0.085D + Mth.TWO_PI * getOrbitIndex() / count;
        Vec3 offset = right.scale(Math.cos(angle) * 1.15D).add(0.0D, Math.sin(angle) * 0.78D, 0.0D);
        setPos(center.add(offset));
    }

    @Override
    public void tick() {
        super.tick();
        if (!isLaunched() || level().isClientSide) return;

        launchedTicks++;
        LivingEntity owner = getOwner() instanceof LivingEntity living ? living : null;
        if (owner == null || !owner.isAlive()) return;

        Entity target = resolveHomingTarget();
        LivingEntity livingTarget = target instanceof LivingEntity living ? living : null;
        if (livingTarget == null || !TimeCrystalSpell.isTrackingTarget(owner, livingTarget, position())) {
            homingTargetUUID = null;
            cachedHomingTarget = null;
            livingTarget = TimeCrystalSpell.findTrackingTarget(owner, position());
            if (livingTarget != null) rememberTarget(livingTarget);
        }

        if (livingTarget != null && launchedTicks > 2) {
            doHomingTowards(livingTarget);
        }
    }

    private void rememberTarget(LivingEntity target) {
        homingTargetUUID = target.getUUID();
        cachedHomingTarget = target;
    }

    private Entity resolveHomingTarget() {
        if (cachedHomingTarget != null && !cachedHomingTarget.isRemoved()) return cachedHomingTarget;
        if (homingTargetUUID != null && level() instanceof ServerLevel server) {
            cachedHomingTarget = server.getEntity(homingTargetUUID);
            return cachedHomingTarget;
        }
        return null;
    }

    private void doHomingTowards(LivingEntity target) {
        Vec3 motion = getDeltaMovement();
        double speed = motion.length();
        if (speed < 1.0E-6D) speed = getSpeed();

        Vec3 targetCenter = target.getBoundingBox().getCenter();
        Vec3 toTarget = targetCenter.subtract(position());
        double distance = toTarget.length();
        if (distance < 1.0E-6D) return;

        double leadTicks = Mth.clamp(distance / Math.max(speed, 0.01D), 0.0D, 8.0D);
        Vec3 predicted = targetCenter.add(target.getDeltaMovement().scale(leadTicks * 0.55D));
        Vec3 desired = predicted.subtract(position()).normalize();
        Vec3 current = motion.lengthSqr() < 1.0E-8D ? desired : motion.normalize();

        double turn = Mth.clamp(0.10D + (launchedTicks - 3) * 0.012D, 0.10D, 0.30D);
        Vec3 curved = current.scale(1.0D - turn).add(desired.scale(turn));
        if (curved.lengthSqr() < 1.0E-8D) curved = desired;
        setDeltaMovement(curved.normalize().scale(speed));
    }

    @Override
    public void trailParticles() {
        if (!isLaunched()) return;
        Vec3 motion = getDeltaMovement();
        int count = Mth.clamp((int)(motion.lengthSqr() * 3.0D), 2, 6);
        Vec3 previous = position().subtract(motion);
        for (int i = 0; i < count; i++) {
            double t = (i + 0.5D) / count;
            Vec3 point = previous.lerp(position(), t);
            Vec3 jitter = Utils.getRandomVec3(0.045D);
            level().addParticle(ParticleTypes.END_ROD,
                    point.x + jitter.x, point.y + jitter.y, point.z + jitter.z,
                    jitter.x * 0.25D, jitter.y * 0.25D, jitter.z * 0.25D);
        }
    }

    @Override
    public void impactParticles(double x, double y, double z) {
        MagicManager.spawnParticles(level(), ParticleTypes.END_ROD, x, y, z, 30, 0.32D, 0.32D, 0.32D, 0.08D, true);
    }

    @Override public float getSpeed() { return 1.85F; }
    @Override public Optional<Supplier<SoundEvent>> getImpactSound() { return Optional.empty(); }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!level().isClientSide) {
            DamageSources.applyDamage(hit.getEntity(), damage, TimeRegistry.TIME_CRYSTAL_SPELL.getDamageSource(this, getOwner()));
            hit.getEntity().invulnerableTime = 0;
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (!level().isClientSide) discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Launched", isLaunched());
        tag.putInt("OrbitIndex", getOrbitIndex());
        tag.putInt("OrbitCount", getOrbitCount());
        tag.putInt("LaunchedTicks", launchedTicks);
        if (homingTargetUUID != null) tag.putUUID("HomingTarget", homingTargetUUID);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(LAUNCHED, tag.getBoolean("Launched"));
        entityData.set(ORBIT_INDEX, tag.getInt("OrbitIndex"));
        entityData.set(ORBIT_COUNT, Math.max(1, tag.getInt("OrbitCount")));
        launchedTicks = tag.getInt("LaunchedTicks");
        if (tag.hasUUID("HomingTarget")) homingTargetUUID = tag.getUUID("HomingTarget");
        noPhysics = !isLaunched();
    }
}
