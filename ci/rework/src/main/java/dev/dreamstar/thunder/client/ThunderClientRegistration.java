package dev.dreamstar.thunder.client;
import dev.dreamstar.thunder.ThunderRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="dreamstar",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ThunderClientRegistration {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(ThunderRegistry.SLASH,ThunderSlashRenderer::new);}
}