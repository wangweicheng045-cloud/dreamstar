package dev.dreamstar.time;

import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.capabilities.magic.PlayerRecasts;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import io.redspace.ironsspellbooks.capabilities.magic.RecastResult;

/** Recast instance whose first visible charge is not consumed by the initial cast. */
public final class TimeCrystalRecast extends RecastInstance {
    public TimeCrystalRecast(String spellId, int spellLevel, int crystalCount, int ticksToLive, CastSource castSource) {
        super();
        this.spellId = spellId;
        this.spellLevel = spellLevel;
        this.remainingRecasts = crystalCount;
        this.totalRecasts = crystalCount;
        this.ticksToLive = ticksToLive;
        this.remainingTicks = ticksToLive;
        this.castSource = castSource;
        this.castData = null;
    }

    /** Consume one status-bar charge without refreshing the 10-second lifetime. */
    public void consumeOne(PlayerRecasts recasts) {
        if (remainingRecasts <= 0) return;
        remainingRecasts--;
        if (remainingRecasts > 0) {
            recasts.syncToPlayer(this);
        } else {
            recasts.removeRecast(this, RecastResult.USED_ALL_RECASTS);
        }
    }
}
