package dev.dreamstar.time;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import io.redspace.ironsspellbooks.damage.*;

/** Saved on the victim, so unload/reload cannot silently delete the damage ledger. */
public final class TimeLockState {
    public static final String KEY="dreamstar_time_lock";
    public static boolean locked(LivingEntity e){return e.hasEffect(TimeRegistry.EFFECT)||e.getPersistentData().contains(KEY);}
    public static void tickHeld(LivingEntity e){
        var n=e.getPersistentData().getCompound(KEY);
        var domain=TimeDomainCombo.containing(e);
        if(domain!=null){
            boolean entered=!n.getBoolean("domainHeld");
            n.putBoolean("domainHeld",true);n.putUUID("domain",domain.getUUID());n.putLong("end",Long.MAX_VALUE);
            if(entered)n.putLong("whaleTargetAt",e.level().getGameTime()+40);
            // An infinite effect as well as an independent ledger: neither vanilla nor rank countdown can expire it.
            var effect=e.getEffect(TimeRegistry.EFFECT);
            if(effect==null||effect.getDuration()!=-1){e.addEffect(new MobEffectInstance(TimeRegistry.EFFECT,-1,0,false,false,true));TimeEffectSync.start(e);}
        }else if(n.getBoolean("domainHeld")||e.level().getGameTime()>=n.getLong("end")){release(e);return;}
        hold(e,n);
    }
    public static boolean apply(LivingEntity target,LivingEntity caster,int level){
        if(target.level().isClientSide||!target.isAlive()||locked(target)||target.isSpectator())return false;
        if(target instanceof net.minecraft.world.entity.player.Player p&&p.getAbilities().instabuild)return false;
        CompoundTag n=new CompoundTag();n.putLong("end",target.level().getGameTime()+TimeLockSpell.duration(level));
        n.putUUID("caster",caster.getUUID());n.putDouble("power",TimeRegistry.SPELL.getSpellPower(level,caster));
        n.putDouble("x",target.getX());n.putDouble("y",target.getY());n.putDouble("z",target.getZ());
        n.putFloat("yaw",target.getYRot());n.putFloat("pitch",target.getXRot());n.putFloat("head",target.yHeadRot);n.putFloat("body",target.yBodyRot);
        n.putFloat("health",target.getHealth());n.putFloat("absorption",target.getAbsorptionAmount());
        n.putBoolean("gravity",target.isNoGravity());n.putBoolean("ai",target instanceof Mob m&&m.isNoAi());
        n.putFloat("swing",target.attackAnim);n.putInt("tick",target.tickCount);
        var domain=TimeDomainCombo.containing(target);
        if(domain!=null){n.putBoolean("domainHeld",true);n.putUUID("domain",domain.getUUID());n.putLong("end",Long.MAX_VALUE);n.putLong("whaleTargetAt",target.level().getGameTime()+40);}
        target.getPersistentData().put(KEY,n);
        target.stopRiding();target.stopUsingItem();
        if(!target.addEffect(new MobEffectInstance(TimeRegistry.EFFECT,domain!=null?-1:TimeLockSpell.duration(level),0,false,false,true))){target.getPersistentData().remove(KEY);return false;}
        io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(target).resetCastingState();
        if(target instanceof Mob mob){mob.getNavigation().stop();mob.setNoAi(true);}
        hold(target,n);TimeEffectSync.start(target);return true;
    }
    public static void hold(LivingEntity e,CompoundTag n){
        double x=n.getDouble("x"),y=n.getDouble("y"),z=n.getDouble("z");
        if(e instanceof ServerPlayer player){
            if(e.distanceToSqr(x,y,z)>.000001||e.getYRot()!=n.getFloat("yaw")||e.getXRot()!=n.getFloat("pitch"))player.connection.teleport(x,y,z,n.getFloat("yaw"),n.getFloat("pitch"));
        }
        e.setPos(x,y,z);e.xOld=e.xo=x;e.yOld=e.yo=y;e.zOld=e.zo=z;
        e.setDeltaMovement(Vec3.ZERO);e.setNoGravity(true);e.fallDistance=0;
        e.setYRot(n.getFloat("yaw"));e.yRotO=e.getYRot();e.setXRot(n.getFloat("pitch"));e.xRotO=e.getXRot();
        e.yHeadRot=e.yHeadRotO=n.getFloat("head");e.yBodyRot=e.yBodyRotO=n.getFloat("body");
        e.attackAnim=e.oAttackAnim=n.getFloat("swing");e.tickCount=n.getInt("tick");e.hurtTime=0;e.deathTime=0;
        e.setHealth(n.getFloat("health"));e.setAbsorptionAmount(n.getFloat("absorption"));
    }
    public static void bank(LivingEntity e,float amount){
        if(!Float.isFinite(amount)||amount<=0)return;
        CompoundTag n=e.getPersistentData().getCompound(KEY);
        n.putDouble("damage",Math.min(1e9,n.getDouble("damage")+amount));
    }
    public static void release(LivingEntity e){
        release(e,1);
    }
    public static void release(LivingEntity e,double multiplier){
        if(!(e.level() instanceof ServerLevel world)||!e.getPersistentData().contains(KEY))return;
        CompoundTag n=e.getPersistentData().getCompound(KEY).copy();
        // Remove the ledger first: settlement must not be captured again by the damage listener.
        e.getPersistentData().remove(KEY);e.removeEffect(TimeRegistry.EFFECT);TimeEffectSync.stop(e);e.setNoGravity(n.getBoolean("gravity"));
        if(e instanceof Mob mob)mob.setNoAi(n.getBoolean("ai"));
        double amount=n.getDouble("damage")*Math.max(0,n.getDouble("power"))*multiplier;
        if(amount>0&&e.isAlive()){
            Entity caster=n.hasUUID("caster")?world.getEntity(n.getUUID("caster")):null;
            e.invulnerableTime=0;
            DamageSources.applyDamage(e,(float)Math.min(amount,1e9),SpellDamageSource.source(caster!=null?caster:e,caster,TimeRegistry.SPELL));
        }
    }
}
