package net.Gabou.projectatmosphere.wind;

/** Simple inclusive float range. */
public record FloatRange(float min, float max) {
    public float random(java.util.Random rng) {
        return min + rng.nextFloat() * (max - min);
    }
}

