package dev.dreamstar.spell;

import dev.dreamstar.Dreamstar;
import dev.dreamstar.domain.DomainEntity;
import dev.dreamstar.domain.DomainFamilyLock;
import dev.dreamstar.domain.DomainTiming;
import dev.dreamstar.whale.WhaleAllies;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class DreamStarSpell extends AbstractSpell {
    private static final ResourceLocation SPELL_ID = new ResourceLocation("dreamstar", "dream_star_domain");
    private static final int COOLDOWN_SECONDS = 7 * 60;
    private static final int CAST_TICKS = 5 * 20;
    private static final double CAST_HOVER_HEIGHT = 2.0D;

    private final DefaultConfig config = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.ICE_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(COOLDOWN_SECONDS)
            .build();

    public DreamStarSpell() {
        this.baseManaCost = 120;
        this.manaCostPerLevel = 0;
        this.castTime = CAST_TICKS;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return this.config;
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    @Override
    public int getEffectiveCastTime(int spellLevel, LivingEntity entity) {
        return CAST_TICKS;
    }

    @Override
    public int getSpellCooldown() {
        return COOLDOWN_SECONDS * 20;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.dreamstar.duration", 60),
                Component.translatable("ui.dreamstar.radius", Dreamstar.DOMAIN_RADIUS)
        );
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity caster, MagicData magicData) {
        if (!(level instanceof ServerLevel server)) return true;

        UUID familyId = WhaleAllies.family(caster);
        if (!DomainFamilyLock.isDomainActive(server, familyId)) return true;

        if (caster instanceof ServerPlayer player) {
            long seconds = (DomainFamilyLock.remainingActiveTicks(server, familyId) + 19L) / 20L;
            player.displayClientMessage(Component.translatable("ui.dreamstar.family_domain_active", seconds), true);
        }
        return false;
    }

    /** Five-second levitation and inward-moving enchanting particles while the spell is being cast. */
    @Override
    public void onServerCastTick(Level level, int spellLevel, LivingEntity caster, MagicData magicData) {
        if (!(level instanceof ServerLevel server)) return;

        double groundY = groundY(caster);
        double targetY = groundY + CAST_HOVER_HEIGHT;
        double error = targetY - caster.getY();

        // A player's ordinary movement packets overwrite Entity#move. Use a small server-authoritative
        // teleport step each tick so the rise is visibly synchronized instead of being rubber-banded.
        double step = Mth.clamp(error, -0.020D, 0.026D);
        if (Math.abs(step) > 0.001D) {
            double nextY = caster.getY() + step;
            if (caster instanceof ServerPlayer player) {
                player.connection.teleport(
                        player.getX(), nextY, player.getZ(), player.getYRot(), player.getXRot());
            } else {
                caster.move(MoverType.SELF, new Vec3(0.0D, step, 0.0D));
            }
        }

        Vec3 motion = caster.getDeltaMovement();
        caster.setDeltaMovement(motion.x * 0.35D, 0.0D, motion.z * 0.35D);
        caster.fallDistance = 0.0F;

        Vec3 pullTarget = new Vec3(
                caster.getX(),
                caster.getY() + caster.getBbHeight() * 0.58D,
                caster.getZ());

        // Vanilla ENCHANT particles interpret xyz as the point they converge on, and the delta
        // arguments as the initial offset. This makes particles visibly fly inward toward the caster.
        for (int i = 0; i < 9; i++) {
            double angle = caster.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = 2.0D + caster.getRandom().nextDouble() * 2.6D;
            double sourceX = caster.getX() + Math.cos(angle) * radius;
            double sourceY = caster.getY() - 0.15D
                    + caster.getRandom().nextDouble() * (caster.getBbHeight() + 2.0D);
            double sourceZ = caster.getZ() + Math.sin(angle) * radius;

            server.sendParticles(
                    ParticleTypes.ENCHANT,
                    pullTarget.x, pullTarget.y, pullTarget.z,
                    0,
                    sourceX - pullTarget.x,
                    sourceY - pullTarget.y,
                    sourceZ - pullTarget.z,
                    1.0D);
        }
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data) {
        if (!level.isClientSide && level instanceof ServerLevel server) {
            UUID familyId = WhaleAllies.family(caster);
            if (DomainFamilyLock.isDomainActive(server, familyId)) return;

            DomainEntity domain = (DomainEntity) ((EntityType<?>) Dreamstar.DOMAIN.get()).create(level);
            if (domain != null) {
                domain.setPos(caster.getX(), groundY(caster), caster.getZ());
                domain.initialize(Dreamstar.DOMAIN_RADIUS, caster);
                if (level.addFreshEntity((Entity) domain)) {
                    DomainFamilyLock.lockActiveDomain(server, familyId, DomainTiming.DURATION);
                }
            }
        }
        super.onCast(level, spellLevel, caster, source, data);
    }

    private static double groundY(LivingEntity caster) {
        Vec3 from = new Vec3(caster.getX(), caster.getY() + 0.25D, caster.getZ());
        Vec3 to = from.add(0.0D, -64.0D, 0.0D);
        var hit = caster.level().clip(new ClipContext(
                from,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                caster
        ));
        return hit.getType() == HitResult.Type.MISS ? caster.getY() : hit.getLocation().y;
    }
}
