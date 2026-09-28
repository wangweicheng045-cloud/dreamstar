package dev.dreamstar.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public final class ConstellationParticle extends TextureSheetParticle {
    private final float baseSize;
    private final float spinSpeed;

    private ConstellationParticle(ClientLevel level, double x, double y, double z,
                                  double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        hasPhysics = false;
        friction = .992f;
        lifetime = 90 + random.nextInt(80);
        baseSize = .55f + random.nextFloat() * 1.65f;
        quadSize = baseSize;
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
        spinSpeed = (random.nextFloat() - .5f) * .009f;
        rCol = gCol = bCol = 1f;
        alpha = 0f;
        pickSprite(sprites);
    }

    @Override public void tick() {
        xo = x; yo = y; zo = z;
        if (age++ >= lifetime) { remove(); return; }
        x += xd; y += yd; z += zd;
        xd *= friction; yd = yd * friction + .00035; zd *= friction;
        oRoll = roll; roll += spinSpeed;
        float fadeIn = Mth.clamp(age / 14f, 0f, 1f);
        float fadeOut = Mth.clamp((lifetime - age) / 22f, 0f, 1f);
        alpha = .78f * Math.min(fadeIn, fadeOut);
        quadSize = baseSize * (.96f + .05f * Mth.sin(age * .08f));
    }

    @Override public int getLightColor(float partialTick) { return 0xF000F0; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                                  double x, double y, double z, double dx, double dy, double dz) {
            return new ConstellationParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
