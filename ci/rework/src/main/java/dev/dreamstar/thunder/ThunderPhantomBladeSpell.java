package dev.dreamstar.thunder;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

public final class ThunderPhantomBladeSpell extends AbstractSpell {
    private final DefaultConfig config = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.LIGHTNING_RESOURCE)
            .setMaxLevel(3)
            .setCooldownSeconds(60)
            .build();

    public ThunderPhantomBladeSpell() {
        baseManaCost = 100;
        manaCostPerLevel = 0;
        castTime = 10;
        baseSpellPower = 1;
        spellPowerPerLevel = 0;
    }

    @Override public ResourceLocation getSpellResource(){ return ThunderRegistry.SPELL_ID; }
    @Override public DefaultConfig getDefaultConfig(){ return config; }
    @Override public CastType getCastType(){ return CastType.LONG; }
    @Override public int getEffectiveCastTime(int spellLevel, LivingEntity caster){ return 10; }
    @Override public SpellRarity getRarity(int level){ return level<=1?SpellRarity.RARE:level==2?SpellRarity.EPIC:SpellRarity.LEGENDARY; }
    @Override public int getMinLevelForRarity(SpellRarity rarity){ return rarity==SpellRarity.RARE?1:rarity==SpellRarity.EPIC?2:rarity==SpellRarity.LEGENDARY?3:0; }

    @Override public AnimationHolder getCastStartAnimation(){ return SpellAnimations.ONE_HANDED_HORIZONTAL_SWING_ANIMATION; }
    @Override public AnimationHolder getCastFinishAnimation(){ return AnimationHolder.pass(); }

    public static int durationTicks(int level){ return level<=1?300:level==2?400:500; }
    public static float slashDamage(int level){ return level<=1?6.0F:level==2?10.0F:15.0F; }
    public static float openingSlashDamage(int level){ return level<=1?10.0F:level==2?13.0F:15.0F; }

    @Override
    public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster){
        return List.of(
                Component.translatable("ui.dreamstar.thunder_phantom_blade.opening_damage", openingSlashDamage(level)),
                Component.translatable("ui.dreamstar.thunder_phantom_blade.damage", slashDamage(level)),
                Component.translatable("ui.dreamstar.thunder_phantom_blade.duration", durationTicks(level)/20)
        );
    }

    @Override
    public void onCast(Level world, int level, LivingEntity caster, CastSource source, MagicData data){
        if(!world.isClientSide){
            ThunderSlashEntity slash = ThunderRegistry.SLASH.create(world);
            if (slash != null) {
                slash.initializeOpening(caster, openingSlashDamage(level), Math.max(1, Math.min(3, level)));
                world.addFreshEntity(slash);
            }
        }
        super.onCast(world,level,caster,source,data);
    }
}
