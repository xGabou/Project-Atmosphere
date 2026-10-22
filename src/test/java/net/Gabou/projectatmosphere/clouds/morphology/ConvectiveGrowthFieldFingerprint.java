package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.type.CloudFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;

/**
 * T257 geometry fingerprint: header signature plus SHA-256 of the complete fixture texture (header,
 * baked displacement map, occupancy grid) - everything the GPU variant reads. Versions candidate-B
 * baselines by what they render, independent of refactors of the Java source.
 */
public final class ConvectiveGrowthFieldFingerprint {
    public static void main(String[] args) throws Exception {
        for (String type : new String[]{"cumulonimbus_calvus", "cumulonimbus_capillatus", "supercell"}) {
            for (long seed : new long[]{20261008L, 20268927L, 20276846L}) {
                var shape = CloudShapeProfile.defaultFor(type, CloudFamily.CUMULONIMBUS, null);
                print(type, seed, "FULL", ConvectiveGrowthFieldPrototype.compile(type, shape, seed));
                if (args.length > 0 && args[0].equals("levels")) for (var level : ConvectiveGrowthFieldPrototype.Detail.values()) {
                    print(type, seed, level.name(), ConvectiveGrowthFieldPrototype.compile(type, shape, seed, level));
                }
            }
        }
    }

    static void print(String type, long seed, String level, StormGeometryProgram p) throws Exception {
        float[] t = p.textureData();
        var bytes = ByteBuffer.allocate(t.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : t) bytes.putInt(Float.floatToRawIntBits(v));
        var sha = MessageDigest.getInstance("SHA-256").digest(bytes.array());
        var hex = new StringBuilder();
        for (byte b : sha) hex.append(String.format("%02x", b));
        System.out.println("T257_FINGERPRINT type=" + type + " seed=" + seed + " level=" + level + " version=" + p.version()
                + " signature=" + Long.toUnsignedString(p.signature(), 16) + " texture=" + hex
                + " size=" + p.textureWidth() + "x" + p.textureHeight());
    }
}
