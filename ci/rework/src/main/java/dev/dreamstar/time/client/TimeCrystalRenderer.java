package dev.dreamstar.time.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.dreamstar.time.TimeCrystal;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class TimeCrystalRenderer extends EntityRenderer<TimeCrystal> {
    public TimeCrystalRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override public ResourceLocation getTextureLocation(TimeCrystal entity) { return TimeModels.texture("time_crystal"); }

    @Override
    public void render(TimeCrystal entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        TimeModels.ensureLoaded();
        pose.pushPose();
        if (entity.isLaunched() && entity.getDeltaMovement().lengthSqr() > 1.0E-6D) {
            var motion = entity.getDeltaMovement().normalize();
            Quaternionf aim = new Quaternionf().rotationTo(
                    new Vector3f(0.0F, -1.0F, 0.0F),
                    new Vector3f((float)motion.x, (float)motion.y, (float)motion.z));
            pose.mulPose(aim);
        } else if (entity.getOwner() instanceof LivingEntity owner) {
            var look = owner.getViewVector(partialTick).normalize();
            Quaternionf aim = new Quaternionf().rotationTo(
                    new Vector3f(0.0F, -1.0F, 0.0F),
                    new Vector3f((float)look.x, (float)look.y, (float)look.z));
            pose.mulPose(aim);
            pose.mulPose(new Quaternionf().rotateY((entity.tickCount + partialTick) * 0.055F));
        }
        TimeModels.draw(TimeModels.crystal, pose,
                buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity))), 0.0F, 1.0F);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }
}
