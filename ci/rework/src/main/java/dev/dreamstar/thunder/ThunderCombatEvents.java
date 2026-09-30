package dev.dreamstar.thunder;
import net.minecraft.server.level.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="dreamstar")
public final class ThunderCombatEvents {
    @SubscribeEvent public static void onLivingHurt(LivingHurtEvent event){
        Entity source=event.getSource().getEntity();
        if(!(source instanceof ServerPlayer player)||!(player.level() instanceof ServerLevel level))return;
        if(event.getSource().getDirectEntity() instanceof ThunderSlashEntity)return;
        LivingEntity target=event.getEntity();
        if(target==player||!target.isAlive()||target.isSpectator())return;
        MobEffectInstance buff=player.getEffect(ThunderRegistry.EFFECT);if(buff==null)return;
        boolean active=!level.getEntitiesOfClass(ThunderSlashEntity.class,target.getBoundingBox().inflate(8),s->s.matchesTarget(target.getId())).isEmpty();if(active)return;
        ThunderSlashEntity slash=ThunderRegistry.SLASH.create(level);if(slash==null)return;
        int spellLevel=Math.max(1,Math.min(3,buff.getAmplifier()+1));slash.initialize(target,player,ThunderPhantomBladeSpell.slashDamage(spellLevel));level.addFreshEntity(slash);
    }
}