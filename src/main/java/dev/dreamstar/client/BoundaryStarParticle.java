package dev.dreamstar.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Small full-bright four-point stars, with a brief flash and gentle upward drift. */
public final class BoundaryStarParticle extends TextureSheetParticle {
    private final float size;

    private BoundaryStarParticle(ClientLevel level, double x, double y, double z,
                                 double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        xd = dx; yd = dy; zd = dz;
        friction = .96f;
        hasPhysics = false;
        lifetime = 28 + random.nextInt(22);
        size = .09f + random.nextFloat() * .11f;
        quadSize = size;
        float tint = random.nextFloat();
        rCol = .5f + tint * .4f;
        gCol = .65f + (1 - tint) * .3f;
        bCol = 1;
        alpha = 0;
        pickSprite(sprites);
    }

    @Override public void tick() {
        super.tick();
        float progress = (float)age / lifetime;
        alpha = Math.max(0, Math.min(1, Math.min(age / 4f, (lifetime - age) / 12f)));
        quadSize = size * (.75f + .4f * (float)Math.sin(progress * Math.PI));
    }
    @Override public int getLightColor(float partialTick) { return 0xF000F0; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                                 double x, double y, double z, double dx, double dy, double dz) {
            return new BoundaryStarParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
