package net.Gabou.projectatmosphere.clouds.field;

/**
 * Stable procedural slot id for one visible cloudlet inside a CloudField.
 */
public record CloudletId(int value) implements Comparable<CloudletId> {
    public CloudletId {
        if (value < 0) {
            throw new IllegalArgumentException("Cloudlet id must be non-negative");
        }
    }

    public static CloudletId of(int value) {
        return new CloudletId(value);
    }

    public int asInt() {
        return value;
    }

    public long mixedSeed(long fieldSeed) {
        long mixed = fieldSeed ^ ((long) value * 0x9E3779B97F4A7C15L);
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return mixed;
    }

    @Override
    public int compareTo(CloudletId other) {
        return Integer.compare(this.value, other.value);
    }
}
