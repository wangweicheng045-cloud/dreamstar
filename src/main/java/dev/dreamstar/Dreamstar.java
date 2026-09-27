package dev.dreamstar;

import dev.dreamstar.domain.DomainEntity;
import dev.dreamstar.effect.DreamStarDomainEffect;
import dev.dreamstar.spell.DreamStarSpell;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(Dreamstar.ID)
public final class Dreamstar {
    public static final String ID = "dreamstar";

    public static final DeferredRegister<AbstractSpell> SPELLS =
            DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, ID);
    public static final RegistryObject<AbstractSpell> DREAM_STAR =
            SPELLS.register("dream_star_domain", DreamStarSpell::new);

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ID);
    public static final RegistryObject<EntityType<DomainEntity>> DOMAIN = ENTITIES.register("domain",
            () -> EntityType.Builder.<DomainEntity>of(DomainEntity::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f).clientTrackingRange(16).updateInterval(20)
                    .fireImmune().build(ID + ":domain"));

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ID);
    public static final RegistryObject<MobEffect> DREAM_STAR_DOMAIN_EFFECT =
            EFFECTS.register("dream_star_domain", () -> new DreamStarDomainEffect()
                    .addAttributeModifier(
                            Attributes.MOVEMENT_SPEED,
                            "c0e2c181-8d14-4fc8-9b34-354840b35840",
                            0.20D,
                            AttributeModifier.Operation.MULTIPLY_TOTAL)
                    .addAttributeModifier(
                            AttributeRegistry.ICE_SPELL_POWER.get(),
                            "835d64c6-3603-4f04-b26c-563d8844ca24",
                            0.40D,
                            AttributeModifier.Operation.MULTIPLY_BASE));

    public static final int DOMAIN_RADIUS = 40;

    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ID);
    public static final RegistryObject<SimpleParticleType> BOUNDARY_STAR =
            PARTICLES.register("boundary_star", () -> new SimpleParticleType(false));

    public Dreamstar() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        SPELLS.register(bus);
        ENTITIES.register(bus);
        EFFECTS.register(bus);
        PARTICLES.register(bus);
    }
}
