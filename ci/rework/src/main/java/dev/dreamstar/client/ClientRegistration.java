package dev.dreamstar.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.dreamstar.Dreamstar;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="dreamstar", value={Dist.CLIENT}, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ClientRegistration {
    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(Dreamstar.BOUNDARY_STAR.get(), BoundaryStarParticle.Provider::new);
        event.registerSpriteSet(Dreamstar.CONSTELLATION.get(), ConstellationParticle.Provider::new);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Dreamstar.DOMAIN.get(), NoopRenderer::new);
        event.registerEntityRenderer(Dreamstar.DOMAIN_CAST_SIGNAL.get(), NoopRenderer::new);
        event.registerEntityRenderer(Dreamstar.STAR_SHARD_ATTACK.get(), NoopRenderer::new);
        event.registerEntityRenderer(Dreamstar.STAR_WHALE.get(), StarWhaleRenderer::new);
        event.registerEntityRenderer(Dreamstar.WHALE_SHARD.get(), WhaleShardRenderer::new);
    }

    @SubscribeEvent
    public static void shaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation("dreamstar", "domain"), DefaultVertexFormat.POSITION), shader -> {
            DomainRenderer.shader = shader;
        });
    }
}
