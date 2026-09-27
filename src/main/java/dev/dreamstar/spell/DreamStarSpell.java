package dev.dreamstar.spell;

import dev.dreamstar.Dreamstar;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

public final class DreamStarSpell extends AbstractSpell {
    private static final ResourceLocation SPELL_ID =
            new ResourceLocation(Dreamstar.ID, "dream_star_domain");

    private final DefaultConfig config = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.ICE_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(180)
            .build();

    public DreamStarSpell() {
        baseManaCost = 120;
        manaCostPerLevel = 0;
        castTime = 0;
        baseSpellPower = 0;
        spellPowerPerLevel = 0;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return config;
    }

    @Override
    public CastType getCastType() {
        return CastType.INSTANT;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.AMETHYST_BLOCK_CHIME);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.dreamstar.duration", 30),
                Component.translatable("ui.dreamstar.radius", Dreamstar.DOMAIN_RADIUS)
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data) {
        if (!level.isClientSide) {
            var domain = Dreamstar.DOMAIN.get().create(level);
            if (domain != null) {
                domain.setPos(caster.position());
                domain.initialize(Dreamstar.DOMAIN_RADIUS, caster.getUUID());
                level.addFreshEntity(domain);
            }
        }
        super.onCast(level, spellLevel, caster, source, data);
    }
}
