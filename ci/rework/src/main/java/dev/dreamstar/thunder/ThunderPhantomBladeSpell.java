package dev.dreamstar.thunder;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import java.util.List;
public final class ThunderPhantomBladeSpell extends AbstractSpell {
    private final DefaultConfig config=new DefaultConfig().setMinRarity(SpellRarity.RARE).setSchoolResource(SchoolRegistry.LIGHTNING_RESOURCE).setMaxLevel(3).setCooldownSeconds(60).build();
    public ThunderPhantomBladeSpell(){baseManaCost=100;manaCostPerLevel=0;castTime=0;baseSpellPower=1;spellPowerPerLevel=0;}
    @Override public ResourceLocation getSpellResource(){return ThunderRegistry.SPELL_ID;}
    @Override public DefaultConfig getDefaultConfig(){return config;}
    @Override public CastType getCastType(){return CastType.INSTANT;}
    @Override public SpellRarity getRarity(int level){return level<=1?SpellRarity.RARE:level==2?SpellRarity.EPIC:SpellRarity.LEGENDARY;}
    @Override public int getMinLevelForRarity(SpellRarity rarity){return rarity==SpellRarity.RARE?1:rarity==SpellRarity.EPIC?2:rarity==SpellRarity.LEGENDARY?3:0;}
    public static int durationTicks(int level){return level<=1?300:level==2?400:500;}
    public static float slashDamage(int level){return level<=1?6F:level==2?10F:15F;}
    @Override public List<MutableComponent> getUniqueInfo(int level,LivingEntity caster){return List.of(Component.translatable("ui.dreamstar.thunder_phantom_blade.duration",durationTicks(level)/20),Component.translatable("ui.dreamstar.thunder_phantom_blade.damage",slashDamage(level)));}
    @Override public void onCast(Level world,int level,LivingEntity caster,CastSource source,MagicData data){
        if(!world.isClientSide) caster.addEffect(new MobEffectInstance(ThunderRegistry.EFFECT,durationTicks(level),Math.max(0,Math.min(2,level-1)),false,true,true));
        super.onCast(world,level,caster,source,data);
    }
}