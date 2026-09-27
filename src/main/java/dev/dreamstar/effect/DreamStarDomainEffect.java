package dev.dreamstar.effect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Personal blessing granted only while the caster remains inside their own Dream Star Domain.
 *
 * Attribute modifiers are registered on the effect itself in Dreamstar:
 * +40% ice spell power and +20% movement speed.
 */
public final class DreamStarDomainEffect extends MobEffect {
    private static final double GROUND_SCAN_DISTANCE = 64.0D;
    private static final double HOVER_HEIGHT = 1.0D;

    public DreamStarDomainEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x80E8FF);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity livingEntity, int amplifier) {
        if (!(livingEntity instanceof ServerPlayer player)) return;

        // If the player has entered a real flight state (creative double-space, or another
        // compatible flight ability), Dream Star must not fight that flight by pulling them
        // back to one block above the ground.
        if (player.getAbilities().flying) {
            player.fallDistance = 0.0F;
            return;
        }

        // The blessing should never build fall damage. Holding Shift intentionally releases
        // the hover and lets gravity bring the caster back to the ground.
        player.fallDistance = 0.0F;
        if (player.isShiftKeyDown()) return;

        Vec3 from = new Vec3(player.getX(), player.getY() + 0.25D, player.getZ());
        Vec3 to = from.add(0.0D, -GROUND_SCAN_DISTANCE, 0.0D);
        var hit = player.level().clip(new ClipContext(
                from,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        if (hit.getType() == HitResult.Type.MISS) return;

        double targetY = hit.getLocation().y + HOVER_HEIGHT;
        double error = targetY - player.getY();
        Vec3 movement = player.getDeltaMovement();

        // A proportional correction gives a soft magical lift instead of teleport snapping.
        // +0.08 roughly offsets vanilla gravity so the equilibrium stays near exactly one block.
        double desiredY = Mth.clamp(error * 0.38D + 0.08D, -0.30D, 0.30D);
        if (Math.abs(error) < 0.035D) desiredY = 0.08D;

        player.setDeltaMovement(movement.x, desiredY, movement.z);
        player.hurtMarked = true;
    }
}
