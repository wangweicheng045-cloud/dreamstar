package dev.dreamstar.client;

import dev.dreamstar.Dreamstar;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps Dream Star hovering movement on the normal ground-movement branch.
 *
 * Without this, Minecraft treats the caster as airborne because their feet are one block above
 * terrain, so horizontal acceleration is reduced to air-control speed. The actual speed bonus is
 * still the +20% movement-speed attribute on the Dream Star effect; this class only removes the
 * unintended airborne penalty while hovering.
 */
@Mod.EventBusSubscriber(modid = Dreamstar.ID, value = Dist.CLIENT)
public final class HoverMovementController {
    private HoverMovementController() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused()) return;

        var player = mc.player;
        if (!player.hasEffect(Dreamstar.DREAM_STAR_DOMAIN_EFFECT.get())) return;

        // Shift intentionally releases the hover, so restore vanilla airborne/ground handling then.
        if (player.isShiftKeyDown()) return;

        // Force only the movement branch to behave as if grounded. Collision is unchanged, so the
        // player still visibly floats one block above terrain and cannot walk through blocks.
        player.setOnGround(true);
        player.fallDistance = 0.0F;
    }
}
