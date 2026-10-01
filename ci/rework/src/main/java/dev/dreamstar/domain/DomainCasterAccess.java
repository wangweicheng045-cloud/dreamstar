package dev.dreamstar.domain;

import dev.dreamstar.whale.WhaleAllies;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Player-agnostic access to a Dream Star Domain caster.
 * Keeps domain logic compatible with mobs, summons and players without
 * forcing callers to cast the caster to Player.
 */
public interface DomainCasterAccess {
    UUID casterUUID();

    UUID ownerUUID();

    default LivingEntity resolveDomainCaster(ServerLevel level) {
        return WhaleAllies.caster(level, this.casterUUID(), this.ownerUUID());
    }
}
