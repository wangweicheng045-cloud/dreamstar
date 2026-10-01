from pathlib import Path
import json, sys

root=Path(sys.argv[1])

def p(rel): return root/rel

# Domain: forced 3-second shutdown if original caster dies/disappears.
p("src/main/java/dev/dreamstar/domain/DomainEntity.java").write_text(r'''package dev.dreamstar.domain;

import dev.dreamstar.Dreamstar;
import dev.dreamstar.whale.StarWhaleEntity;
import dev.dreamstar.whale.WhaleAllies;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

public final class DomainEntity extends Entity implements DomainCasterAccess {
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(DomainEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(DomainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> FORCED_END = SynchedEntityData.defineId(DomainEntity.class, EntityDataSerializers.LONG);
    private static final int FORCED_END_DELAY = 60;

    private UUID ownerUUID;
    private UUID casterUUID;
    private UUID whaleUUID;

    public DomainEntity(EntityType<? extends DomainEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void initialize(float radius, LivingEntity caster) {
        this.initialize(radius, WhaleAllies.family(caster));
        this.casterUUID = caster.getUUID();
    }

    public void initialize(float radius) {
        this.initialize(radius, (UUID)null);
    }

    public void initialize(float radius, UUID ownerUUID) {
        this.entityData.set(START, this.level().getGameTime());
        this.entityData.set(RADIUS, radius);
        this.entityData.set(FORCED_END, -1L);
        this.ownerUUID = ownerUUID;
    }

    public UUID casterUUID() { return this.casterUUID == null ? this.ownerUUID : this.casterUUID; }
    public UUID ownerUUID() { return this.ownerUUID; }
    public long startTick() { return this.entityData.get(START); }
    public float radius() { return this.entityData.get(RADIUS); }
    public boolean forcedEnding() { return this.entityData.get(FORCED_END) >= 0L; }
    public long forcedEndTick() { return this.entityData.get(FORCED_END); }

    public long endTick() {
        long forced = this.forcedEndTick();
        return forced >= 0L ? forced : this.startTick() + DomainTiming.DURATION;
    }

    public long remainingTicks(long now) { return Math.max(0L, this.endTick() - now); }
    public boolean gameplayActive() { return !this.forcedEnding() && this.level().getGameTime() < this.endTick(); }
    public boolean whaleActive() { return !this.forcedEnding() && this.level().getGameTime() - this.startTick() < 1180L; }
    public boolean ownsWhale(UUID id) { return id.equals(this.whaleUUID); }

    public float opacity(float partialTick) {
        double now = (double)this.level().getGameTime() + partialTick;
        double age = now - (double)this.startTick();
        double remaining = (double)this.endTick() - now;
        return (float)Math.max(0.0, Math.min(1.0, Math.min(age / 12.0, remaining / 20.0)));
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(START, 0L);
        this.entityData.define(RADIUS, 40.0f);
        this.entityData.define(FORCED_END, -1L);
    }

    private void beginForcedEnd(ServerLevel server) {
        if (this.forcedEnding()) return;
        this.entityData.set(FORCED_END, server.getGameTime() + FORCED_END_DELAY);
    }

    private boolean originalCasterPresent(ServerLevel server) {
        UUID id = this.casterUUID();
        if (id == null) return false;
        Entity entity = server.getEntity(id);
        return entity instanceof LivingEntity living && living.isAlive() && !living.isRemoved();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        ServerLevel server = (ServerLevel)this.level();
        long now = server.getGameTime();

        if (!this.forcedEnding() && !this.originalCasterPresent(server)) {
            this.beginForcedEnd(server);
        }

        if (now >= this.endTick()) {
            DomainFamilyLock.releaseActiveDomain(server, this.ownerUUID);
            this.discard();
            return;
        }

        if (!this.forcedEnding()) {
            this.tickCasterBlessing();
        }

        if (this.ownerUUID != null && this.whaleActive()) {
            Entity current = this.whaleUUID == null ? null : server.getEntity(this.whaleUUID);
            if (current == null || current.isRemoved()) {
                StarWhaleEntity whale = Dreamstar.STAR_WHALE.get().create(this.level());
                if (whale != null) {
                    whale.initialize(this);
                    if (server.addFreshEntity(whale)) this.whaleUUID = whale.getUUID();
                }
            }
        }
    }

    private void tickCasterBlessing() {
        if (!(this.level() instanceof ServerLevel server)) return;
        LivingEntity caster = this.resolveDomainCaster(server);
        if (caster == null || !caster.isAlive() || caster.isSpectator()) return;
        double radius = this.radius();
        if (caster.position().distanceToSqr(this.position()) > radius * radius) return;
        caster.addEffect(new MobEffectInstance(Dreamstar.DREAM_STAR_DOMAIN_EFFECT.get(), 20, 0, false, false, true));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(START, tag.getLong("Start"));
        this.entityData.set(RADIUS, Math.max(4.0f, Math.min(64.0f, tag.getFloat("Radius"))));
        this.entityData.set(FORCED_END, tag.contains("ForcedEnd") ? tag.getLong("ForcedEnd") : -1L);
        this.ownerUUID = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.casterUUID = tag.hasUUID("Caster") ? tag.getUUID("Caster") : this.ownerUUID;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("Start", this.startTick());
        tag.putFloat("Radius", this.radius());
        tag.putLong("ForcedEnd", this.forcedEndTick());
        if (this.casterUUID != null) tag.putUUID("Caster", this.casterUUID);
        if (this.ownerUUID != null) tag.putUUID("Owner", this.ownerUUID);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
''', encoding="utf-8")

# Allow an early-aborted domain to release its active-family lock.
f=p("src/main/java/dev/dreamstar/domain/DomainFamilyLock.java")
s=f.read_text(encoding="utf-8")
needle="    public static void lockActiveDomain(ServerLevel level, UUID familyId, long durationTicks) {\n"
insert='''    public static void releaseActiveDomain(ServerLevel level, UUID familyId) {
        if (familyId == null) return;
        DomainFamilyLock data = DomainFamilyLock.get(level);
        if (data.activeUntil.remove(familyId) != null) data.setDirty();
    }

'''
if insert not in s:
    s=s.replace(needle,insert+needle)
f.write_text(s,encoding="utf-8")

# Client ending particles use the real (normal or forced) domain end tick.
f=p("src/main/java/dev/dreamstar/client/DomainRenderer.java")
s=f.read_text(encoding="utf-8")
s=s.replace("long remainingTicks = domain.startTick() + 1200L - mc.level.getGameTime();",
            "long remainingTicks = domain.remainingTicks(mc.level.getGameTime());")
f.write_text(s,encoding="utf-8")

# Forced end: new audio + same normal white-screen timing.
f=p("src/main/java/dev/dreamstar/client/DreamStarCinematicController.java")
s=f.read_text(encoding="utf-8")
s=s.replace('private static final ResourceLocation END_AUDIO = new ResourceLocation("dreamstar", "domain_end");',
            'private static final ResourceLocation END_AUDIO = new ResourceLocation("dreamstar", "domain_end");\n    private static final ResourceLocation FORCED_END_AUDIO = new ResourceLocation("dreamstar", "domain_forced_end");')
s=s.replace('private static final Set<Integer> endingAudioStarted = new HashSet<Integer>();',
            'private static final Set<Integer> endingAudioStarted = new HashSet<Integer>();\n    private static final Set<Integer> forcedEndingAudioStarted = new HashSet<Integer>();')
start=s.index("    private static void tickDomainAudio(Minecraft mc) {")
end=s.index("    private static void playWhaleCall(Minecraft mc, DomainEntity domain) {")
method=r'''    private static void tickDomainAudio(Minecraft mc) {
        long now = mc.level.getGameTime();
        DomainEntity containing = null;
        long bestRemaining = Long.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof DomainEntity domain) || domain.isRemoved()) continue;
            double radius = domain.radius();
            long remaining = domain.remainingTicks(now);
            if (mc.player.position().distanceToSqr(domain.position()) > radius * radius || remaining <= 0L || remaining >= bestRemaining) continue;
            containing = domain;
            bestRemaining = remaining;
        }
        if (containing == null) {
            nextWhaleCallTick = Long.MIN_VALUE;
        } else {
            if (containing.forcedEnding() && forcedEndingAudioStarted.add(containing.getId())) {
                SimpleSoundInstance forced = SimpleSoundInstance.forUI(
                        SoundEvent.createVariableRangeEvent(FORCED_END_AUDIO), 1.0f, 1.0f);
                mc.getSoundManager().play(forced);
            }
            if (!containing.forcedEnding()) {
                if (nextWhaleCallTick == Long.MIN_VALUE) {
                    nextWhaleCallTick = now + 160L + (long)mc.level.random.nextInt(121);
                }
                if (bestRemaining > END_AUDIO_LEAD_TICKS && pendingDomainEndTick == Long.MIN_VALUE && now >= nextWhaleCallTick) {
                    DreamStarCinematicController.playWhaleCall(mc, containing);
                    nextWhaleCallTick = now + 400L + (long)mc.level.random.nextInt(201);
                }
            } else {
                nextWhaleCallTick = Long.MIN_VALUE;
            }
            if (bestRemaining <= END_AUDIO_LEAD_TICKS && bestRemaining > 0L
                    && !endingAudioStarted.contains(containing.getId()) && pendingDomainEndTick == Long.MIN_VALUE) {
                endingAudioStarted.add(containing.getId());
                if (!containing.forcedEnding()) {
                    endAudio = SimpleSoundInstance.forUI(
                            SoundEvent.createVariableRangeEvent(END_AUDIO), 1.0f, 1.0f);
                    mc.getSoundManager().play(endAudio);
                }
                pendingDomainEndTick = containing.endTick();
                pendingDomainEndTriggered = false;
            }
        }
        if (pendingDomainEndTick != Long.MIN_VALUE && !pendingDomainEndTriggered && now >= pendingDomainEndTick - 20L) {
            pendingDomainEndTriggered = true;
            DreamStarCinematicController.triggerWhiteSmooth(20, 10, 50);
        }
        if (pendingDomainEndTriggered && now == pendingDomainEndTick) {
            DreamStarCinematicController.muteOtherSounds(mc, END_MUTE_TICKS);
        }
        if (pendingDomainEndTriggered && now >= pendingDomainEndTick + END_MUTE_TICKS + 20L) {
            pendingDomainEndTick = Long.MIN_VALUE;
            pendingDomainEndTriggered = false;
            endAudio = null;
        }
    }

'''
s=s[:start]+method+s[end:]
s=s.replace("endingAudioStarted.clear();","endingAudioStarted.clear();\n        forcedEndingAudioStarted.clear();")
f.write_text(s,encoding="utf-8")

# Time-lock/domain combo is only enabled if the caster has Dream Star Domain buff in the same live domain.
f=p("src/main/java/dev/dreamstar/time/TimeDomainCombo.java")
s=f.read_text(encoding="utf-8")
s=s.replace('d->!d.isRemoved()&&!DomainTiming.expired(target.level().getGameTime(),d.startTick())\n                &&target.position().distanceToSqr(d.position())<=d.radius()*d.radius())',
            'd->!d.isRemoved()&&d.gameplayActive()\n                &&target.position().distanceToSqr(d.position())<=d.radius()*d.radius())')
s=s.replace('if(target==null||!target.getPersistentData().contains(TimeLockState.KEY)\n                ||target.position().distanceToSqr(domain.position())>domain.radius()*domain.radius())return false;',
            'if(target==null||!target.getPersistentData().contains(TimeLockState.KEY)\n                ||!target.getPersistentData().getCompound(TimeLockState.KEY).getBoolean("domainCombo")\n                ||!domain.gameplayActive()\n                ||target.position().distanceToSqr(domain.position())>domain.radius()*domain.radius())return false;')
f.write_text(s,encoding="utf-8")

f=p("src/main/java/dev/dreamstar/time/TimeLockState.java")
s=f.read_text(encoding="utf-8")
if "import dev.dreamstar.Dreamstar;" not in s:
    s=s.replace("package dev.dreamstar.time;\n","package dev.dreamstar.time;\n\nimport dev.dreamstar.Dreamstar;\n")
s=s.replace("var domain=TimeDomainCombo.containing(e);","var domain=n.getBoolean(\"domainCombo\")?TimeDomainCombo.containing(e):null;")
old='''        var domain=TimeDomainCombo.containing(target);
        if(domain!=null){n.putBoolean("domainHeld",true);n.putUUID("domain",domain.getUUID());n.putLong("end",Long.MAX_VALUE);n.putLong("whaleTargetAt",target.level().getGameTime()+40);}'''
new='''        var casterDomain=TimeDomainCombo.containing(caster);
        var targetDomain=TimeDomainCombo.containing(target);
        var domain=caster.hasEffect(Dreamstar.DREAM_STAR_DOMAIN_EFFECT.get())&&casterDomain!=null&&targetDomain!=null
                &&casterDomain.getUUID().equals(targetDomain.getUUID())?targetDomain:null;
        if(domain!=null){n.putBoolean("domainCombo",true);n.putBoolean("domainHeld",true);n.putUUID("domain",domain.getUUID());n.putLong("end",Long.MAX_VALUE);n.putLong("whaleTargetAt",target.level().getGameTime()+40);}'''
if old not in s: raise SystemExit("TimeLockState apply anchor missing")
s=s.replace(old,new)
s=s.replace('''        // Ordinary expiry/collision callers cannot release a victim in a live domain.
        if(TimeDomainCombo.containing(e)!=null)return;''',
'''        // Only a domain-combo time lock is protected from ordinary expiry while its domain is active.
        var state=e.getPersistentData().getCompound(KEY);
        if(state.getBoolean("domainCombo")&&TimeDomainCombo.containing(e)!=null)return;''')
f.write_text(s,encoding="utf-8")

# Thunder combo: 0.4 s delay, 7 frames at 10 FPS, damage on frame 4, 3x base visual size.
f=p("src/main/java/dev/dreamstar/thunder/ThunderSlashEntity.java")
s=f.read_text(encoding="utf-8")
s=s.replace("private static final int TARGET_DELAY_TICKS = 20;","private static final int TARGET_DELAY_TICKS = 8;")
s=s.replace("private static final int TARGET_ANIM_TICKS = 18;","private static final int TARGET_ANIM_TICKS = 14;")
s=s.replace("public int animationFrames() { return mode() == MODE_OPENING_SLASH ? OPENING_FRAME_COUNT : 9; }",
            "public int animationFrames() { return mode() == MODE_OPENING_SLASH ? OPENING_FRAME_COUNT : 7; }")
s=s.replace("    private boolean frozen;","    private boolean frozen;\n    private boolean comboParticlesSpawned;")
s=s.replace("entityData.set(VISUAL_SCALE, Math.max(1.15F, Math.max(target.getBbHeight(), target.getBbWidth()) * 1.35F));",
            "entityData.set(VISUAL_SCALE, Math.max(3.45F, Math.max(target.getBbHeight(), target.getBbWidth()) * 4.05F));")
anchor="    private void spawnOpeningParticles() {"
particle_method=r'''    private void spawnComboParticles() {
        if (!(level() instanceof ServerLevel server)) return;
        double spread = Math.max(0.65D, visualScale() * 0.18D);
        double ySpread = Math.max(0.45D, spread * 0.7D);
        server.sendParticles(ThunderRegistry.COMBO_PARTICLE_1, getX(), getY(), getZ(), 8, spread, ySpread, spread, 0.025D);
        server.sendParticles(ThunderRegistry.COMBO_PARTICLE_2, getX(), getY(), getZ(), 8, spread, ySpread, spread, 0.025D);
        server.sendParticles(ThunderRegistry.COMBO_PARTICLE_3, getX(), getY(), getZ(), 8, spread, ySpread, spread, 0.025D);
    }

'''
if "private void spawnComboParticles()" not in s:
    s=s.replace(anchor,particle_method+anchor)
old='''        if (!level().isClientSide && !frozen) {
            if (t == null || !t.isAlive() || t.isRemoved()) { discard(); return; }
            follow(t);
            frozen = true;
        }

        if (!level().isClientSide && !damageApplied && age >= TARGET_DAMAGE_TICK) {'''
new='''        if (!level().isClientSide && !frozen) {
            if (t == null || !t.isAlive() || t.isRemoved()) { discard(); return; }
            follow(t);
            frozen = true;
        }

        if (!level().isClientSide && !comboParticlesSpawned && age >= 0L) {
            comboParticlesSpawned = true;
            spawnComboParticles();
        }

        if (!level().isClientSide && !damageApplied && age >= TARGET_DAMAGE_TICK) {'''
if old not in s: raise SystemExit("ThunderSlash tick anchor missing")
s=s.replace(old,new)
s=s.replace('        frozen = tag.getBoolean("Frozen");','        frozen = tag.getBoolean("Frozen");\n        comboParticlesSpawned = tag.getBoolean("ComboParticlesSpawned");')
s=s.replace('        tag.putBoolean("Frozen", frozen);','        tag.putBoolean("Frozen", frozen);\n        tag.putBoolean("ComboParticlesSpawned", comboParticlesSpawned);')
f.write_text(s,encoding="utf-8")

# Particle registry.
p("src/main/java/dev/dreamstar/thunder/ThunderRegistry.java").write_text(r'''package dev.dreamstar.thunder;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.core.particles.SimpleParticleType;
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
    public static final ResourceLocation COMBO_PARTICLE_1_ID=new ResourceLocation("dreamstar","thunder_combo_1");
    public static final ResourceLocation COMBO_PARTICLE_2_ID=new ResourceLocation("dreamstar","thunder_combo_2");
    public static final ResourceLocation COMBO_PARTICLE_3_ID=new ResourceLocation("dreamstar","thunder_combo_3");
    public static final ThunderPhantomBladeSpell SPELL=new ThunderPhantomBladeSpell();
    public static final MobEffect EFFECT=new ThunderPhantomBladeEffect();
    public static EntityType<ThunderSlashEntity> SLASH;
    public static SimpleParticleType COMBO_PARTICLE_1;
    public static SimpleParticleType COMBO_PARTICLE_2;
    public static SimpleParticleType COMBO_PARTICLE_3;

    @SubscribeEvent public static void register(RegisterEvent e){
        e.register(ForgeRegistries.Keys.MOB_EFFECTS,h->h.register(EFFECT_ID,EFFECT));
        e.register(ForgeRegistries.Keys.ENTITY_TYPES,h->{
            SLASH=EntityType.Builder.<ThunderSlashEntity>of(ThunderSlashEntity::new,MobCategory.MISC)
                    .sized(.1f,.1f).clientTrackingRange(16).updateInterval(1).fireImmune().build(SLASH_ID.toString());
            h.register(SLASH_ID,SLASH);
        });
        e.register(ForgeRegistries.Keys.PARTICLE_TYPES,h->{
            COMBO_PARTICLE_1=new SimpleParticleType(false);
            COMBO_PARTICLE_2=new SimpleParticleType(false);
            COMBO_PARTICLE_3=new SimpleParticleType(false);
            h.register(COMBO_PARTICLE_1_ID,COMBO_PARTICLE_1);
            h.register(COMBO_PARTICLE_2_ID,COMBO_PARTICLE_2);
            h.register(COMBO_PARTICLE_3_ID,COMBO_PARTICLE_3);
        });
        e.register(SpellRegistry.SPELL_REGISTRY_KEY,h->h.register(SPELL_ID,SPELL));
    }
}
''',encoding="utf-8")

p("src/main/java/dev/dreamstar/thunder/client/ThunderComboParticle.java").write_text(r'''package dev.dreamstar.thunder.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public final class ThunderComboParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    private ThunderComboParticle(ClientLevel level, double x, double y, double z,
                                 double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        this.sprites = sprites;
        this.lifetime = 20;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.quadSize = 0.55F + this.random.nextFloat() * 0.45F;
        this.xd *= 0.35D;
        this.yd *= 0.35D;
        this.zd *= 0.35D;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.removed) {
            this.setSpriteFromAge(this.sprites);
            float life = (float)this.age / (float)this.lifetime;
            this.alpha = life < 0.70F ? 1.0F : Math.max(0.0F, (1.0F - life) / 0.30F);
            this.quadSize *= 1.012F;
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double vx, double vy, double vz) {
            return new ThunderComboParticle(level, x, y, z, vx, vy, vz, this.sprites);
        }
    }
}
''',encoding="utf-8")

p("src/main/java/dev/dreamstar/thunder/client/ThunderClientRegistration.java").write_text(r'''package dev.dreamstar.thunder.client;

import dev.dreamstar.thunder.ThunderRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="dreamstar",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ThunderClientRegistration {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e){
        e.registerEntityRenderer(ThunderRegistry.SLASH,ThunderSlashRenderer::new);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent e){
        e.registerSpriteSet(ThunderRegistry.COMBO_PARTICLE_1, ThunderComboParticle.Provider::new);
        e.registerSpriteSet(ThunderRegistry.COMBO_PARTICLE_2, ThunderComboParticle.Provider::new);
        e.registerSpriteSet(ThunderRegistry.COMBO_PARTICLE_3, ThunderComboParticle.Provider::new);
    }
}
''',encoding="utf-8")

# Resource declarations and descriptions.
sounds=p("src/main/resources/assets/dreamstar/sounds.json")
data=json.loads(sounds.read_text(encoding="utf-8"))
data["domain_forced_end"]={"sounds":[{"name":"dreamstar:domain_forced_end","stream":True}]}
sounds.write_text(json.dumps(data,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")

particles=p("src/main/resources/assets/dreamstar/particles")
particles.mkdir(parents=True,exist_ok=True)
for i in range(1,4):
    (particles/f"thunder_combo_{i}.json").write_text(json.dumps({"textures":[f"dreamstar:thunder_combo_{i}"]},separators=(",",":"))+"\n",encoding="utf-8")

for rel,txt in [
("src/main/resources/assets/dreamstar/lang/zh_cn.json","吟唱0.5秒后，朝玩家面向前方5格范围触发一道开场雷刃斩击，并附带少量雷电粒子；开场斩击在触发0.2秒后结算伤害，然后获得雷鸣幻刃增益。增益期间，攻击目标会在0.4秒后于目标当时所在位置追加一道7帧紫色雷属性幻刃斩击，第4帧结算伤害，并伴随3种雷电粒子。"),
("src/main/resources/assets/dreamstar/lang/en_us.json","After a 0.5 second cast, unleash an opening thunder slash through a 5-block frontal area with a small burst of lightning particles. The opening slash deals damage 0.2 seconds after appearing, then grants Thunder Phantom Blade. While buffed, damaging a target creates a 7-frame purple lightning slash at that target's position after 0.4 seconds; damage lands on frame 4 with three accompanying lightning particle effects.")
]:
    q=p(rel); d=json.loads(q.read_text(encoding="utf-8")); d["spell.dreamstar.thunder_phantom_blade.description"]=txt
    q.write_text(json.dumps(d,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
