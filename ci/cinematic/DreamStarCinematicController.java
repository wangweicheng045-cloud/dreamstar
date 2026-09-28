package dev.dreamstar.client;

import dev.dreamstar.Dreamstar;
import dev.dreamstar.domain.DomainEntity;
import dev.dreamstar.domain.DomainTiming;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/** Client-only timing for Dream Star's chant, white flashes, whale ambience, and ending cinematic. */
@Mod.EventBusSubscriber(modid = Dreamstar.ID, value = Dist.CLIENT)
public final class DreamStarCinematicController {
    private static final String SPELL_ID = "dreamstar:dream_star_domain";
    private static final ResourceLocation CAST_AUDIO = new ResourceLocation(Dreamstar.ID, "domain_cast");
    private static final ResourceLocation END_AUDIO = new ResourceLocation(Dreamstar.ID, "domain_end");
    private static final ResourceLocation WHALE_HIGH = new ResourceLocation(Dreamstar.ID, "whale_high");
    private static final ResourceLocation WHALE_MID = new ResourceLocation(Dreamstar.ID, "whale_mid");

    private static final int CAST_TOTAL_TICKS = 100;
    // Audio starts 2.30 s into the five-second cast, leaving exactly 2.70 s to the cast-end flash.
    private static final int CAST_AUDIO_START_TICK = 46;
    // End audio starts 7.60 s before the 60-second domain expires.
    private static final int END_AUDIO_LEAD_TICKS = 152;
    private static final int END_MUTE_TICKS = 60;

    private static final Set<Integer> endingAudioStarted = new HashSet<>();
    private static ClientLevel trackedLevel;

    private static boolean wasDreamCasting;
    private static int castTicks;
    private static SimpleSoundInstance castAudio;

    private static long pendingDomainEndTick = Long.MIN_VALUE;
    private static boolean pendingDomainEndTriggered;
    private static SimpleSoundInstance endAudio;

    private static long nextWhaleCallTick = Long.MIN_VALUE;
    private static int whiteHoldTicks;
    private static int whiteFadeTicks;
    private static int whiteFadeTotal;
    private static int muteTicks;

    private DreamStarCinematicController() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset(mc);
            return;
        }
        if (trackedLevel != mc.level) {
            reset(mc);
            trackedLevel = mc.level;
        }
        if (mc.isPaused()) return;

        tickCast(mc);
        tickDomainAudio(mc);
        tickWhiteFlash();
        tickMute(mc);
    }

    private static void tickCast(Minecraft mc) {
        boolean dreamCasting = ClientMagicData.isCasting()
                && SPELL_ID.equals(ClientMagicData.getCastingSpellId());

        if (dreamCasting) {
            if (!wasDreamCasting) {
                castTicks = 0;
                stopCastAudio(mc);
            }
            castTicks++;

            if (castTicks == CAST_AUDIO_START_TICK) {
                castAudio = SimpleSoundInstance.forUI(
                        SoundEvent.createVariableRangeEvent(CAST_AUDIO), 1.0F, 1.18F);
                mc.getSoundManager().play(castAudio);
            }
        } else if (wasDreamCasting) {
            // Ending before the five-second completion is a cancelled chant: cut its audio at once.
            if (castTicks < CAST_TOTAL_TICKS - 2) {
                stopCastAudio(mc);
            } else {
                // Successful cast: approximately two seconds of white transition.
                triggerWhite(10, 30);
            }
            castTicks = 0;
        }

        wasDreamCasting = dreamCasting;
    }

    private static void tickDomainAudio(Minecraft mc) {
        long now = mc.level.getGameTime();
        DomainEntity containing = null;
        long bestRemaining = Long.MAX_VALUE;

        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof DomainEntity domain) || domain.isRemoved()) continue;
            double radius = domain.radius();
            if (mc.player.position().distanceToSqr(domain.position()) > radius * radius) continue;

            long remaining = domain.startTick() + DomainTiming.DURATION - now;
            if (remaining > 0L && remaining < bestRemaining) {
                containing = domain;
                bestRemaining = remaining;
            }
        }

        if (containing == null) {
            nextWhaleCallTick = Long.MIN_VALUE;
        } else {
            if (nextWhaleCallTick == Long.MIN_VALUE) {
                nextWhaleCallTick = now + 160L + mc.level.random.nextInt(121);
            }

            if (bestRemaining > END_AUDIO_LEAD_TICKS
                    && pendingDomainEndTick == Long.MIN_VALUE
                    && now >= nextWhaleCallTick) {
                playWhaleCall(mc, containing);
                nextWhaleCallTick = now + 400L + mc.level.random.nextInt(201);
            }

            if (bestRemaining <= END_AUDIO_LEAD_TICKS
                    && bestRemaining > 0L
                    && !endingAudioStarted.contains(containing.getId())
                    && pendingDomainEndTick == Long.MIN_VALUE) {
                endingAudioStarted.add(containing.getId());
                endAudio = SimpleSoundInstance.forUI(
                        SoundEvent.createVariableRangeEvent(END_AUDIO), 1.0F, 1.0F);
                mc.getSoundManager().play(endAudio);
                pendingDomainEndTick = containing.startTick() + DomainTiming.DURATION;
                pendingDomainEndTriggered = false;
            }
        }

        if (pendingDomainEndTick != Long.MIN_VALUE
                && !pendingDomainEndTriggered
                && now >= pendingDomainEndTick - 20L) {
            pendingDomainEndTriggered = true;
            // Begin one full second before collapse; total white/fade time remains about three seconds.
            triggerWhite(10, 50);
        }

        if (pendingDomainEndTriggered && now == pendingDomainEndTick) {
            muteOtherSounds(mc, END_MUTE_TICKS);
        }

        if (pendingDomainEndTriggered && now >= pendingDomainEndTick + END_MUTE_TICKS + 20L) {
            pendingDomainEndTick = Long.MIN_VALUE;
            pendingDomainEndTriggered = false;
            endAudio = null;
        }
    }

    private static void playWhaleCall(Minecraft mc, DomainEntity domain) {
        Vec3 player = mc.player.position();
        Vec3 offset = new Vec3(
                mc.level.random.nextDouble() * 2.0D - 1.0D,
                (mc.level.random.nextDouble() * 2.0D - 1.0D) * 0.45D,
                mc.level.random.nextDouble() * 2.0D - 1.0D
        );
        if (offset.lengthSqr() < 1.0E-4D) offset = new Vec3(1, 0, 0);
        offset = offset.normalize().scale(10.0D + mc.level.random.nextDouble() * 12.0D);
        Vec3 pos = player.add(offset);

        double r2 = domain.radius() * domain.radius();
        for (int i = 0; i < 3 && pos.distanceToSqr(domain.position()) > r2; i++) {
            pos = pos.lerp(domain.position(), 0.5D);
        }

        ResourceLocation id = mc.level.random.nextBoolean() ? WHALE_HIGH : WHALE_MID;
        mc.level.playLocalSound(
                pos.x, pos.y, pos.z,
                SoundEvent.createVariableRangeEvent(id),
                SoundSource.AMBIENT,
                1.15F,
                0.97F + mc.level.random.nextFloat() * 0.06F,
                false
        );
    }

    private static void triggerWhite(int holdTicks, int fadeTicks) {
        whiteHoldTicks = Math.max(whiteHoldTicks, holdTicks);
        whiteFadeTicks = Math.max(whiteFadeTicks, fadeTicks);
        whiteFadeTotal = Math.max(whiteFadeTotal, fadeTicks);
    }

    private static void tickWhiteFlash() {
        if (whiteHoldTicks > 0) {
            whiteHoldTicks--;
        } else if (whiteFadeTicks > 0) {
            whiteFadeTicks--;
        } else {
            whiteFadeTotal = 0;
        }
    }

    @SubscribeEvent
    public static void renderWhite(RenderGuiEvent.Post event) {
        float alpha;
        if (whiteHoldTicks > 0) {
            alpha = 1.0F;
        } else if (whiteFadeTicks > 0 && whiteFadeTotal > 0) {
            alpha = Mth.clamp(whiteFadeTicks / (float) whiteFadeTotal, 0.0F, 1.0F);
        } else {
            return;
        }

        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        int color = (a << 24) | 0x00FFFFFF;
        event.getGuiGraphics().fill(
                0, 0,
                event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight(),
                color
        );
    }

    private static void muteOtherSounds(Minecraft mc, int ticks) {
        for (SoundSource source : SoundSource.values()) {
            if (source != SoundSource.MASTER) {
                mc.getSoundManager().updateSourceVolume(source, 0.0F);
            }
        }
        muteTicks = Math.max(muteTicks, ticks);
    }

    private static void tickMute(Minecraft mc) {
        if (muteTicks <= 0) return;
        muteTicks--;
        if (muteTicks == 0) restoreSoundVolumes(mc);
    }

    private static void restoreSoundVolumes(Minecraft mc) {
        for (SoundSource source : SoundSource.values()) {
            if (source != SoundSource.MASTER) {
                mc.getSoundManager().updateSourceVolume(source, mc.options.getSoundSourceVolume(source));
            }
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
        if (endAudio != null) mc.getSoundManager().stop(endAudio);
        if (muteTicks > 0) restoreSoundVolumes(mc);

        trackedLevel = null;
        wasDreamCasting = false;
        castTicks = 0;
        endAudio = null;
        pendingDomainEndTick = Long.MIN_VALUE;
        pendingDomainEndTriggered = false;
        nextWhaleCallTick = Long.MIN_VALUE;
        endingAudioStarted.clear();
        whiteHoldTicks = 0;
        whiteFadeTicks = 0;
        whiteFadeTotal = 0;
        muteTicks = 0;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset(Minecraft.getInstance());
    }
}
