package dev.dreamstar.client;

import dev.dreamstar.domain.DomainCastSignalEntity;
import dev.dreamstar.domain.DomainEntity;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="dreamstar", value={Dist.CLIENT})
public final class DreamStarCinematicController {
    private static final String SPELL_ID = "dreamstar:dream_star_domain";
    private static final ResourceLocation CAST_AUDIO = new ResourceLocation("dreamstar", "domain_cast");
    private static final ResourceLocation END_AUDIO = new ResourceLocation("dreamstar", "domain_end");
    private static final ResourceLocation WHALE_HIGH = new ResourceLocation("dreamstar", "whale_high");
    private static final ResourceLocation WHALE_MID = new ResourceLocation("dreamstar", "whale_mid");
    private static final int CAST_TOTAL_TICKS = 100;
    private static final int CAST_AUDIO_START_TICK = 46;
    private static final float CAST_AUDIO_VOLUME = 1.30f;
    private static final double DOMAIN_CAST_RADIUS_SQR = 1600.0;
    private static final int END_AUDIO_LEAD_TICKS = 152;
    private static final int END_MUTE_TICKS = 60;
    private static final Set<Integer> endingAudioStarted = new HashSet<Integer>();
    private static final Map<Integer, SimpleSoundInstance> remoteCastAudios = new HashMap<Integer, SimpleSoundInstance>();
    private static final Set<Integer> remoteCastAudioStarted = new HashSet<Integer>();
    private static final Set<Integer> remoteCastCompleted = new HashSet<Integer>();
    private static final Set<Integer> remoteCastWhiteTriggered = new HashSet<Integer>();
    private static ClientLevel trackedLevel;
    private static boolean wasDreamCasting;
    private static int castTicks;
    private static SimpleSoundInstance castAudio;
    private static long pendingDomainEndTick;
    private static boolean pendingDomainEndTriggered;
    private static SimpleSoundInstance endAudio;
    private static long nextWhaleCallTick;
    private static int whiteFadeInTicks;
    private static int whiteFadeInTotal;
    private static int whiteHoldTicks;
    private static int whiteFadeTicks;
    private static int whiteFadeTotal;
    private static int muteTicks;

    private DreamStarCinematicController() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset(mc);
            return;
        }
        if (trackedLevel != mc.level) {
            reset(mc);
            trackedLevel = mc.level;
        }
        if (mc.isPaused()) {
            return;
        }
        tickCast(mc);
        tickRemoteDomainCasts(mc);
        tickDomainAudio(mc);
        tickWhiteFlash();
        tickMute(mc);
    }

    private static void tickCast(Minecraft mc) {
        boolean dreamCasting = ClientMagicData.isCasting() && SPELL_ID.equals(ClientMagicData.getCastingSpellId());
        if (dreamCasting) {
            if (!wasDreamCasting) {
                castTicks = 0;
                stopCastAudio(mc);
            }
            if (++castTicks == CAST_AUDIO_START_TICK) {
                castAudio = SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(CAST_AUDIO), 1.0f, CAST_AUDIO_VOLUME);
                mc.getSoundManager().play(castAudio);
            }
        } else if (wasDreamCasting) {
            if (castTicks < 98) {
                stopCastAudio(mc);
            } else {
                triggerWhite(10, 30);
            }
            castTicks = 0;
        }
        wasDreamCasting = dreamCasting;
    }

    private static void tickRemoteDomainCasts(Minecraft mc) {
        Set<Integer> liveSignals = new HashSet<Integer>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof DomainCastSignalEntity signal) || signal.isRemoved()) continue;
            int id = signal.getId();
            liveSignals.add(id);
            if (signal.completed()) {
                remoteCastCompleted.add(id);
            }

            double distanceSqr = mc.player.position().distanceToSqr(signal.position());
            long age = signal.castAge();
            if (distanceSqr <= DOMAIN_CAST_RADIUS_SQR) {
                if (age >= CAST_AUDIO_START_TICK && age <= CAST_AUDIO_START_TICK + 14L && remoteCastAudioStarted.add(id)) {
                    SimpleSoundInstance sound = SimpleSoundInstance.forUI(
                            SoundEvent.createVariableRangeEvent(CAST_AUDIO), 1.0f, CAST_AUDIO_VOLUME);
                    remoteCastAudios.put(id, sound);
                    mc.getSoundManager().play(sound);
                }
                if (age >= 98L && age <= 108L && remoteCastWhiteTriggered.add(id)) {
                    triggerWhite(10, 30);
                }
            } else {
                SimpleSoundInstance sound = remoteCastAudios.remove(id);
                if (sound != null && !signal.completed()) {
                    mc.getSoundManager().stop(sound);
                }
            }
        }

        Set<Integer> known = new HashSet<Integer>(remoteCastAudioStarted);
        known.addAll(remoteCastWhiteTriggered);
        for (Integer id : known) {
            if (liveSignals.contains(id)) continue;
            SimpleSoundInstance sound = remoteCastAudios.remove(id);
            if (sound != null && !remoteCastCompleted.contains(id)) {
                mc.getSoundManager().stop(sound);
            }
            remoteCastAudioStarted.remove(id);
            remoteCastCompleted.remove(id);
            remoteCastWhiteTriggered.remove(id);
        }
    }

    private static void tickDomainAudio(Minecraft mc) {
        long now = mc.level.getGameTime();
        DomainEntity containing = null;
        long bestRemaining = Long.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            long remaining;
            DomainEntity domain;
            if (!(entity instanceof DomainEntity) || (domain = (DomainEntity)entity).isRemoved()) continue;
            double radius = domain.radius();
            if (mc.player.position().distanceToSqr(domain.position()) > radius * radius || (remaining = domain.startTick() + 1200L - now) <= 0L || remaining >= bestRemaining) continue;
            containing = domain;
            bestRemaining = remaining;
        }
        if (containing == null) {
            nextWhaleCallTick = Long.MIN_VALUE;
        } else {
            if (nextWhaleCallTick == Long.MIN_VALUE) {
                nextWhaleCallTick = now + 160L + (long)mc.level.random.nextInt(121);
            }
            if (bestRemaining > 152L && pendingDomainEndTick == Long.MIN_VALUE && now >= nextWhaleCallTick) {
                playWhaleCall(mc, containing);
                nextWhaleCallTick = now + 400L + (long)mc.level.random.nextInt(201);
            }
            if (bestRemaining <= 152L && bestRemaining > 0L && !endingAudioStarted.contains(containing.getId()) && pendingDomainEndTick == Long.MIN_VALUE) {
                endingAudioStarted.add(containing.getId());
                endAudio = SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(END_AUDIO), 1.0f, 1.0f);
                mc.getSoundManager().play(endAudio);
                pendingDomainEndTick = containing.startTick() + 1200L;
                pendingDomainEndTriggered = false;
            }
        }
        if (pendingDomainEndTick != Long.MIN_VALUE && !pendingDomainEndTriggered && now >= pendingDomainEndTick - 20L) {
            pendingDomainEndTriggered = true;
            triggerWhiteSmooth(20, 10, 50);
        }
        if (pendingDomainEndTriggered && now == pendingDomainEndTick) {
            muteOtherSounds(mc, 60);
        }
        if (pendingDomainEndTriggered && now >= pendingDomainEndTick + 80L) {
            pendingDomainEndTick = Long.MIN_VALUE;
            pendingDomainEndTriggered = false;
            endAudio = null;
        }
    }

    private static void playWhaleCall(Minecraft mc, DomainEntity domain) {
        Vec3 player = mc.player.position();
        Vec3 offset = new Vec3(mc.level.random.nextDouble() * 2.0 - 1.0, (mc.level.random.nextDouble() * 2.0 - 1.0) * 0.45, mc.level.random.nextDouble() * 2.0 - 1.0);
        if (offset.lengthSqr() < 1.0E-4) {
            offset = new Vec3(1.0, 0.0, 0.0);
        }
        offset = offset.normalize().scale(10.0 + mc.level.random.nextDouble() * 12.0);
        Vec3 pos = player.add(offset);
        double r2 = domain.radius() * domain.radius();
        for (int i = 0; i < 3 && pos.distanceToSqr(domain.position()) > r2; ++i) {
            pos = pos.lerp(domain.position(), 0.5);
        }
        ResourceLocation id = mc.level.random.nextBoolean() ? WHALE_HIGH : WHALE_MID;
        mc.level.playLocalSound(pos.x, pos.y, pos.z, SoundEvent.createVariableRangeEvent(id), SoundSource.AMBIENT, 1.15f, 0.97f + mc.level.random.nextFloat() * 0.06f, false);
    }

    private static void triggerWhite(int holdTicks, int fadeTicks) {
        whiteFadeInTicks = 0;
        whiteFadeInTotal = 0;
        whiteHoldTicks = Math.max(whiteHoldTicks, holdTicks);
        whiteFadeTicks = Math.max(whiteFadeTicks, fadeTicks);
        whiteFadeTotal = Math.max(whiteFadeTotal, fadeTicks);
    }

    private static void triggerWhiteSmooth(int fadeInTicks, int holdTicks, int fadeTicks) {
        whiteFadeInTicks = Math.max(whiteFadeInTicks, fadeInTicks);
        whiteFadeInTotal = Math.max(whiteFadeInTotal, fadeInTicks);
        whiteHoldTicks = Math.max(whiteHoldTicks, holdTicks);
        whiteFadeTicks = Math.max(whiteFadeTicks, fadeTicks);
        whiteFadeTotal = Math.max(whiteFadeTotal, fadeTicks);
    }

    private static void tickWhiteFlash() {
        if (whiteFadeInTicks > 0) {
            --whiteFadeInTicks;
        } else if (whiteHoldTicks > 0) {
            --whiteHoldTicks;
        } else if (whiteFadeTicks > 0) {
            --whiteFadeTicks;
        } else {
            whiteFadeInTotal = 0;
            whiteFadeTotal = 0;
        }
    }

    @SubscribeEvent
    public static void renderWhite(RenderGuiEvent.Post event) {
        float alpha;
        if (whiteFadeInTicks > 0 && whiteFadeInTotal > 0) {
            alpha = Mth.clamp(1.0f - (float)whiteFadeInTicks / (float)whiteFadeInTotal, 0.0f, 1.0f);
        } else if (whiteHoldTicks > 0) {
            alpha = 1.0f;
        } else if (whiteFadeTicks > 0 && whiteFadeTotal > 0) {
            alpha = Mth.clamp((float)whiteFadeTicks / (float)whiteFadeTotal, 0.0f, 1.0f);
        } else {
            return;
        }
        int a = Mth.clamp((int)(alpha * 255.0f), 0, 255);
        int color = a << 24 | 0xFFFFFF;
        event.getGuiGraphics().fill(0, 0, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(), color);
    }

    private static void muteOtherSounds(Minecraft mc, int ticks) {
        for (SoundSource source : SoundSource.values()) {
            if (source == SoundSource.MASTER) continue;
            mc.getSoundManager().updateSourceVolume(source, 0.0f);
        }
        muteTicks = Math.max(muteTicks, ticks);
    }

    private static void tickMute(Minecraft mc) {
        if (muteTicks <= 0) {
            return;
        }
        if (--muteTicks == 0) {
            restoreSoundVolumes(mc);
        }
    }

    private static void restoreSoundVolumes(Minecraft mc) {
        for (SoundSource source : SoundSource.values()) {
            if (source == SoundSource.MASTER) continue;
            mc.getSoundManager().updateSourceVolume(source, mc.options.getSoundSourceVolume(source));
        }
    }

    private static void stopCastAudio(Minecraft mc) {
        if (castAudio != null) {
            mc.getSoundManager().stop(castAudio);
            castAudio = null;
        }
    }

    private static void reset(Minecraft mc) {
        stopCastAudio(mc);
        if (endAudio != null) {
            mc.getSoundManager().stop(endAudio);
        }
        if (muteTicks > 0) {
            restoreSoundVolumes(mc);
        }
        trackedLevel = null;
        wasDreamCasting = false;
        castTicks = 0;
        endAudio = null;
        pendingDomainEndTick = Long.MIN_VALUE;
        pendingDomainEndTriggered = false;
        nextWhaleCallTick = Long.MIN_VALUE;
        endingAudioStarted.clear();
        for (SimpleSoundInstance sound : remoteCastAudios.values()) {
            mc.getSoundManager().stop(sound);
        }
        remoteCastAudios.clear();
        remoteCastAudioStarted.clear();
        remoteCastCompleted.clear();
        remoteCastWhiteTriggered.clear();
        whiteFadeInTicks = 0;
        whiteFadeInTotal = 0;
        whiteHoldTicks = 0;
        whiteFadeTicks = 0;
        whiteFadeTotal = 0;
        muteTicks = 0;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset(Minecraft.getInstance());
    }

    static {
        pendingDomainEndTick = Long.MIN_VALUE;
        nextWhaleCallTick = Long.MIN_VALUE;
    }
}
