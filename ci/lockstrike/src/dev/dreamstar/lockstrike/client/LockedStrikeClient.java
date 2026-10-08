package dev.dreamstar.lockstrike.client;
import dev.dreamstar.lockstrike.LockedStrikeRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="dreamstar",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class LockedStrikeClient {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(LockedStrikeRegistry.ENTITY,LockedStrikeRenderer::new);}
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent e){e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->LockedStrikeRenderer.reload());}
}
