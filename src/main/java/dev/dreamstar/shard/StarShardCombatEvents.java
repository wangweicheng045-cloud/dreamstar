package dev.dreamstar.shard;

import dev.dreamstar.Dreamstar;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Starts one shard sequence whenever a Dream Star-buffed player damages a living target. */
@Mod.EventBusSubscriber(modid = Dreamstar.ID)
public final class StarShardCombatEvents {
    private StarShardCombatEvents() {}

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (!(attacker.level() instanceof ServerLevel level)) return;
        if (event.getSource().getDirectEntity() instanceof StarShardAttackEntity) return;
        if (!attacker.hasEffect(Dreamstar.DREAM_STAR_DOMAIN_EFFECT.get())) return;
        if (target == attacker || !target.isAlive() || target.isSpectator()) return;

        // Do not stack several timelines on the same target from rapid multi-hit spells.
        boolean alreadyActive = !level.getEntitiesOfClass(
                StarShardAttackEntity.class,
                target.getBoundingBox().inflate(4.0D),
                sequence -> sequence.matches(attacker.getUUID(), target.getId())
        ).isEmpty();
        if (alreadyActive) return;

        StarShardAttackEntity sequence = Dreamstar.STAR_SHARD_ATTACK.get().create(level);
        if (sequence == null) return;

        sequence.initialize(target, attacker, attacker.getRandom().nextInt());
        level.addFreshEntity(sequence);
    }
}