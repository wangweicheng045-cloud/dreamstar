package dev.dreamstar.time;

import dev.dreamstar.whale.WhaleAllies;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import io.redspace.ironsspellbooks.capabilities.magic.RecastResult;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public final class TimeCrystalSpell extends AbstractSpell {
    public static final int ACTIVE_TICKS = 200;
    public static final int AUTO_FIRE_INTERVAL = 20;
    public static final double TARGET_RANGE = 40.0D;
    private static final String NEXT_AUTO_FIRE_TAG = "dreamstar.time_crystal.next_auto_fire";

    private final DefaultConfig config = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(4)
            .setCooldownSeconds(20)
            .build();

    public TimeCrystalSpell() {
        baseManaCost = 80;
        manaCostPerLevel = 5;
        castTime = 0;
        baseSpellPower = 1;
        spellPowerPerLevel = 0;
    }

    @Override public net.minecraft.resources.ResourceLocation getSpellResource() { return TimeRegistry.TIME_CRYSTAL_SPELL_ID; }
    @Override public DefaultConfig getDefaultConfig() { return config; }
    @Override public CastType getCastType() { return CastType.INSTANT; }

    @Override
    public SpellRarity getRarity(int level) {
        return switch (Math.max(1, Math.min(4, level))) {
            case 1 -> SpellRarity.UNCOMMON;
            case 2 -> SpellRarity.RARE;
            case 3 -> SpellRarity.EPIC;
            default -> SpellRarity.LEGENDARY;
        };
    }

    @Override
    public int getMinLevelForRarity(SpellRarity rarity) {
        return switch (rarity) {
            case UNCOMMON -> 1;
            case RARE -> 2;
            case EPIC -> 3;
            case LEGENDARY -> 4;
            default -> 0;
        };
    }

    public static int crystalCount(int level) { return Math.max(3, Math.min(6, level + 2)); }
    public static float baseDamage(int level) { return 2.0F + 2.0F * Math.max(1, Math.min(4, level)); }

    public float projectileDamage(int level, LivingEntity caster) {
        return baseDamage(level) * getEntityPowerMultiplier(caster);
    }

    @Override public int getRecastCount(int spellLevel, @Nullable LivingEntity entity) { return crystalCount(spellLevel); }

    @Override
    public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.dreamstar.time_crystal.damage", baseDamage(level)),
                Component.translatable("ui.dreamstar.time_crystal.count", crystalCount(level)),
                Component.translatable("ui.dreamstar.time_crystal.duration", ACTIVE_TICKS / 20),
                Component.translatable("ui.dreamstar.time_crystal.auto")
        );
    }

    @Override
    public void onCast(Level world, int level, LivingEntity caster, CastSource source, MagicData data) {
        if (world instanceof ServerLevel server && caster instanceof ServerPlayer player) {
            var recasts = data.getPlayerRecasts();
            RecastInstance active = recasts.getRecastInstance(getSpellId());
            if (recasts.hasRecastForSpell(getSpellId()) && active != null) {
                fireAllRemaining(player, level);
                recasts.removeRecast(active, RecastResult.USED_ALL_RECASTS);
                player.getPersistentData().remove(NEXT_AUTO_FIRE_TAG);
            } else {
                int count = crystalCount(level);
                spawnOrbitingCrystals(server, player, level, count);
                recasts.addRecast(new TimeCrystalRecast(getSpellId(), level, count, ACTIVE_TICKS, source), data);
                player.getPersistentData().putLong(NEXT_AUTO_FIRE_TAG, server.getGameTime() + AUTO_FIRE_INTERVAL);
            }
        }
        super.onCast(world, level, caster, source, data);
    }

    @Override
    public void onRecastFinished(ServerPlayer player, RecastInstance instance, RecastResult result,
                                 io.redspace.ironsspellbooks.api.spells.ICastDataSerializable castData) {
        discardOrbiting(player);
        player.getPersistentData().remove(NEXT_AUTO_FIRE_TAG);
        super.onRecastFinished(player, instance, result, castData);
    }

    static void serverTick(ServerPlayer player) {
        MagicData data = MagicData.getPlayerMagicData(player);
        var recasts = data.getPlayerRecasts();
        RecastInstance active = recasts.getRecastInstance(TimeRegistry.TIME_CRYSTAL_SPELL.getSpellId());
        if (!recasts.hasRecastForSpell(TimeRegistry.TIME_CRYSTAL_SPELL.getSpellId()) || active == null) {
            player.getPersistentData().remove(NEXT_AUTO_FIRE_TAG);
            return;
        }
        long now = player.serverLevel().getGameTime();
        long next = player.getPersistentData().getLong(NEXT_AUTO_FIRE_TAG);
        if (next <= 0L) {
            player.getPersistentData().putLong(NEXT_AUTO_FIRE_TAG, now + AUTO_FIRE_INTERVAL);
            return;
        }
        if (now < next) return;

        if (!hasLaunchTrigger(player)) return;
        if (fireOne(player)) {
            player.getPersistentData().putLong(NEXT_AUTO_FIRE_TAG, now + AUTO_FIRE_INTERVAL);
            if (active instanceof TimeCrystalRecast crystalRecast) {
                crystalRecast.consumeOne(recasts);
            } else {
                // Only relevant if the server was restarted during this ten-second state.
                recasts.decrementRecastCount(TimeRegistry.TIME_CRYSTAL_SPELL.getSpellId());
            }
        }
    }

    private static void spawnOrbitingCrystals(ServerLevel level, ServerPlayer player, int spellLevel, int count) {
        float damage = TimeRegistry.TIME_CRYSTAL_SPELL.projectileDamage(spellLevel, player);
        for (int i = 0; i < count; i++) {
            TimeCrystal crystal = new TimeCrystal(TimeRegistry.CRYSTAL, level);
            crystal.setOwner(player);
            crystal.setDamage(damage);
            crystal.setOrbitSlot(i, count);
            crystal.moveTo(player.getX(), player.getEyeY(), player.getZ(), player.getYRot(), player.getXRot());
            level.addFreshEntity(crystal);
        }
    }

    private static List<TimeCrystal> orbiting(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(6.0D);
        return player.serverLevel().getEntitiesOfClass(TimeCrystal.class, box,
                        crystal -> !crystal.isLaunched() && crystal.getOwner() == player)
                .stream().sorted(Comparator.comparingInt(TimeCrystal::getOrbitIndex)).toList();
    }

    private static boolean fireOne(ServerPlayer player) {
        List<TimeCrystal> crystals = orbiting(player);
        if (crystals.isEmpty()) return false;
        crystals.get(0).launchForward(player.getLookAngle());
        reindex(crystals.subList(1, crystals.size()));
        return true;
    }

    private static void fireAllRemaining(ServerPlayer player, int spellLevel) {
        Vec3 forward = player.getLookAngle();
        for (TimeCrystal crystal : orbiting(player)) {
            crystal.launchForward(forward);
        }
    }

    private static void reindex(List<TimeCrystal> remaining) {
        int count = remaining.size();
        for (int i = 0; i < count; i++) remaining.get(i).setOrbitSlot(i, count);
    }

    private static void discardOrbiting(ServerPlayer player) {
        for (TimeCrystal crystal : orbiting(player)) crystal.discard();
    }

    public static boolean hasLaunchTrigger(ServerPlayer player) {
        return findTrackingTarget(player, player.position()) != null;
    }

    @Nullable
    public static LivingEntity findTrackingTarget(LivingEntity owner, Vec3 origin) {
        if (!(owner.level() instanceof ServerLevel level)) return null;

        LivingEntity attacked = owner.getLastHurtMob();
        if (attacked != null && isTrackingTarget(owner, attacked, origin)) return attacked;

        var family = WhaleAllies.family(owner);
        AABB search = new AABB(origin, origin).inflate(TARGET_RANGE);
        return level.getEntitiesOfClass(LivingEntity.class, search, target -> {
                    if (target == owner || !target.isAlive() || target.isSpectator()) return false;
                    if (target.position().distanceToSqr(origin) > TARGET_RANGE * TARGET_RANGE) return false;
                    if (!WhaleAllies.hostile(target, owner.getUUID(), family, owner)) return false;
                    return target instanceof Monster || target instanceof Mob mob && mob.getTarget() == owner;
                }).stream()
                .min(Comparator.comparingDouble(target -> target.position().distanceToSqr(origin)))
                .orElse(null);
    }

    public static boolean isTrackingTarget(LivingEntity owner, LivingEntity target, Vec3 origin) {
        if (target == owner || !target.isAlive() || target.isRemoved() || target.isSpectator()) return false;
        if (target.position().distanceToSqr(origin) > TARGET_RANGE * TARGET_RANGE) return false;

        var family = WhaleAllies.family(owner);
        if (!WhaleAllies.hostile(target, owner.getUUID(), family, owner)) return false;
        if (target == owner.getLastHurtMob()) return true;
        return target instanceof Monster || target instanceof Mob mob && mob.getTarget() == owner;
    }
}
