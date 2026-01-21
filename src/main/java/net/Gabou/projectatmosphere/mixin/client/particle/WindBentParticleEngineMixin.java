package net.Gabou.projectatmosphere.mixin.client.particle;

import net.Gabou.projectatmosphere.modules.wind.WindConfig;
import net.Gabou.projectatmosphere.particles.WindParticlePusher;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class WindBentParticleEngineMixin {

    @Shadow protected ClientLevel level;

    @Inject(method = "tickParticle", at = @At("TAIL"))
    private void project_atmosphere$applyWindAfterTick(Particle particle, CallbackInfo ci) {
        if (!project_atmosphere$shouldAffect(particle)) return;

        Vec3 wind = WindParticlePusher.computeWindPush(this.level, particle.getPos());
        if (wind.lengthSqr() <= 1.0E-8) return;

        ParticleMotionAccessor motion = (ParticleMotionAccessor) particle;

        float weight = project_atmosphere$getWeight(particle);
        float bendStrength = WindConfig.particleBendStrength();
        project_atmosphere$applyWindBendXZ(motion, wind, weight, bendStrength);
    }

    @Unique
    private static void project_atmosphere$applyWindBendXZ(ParticleMotionAccessor motion, Vec3 wind, float weight, float bendStrength) {
        double mx = motion.projectatmosphere$getXd();
        double mz = motion.projectatmosphere$getZd();

        float lerp = bendStrength / Math.max(0.01f, weight);
        lerp = Mth.clamp(lerp, 0.0f, 1.0f);

        double nextX = mx + (wind.x - mx) * lerp;
        double nextZ = mz + (wind.z - mz) * lerp;

        float drag = motion.projectatmosphere$getFriction();
        motion.projectatmosphere$setXd(nextX * drag);
        motion.projectatmosphere$setZd(nextZ * drag);
    }

    @Unique
    private static boolean project_atmosphere$shouldAffect(Particle p) {
        return p instanceof CampfireSmokeParticle
                || p instanceof SmokeParticle
                || p instanceof AshParticle
                || p instanceof DustParticle
                || p instanceof DustColorTransitionParticle
                || p instanceof FallingDustParticle
                || p instanceof SnowflakeParticle;
    }

    @Unique
    private static float project_atmosphere$getWeight(Particle p) {
        if (p instanceof CampfireSmokeParticle) return 1.5f;
        if (p instanceof SmokeParticle) return 2.0f;
        if (p instanceof AshParticle) return 2.0f;
        if (p instanceof DustParticle || p instanceof DustColorTransitionParticle) return 3.0f;
        if (p instanceof FallingDustParticle) return 5.0f;
        if (p instanceof SnowflakeParticle) return 1.2f;
        return 3.0f;
    }
}
