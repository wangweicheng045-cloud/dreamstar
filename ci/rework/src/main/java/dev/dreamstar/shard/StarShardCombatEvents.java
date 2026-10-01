/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraftforge.eventbus.api.SubscribeEvent
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber
 */
package dev.dreamstar.shard;

import dev.dreamstar.Dreamstar;
import dev.dreamstar.shard.StarShardAttackEntity;
import dev.dreamstar.whale.WhaleShardEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="dreamstar")
public final class StarShardCombatEvents {
    private StarShardCombatEvents() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        Entity entity = event.getSource().getEntity();
        if (!(entity instanceof LivingEntity attacker)) {
            return;
        }
        Level level = attacker.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel level2 = (ServerLevel)level;
        if (event.getSource().getDirectEntity() instanceof StarShardAttackEntity || event.getSource().getDirectEntity() instanceof WhaleShardEntity) {
            return;
        }
        if (!attacker.hasEffect(Dreamstar.DREAM_STAR_DOMAIN_EFFECT.get())) {
            return;
        }
        if (target == attacker || !target.isAlive() || target.isSpectator()) {
            return;
        }
        boolean alreadyActive = !level2.getEntitiesOfClass(StarShardAttackEntity.class, target.getBoundingBox().inflate(4.0), sequence -> sequence.matches(attacker.getUUID(), target.getId())).isEmpty();
        boolean bl = alreadyActive;
        if (alreadyActive) {
            return;
        }
        StarShardAttackEntity sequence2 = (StarShardAttackEntity)Dreamstar.STAR_SHARD_ATTACK.get().create(level2);
        if (sequence2 == null) {
            return;
        }
        sequence2.initialize(target, attacker, attacker.getRandom().nextInt());
        level2.addFreshEntity(sequence2);
    }
}
