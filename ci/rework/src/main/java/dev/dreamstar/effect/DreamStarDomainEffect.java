/*
 * Decompiled with CFR 0.152.
 */
package dev.dreamstar.effect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class DreamStarDomainEffect
extends MobEffect {
    private static final double GROUND_SCAN_DISTANCE = 64.0;
    private static final double HOVER_HEIGHT = 1.0;

    public DreamStarDomainEffect() {
        super(MobEffectCategory.BENEFICIAL, 8448255);
    }

    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    public void applyEffectTick(LivingEntity livingEntity, int amplifier) {
        if (livingEntity instanceof ServerPlayer player && player.getAbilities().flying) {
            livingEntity.fallDistance = 0.0f;
            return;
        }
        livingEntity.fallDistance = 0.0f;
        if (livingEntity instanceof ServerPlayer player && player.isShiftKeyDown()) {
            return;
        }
        Vec3 from = new Vec3(livingEntity.getX(), livingEntity.getY() + 0.25, livingEntity.getZ());
        Vec3 to = from.add(0.0, -64.0, 0.0);
        BlockHitResult hit = livingEntity.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, livingEntity));
        if (hit.getType() == HitResult.Type.MISS) {
            return;
        }
        double targetY = hit.getLocation().y + 1.0;
        double error = targetY - livingEntity.getY();
        Vec3 movement = livingEntity.getDeltaMovement();
        double desiredY = Mth.clamp((double)(error * 0.38 + 0.08), (double)-0.3, (double)0.3);
        if (Math.abs(error) < 0.035) {
            desiredY = 0.08;
        }
        livingEntity.setDeltaMovement(movement.x, desiredY, movement.z);
        livingEntity.hurtMarked = true;
    }
}
