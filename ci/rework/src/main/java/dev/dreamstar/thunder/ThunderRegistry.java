package dev.dreamstar.thunder;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid="dreamstar",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ThunderRegistry {
    public static final ResourceLocation SPELL_ID=new ResourceLocation("dreamstar","thunder_phantom_blade");
    public static final ResourceLocation EFFECT_ID=new ResourceLocation("dreamstar","thunder_phantom_blade");
    public static final ResourceLocation SLASH_ID=new ResourceLocation("dreamstar","thunder_slash");
    public static final ThunderPhantomBladeSpell SPELL=new ThunderPhantomBladeSpell();
    public static final MobEffect EFFECT=new ThunderPhantomBladeEffect();
    public static EntityType<ThunderSlashEntity> SLASH;
    @SubscribeEvent public static void register(RegisterEvent e){
        e.register(ForgeRegistries.Keys.MOB_EFFECTS,h->h.register(EFFECT_ID,EFFECT));
        e.register(ForgeRegistries.Keys.ENTITY_TYPES,h->{ SLASH=EntityType.Builder.<ThunderSlashEntity>of(ThunderSlashEntity::new,MobCategory.MISC).sized(.1f,.1f).clientTrackingRange(128).updateInterval(1).fireImmune().build(SLASH_ID.toString()); h.register(SLASH_ID,SLASH); });
        e.register(SpellRegistry.SPELL_REGISTRY_KEY,h->h.register(SPELL_ID,SPELL));
    }
}
