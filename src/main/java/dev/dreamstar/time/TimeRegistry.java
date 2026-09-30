package dev.dreamstar.time;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;

@Mod.EventBusSubscriber(modid="dreamstar",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class TimeRegistry {
    public static final ResourceLocation SPELL_ID=new ResourceLocation("dreamstar","time_lock");
    public static final ResourceLocation CRYSTAL_ID=new ResourceLocation("dreamstar","time_crystal");
    public static final ResourceLocation TIME_CRYSTAL_SPELL_ID=CRYSTAL_ID;
    public static final ResourceLocation TIME_LOCK_SOUND_ID=new ResourceLocation("dreamstar","time_lock_loop");
    public static final ResourceLocation WHALE_FRACTURE_SOUND_ID=new ResourceLocation("dreamstar","whale_fracture");
    public static final ResourceLocation TIME_CRYSTAL_LAUNCH_SOUND_ID=new ResourceLocation("dreamstar","time_crystal_launch");
    public static final TimeLockSpell SPELL=new TimeLockSpell();
    public static final TimeCrystalSpell TIME_CRYSTAL_SPELL=new TimeCrystalSpell();
    public static final MobEffect EFFECT=new MobEffect(MobEffectCategory.HARMFUL,0xb147ef) {};
    public static final SoundEvent TIME_LOCK_SOUND=SoundEvent.createVariableRangeEvent(TIME_LOCK_SOUND_ID);
    public static final SoundEvent WHALE_FRACTURE_SOUND=SoundEvent.createVariableRangeEvent(WHALE_FRACTURE_SOUND_ID);
    public static final SoundEvent TIME_CRYSTAL_LAUNCH_SOUND=SoundEvent.createVariableRangeEvent(TIME_CRYSTAL_LAUNCH_SOUND_ID);
    public static EntityType<TimeCrystal> CRYSTAL;
    public static EntityType<TimeFracture> FRACTURE;
    @SubscribeEvent public static void register(RegisterEvent e){
        e.register(ForgeRegistries.Keys.MOB_EFFECTS,h->h.register(SPELL_ID,EFFECT));
        e.register(ForgeRegistries.Keys.SOUND_EVENTS,h->{
            h.register(TIME_LOCK_SOUND_ID,TIME_LOCK_SOUND);
            h.register(WHALE_FRACTURE_SOUND_ID,WHALE_FRACTURE_SOUND);
            h.register(TIME_CRYSTAL_LAUNCH_SOUND_ID,TIME_CRYSTAL_LAUNCH_SOUND);
        });
        e.register(ForgeRegistries.Keys.ENTITY_TYPES,h->{
            CRYSTAL=EntityType.Builder.<TimeCrystal>of(TimeCrystal::new,MobCategory.MISC)
                .sized(.45f,.9f).clientTrackingRange(12).updateInterval(1).fireImmune().build(CRYSTAL_ID.toString());
            h.register(CRYSTAL_ID,CRYSTAL);
            var fractureId=new ResourceLocation("dreamstar","time_fracture");
            FRACTURE=EntityType.Builder.<TimeFracture>of(TimeFracture::new,MobCategory.MISC).sized(.1f,.1f).clientTrackingRange(12).updateInterval(1).fireImmune().build(fractureId.toString());
            h.register(fractureId,FRACTURE);
        });
        e.register(SpellRegistry.SPELL_REGISTRY_KEY,h->{h.register(SPELL_ID,SPELL);h.register(TIME_CRYSTAL_SPELL_ID,TIME_CRYSTAL_SPELL);});
    }
}
