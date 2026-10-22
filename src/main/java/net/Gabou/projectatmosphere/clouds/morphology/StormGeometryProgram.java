package net.Gabou.projectatmosphere.clouds.morphology;

/** Experimental inspection interface, not a production/save/network contract. */
public interface StormGeometryProgram {
    float radius();
    float height();
    float axisX(float fraction);
    float density(float x, float y, float z, StormMorphologyPrototype.Scale scale);
    StormMorphologyPrototype.Bounds bounds();
    float[] packed();
    long signature();
    int version();
    boolean growthDefined();
    /** Fixture texture content (RGBA32F, row-major); default: the packed recipe as one row. */
    default float[] textureData() { return packed(); }
    default int textureWidth() { return packed().length / 4; }
    default int textureHeight() { return 1; }

    static StormGeometryProgram legacy(StormMorphologyPrototype.Program p) {
        return new StormGeometryProgram() {
            public float radius() { return p.radius(); }
            public float height() { return p.height(); }
            public float axisX(float t) { return p.axisX(t); }
            public float density(float x, float y, float z, StormMorphologyPrototype.Scale s) { return p.density(x,y,z,s); }
            public StormMorphologyPrototype.Bounds bounds() { return p.bounds(); }
            public float[] packed() { return p.packed(); }
            public long signature() { return p.signature(); }
            public int version() { return StormMorphologyPrototype.VERSION; }
            public boolean growthDefined() { return false; }
        };
    }
}
