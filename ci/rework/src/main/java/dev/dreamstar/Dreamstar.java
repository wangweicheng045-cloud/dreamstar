package dev.dreamstar;

import dev.dreamstar.domain.DomainEntity;
import dev.dreamstar.domain.DomainCastSignalEntity;
import dev.dreamstar.effect.DreamStarDomainEffect;
import dev.dreamstar.shard.StarShardAttackEntity;
import dev.dreamstar.spell.DreamStarSpell;
import dev.dreamstar.whale.StarWhaleEntity;
import dev.dreamstar.whale.WhaleShardEntity;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(value="dreamstar")
public final class Dreamstar {
    public static final String ID = "dreamstar";
    public static final DeferredRegister<AbstractSpell> SPELLS = DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, "dreamstar");
    public static final RegistryObject<AbstractSpell> DREAM_STAR = SPELLS.register("dream_star_domain", DreamStarSpell::new);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "dreamstar");
    public static final RegistryObject<EntityType<DomainEntity>> DOMAIN = ENTITIES.register("domain", () -> EntityType.Builder.of(DomainEntity::new, MobCategory.MISC).sized(0.1f, 0.1f).clientTrackingRange(16).updateInterval(20).fireImmune().build("dreamstar:domain"));
    public static final RegistryObject<EntityType<DomainCastSignalEntity>> DOMAIN_CAST_SIGNAL = ENTITIES.register("domain_cast_signal", () -> EntityType.Builder.of(DomainCastSignalEntity::new, MobCategory.MISC).sized(0.1f, 0.1f).clientTrackingRange(16).updateInterval(1).fireImmune().build("dreamstar:domain_cast_signal"));
    public static final RegistryObject<EntityType<StarShardAttackEntity>> STAR_SHARD_ATTACK = ENTITIES.register("star_shard_attack", () -> EntityType.Builder.of(StarShardAttackEntity::new, MobCategory.MISC).sized(0.1f, 0.1f).clientTrackingRange(16).updateInterval(1).fireImmune().build("dreamstar:star_shard_attack"));
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, "dreamstar");
    public static final RegistryObject<MobEffect> DREAM_STAR_DOMAIN_EFFECT = EFFECTS.register("dream_star_domain", () -> new DreamStarDomainEffect().addAttributeModifier(Attributes.MOVEMENT_SPEED, "c0e2c181-8d14-4fc8-9b34-354840b35840", 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL).addAttributeModifier((Attribute)AttributeRegistry.ENDER_SPELL_POWER.get(), "835d64c6-3603-4f04-b26c-563d8844ca24", 0.4, AttributeModifier.Operation.MULTIPLY_BASE));
    public static final int DOMAIN_RADIUS = 40;
    public static final RegistryObject<EntityType<StarWhaleEntity>> STAR_WHALE = ENTITIES.register("star_whale", () -> EntityType.Builder.of(StarWhaleEntity::new, MobCategory.MISC).sized(3.5f, 3.5f).clientTrackingRange(16).updateInterval(1).fireImmune().build("dreamstar:star_whale"));
    public static final RegistryObject<EntityType<WhaleShardEntity>> WHALE_SHARD = ENTITIES.register("whale_shard", () -> EntityType.Builder.of(WhaleShardEntity::new, MobCategory.MISC).sized(0.5f, 1.7f).clientTrackingRange(16).updateInterval(2).fireImmune().build("dreamstar:whale_shard"));
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, "dreamstar");
    public static final RegistryObject<SimpleParticleType> BOUNDARY_STAR = PARTICLES.register("boundary_star", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> CONSTELLATION = PARTICLES.register("constellation", () -> new SimpleParticleType(false));

    public Dreamstar() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        SPELLS.register(bus);
        ENTITIES.register(bus);
        EFFECTS.register(bus);
        PARTICLES.register(bus);
    }
}
