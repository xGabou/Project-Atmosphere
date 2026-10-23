package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260;
import net.Gabou.projectatmosphere.clouds.morphology.NeutralLightVolume;
import net.Gabou.projectatmosphere.clouds.morphology.StormGeometryProgram;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype;

/**
 * T260.5: a frozen convective-system program with its baked neutral light appended to the fixture texture. Geometry,
 * density and bounds are the wrapped program's own; only the texture (and so its signature) grows by the light rows.
 */
final class NeutralLitProgram implements StormGeometryProgram {
    final StormGeometryProgram base;
    final NeutralLightVolume light;
    private final float[] texture;
    private final int rows;
    private final long signature;

    NeutralLitProgram(ConvectiveSystemT260.Program base, float extinction) { this(base, base.volume(), extinction); }

    /** T260.6: any sparse-brick program (the reconstruction arms), lit by the identical, frozen T260.5 bake. */
    NeutralLitProgram(StormGeometryProgram base, net.Gabou.projectatmosphere.clouds.morphology.SparseBrickVolume volume, float extinction) {
        this(base, volume, extinction, NeutralLightVolume.SPACING);
    }

    /** T260.11: region assemblies light on a coarser lattice when km-wide. */
    NeutralLitProgram(StormGeometryProgram base, net.Gabou.projectatmosphere.clouds.morphology.SparseBrickVolume volume, float extinction, float spacing) {
        this.base = base;
        light = new NeutralLightVolume(volume, extinction, spacing);
        texture = light.appendTo(base.textureData());
        rows = light.rows(base.textureHeight());
        signature = light.signature(base.signature());
    }

    @Override public float radius() { return base.radius(); }
    @Override public float height() { return base.height(); }
    @Override public float axisX(float fraction) { return base.axisX(fraction); }
    @Override public float density(float x, float y, float z, StormMorphologyPrototype.Scale scale) { return base.density(x, y, z, scale); }
    @Override public StormMorphologyPrototype.Bounds bounds() { return base.bounds(); }
    @Override public float[] packed() { return base.packed(); }
    @Override public long signature() { return signature; }
    @Override public int version() { return base.version(); }
    @Override public boolean growthDefined() { return true; }
    @Override public float[] textureData() { return texture; }
    @Override public int textureWidth() { return base.textureWidth(); }
    @Override public int textureHeight() { return rows; }
}
