package dev.dreamstar.thunder;

import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class ThunderSlashEntity extends Entity {
    public static final int MODE_TARGET_SLASH = 0;
    public static final int MODE_OPENING_SLASH = 1;

    private static final int TARGET_DELAY_TICKS = 4;
    public static final int TARGET_FRAME_COUNT = 9;
    private static final float TARGET_FPS = 12.0F;
    private static final int TARGET_VISUAL_TICKS = 15;
    private static final int TARGET_DAMAGE_TICK = 5;

    public static final int OPENING_FRAME_COUNT = 12;
    private static final float OPENING_FPS = 12.0F;
    private static final int OPENING_ANIM_TICKS = 20;
    private static final int OPENING_DAMAGE_TICK = 4;
    private static final double OPENING_DEPTH = 5.0D;
    private static final double OPENING_HALF_WIDTH = 2.5D;
    private static final double OPENING_HALF_HEIGHT = 2.5D;

    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(ThunderSlashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(ThunderSlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> VISUAL_SCALE = SynchedEntityData.defineId(ThunderSlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> ANIMATION_START_TICK = SynchedEntityData.defineId(ThunderSlashEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(ThunderSlashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> FACING_YAW = SynchedEntityData.defineId(ThunderSlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FACING_PITCH = SynchedEntityData.defineId(ThunderSlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> OPENING_SPELL_LEVEL = SynchedEntityData.defineId(ThunderSlashEntity.class, EntityDataSerializers.INT);
    private static final ResourceLocation OPENING_SOUND_ID = new ResourceLocation("dreamstar", "thunder_opening_slash");

    private UUID ownerUuid;
    private boolean damageApplied;
    private boolean frozen;

    public ThunderSlashEntity(EntityType<? extends ThunderSlashEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TARGET_ID, -1);
        entityData.define(DAMAGE, 0F);
        entityData.define(VISUAL_SCALE, 1.15F);
        entityData.define(ANIMATION_START_TICK, 0L);
        entityData.define(MODE, MODE_TARGET_SLASH);
        entityData.define(FACING_YAW, 0F);
        entityData.define(FACING_PITCH, 0F);
        entityData.define(OPENING_SPELL_LEVEL, 1);
    }

    public void initialize(LivingEntity target, LivingEntity owner, float damage) {
        entityData.set(MODE, MODE_TARGET_SLASH);
        entityData.set(TARGET_ID, target.getId());
        entityData.set(DAMAGE, damage);
        entityData.set(ANIMATION_START_TICK, level().getGameTime() + TARGET_DELAY_TICKS);
        ownerUuid = owner.getUUID();
        follow(target);
    }

    public void initializeOpening(LivingEntity owner, float damage, int spellLevel) {
        entityData.set(MODE, MODE_OPENING_SLASH);
        entityData.set(TARGET_ID, -1);
        entityData.set(DAMAGE, damage);
        entityData.set(ANIMATION_START_TICK, level().getGameTime());
        entityData.set(OPENING_SPELL_LEVEL, Math.max(1, Math.min(3, spellLevel)));
        ownerUuid = owner.getUUID();
        entityData.set(FACING_YAW, owner.getYRot());
        entityData.set(FACING_PITCH, owner.getXRot());

        Vec3 forward = owner.getLookAngle().normalize();
        Vec3 origin = owner.position().add(0.0D, owner.getBbHeight() * 0.7D, 0.0D);
        Vec3 center = origin.add(forward.scale(OPENING_DEPTH * 0.5D));
        setPos(center.x, center.y, center.z);
        setYRot(owner.getYRot());
        setXRot(0.0F);
        entityData.set(VISUAL_SCALE, 6.5F);
        spawnOpeningParticles();
        playOpeningSound();
        frozen = true;
    }

    public int targetId() { return entityData.get(TARGET_ID); }
    public int mode() { return entityData.get(MODE); }
    public LivingEntity target() { Entity e = level().getEntity(targetId()); return e instanceof LivingEntity l ? l : null; }
    public float visualScale() { return entityData.get(VISUAL_SCALE); }
    public float facingYaw() { return entityData.get(FACING_YAW); }
    public float facingPitch() { return entityData.get(FACING_PITCH); }
    public long animationAge() { return level().getGameTime() - entityData.get(ANIMATION_START_TICK); }
    public int animationFrames() { return mode() == MODE_OPENING_SLASH ? OPENING_FRAME_COUNT : TARGET_FRAME_COUNT; }
    public int animationDuration() { return mode() == MODE_OPENING_SLASH ? OPENING_ANIM_TICKS : TARGET_VISUAL_TICKS; }

    public int openingFrame(float partialTick) {
        double elapsedTicks = Math.max(0.0D, animationAge() + partialTick);
        int frame = (int)Math.floor(elapsedTicks * OPENING_FPS / 20.0D);
        return Math.max(0, Math.min(OPENING_FRAME_COUNT - 1, frame));
    }

    public int targetFrame(float partialTick) {
        double elapsedTicks = Math.max(0.0D, animationAge() + partialTick);
        int frame = (int)Math.floor(elapsedTicks * TARGET_FPS / 20.0D);
        return Math.max(0, Math.min(TARGET_FRAME_COUNT - 1, frame));
    }

    private LivingEntity owner() {
        if (ownerUuid == null || !(level() instanceof ServerLevel s)) return null;
        Entity e = s.getEntity(ownerUuid);
        return e instanceof LivingEntity l ? l : null;
    }

    public boolean matchesTarget(int id) {
        return mode() == MODE_TARGET_SLASH && targetId() == id && !isRemoved();
    }

    private static Vec3 horizontalFacing(LivingEntity living) {
        Vec3 look = living.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() < 1.0E-6D) {
            Vec3 yawDirection = Vec3.directionFromRotation(0.0F, living.getYRot());
            horizontal = new Vec3(yawDirection.x, 0.0D, yawDirection.z);
        }
        return horizontal.normalize();
    }

    private void follow(LivingEntity target) {
        Vec3 c = target.getBoundingBox().getCenter();
        setPos(c.x, c.y, c.z);
        entityData.set(VISUAL_SCALE, Math.max(1.15F, Math.max(target.getBbHeight(), target.getBbWidth()) * 1.35F));
    }

    private void spawnOpeningParticles() {
        if (!(level() instanceof ServerLevel server)) return;
        MagicManager.spawnParticles(server, ParticleHelper.ELECTRICITY,
                getX(), getY(), getZ(),
                6, 0.8D, 0.45D, 0.8D, 0.055D, false);
    }

    private void playOpeningSound() {
        if (!(level() instanceof ServerLevel server)) return;
        server.playSound(null, getX(), getY(), getZ(),
                SoundEvent.createVariableRangeEvent(OPENING_SOUND_ID), SoundSource.PLAYERS,
                1.2F, 1.0F);
    }

    private void playComboSound() {
        if (!(level() instanceof ServerLevel server)) return;
        server.playSound(null, getX(), getY(), getZ(),
                SoundRegistry.LIGHTNING_LANCE_CAST.get(), SoundSource.PLAYERS,
                1.0F, 1.0F);
    }

    private boolean canHitOpeningTarget(LivingEntity owner, Entity target) {
        return target != owner
                && target instanceof LivingEntity living
                && living.isAlive()
                && !living.isSpectator()
                && !DamageSources.isFriendlyFireBetween(target, owner);
    }

    private void performOpeningDamage() {
        LivingEntity owner = owner();
        if (!(level() instanceof ServerLevel server) || owner == null || !owner.isAlive()) return;

        Vec3 forward = Vec3.directionFromRotation(facingPitch(), facingYaw()).normalize();
        Vec3 referenceUp = Math.abs(forward.dot(new Vec3(0.0D, 1.0D, 0.0D))) > 0.999D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = forward.cross(referenceUp).normalize();
        Vec3 up = right.cross(forward).normalize();
        Vec3 slashCenter = position();
        Vec3 origin = slashCenter.subtract(forward.scale(OPENING_DEPTH * 0.5D));
        AABB candidates = AABB.ofSize(slashCenter, OPENING_DEPTH + OPENING_HALF_WIDTH * 2.0D,
                OPENING_HALF_HEIGHT * 2.0D + OPENING_DEPTH,
                OPENING_DEPTH + OPENING_HALF_WIDTH * 2.0D);

        for (Entity entity : server.getEntities(owner, candidates, e -> canHitOpeningTarget(owner, e))) {
            Vec3 point = entity.getBoundingBox().getCenter();
            Vec3 relative = point.subtract(origin);
            double forwardDistance = relative.dot(forward);
            double sideDistance = Math.abs(relative.dot(right));
            double verticalDistance = Math.abs(relative.dot(up));

            if (forwardDistance < 0.0D || forwardDistance > OPENING_DEPTH) continue;
            if (sideDistance > OPENING_HALF_WIDTH) continue;
            if (verticalDistance > OPENING_HALF_HEIGHT) continue;

            DamageSources.applyDamage(entity, entityData.get(DAMAGE), ThunderRegistry.SPELL.getDamageSource(this, owner));
            entity.invulnerableTime = 0;
            MagicManager.spawnParticles(server, ParticleHelper.ELECTRICITY,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ(),
                    3, entity.getBbWidth() * 0.2D, entity.getBbHeight() * 0.2D, entity.getBbWidth() * 0.2D, 0.04D, false);
        }
    }

    private void grantOpeningBuff() {
        LivingEntity owner = owner();
        if (owner == null || !owner.isAlive()) return;
        int level = Math.max(1, Math.min(3, entityData.get(OPENING_SPELL_LEVEL)));
        owner.addEffect(new MobEffectInstance(
                ThunderRegistry.EFFECT,
                ThunderPhantomBladeSpell.durationTicks(level),
                level - 1,
                false,
                true,
                true));
    }

    @Override
    public void tick() {
        super.tick();
        long age = animationAge();

        if (mode() == MODE_OPENING_SLASH) {
            if (!level().isClientSide && !damageApplied && age >= OPENING_DAMAGE_TICK) {
                damageApplied = true;
                performOpeningDamage();
                grantOpeningBuff();
            }
            if (age >= OPENING_ANIM_TICKS) discard();
            return;
        }

        LivingEntity t = target();
        if (!level().isClientSide && age < 0L) {
            if (t == null || !t.isAlive() || t.isRemoved()) { discard(); return; }
            follow(t);
            return;
        }

        if (!level().isClientSide && !frozen) {
            if (t == null || !t.isAlive() || t.isRemoved()) { discard(); return; }
            follow(t);
            frozen = true;
            playComboSound();
        }

        if (!level().isClientSide && !damageApplied && age >= TARGET_DAMAGE_TICK) {
            damageApplied = true;
            if (t != null && t.isAlive() && !t.isRemoved()) {
                LivingEntity o = owner();
                if (o != null && o.isAlive()) {
                    float scaledDamage = entityData.get(DAMAGE) * ThunderRegistry.SPELL.getEntityPowerMultiplier(o);
                    DamageSources.applyDamage(t, scaledDamage, ThunderRegistry.SPELL.getDamageSource(this, o));
                    t.invulnerableTime = 0;
                    if (level() instanceof ServerLevel server) {
                        MagicManager.spawnParticles(server, ParticleHelper.ELECTRICITY,
                                t.getX(), t.getY() + t.getBbHeight() * 0.5D, t.getZ(),
                                8, t.getBbWidth() * 0.25D, t.getBbHeight() * 0.25D, t.getBbWidth() * 0.25D, 0.05D, false);
                    }
                }
            }
        }

        if (age >= TARGET_VISUAL_TICKS) discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 262144.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) ownerUuid = tag.getUUID("Owner");
        entityData.set(TARGET_ID, tag.getInt("Target"));
        entityData.set(DAMAGE, tag.getFloat("Damage"));
        entityData.set(VISUAL_SCALE, tag.contains("VisualScale") ? tag.getFloat("VisualScale") : 1.15F);
        entityData.set(ANIMATION_START_TICK, tag.getLong("AnimationStartTick"));
        entityData.set(MODE, tag.getInt("Mode"));
        entityData.set(FACING_YAW, tag.getFloat("FacingYaw"));
        entityData.set(FACING_PITCH, tag.getFloat("FacingPitch"));
        entityData.set(OPENING_SPELL_LEVEL, tag.contains("OpeningSpellLevel") ? tag.getInt("OpeningSpellLevel") : 1);
        damageApplied = tag.getBoolean("DamageApplied");
        frozen = tag.getBoolean("Frozen");
        setYRot(entityData.get(FACING_YAW));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) tag.putUUID("Owner", ownerUuid);
        tag.putInt("Target", targetId());
        tag.putFloat("Damage", entityData.get(DAMAGE));
        tag.putFloat("VisualScale", visualScale());
        tag.putLong("AnimationStartTick", entityData.get(ANIMATION_START_TICK));
        tag.putInt("Mode", mode());
        tag.putFloat("FacingYaw", facingYaw());
        tag.putFloat("FacingPitch", facingPitch());
        tag.putInt("OpeningSpellLevel", entityData.get(OPENING_SPELL_LEVEL));
        tag.putBoolean("DamageApplied", damageApplied);
        tag.putBoolean("Frozen", frozen);
    }
}
