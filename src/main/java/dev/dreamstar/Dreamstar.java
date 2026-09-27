package dev.dreamstar;

import dev.dreamstar.domain.DomainEntity;
import dev.dreamstar.spell.DreamStarSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(Dreamstar.ID)
public final class Dreamstar {
    public static final String ID = "dreamstar";
    public static final DeferredRegister<AbstractSpell> SPELLS = DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, ID);
    public static final RegistryObject<AbstractSpell> DREAM_STAR = SPELLS.register("dream_star_domain", DreamStarSpell::new);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ID);
    public static final RegistryObject<EntityType<DomainEntity>> DOMAIN = ENTITIES.register("domain",
            () -> EntityType.Builder.<DomainEntity>of(DomainEntity::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f).clientTrackingRange(16).updateInterval(20)
                    .fireImmune().build(ID + ":domain"));

    public static final int DOMAIN_RADIUS = 40;
    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ID);
    public static final RegistryObject<SimpleParticleType> BOUNDARY_STAR = PARTICLES.register("boundary_star",
            () -> new SimpleParticleType(false));

    public Dreamstar() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        SPELLS.register(bus);
        ENTITIES.register(bus);
        PARTICLES.register(bus);
    }
}
