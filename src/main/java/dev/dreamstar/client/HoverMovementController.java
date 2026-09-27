package dev.dreamstar.client;

import dev.dreamstar.Dreamstar;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Gives the Dream Star hover its own horizontal movement model.
 *
 * Minecraft normally treats a player hovering one block above terrain as airborne, which heavily
 * reduces horizontal control. Here we directly set the horizontal velocity from player input while
 * hovering so the result is independent of vanilla air friction.
 */
@Mod.EventBusSubscriber(modid = Dreamstar.ID, value = Dist.CLIENT)
public final class HoverMovementController {
    // Reduced to half of the previous 2.72 blocks/tick setting.
    private static final double HOVER_MOVE_SPEED = 1.36D;

    private HoverMovementController() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // Apply on both START and END so vanilla airborne travel/friction cannot eat the boost.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused()) return;

        var player = mc.player;
        if (!player.hasEffect(Dreamstar.DREAM_STAR_DOMAIN_EFFECT.get())) return;

        // Real flight always wins. Creative double-space (or another flight provider) can move
        // freely in all directions and is not constrained by Dream Star hover movement.
        if (player.getAbilities().flying) return;

        // Shift intentionally releases the hover and returns movement to vanilla ground/fall logic.
        if (player.isShiftKeyDown()) return;

        float forward = player.input.forwardImpulse;
        float strafe = player.input.leftImpulse;

        Vec3 old = player.getDeltaMovement();

        if (Math.abs(forward) < 0.001F && Math.abs(strafe) < 0.001F) {
            // Stop residual horizontal drift quickly while preserving the server-controlled hover Y.
            player.setDeltaMovement(0.0D, old.y, 0.0D);
            return;
        }

        // Normalize diagonal input so W+D is not faster than W.
        double inputLength = Math.sqrt(forward * forward + strafe * strafe);
        if (inputLength > 1.0D) {
            forward /= (float) inputLength;
            strafe /= (float) inputLength;
        }

        double yaw = Math.toRadians(player.getYRot());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);

        // Minecraft yaw 0 faces +Z. Positive leftImpulse is camera-left.
        double motionX = (strafe * cos - forward * sin) * HOVER_MOVE_SPEED;
        double motionZ = (forward * cos + strafe * sin) * HOVER_MOVE_SPEED;

        player.setDeltaMovement(motionX, old.y, motionZ);
        player.setOnGround(true);
        player.fallDistance = 0.0F;
    }
}
