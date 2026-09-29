package dev.dreamstar.time;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.util.*;

@Mod.EventBusSubscriber(modid="dreamstar")
public final class TimeLockEvents {
    private record HitParticles(ServerLevel level,UUID target,long until){}
    private static final List<HitParticles> hitParticles=new ArrayList<>();
    private static void hitParticles(ServerLevel level,LivingEntity target,int count){
        var center=target.getBoundingBox().getCenter();
        MagicManager.spawnParticles(level,ParticleHelper.UNSTABLE_ENDER,center.x,center.y,center.z,count,
                Math.max(.25,target.getBbWidth()*.65),Math.max(.35,target.getBbHeight()*.45),Math.max(.25,target.getBbWidth()*.65),.08,true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void spell(io.redspace.ironsspellbooks.api.events.SpellPreCastEvent e){if(TimeLockState.locked(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void tick(LivingEvent.LivingTickEvent event){
        var e=event.getEntity();
        if(e.level().isClientSide){if(e.hasEffect(TimeRegistry.EFFECT)){e.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);event.setCanceled(true);}return;}
        if(!e.getPersistentData().contains(TimeLockState.KEY))return;
        TimeLockState.tickHeld(e);if(e.getPersistentData().contains(TimeLockState.KEY))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingAttackEvent event){
        var e=event.getEntity();
        if(e.getPersistentData().contains(TimeLockState.KEY)){
            if(!e.level().isClientSide){
                TimeLockState.bank(e,event.getAmount());
                if(e.level() instanceof ServerLevel level){
                    hitParticles(level,e,18);
                    hitParticles.add(new HitParticles(level,e.getUUID(),level.getGameTime()+20));
                }
            }
            event.setCanceled(true);
        }else if(event.getSource().getEntity() instanceof LivingEntity attacker&&TimeLockState.locked(attacker))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void death(LivingDeathEvent e){if(TimeLockState.locked(e.getEntity())){e.setCanceled(true);var n=e.getEntity().getPersistentData().getCompound(TimeLockState.KEY);e.getEntity().setHealth(Math.max(1,n.getFloat("health")));}}
    @SubscribeEvent public static void knockback(LivingKnockBackEvent e){if(TimeLockState.locked(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void heal(LivingHealEvent e){if(TimeLockState.locked(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void attack(AttackEntityEvent e){if(TimeLockState.locked(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void use(PlayerInteractEvent e){if(TimeLockState.locked(e.getEntity())&&e.isCancelable())e.setCanceled(true);}
    @SubscribeEvent public static void item(LivingEntityUseItemEvent.Start e){if(TimeLockState.locked(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void drop(ItemTossEvent e){if(TimeLockState.locked(e.getPlayer())){e.setCanceled(true);e.getPlayer().getInventory().placeItemBackInInventory(e.getEntity().getItem());}}
    @SubscribeEvent public static void block(BlockEvent.BreakEvent e){if(TimeLockState.locked(e.getPlayer()))e.setCanceled(true);}
    @SubscribeEvent public static void place(BlockEvent.EntityPlaceEvent e){if(e.getEntity() instanceof LivingEntity l&&TimeLockState.locked(l))e.setCanceled(true);}
    @SubscribeEvent public static void teleport(EntityTeleportEvent e){if(e.getEntity() instanceof LivingEntity l&&TimeLockState.locked(l)&&e.isCancelable())e.setCanceled(true);}
    @SubscribeEvent public static void dimension(net.minecraftforge.event.entity.EntityTravelToDimensionEvent e){if(e.getEntity() instanceof LivingEntity l&&TimeLockState.locked(l))e.setCanceled(true);}
    @SubscribeEvent public static void end(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var server=ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        for(var p:server.getPlayerList().getPlayers())if(p.getPersistentData().contains(TimeLockState.KEY)){
            TimeLockState.tickHeld(p);
        }
        var bursts=hitParticles.iterator();
        while(bursts.hasNext()){
            var burst=bursts.next();
            if(burst.level().getServer()!=server||burst.level().getGameTime()>=burst.until()){bursts.remove();continue;}
            if(!(burst.level().getEntity(burst.target()) instanceof LivingEntity target)){bursts.remove();continue;}
            hitParticles(burst.level(),target,1);
        }
    }
    @SubscribeEvent public static void remove(MobEffectEvent.Remove e){if(e.getEffect()==TimeRegistry.EFFECT&&e.getEntity().getPersistentData().contains(TimeLockState.KEY))e.setCanceled(true);}
    @SubscribeEvent public static void domainEnd(TickEvent.LevelTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof net.minecraft.server.level.ServerLevel level))return;
        // Resolve after ALL entity ticks: a domain may disappear after its victim already ticked this frame.
        for(var entity:level.getAllEntities())if(entity instanceof LivingEntity target&&target.getPersistentData().contains(TimeLockState.KEY)
                &&target.getPersistentData().getCompound(TimeLockState.KEY).getBoolean("domainHeld")&&TimeDomainCombo.containing(target)==null)
            TimeLockState.release(target);
    }
}
