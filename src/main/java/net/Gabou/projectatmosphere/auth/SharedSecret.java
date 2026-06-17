package net.Gabou.projectatmosphere.auth;

import net.Gabou.projectatmosphere.ProjectAtmosphere;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

public final class SharedSecret {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SECRET = ProjectAtmosphere.MODID + ":auth:v1";

    private SharedSecret() {
    }

    public static long createNonce() {
        return RANDOM.nextLong();
    }

    public static String computeResponse(UUID uuid, long nonce) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(SECRET.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(uuid.toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Long.toString(nonce).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to compute auth response.", exception);
        }
    }

    public static boolean verifyResponse(UUID uuid, long nonce, String response) {
        return response != null && computeResponse(uuid, nonce).equals(response);
    }
}
