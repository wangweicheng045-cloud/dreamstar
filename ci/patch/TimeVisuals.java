package dev.dreamstar.time.client;
import dev.dreamstar.time.TimeRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.renderer.*;
import net.minecraft.world.entity.LivingEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.util.*;

@Mod.EventBusSubscriber(modid="dreamstar",value=Dist.CLIENT)
public final class TimeVisuals {
    private static final Map<Integer,float[]> poses=new HashMap<>();
    private static final Deque<float[]> colors=new ArrayDeque<>();
    private static final Map<Integer,SimpleSoundInstance> lockSounds=new HashMap<>();
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void pre(RenderLivingEvent.Pre<?,?> event){
        var e=event.getEntity();if(!e.hasEffect(TimeRegistry.EFFECT)){poses.remove(e.getId());return;}
        float[] p=poses.computeIfAbsent(e.getId(),k->new float[]{e.yHeadRot,e.yBodyRot,e.getXRot(),e.attackAnim,e.tickCount});
        e.yHeadRot=e.yHeadRotO=p[0];e.yBodyRot=e.yBodyRotO=p[1];e.setXRot(p[2]);e.xRotO=p[2];e.attackAnim=e.oAttackAnim=p[3];e.tickCount=(int)p[4];e.walkAnimation.setSpeed(0);
        e.xOld=e.xo=e.getX();e.yOld=e.yo=e.getY();e.zOld=e.zo=e.getZ();
        if(event.getMultiBufferSource() instanceof MultiBufferSource.BufferSource b)b.endBatch();
        colors.push(RenderSystem.getShaderColor().clone());RenderSystem.setShaderColor(1f,.22f,.86f,1f);
    }
    @SubscribeEvent public static void post(RenderLivingEvent.Post<?,?> event){
        if(!event.getEntity().hasEffect(TimeRegistry.EFFECT)||colors.isEmpty())return;
        if(event.getMultiBufferSource() instanceof MultiBufferSource.BufferSource b)b.endBatch();
        float[] c=colors.pop();RenderSystem.setShaderColor(c[0],c[1],c[2],c[3]);
    }
    @SubscribeEvent public static void clocks(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        TimeModels.ensureLoaded();
        poses.keySet().removeIf(id->!(mc.level.getEntity(id) instanceof LivingEntity e)||!e.hasEffect(TimeRegistry.EFFECT));
        var camera=event.getCamera().getPosition();var pose=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();
        float seconds=(mc.level.getGameTime()+event.getPartialTick())/20f;
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof LivingEntity e&&e.hasEffect(TimeRegistry.EFFECT)&&e.distanceToSqr(camera)<16384){
            var center=e.getBoundingBox().getCenter();
            // Tick inner radius is 14.5 model units. Inclination shortens its projection by sqrt(2).
            float scale=(float)(Math.sqrt(e.getBbHeight()*e.getBbHeight()+2*e.getBbWidth()*e.getBbWidth())*.5*1.25/(14.5/16/Math.sqrt(2)));
            pose.pushPose();pose.translate(center.x-camera.x,center.y-camera.y,center.z-camera.z);
            pose.mulPose(Axis.XP.rotationDegrees(45));pose.scale(scale,scale,scale);
            TimeModels.draw(TimeModels.clock,pose,buffers.getBuffer(RenderType.entityTranslucent(TimeModels.texture("time_clock"))),seconds*5,.55f);
            pose.popPose();
        }
        buffers.endBatch(RenderType.entityTranslucent(TimeModels.texture("time_clock")));
    }
    @SubscribeEvent public static void sounds(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.level==null){stopLockSounds(mc);return;}
        var active=new HashSet<Integer>();
        if(mc.player!=null&&mc.player.hasEffect(TimeRegistry.EFFECT))lockSound(mc,mc.player,active);
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof LivingEntity e&&e.hasEffect(TimeRegistry.EFFECT))lockSound(mc,e,active);
        var iterator=lockSounds.entrySet().iterator();
        while(iterator.hasNext()){var entry=iterator.next();if(!active.contains(entry.getKey())){mc.getSoundManager().stop(entry.getValue());iterator.remove();}}
    }
    private static void lockSound(Minecraft mc,LivingEntity e,Set<Integer> active){
        active.add(e.getId());
        if(lockSounds.containsKey(e.getId()))return;
        var sound=new SimpleSoundInstance(TimeRegistry.TIME_LOCK_SOUND_ID,SoundSource.PLAYERS,1f,1f,
                SoundInstance.createUnseededRandom(),true,0,SoundInstance.Attenuation.LINEAR,
                e.getX(),e.getY()+e.getBbHeight()*.5,e.getZ(),false);
        lockSounds.put(e.getId(),sound);mc.getSoundManager().play(sound);
    }
    private static void stopLockSounds(Minecraft mc){for(var sound:lockSounds.values())mc.getSoundManager().stop(sound);lockSounds.clear();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){stopLockSounds(Minecraft.getInstance());poses.clear();colors.clear();}
    @SubscribeEvent public static void input(MovementInputUpdateEvent e){if(e.getEntity().hasEffect(TimeRegistry.EFFECT)){var i=e.getInput();i.forwardImpulse=i.leftImpulse=0;i.jumping=false;i.shiftKeyDown=false;i.up=i.down=i.left=i.right=false;}}
}
