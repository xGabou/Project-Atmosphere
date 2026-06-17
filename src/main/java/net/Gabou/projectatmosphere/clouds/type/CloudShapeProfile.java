package net.Gabou.projectatmosphere.clouds.type;

import net.minecraft.util.Mth;

import java.util.Objects;

/**
 * Data-driven procedural shape controls for PA cloud volumes.
 */
public final class CloudShapeProfile {
    public static final CloudShapeProfile DEFAULT = new CloudShapeProfile(
            "projectatmosphere:shape/default",
            48.0F,
            10.0F,
            24.0F,
            4,
            9,
            0.50F,
            0.00F,
            0.00F,
            0.00F,
            0.20F,
            0.00F,
            0.00F,
            0.32F,
            0.00F
    );

    private final String shapeId;
    private final float baseRadius;
    private final float baseOffset;
    private final float topOffset;
    private final int lobeCountMin;
    private final int lobeCountMax;
    private final float lobeStrength;
    private final float verticalTilt;
    private final float windShearStrength;
    private final float cellSplitStrength;
    private final float towerNarrowing;
    private final float anvilSpread;
    private final float baseFlattening;
    private final float edgeRaggedness;
    private final float stormWallStrength;

    public CloudShapeProfile(
            String shapeId,
            float baseRadius,
            float baseOffset,
            float topOffset,
            int lobeCountMin,
            int lobeCountMax,
            float lobeStrength,
            float verticalTilt,
            float windShearStrength,
            float cellSplitStrength,
            float towerNarrowing,
            float anvilSpread,
            float baseFlattening,
            float edgeRaggedness,
            float stormWallStrength
    ) {
        this.shapeId = normalizeId(shapeId, DEFAULT_ID());
        this.baseRadius = Mth.clamp(baseRadius, 1.0F, 512.0F);
        this.baseOffset = Mth.clamp(baseOffset, 0.0F, 512.0F);
        this.topOffset = Mth.clamp(topOffset, 1.0F, 512.0F);
        this.lobeCountMin = Mth.clamp(lobeCountMin, 1, 32);
        this.lobeCountMax = Mth.clamp(Math.max(lobeCountMin, lobeCountMax), 1, 32);
        this.lobeStrength = clamp01(lobeStrength);
        this.verticalTilt = Mth.clamp(verticalTilt, -1.0F, 1.0F);
        this.windShearStrength = Mth.clamp(windShearStrength, -1.0F, 1.0F);
        this.cellSplitStrength = clamp01(cellSplitStrength);
        this.towerNarrowing = clamp01(towerNarrowing);
        this.anvilSpread = clamp01(anvilSpread);
        this.baseFlattening = clamp01(baseFlattening);
        this.edgeRaggedness = clamp01(edgeRaggedness);
        this.stormWallStrength = clamp01(stormWallStrength);
    }

    public String getShapeId() {
        return shapeId;
    }

    public float getBaseRadius() {
        return baseRadius;
    }

    public float getBaseOffset() {
        return baseOffset;
    }

    public float getTopOffset() {
        return topOffset;
    }

    public int getLobeCountMin() {
        return lobeCountMin;
    }

    public int getLobeCountMax() {
        return lobeCountMax;
    }

    public float getLobeStrength() {
        return lobeStrength;
    }

    public float getVerticalTilt() {
        return verticalTilt;
    }

    public float getWindShearStrength() {
        return windShearStrength;
    }

    public float getCellSplitStrength() {
        return cellSplitStrength;
    }

    public float getTowerNarrowing() {
        return towerNarrowing;
    }

    public float getAnvilSpread() {
        return anvilSpread;
    }

    public float getBaseFlattening() {
        return baseFlattening;
    }

    public float getEdgeRaggedness() {
        return edgeRaggedness;
    }

    public float getStormWallStrength() {
        return stormWallStrength;
    }

    public static CloudShapeProfile defaultFor(String cloudTypeId, CloudFamily family, CloudVisualProfile visualProfile) {
        String normalized = cloudTypeId == null ? "" : cloudTypeId.trim();
        return switch (normalized) {
            case "vapor_cluster" -> new CloudShapeProfile("projectatmosphere:shape/vapor", 34.0F, 7.0F, 16.0F, 3, 7, 0.34F, 0.00F, 0.12F, 0.06F, 0.08F, 0.00F, 0.10F, 0.42F, 0.00F);
            case "cumulus_humilis" -> new CloudShapeProfile("projectatmosphere:shape/cumulus_humilis", 42.0F, 10.0F, 20.0F, 5, 10, 0.66F, 0.02F, 0.10F, 0.10F, 0.14F, 0.00F, 0.28F, 0.58F, 0.00F);
            case "cumulus_mediocris" -> new CloudShapeProfile("projectatmosphere:shape/cumulus_mediocris", 56.0F, 15.0F, 40.0F, 6, 12, 0.72F, 0.05F, 0.14F, 0.14F, 0.34F, 0.00F, 0.24F, 0.58F, 0.00F);
            case "cumulus_congestus" -> new CloudShapeProfile("projectatmosphere:shape/cumulus_congestus", 64.0F, 18.0F, 96.0F, 6, 13, 0.78F, 0.10F, 0.22F, 0.26F, 0.74F, 0.04F, 0.18F, 0.62F, 0.18F);
            case "cumulonimbus_calvus" -> new CloudShapeProfile("projectatmosphere:shape/cumulonimbus_calvus", 88.0F, 28.0F, 150.0F, 7, 14, 0.84F, 0.16F, 0.34F, 0.42F, 0.86F, 0.22F, 0.24F, 0.72F, 0.62F);
            case "cumulonimbus_capillatus" -> new CloudShapeProfile("projectatmosphere:shape/cumulonimbus_capillatus", 116.0F, 34.0F, 170.0F, 8, 16, 0.90F, 0.20F, 0.54F, 0.54F, 0.78F, 0.92F, 0.26F, 0.78F, 0.82F);
            case "stratus_nebulosus" -> new CloudShapeProfile("projectatmosphere:shape/stratus_nebulosus", 180.0F, 4.0F, 9.0F, 2, 4, 0.08F, 0.00F, 0.28F, 0.00F, 0.00F, 0.00F, 0.98F, 0.16F, 0.00F);
            case "stratocumulus" -> new CloudShapeProfile("projectatmosphere:shape/stratocumulus", 130.0F, 7.0F, 18.0F, 8, 18, 0.58F, 0.02F, 0.24F, 0.32F, 0.08F, 0.00F, 0.78F, 0.52F, 0.00F);
            case "nimbostratus" -> new CloudShapeProfile("projectatmosphere:shape/nimbostratus", 210.0F, 8.0F, 24.0F, 3, 6, 0.16F, 0.00F, 0.32F, 0.04F, 0.00F, 0.00F, 0.94F, 0.28F, 0.40F);
            case "cirrus" -> new CloudShapeProfile("projectatmosphere:shape/cirrus", 190.0F, 2.0F, 7.0F, 2, 5, 0.22F, 0.28F, 0.90F, 0.22F, 0.00F, 0.08F, 0.34F, 0.86F, 0.00F);
            default -> inferForFamily(normalized, family, visualProfile);
        };
    }

    public static CloudShapeProfile blend(CloudShapeProfile from, CloudShapeProfile to, float mix) {
        CloudShapeProfile left = from != null ? from : to;
        CloudShapeProfile right = to != null ? to : from;
        if (left == null && right == null) {
            return DEFAULT;
        }
        if (left == right) {
            return left;
        }

        float t = clamp01(mix);
        return new CloudShapeProfile(
                t < 0.5F ? left.shapeId : right.shapeId,
                lerp(left.baseRadius, right.baseRadius, t),
                lerp(left.baseOffset, right.baseOffset, t),
                lerp(left.topOffset, right.topOffset, t),
                Math.round(lerp(left.lobeCountMin, right.lobeCountMin, t)),
                Math.round(lerp(left.lobeCountMax, right.lobeCountMax, t)),
                lerp(left.lobeStrength, right.lobeStrength, t),
                lerp(left.verticalTilt, right.verticalTilt, t),
                lerp(left.windShearStrength, right.windShearStrength, t),
                lerp(left.cellSplitStrength, right.cellSplitStrength, t),
                lerp(left.towerNarrowing, right.towerNarrowing, t),
                lerp(left.anvilSpread, right.anvilSpread, t),
                lerp(left.baseFlattening, right.baseFlattening, t),
                lerp(left.edgeRaggedness, right.edgeRaggedness, t),
                lerp(left.stormWallStrength, right.stormWallStrength, t)
        );
    }

    private static CloudShapeProfile inferForFamily(String cloudTypeId, CloudFamily family, CloudVisualProfile visualProfile) {
        CloudVisualProfile visual = visualProfile != null ? visualProfile : new CloudVisualProfile(1.0F, 0.0F, 0.2F, 0.2F, 0.0F, 0.02F, 0.12F, 0.12F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F);
        float tower = visual.getTowerStrength();
        float anvil = visual.getAnvilStrength();
        float layer = Mth.clamp((visual.getHeightSquash() - 1.0F) / 3.0F, 0.0F, 1.0F);
        float radius = Mth.lerp(layer, 56.0F + tower * 40.0F, 120.0F);
        float base = Mth.lerp(layer, 14.0F + tower * 18.0F, 8.0F);
        float top = Mth.lerp(layer, 24.0F + tower * 104.0F + anvil * 26.0F, 18.0F);
        float storm = family == CloudFamily.CUMULONIMBUS ? 0.45F : 0.0F;
        return new CloudShapeProfile(
                "projectatmosphere:shape/" + (cloudTypeId == null || cloudTypeId.isBlank() ? "custom" : cloudTypeId),
                radius,
                base,
                top,
                4,
                9,
                Mth.lerp(layer, 0.55F, 0.22F),
                tower * 0.12F,
                layer * 0.22F + anvil * 0.18F,
                tower * 0.24F,
                tower * 0.65F,
                anvil,
                layer,
                visual.getEdgeErosionStrength(),
                storm
        );
    }

    private static String normalizeId(String id, String fallback) {
        String normalized = id == null ? "" : id.trim();
        return normalized.isBlank() ? Objects.requireNonNull(fallback, "fallback") : normalized;
    }

    private static String DEFAULT_ID() {
        return "projectatmosphere:shape/default";
    }

    private static float lerp(float from, float to, float mix) {
        return from + ((to - from) * mix);
    }

    private static float clamp01(float value) {
        return Mth.clamp(value, 0.0F, 1.0F);
    }
}
