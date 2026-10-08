package dev.dreamstar.lockstrike;

import dev.dreamstar.cannon.HoverCannonEntity;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import java.util.*;

public final class LockedStrikeSpell extends AbstractSpell {
    private final DefaultConfig config=new DefaultConfig().setMinRarity(SpellRarity.EPIC).setSchoolResource(SchoolRegistry.ICE_RESOURCE).setMaxLevel(2).setCooldownSeconds(40).build();
    public LockedStrikeSpell(){baseManaCost=60;manaCostPerLevel=0;castTime=0;baseSpellPower=1;spellPowerPerLevel=0;}
    @Override public ResourceLocation getSpellResource(){return LockedStrikeRegistry.ID;}
    @Override public DefaultConfig getDefaultConfig(){return config;}
    @Override public CastType getCastType(){return CastType.INSTANT;}
    @Override public SpellRarity getRarity(int level){return level<=1?SpellRarity.EPIC:SpellRarity.LEGENDARY;}
    @Override public int getMinLevelForRarity(SpellRarity rarity){return rarity==SpellRarity.EPIC?1:rarity==SpellRarity.LEGENDARY?2:0;}
    @Override public boolean checkPreCastConditions(Level world,int level,LivingEntity caster,MagicData data){
        if(!(world instanceof ServerLevel server))return true;
        if(LockedStrikeEntity.forCaster(caster)!=null)return false;
        boolean equipped=HoverCannonEntity.owned(server,caster.getUUID()).stream().anyMatch(c->!c.dismissing());
        LivingEntity target=LockedStrikeEntity.acquireTarget(caster,30);
        if(!equipped&&caster instanceof ServerPlayer p)p.displayClientMessage(Component.translatable("ui.dreamstar.locked_strike.require_cannon"),true);
        else if(target==null&&caster instanceof ServerPlayer p)p.displayClientMessage(Component.translatable("ui.dreamstar.locked_strike.no_target"),true);
        return equipped&&target!=null;
    }
    @Override public void onCast(Level world,int level,LivingEntity caster,CastSource source,MagicData data){
        if(world instanceof ServerLevel){var target=LockedStrikeEntity.acquireTarget(caster,30);if(target!=null)LockedStrikeEntity.begin(caster,target,level);}
        super.onCast(world,level,caster,source,data);
    }
    public float rangedDamage(int level){return level<=1?3f:4f;}
    public float finisherBeamDamage(int level){return level<=1?14f:16f;}
    public float meleeDamage(int level){return level<=1?2f:3f;}
    @Override public List<MutableComponent> getUniqueInfo(int level,LivingEntity caster){return List.of(
        Component.translatable("ui.dreamstar.locked_strike.require_cannon"),
        Component.translatable("ui.dreamstar.locked_strike.ranged",(int)rangedDamage(level),(int)finisherBeamDamage(level)),
        Component.translatable("ui.dreamstar.locked_strike.melee",(int)meleeDamage(level),10,10),
        Component.translatable("ui.dreamstar.locked_strike.extend",10));}
}
