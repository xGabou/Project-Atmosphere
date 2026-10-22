package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.type.CloudFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;

/** T258: candidate C geometry fingerprints (header signature + SHA-256 of the complete fixture texture), every level. */
public final class StormAnatomyCFingerprint {
    public static void main(String[] args) throws Exception {
        for (String type : new String[]{"cumulonimbus_calvus", "cumulonimbus_capillatus", "supercell"}) {
            for (long seed : new long[]{20261008L, 20268927L, 20276846L}) {
                var shape = CloudShapeProfile.defaultFor(type, CloudFamily.CUMULONIMBUS, null);
                for (var level : StormAnatomyCandidateC.Level.values()) {
                    var p = StormAnatomyCandidateC.compile(type, shape, seed, level);
                    float[] t = p.textureData();
                    var bytes = java.nio.ByteBuffer.allocate(t.length * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    for (float v : t) bytes.putInt(Float.floatToRawIntBits(v));
                    String sha = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.array()));
                    System.out.println("T258_FINGERPRINT type=" + type + " seed=" + seed + " level=" + level + " version=" + p.version()
                            + " signature=" + Long.toUnsignedString(p.signature(), 16) + " texture=" + sha + " size=" + p.textureWidth() + "x" + p.textureHeight());
                }
            }
        }
    }
}
