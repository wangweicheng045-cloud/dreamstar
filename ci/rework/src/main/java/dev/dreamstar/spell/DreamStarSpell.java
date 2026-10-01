/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.redspace.ironsspellbooks.api.config.DefaultConfig
 *  io.redspace.ironsspellbooks.api.magic.MagicData
 *  io.redspace.ironsspellbooks.api.registry.SchoolRegistry
 *  io.redspace.ironsspellbooks.api.spells.AbstractSpell
 *  io.redspace.ironsspellbooks.api.spells.CastSource
 *  io.redspace.ironsspellbooks.api.spells.CastType
 *  io.redspace.ironsspellbooks.api.spells.SpellRarity
 */
package dev.dreamstar.spell;

import dev.dreamstar.Dreamstar;
import dev.dreamstar.domain.DomainEntity;
import dev.dreamstar.domain.DomainFamilyLock;
import dev.dreamstar.whale.WhaleAllies;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class DreamStarSpell
extends AbstractSpell {
    private static final ResourceLocation SPELL_ID = new ResourceLocation("dreamstar", "dream_star_domain");
    private static final int COOLDOWN_SECONDS = 180;
    private static final int CAST_TICKS = 100;
    private static final double CAST_HOVER_HEIGHT = 2.0;
    private final DefaultConfig config = new DefaultConfig().setMinRarity(SpellRarity.LEGENDARY).setSchoolResource(SchoolRegistry.ENDER_RESOURCE).setMaxLevel(1).setCooldownSeconds(180.0).build();

    public DreamStarSpell() {
        this.baseManaCost = 120;
        this.manaCostPerLevel = 0;
        this.castTime = 100;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
    }

    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    public DefaultConfig getDefaultConfig() {
        return this.config;
    }

    public CastType getCastType() {
        return CastType.LONG;
    }

    public int getEffectiveCastTime(int spellLevel, LivingEntity entity) {
        return 100;
    }

    public int getSpellCooldown() {
        return COOLDOWN_SECONDS * 20;
    }

    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(Component.translatable((String)"ui.dreamstar.duration", (Object[])new Object[]{60}), Component.translatable((String)"ui.dreamstar.radius", (Object[])new Object[]{40}));
    }

    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity caster, MagicData magicData) {
        if (!(level instanceof ServerLevel)) {
            return true;
        }
        ServerLevel server = (ServerLevel)level;
        UUID familyId = WhaleAllies.family(caster);
        if (!DomainFamilyLock.isDomainActive(server, familyId)) {
            return true;
        }
        if (caster instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer)caster;
            long seconds = (DomainFamilyLock.remainingActiveTicks(server, familyId) + 19L) / 20L;
            player.displayClientMessage(Component.translatable((String)"ui.dreamstar.family_domain_active", (Object[])new Object[]{seconds}), true);
        }
        return false;
    }

    public void onServerCastTick(Level level, int spellLevel, LivingEntity caster, MagicData magicData) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel server = (ServerLevel)level;
        double groundY = DreamStarSpell.groundY(caster);
        double targetY = groundY + 2.0;
        double error = targetY - caster.getY();
        double step = Mth.clamp((double)error, (double)-0.02, (double)0.026);
        if (Math.abs(step) > 0.001) {
            double nextY = caster.getY() + step;
            if (caster instanceof ServerPlayer) {
                ServerPlayer player = (ServerPlayer)caster;
                player.connection.teleport(player.getX(), nextY, player.getZ(), player.getYRot(), player.getXRot());
            } else {
                caster.move(MoverType.SELF, new Vec3(0.0, step, 0.0));
            }
        }
        Vec3 motion = caster.getDeltaMovement();
        caster.setDeltaMovement(motion.x * 0.35, 0.0, motion.z * 0.35);
        caster.fallDistance = 0.0f;
        Vec3 pullTarget = new Vec3(caster.getX(), caster.getY() + (double)caster.getBbHeight() * 0.58, caster.getZ());
        for (int i = 0; i < 9; ++i) {
            double angle = caster.getRandom().nextDouble() * Math.PI * 2.0;
            double radius = 2.0 + caster.getRandom().nextDouble() * 2.6;
            double sourceX = caster.getX() + Math.cos(angle) * radius;
            double sourceY = caster.getY() - 0.15 + caster.getRandom().nextDouble() * ((double)caster.getBbHeight() + 2.0);
            double sourceZ = caster.getZ() + Math.sin(angle) * radius;
            server.sendParticles(ParticleTypes.ENCHANT, pullTarget.x, pullTarget.y, pullTarget.z, 0, sourceX - pullTarget.x, sourceY - pullTarget.y, sourceZ - pullTarget.z, 1.0);
        }
    }

    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data) {
        if (!level.isClientSide && level instanceof ServerLevel) {
            ServerLevel server = (ServerLevel)level;
            UUID familyId = WhaleAllies.family(caster);
            if (DomainFamilyLock.isDomainActive(server, familyId)) {
                return;
            }
            DomainEntity domain = (DomainEntity)Dreamstar.DOMAIN.get().create(level);
            if (domain != null) {
                domain.setPos(caster.getX(), DreamStarSpell.groundY(caster), caster.getZ());
                domain.initialize(40.0f, caster);
                if (level.addFreshEntity(domain)) {
                    DomainFamilyLock.lockActiveDomain(server, familyId, 1200L);
                }
            }
        }
        super.onCast(level, spellLevel, caster, source, data);
    }

    private static double groundY(LivingEntity caster) {
        Vec3 from = new Vec3(caster.getX(), caster.getY() + 0.25, caster.getZ());
        Vec3 to = from.add(0.0, -64.0, 0.0);
        BlockHitResult hit = caster.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS ? caster.getY() : hit.getLocation().y;
    }
}
