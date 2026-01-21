package net.Gabou.projectatmosphere.mixin.client.particle;

import net.Gabou.projectatmosphere.particles.WindParticlePusher;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.AshParticle;
import net.minecraft.client.particle.CampfireSmokeParticle;
import net.minecraft.client.particle.DustColorTransitionParticle;
import net.minecraft.client.particle.DustParticle;
import net.minecraft.client.particle.FallingDustParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SmokeParticle;
import net.minecraft.client.particle.SnowflakeParticle;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Particle.class, remap = false)
public abstract class WindBentParticleMixin {
    @Shadow protected ClientLevel level;
    @Shadow protected double x;
    @Shadow protected double y;
    @Shadow protected double z;
    @Shadow protected double xd;
    @Shadow protected double yd;
    @Shadow protected double zd;

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void projectatmosphere$applyWindBend(CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof CampfireSmokeParticle
                || self instanceof SmokeParticle
                || self instanceof AshParticle
                || self instanceof DustParticle
                || self instanceof DustColorTransitionParticle
                || self instanceof FallingDustParticle
                || self instanceof SnowflakeParticle)) {
            return;
        }

        Vec3 push = WindParticlePusher.computeWindPush(this.level, new Vec3(this.x, this.y, this.z));
        if (push.lengthSqr() <= 0.0) {
            return;
        }

        float scale;
        if (self instanceof CampfireSmokeParticle) {
            scale = 0.6f;
        } else if (self instanceof SmokeParticle) {
            scale = 0.75f;
        } else if (self instanceof AshParticle) {
            scale = 0.7f;
        } else if (self instanceof DustParticle || self instanceof DustColorTransitionParticle) {
            scale = 0.55f;
        } else if (self instanceof FallingDustParticle) {
            scale = 0.45f;
        } else if (self instanceof SnowflakeParticle) {
            scale = 0.85f;
        } else {
            scale = 0.5f;
        }

        this.xd += push.x * scale;
        this.yd += push.y * (scale * 0.5f);
        this.zd += push.z * scale;
    }
}
