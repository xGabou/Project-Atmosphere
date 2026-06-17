package net.Gabou.projectatmosphere.mixin.client.particle;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticleMotionAccessor {

    @Accessor("xd")
    double projectatmosphere$getXd();

    @Accessor("zd")
    double projectatmosphere$getZd();

    @Accessor("xd")
    void projectatmosphere$setXd(double value);

    @Accessor("zd")
    void projectatmosphere$setZd(double value);

    @Accessor("friction")
    float projectatmosphere$getFriction();
}
