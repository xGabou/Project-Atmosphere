package net.Gabou.projectatmosphere.clouds.type;

import net.minecraft.util.Mth;

import java.util.Objects;

public final class CloudMaterialProfile {
    public static final CloudMaterialProfile DEFAULT = new CloudMaterialProfile(
            "projectatmosphere:procedural/default",
            "",
            0.12F,
            0.00F,
            1.00F,
            0.24F,
            0.18F,
            0.12F,
            0.72F,
            1.00F
    );

    private final String materialId;
    private final String textureId;
    private final float darkness;
    private final float precipitationTint;
    private final float opacityBias;
    private final float undersideDarkness;
    private final float edgeErosion;
    private final float stormCoreDarkening;
    private final float shadowContribution;
    private final float lightningResponse;

    public CloudMaterialProfile(
            String materialId,
            String textureId,
            float darkness,
            float precipitationTint,
            float opacityBias,
            float undersideDarkness,
            float edgeErosion,
            float stormCoreDarkening,
            float shadowContribution,
            float lightningResponse
    ) {
        this.materialId = normalizeId(materialId, DEFAULT_ID());
        this.textureId = textureId == null ? "" : textureId.trim();
        this.darkness = clamp01(darkness);
        this.precipitationTint = clamp01(precipitationTint);
        this.opacityBias = Mth.clamp(opacityBias, 0.0F, 2.0F);
        this.undersideDarkness = clamp01(undersideDarkness);
        this.edgeErosion = clamp01(edgeErosion);
        this.stormCoreDarkening = clamp01(stormCoreDarkening);
        this.shadowContribution = clamp01(shadowContribution);
        this.lightningResponse = Mth.clamp(lightningResponse, 0.0F, 2.0F);
    }

    public String getMaterialId() {
        return materialId;
    }

    public String getTextureId() {
        return textureId;
    }

    public boolean hasTexture() {
        return !textureId.isBlank();
    }

    public float getDarkness() {
        return darkness;
    }

    public float getPrecipitationTint() {
        return precipitationTint;
    }

    public float getOpacityBias() {
        return opacityBias;
    }

    public float getUndersideDarkness() {
        return undersideDarkness;
    }

    public float getEdgeErosion() {
        return edgeErosion;
    }

    public float getStormCoreDarkening() {
        return stormCoreDarkening;
    }

    public float getShadowContribution() {
        return shadowContribution;
    }

    public float getLightningResponse() {
        return lightningResponse;
    }

    public CloudMaterialProfile withVisualDefaults(CloudVisualProfile visualProfile) {
        if (visualProfile == null) {
            return this;
        }

        return new CloudMaterialProfile(
                materialId,
                textureId,
                Math.max(darkness, visualProfile.getBaseDarkness() * 0.70F),
                Math.max(precipitationTint, visualProfile.getPrecipitationCoreStrength() * 0.65F),
                opacityBias,
                Math.max(undersideDarkness, visualProfile.getBaseDarkness()),
                Math.max(edgeErosion, visualProfile.getEdgeErosionStrength() * 0.45F),
                Math.max(stormCoreDarkening, visualProfile.getPrecipitationCoreStrength()),
                Math.max(shadowContribution, visualProfile.getDensityMultiplier() * 0.34F),
                lightningResponse
        );
    }

    public static CloudMaterialProfile storm(String materialId, float darkness, float precipitationTint, float stormCoreDarkening) {
        return new CloudMaterialProfile(
                materialId,
                "",
                darkness,
                precipitationTint,
                1.0F,
                darkness,
                0.18F,
                stormCoreDarkening,
                Math.max(0.72F, darkness),
                1.25F
        );
    }

    public static CloudMaterialProfile blend(CloudMaterialProfile from, CloudMaterialProfile to, float mix) {
        CloudMaterialProfile left = from != null ? from : to;
        CloudMaterialProfile right = to != null ? to : from;
        if (left == null && right == null) {
            return DEFAULT;
        }
        if (left == right) {
            return left;
        }

        float t = clamp01(mix);
        String blendedMaterialId = t < 0.5F ? left.materialId : right.materialId;
        String blendedTextureId = t < 0.5F ? left.textureId : right.textureId;
        return new CloudMaterialProfile(
                blendedMaterialId,
                blendedTextureId,
                lerp(left.darkness, right.darkness, t),
                lerp(left.precipitationTint, right.precipitationTint, t),
                lerp(left.opacityBias, right.opacityBias, t),
                lerp(left.undersideDarkness, right.undersideDarkness, t),
                lerp(left.edgeErosion, right.edgeErosion, t),
                lerp(left.stormCoreDarkening, right.stormCoreDarkening, t),
                lerp(left.shadowContribution, right.shadowContribution, t),
                lerp(left.lightningResponse, right.lightningResponse, t)
        );
    }

    private static String normalizeId(String id, String fallback) {
        String normalized = id == null ? "" : id.trim();
        return normalized.isBlank() ? Objects.requireNonNull(fallback, "fallback") : normalized;
    }

    private static String DEFAULT_ID() {
        return "projectatmosphere:procedural/default";
    }

    private static float lerp(float from, float to, float mix) {
        return from + ((to - from) * mix);
    }

    private static float clamp01(float value) {
        return Mth.clamp(value, 0.0F, 1.0F);
    }
}
