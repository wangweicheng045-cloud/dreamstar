package dev.dreamstar.lockstrike;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;

@Mod.EventBusSubscriber(modid="dreamstar",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class LockedStrikeRegistry {
    public static final ResourceLocation ID=new ResourceLocation("dreamstar","locked_strike");
    public static final LockedStrikeSpell SPELL=new LockedStrikeSpell();
    public static EntityType<LockedStrikeEntity> ENTITY;
    @SubscribeEvent public static void register(RegisterEvent e){
        e.register(SpellRegistry.SPELL_REGISTRY_KEY,h->h.register(ID,SPELL));
        e.register(ForgeRegistries.Keys.ENTITY_TYPES,h->{
            ENTITY=EntityType.Builder.<LockedStrikeEntity>of(LockedStrikeEntity::new,MobCategory.MISC).sized(.1f,.1f).clientTrackingRange(24).updateInterval(1).fireImmune().build(ID.toString());
            h.register(ID,ENTITY);
        });
    }
}
