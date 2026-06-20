package net.Gabou.projectatmosphere.clouds.client.render;

import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.clouds.type.CloudMaterialProfile;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Canonical CPU-side cloud density model.
 * <p>
 * The shader mirrors this stage order. Java may use fewer noise octaves than
 * the GPU, but the body, lobe, vertical, erosion, shadow, lighting, and
 * fallback semantics must stay aligned here.
 */
public final class CloudDensityProvider {
    private static final int MAX_CPU_LOBES = 16;

    private CloudDensityProvider() {

    }

    public static float getEffectiveDensity(@Nullable CloudRenderSnapshot snapshot) {
        return deriveInputs(snapshot).effectiveDensity();
    }

    public static float getEffectiveCoverage(@Nullable CloudRenderSnapshot snapshot) {
        return deriveInputs(snapshot).effectiveCoverage();
    }

    public static float getLifecycleFactor(@Nullable CloudRenderSnapshot snapshot) {
        return deriveInputs(snapshot).lifecycleFactor();
    }

    public static boolean hasVisibleDensity(@Nullable CloudRenderSnapshot snapshot) {
        DensityInputs inputs = deriveInputs(snapshot);
        return inputs.effectiveDensity() > 0.001F && inputs.effectiveCoverage() > 0.001F;
    }

    public static float sampleDensity(@Nullable CloudRenderSnapshot snapshot, @Nullable Vec3 worldPosition) {
        return sample(snapshot, worldPosition).finalDensity();
    }

    public static CloudDensitySample sample(@Nullable CloudRenderSnapshot snapshot, @Nullable Vec3 worldPosition) {
        DensityInputs inputs = deriveInputs(snapshot);
        if (!inputs.valid() || snapshot == null || worldPosition == null || snapshot.getRegionCenter() == null) {
            return CloudDensitySample.EMPTY;
        }

        LocalSample local = toLocalSample(snapshot, worldPosition);
        if (!local.valid()) {
            return CloudDensitySample.EMPTY;
        }

        float verticalEnvelope = sampleVerticalEnvelope(snapshot, inputs, local.y01());
        if (verticalEnvelope <= 0.0001F) {
            return CloudDensitySample.EMPTY;
        }

        float primaryMass = samplePrimaryMass(snapshot, inputs, local);
        float secondaryLobes = sampleSecondaryLobes(snapshot, inputs, local);
        float tertiaryPuffs = sampleTertiaryPuffs(snapshot, inputs, local);
        float preErosion = Mth.clamp(
                Math.max(primaryMass, secondaryLobes * inputs.lobeStrength())
                        + tertiaryPuffs * inputs.puffStrength(),
                0.0F,
                1.35F
        );

        float edgeFactor = sampleEdgeFactor(local);
        float coreFactor = Mth.clamp(
                smoothstep(0.34F, 0.80F, preErosion) * (1.0F - edgeFactor * 0.68F),
                0.0F,
                1.0F
        );
        float erosion = sampleSoftErosion(snapshot, inputs, local, edgeFactor);
        float erodedMass = Math.max(0.0F, preErosion - erosion);
        float softMass = smoothstep(0.045F, 0.72F, erodedMass);
        float corePreserve = coreFactor * Mth.lerp(inputs.stormDepth(), 0.24F, 0.46F);
        float densityShape = Math.max(softMass, corePreserve);
        float coverageFill = Mth.lerp(inputs.effectiveCoverage(), 0.28F, 1.0F);
        float finalDensity = Mth.clamp(
                densityShape * verticalEnvelope * inputs.effectiveDensity() * coverageFill,
                0.0F,
                1.0F
        );
        float opticalDepthHint = Mth.clamp(
                finalDensity * (0.42F + inputs.effectiveCoverage() * 0.36F + inputs.stormDepth() * 0.22F),
                0.0F,
                1.0F
        );
        float precipitationCoreFactor = Mth.clamp(
                (1.0F - smoothstep(0.22F, 0.74F, local.horizontalNorm()))
                        * (1.0F - smoothstep(0.42F, 1.02F, local.y01()))
                        * (snapshot.getPrecipitationCoreStrength() + snapshot.getPrecipitationTier().getRepresentativeIntensity() * 0.55F),
                0.0F,
                1.0F
        );

        return new CloudDensitySample(
                finalDensity,
                preErosion,
                primaryMass,
                secondaryLobes,
                tertiaryPuffs,
                verticalEnvelope,
                edgeFactor,
                coreFactor,
                erosion,
                opticalDepthHint,
                precipitationCoreFactor
        );
    }

    public static float sampleShadowDensity(@Nullable CloudRenderSnapshot snapshot, float worldX, float worldZ) {
        DensityInputs inputs = deriveInputs(snapshot);
        if (!inputs.valid() || snapshot == null) {
            return 0.0F;
        }

        float heightRange = Math.max(1.0F, snapshot.getCloudTopY() - snapshot.getCloudBaseY());
        float[] heights = new float[] {0.14F, 0.30F, 0.48F, 0.66F, 0.84F};
        float accumulated = 0.0F;
        for (float height : heights) {
            Vec3 position = new Vec3(worldX, snapshot.getCloudBaseY() + heightRange * height, worldZ);
            CloudDensitySample sample = sample(snapshot, position);
            accumulated = 1.0F - ((1.0F - accumulated) * (1.0F - sample.finalDensity() * (0.72F + sample.coreFactor() * 0.28F)));
        }

        float stormBias = Math.max(snapshot.getStormVisualTier().getShadowBias(), snapshot.getStormCoreDarkening());
        float materialShadow = snapshot.getMaterialProfile().getShadowContribution();
        return Mth.clamp(
                accumulated
                        * snapshot.getShadowContribution()
                        * (0.70F + inputs.effectiveCoverage() * 0.20F + stormBias * 0.16F + materialShadow * 0.12F),
                0.0F,
                1.0F
        );
    }

    public static float sampleLightingDensity(@Nullable CloudRenderSnapshot snapshot, @Nullable Vec3 worldPosition) {
        CloudDensitySample sample = sample(snapshot, worldPosition);
        return Mth.clamp(sample.finalDensity() * (0.70F + sample.coreFactor() * 0.30F), 0.0F, 1.0F);
    }

    public static float sampleFallbackDarkeningDensity(@Nullable CloudRenderSnapshot snapshot, @Nullable Vec3 samplePosition) {
        if (snapshot == null || samplePosition == null || snapshot.getRegionCenter() == null) {
            return 0.0F;
        }

        float shadowDensity = sampleShadowDensity(snapshot, (float) samplePosition.x(), (float) samplePosition.z());
        if (shadowDensity <= 0.001F) {
            return 0.0F;
        }

        float verticalDistance = 0.0F;
        if (samplePosition.y() > snapshot.getCloudTopY()) {
            verticalDistance = (float) (samplePosition.y() - snapshot.getCloudTopY());
        } else if (samplePosition.y() < snapshot.getCloudBaseY()) {
            verticalDistance = (float) (snapshot.getCloudBaseY() - samplePosition.y());
        }

        float verticalReach = Math.max(48.0F, snapshot.getRegionRadius() * 0.42F);
        float verticalFade = 1.0F - smoothstep(verticalReach, verticalReach * 2.4F, verticalDistance);
        return Mth.clamp(shadowDensity * verticalFade, 0.0F, 1.0F);
    }

    public static DensityInputs deriveInputs(@Nullable CloudRenderSnapshot snapshot) {
        if (snapshot == null || !snapshot.isEnabled() || snapshot.getRegionCenter() == null || snapshot.getRegionRadius() <= 0.0F) {
            return DensityInputs.EMPTY;
        }

        float heightRange = snapshot.getCloudTopY() - snapshot.getCloudBaseY();
        if (heightRange <= 0.001F) {
            return DensityInputs.EMPTY;
        }

        CloudShapeProfile shape = snapshot.getShapeProfile() == null ? CloudShapeProfile.DEFAULT : snapshot.getShapeProfile();
        CloudMaterialProfile material = snapshot.getMaterialProfile() == null ? CloudMaterialProfile.DEFAULT : snapshot.getMaterialProfile();
        CloudMorphologyFamily family = snapshot.getMorphologyFamily() == null ? CloudMorphologyFamily.PUFF : snapshot.getMorphologyFamily();
        float lifecycle = Mth.clamp(snapshot.getGrowth() * (1.0F - snapshot.getDecay()), 0.0F, 1.0F);
        float precipitationPacking = 1.0F + Mth.clamp(
                snapshot.getPrecipitationTier().getRepresentativeIntensity() + snapshot.getPrecipitationCoreStrength(),
                0.0F,
                1.0F
        ) * 0.14F;
        float effectiveDensity = Mth.clamp(
                snapshot.getDensity()
                        * snapshot.getDensityMultiplier()
                        * lifecycle
                        * material.getOpacityBias()
                        * precipitationPacking,
                0.0F,
                1.0F
        );
        float effectiveCoverage = Mth.clamp(
                snapshot.getCoverage()
                        * snapshot.getCoverageMultiplier()
                        * lifecycle,
                0.0F,
                1.0F
        );
        float sheetness = isSheet(family) ? 1.0F : Mth.clamp((snapshot.getHeightSquash() - 1.0F) * 0.42F, 0.0F, 1.0F);
        float towerness = isTower(family) ? Math.max(0.72F, snapshot.getTowerStrength()) : Mth.clamp(snapshot.getTowerStrength(), 0.0F, 1.0F);
        float stormDepth = Mth.clamp(
                Math.max(snapshot.getStormVisualTier().getDarkness(), material.getStormCoreDarkening()) * 0.45F
                        + snapshot.getPrecipitationCoreStrength() * 0.24F
                        + towerness * 0.20F
                        + snapshot.getAnvilStrength() * 0.11F,
                0.0F,
                1.0F
        );
        float verticalThickness = Mth.clamp(snapshot.getVerticalThickness(), 0.05F, 4.0F);
        float baseRadiusScale = Mth.clamp(shape.getBaseRadius() / Math.max(1.0F, snapshot.getRegionRadius()), 0.24F, 1.45F);
        float baseOffset = Mth.clamp(shape.getBaseOffset() / heightRange, 0.0F, 0.92F);
        float topOffset = Mth.clamp(shape.getTopOffset() / heightRange, 0.02F, 1.75F);
        int lobeCount = Mth.clamp(Math.round(Mth.lerp(hash01(snapshot.getCloudSeed(), 17), shape.getLobeCountMin(), shape.getLobeCountMax())), 1, MAX_CPU_LOBES);
        float lobeStrength = Mth.clamp(shape.getLobeStrength(), 0.0F, 1.0F);
        float puffStrength = Mth.clamp(0.10F + lobeStrength * 0.26F + towerness * 0.16F - sheetness * 0.08F, 0.04F, 0.48F);

        return new DensityInputs(
                true,
                lifecycle,
                effectiveDensity,
                effectiveCoverage,
                sheetness,
                towerness,
                stormDepth,
                verticalThickness,
                baseRadiusScale,
                baseOffset,
                topOffset,
                lobeCount,
                lobeStrength,
                puffStrength
        );
    }

    private static LocalSample toLocalSample(@NotNull CloudRenderSnapshot snapshot, @NotNull Vec3 worldPosition) {
        float heightRange = Math.max(snapshot.getCloudTopY() - snapshot.getCloudBaseY(), 0.001F);
        float y01 = (float) ((worldPosition.y() - snapshot.getCloudBaseY()) / heightRange);
        float verticalPadding = Math.max(0.05F, Mth.clamp(snapshot.getVerticalThickness() * 0.055F, 0.04F, 0.22F));
        if (y01 < -verticalPadding || y01 > 1.0F + verticalPadding) {
            return LocalSample.INVALID;
        }

        Vec3 center = snapshot.getRegionCenter();
        Vec3 velocity = snapshot.getVelocity() == null ? Vec3.ZERO : snapshot.getVelocity();
        float dx = (float) (worldPosition.x() - center.x());
        float dz = (float) (worldPosition.z() - center.z());
        float directionX = (float) velocity.x();
        float directionZ = (float) velocity.z();
        float lengthSq = directionX * directionX + directionZ * directionZ;
        if (lengthSq <= 0.000001F) {
            float angle = hash01(snapshot.getCloudSeed(), 401) * (float) (Math.PI * 2.0D);
            directionX = Mth.cos(angle);
            directionZ = Mth.sin(angle);
        } else {
            float inverseLength = Mth.invSqrt(lengthSq);
            directionX *= inverseLength;
            directionZ *= inverseLength;
        }

        CloudShapeProfile shape = snapshot.getShapeProfile();
        float shear = (y01 - 0.35F) * (shape.getWindShearStrength() * 0.22F + shape.getVerticalTilt() * 0.14F);
        dx -= directionX * snapshot.getRegionRadius() * shear;
        dz -= directionZ * snapshot.getRegionRadius() * shear;

        float radius = Math.max(1.0F, snapshot.getRegionRadius());
        float localX = dx / radius;
        float localZ = dz / radius;
        float horizontalNorm = Mth.sqrt(localX * localX + localZ * localZ);
        if (horizontalNorm > 1.65F) {
            return LocalSample.INVALID;
        }

        return new LocalSample(true, localX, y01, localZ, horizontalNorm);
    }

    private static float samplePrimaryMass(@NotNull CloudRenderSnapshot snapshot, @NotNull DensityInputs inputs, @NotNull LocalSample local) {
        CloudShapeProfile shape = snapshot.getShapeProfile();
        float y = local.y01();
        float baseFlattening = Mth.clamp(shape.getBaseFlattening(), 0.0F, 1.0F);
        float sheetness = inputs.sheetness();
        float towerness = inputs.towerness();
        float baseRadius = Mth.lerp(sheetness, inputs.baseRadiusScale(), Math.max(inputs.baseRadiusScale(), 0.96F));
        float topNarrow = Mth.lerp(towerness * smoothstep(0.42F, 1.0F, y), 1.0F, Mth.lerp(shape.getTowerNarrowing(), 0.82F, 0.46F));
        float anvilSpread = 1.0F + snapshot.getAnvilStrength() * shape.getAnvilSpread() * smoothstep(0.58F, 0.90F, y) * 0.82F;
        float horizontalRadius = baseRadius * topNarrow * anvilSpread;
        horizontalRadius *= Mth.lerp(sheetness, 0.92F, 1.30F);
        float zRadius = horizontalRadius * Mth.lerp(sheetness, 0.88F, 1.12F);
        float bodyCenterY = Mth.lerp(sheetness, 0.48F, 0.42F + inputs.baseOffset() * 0.10F);
        float bodyHeight = Mth.clamp(
                Mth.lerp(
                        sheetness,
                        0.36F + inputs.verticalThickness() * 0.11F + towerness * 0.26F + inputs.topOffset() * 0.04F,
                        0.20F + inputs.verticalThickness() * 0.05F + inputs.topOffset() * 0.025F
                ),
                0.16F,
                0.86F
        );

        float body = ellipsoid(local.x(), y, local.z(), 0.0F, bodyCenterY, 0.0F, horizontalRadius, bodyHeight, zRadius);
        float baseShelf = (1.0F - smoothstep(0.04F, 0.28F, y))
                * (1.0F - smoothstep(0.72F, 1.18F, local.horizontalNorm()))
                * (0.18F + baseFlattening * 0.46F);
        float towerColumn = towerness
                * (1.0F - smoothstep(0.22F, 0.72F, local.horizontalNorm()))
                * smoothstep(0.08F, 0.32F, y)
                * (1.0F - smoothstep(0.95F, 1.08F, y))
                * 0.48F;
        float sheetLayer = sheetness
                * (1.0F - smoothstep(0.58F, 1.12F, local.horizontalNorm()))
                * (1.0F - smoothstep(0.18F + inputs.verticalThickness() * 0.04F, 0.52F, Math.abs(y - 0.48F)))
                * 0.52F;
        return Mth.clamp(Math.max(body, Math.max(baseShelf, Math.max(towerColumn, sheetLayer))), 0.0F, 1.0F);
    }

    private static float sampleSecondaryLobes(@NotNull CloudRenderSnapshot snapshot, @NotNull DensityInputs inputs, @NotNull LocalSample local) {
        CloudShapeProfile shape = snapshot.getShapeProfile();
        float result = 0.0F;
        float sheetness = inputs.sheetness();
        float towerness = inputs.towerness();
        for (int i = 0; i < inputs.lobeCount(); i++) {
            float fi = i;
            float angle = fi * 2.399963F + hash01(snapshot.getCloudSeed(), 41 + i * 11) * 1.65F;
            float ring = Mth.lerp(hash01(snapshot.getCloudSeed(), 97 + i * 13), 0.08F, Mth.lerp(sheetness, 0.52F, 0.92F));
            float yRand = hash01(snapshot.getCloudSeed(), 173 + i * 17);
            float y = Mth.lerp(yRand, 0.20F, 0.78F);
            y = Mth.lerp(sheetness, y, 0.42F + (yRand - 0.5F) * 0.30F);
            y = Mth.lerp(towerness * 0.72F, y, Mth.lerp(fi / Math.max(1.0F, inputs.lobeCount() - 1.0F), 0.10F, 0.92F));

            float radialJitter = Mth.lerp(hash01(snapshot.getCloudSeed(), 251 + i * 19), 0.74F, 1.16F);
            float centerX = Mth.cos(angle) * ring * radialJitter;
            float centerZ = Mth.sin(angle) * ring * radialJitter;

            float radiusJitter = Mth.lerp(hash01(snapshot.getCloudSeed(), 307 + i * 23), 0.78F, 1.24F);
            float rx = Mth.lerp(hash01(snapshot.getCloudSeed(), 359 + i * 29), 0.18F, 0.36F) * radiusJitter;
            float ry = Mth.lerp(hash01(snapshot.getCloudSeed(), 421 + i * 31), 0.12F, 0.28F) * radiusJitter;
            float rz = Mth.lerp(hash01(snapshot.getCloudSeed(), 463 + i * 37), 0.18F, 0.35F) * radiusJitter;
            rx *= Mth.lerp(sheetness, 1.0F, 1.58F);
            rz *= Mth.lerp(sheetness, 1.0F, 1.38F);
            ry *= Mth.lerp(sheetness, 1.14F, 0.62F);
            rx *= Mth.lerp(towerness * smoothstep(0.58F, 1.0F, y), 1.0F, Mth.lerp(shape.getTowerNarrowing(), 0.82F, 0.48F));
            rz *= Mth.lerp(towerness * smoothstep(0.58F, 1.0F, y), 1.0F, Mth.lerp(shape.getTowerNarrowing(), 0.82F, 0.48F));
            ry *= 1.0F + towerness * 0.30F;

            result = Math.max(result, ellipsoid(local.x(), local.y01(), local.z(), centerX, y, centerZ, rx, ry, rz));
        }

        float attach = 1.0F - smoothstep(1.04F, 1.44F, local.horizontalNorm());
        return Mth.clamp(result * attach, 0.0F, 1.0F);
    }

    private static float sampleTertiaryPuffs(@NotNull CloudRenderSnapshot snapshot, @NotNull DensityInputs inputs, @NotNull LocalSample local) {
        Vec3 noisePosition = new Vec3(
                local.x() * 4.0F + snapshot.getCloudSeed() * 0.00013F,
                local.y01() * 2.4F + snapshot.getWorldTime() * 0.0007F,
                local.z() * 4.0F - snapshot.getCloudSeed() * 0.00019F
        );
        float bodyNoise = fbm(noisePosition, 3);
        float detailNoise = fbm(noisePosition.scale(2.13D).add(17.0D, 3.0D, 11.0D), 2);
        float edgeWeight = smoothstep(0.30F, 1.08F, local.horizontalNorm());
        float topWeight = smoothstep(0.34F, 0.86F, local.y01());
        float sheetWeight = inputs.sheetness() * 0.34F;
        float puff = Mth.clamp((bodyNoise * 0.62F + detailNoise * 0.38F - 0.42F) * 1.35F, 0.0F, 1.0F);
        return puff * Mth.clamp(edgeWeight * 0.58F + topWeight * 0.34F + sheetWeight, 0.0F, 1.0F);
    }

    private static float sampleVerticalEnvelope(@NotNull CloudRenderSnapshot snapshot, @NotNull DensityInputs inputs, float y01) {
        float padding = Mth.clamp(0.045F + inputs.verticalThickness() * 0.045F, 0.045F, 0.22F);
        float baseSoftness = Math.max(0.025F, snapshot.getBaseSoftness());
        float topSoftness = Math.max(0.035F, snapshot.getTopSoftness());
        float base = smoothstep(-padding, baseSoftness, y01);
        float top = 1.0F - smoothstep(1.0F - topSoftness, 1.0F + padding, y01);
        float denseStart = Mth.lerp(inputs.sheetness(), 0.08F, 0.02F);
        float denseEnd = Mth.lerp(inputs.towerness(), 0.70F + inputs.verticalThickness() * 0.08F, 0.94F);
        denseEnd = Mth.lerp(inputs.sheetness(), denseEnd, 0.58F + inputs.verticalThickness() * 0.06F);
        float denseBand = smoothstep(-0.02F, denseStart, y01) * (1.0F - smoothstep(denseEnd, 1.06F, y01));
        float fillWeight = Mth.clamp(inputs.verticalThickness() * 0.30F + inputs.towerness() * 0.46F + inputs.sheetness() * 0.24F, 0.0F, 1.0F);
        float puffyTop = Mth.lerp(inputs.towerness(), 1.0F, 0.82F + smoothstep(0.38F, 0.86F, y01) * 0.18F);
        return Mth.clamp(base * top * Mth.lerp(fillWeight, denseBand, 1.0F) * puffyTop, 0.0F, 1.0F);
    }

    private static float sampleSoftErosion(
            @NotNull CloudRenderSnapshot snapshot,
            @NotNull DensityInputs inputs,
            @NotNull LocalSample local,
            float edgeFactor
    ) {
        float erosionStrength = Mth.clamp(
                snapshot.getEdgeErosionStrength()
                        + snapshot.getMaterialProfile().getEdgeErosion() * 0.45F
                        + snapshot.getShapeProfile().getEdgeRaggedness() * 0.45F,
                0.0F,
                1.0F
        );
        if (erosionStrength <= 0.001F) {
            return 0.0F;
        }

        Vec3 noisePosition = new Vec3(
                local.x() * 5.3F + snapshot.getCloudSeed() * 0.00031F,
                local.y01() * 3.1F - snapshot.getWorldTime() * 0.0009F,
                local.z() * 5.3F + snapshot.getCloudSeed() * 0.00017F
        );
        float erosionNoise = fbm(noisePosition, 3);
        float verticalEdge = Math.max(1.0F - smoothstep(0.05F, 0.24F, local.y01()), smoothstep(0.76F, 1.04F, local.y01()));
        float erosionMask = Mth.clamp(edgeFactor * 0.82F + verticalEdge * 0.18F, 0.0F, 1.0F);
        float cellular = inputs.sheetness() > 0.55F
                ? Mth.clamp((0.56F - erosionNoise) * snapshot.getShapeProfile().getCellSplitStrength(), 0.0F, 0.24F)
                : 0.0F;
        float softErosion = smoothstep(0.42F, 0.86F, erosionNoise) * erosionMask * Mth.lerp(erosionStrength, 0.05F, 0.34F);
        return Mth.clamp(softErosion + cellular, 0.0F, 0.42F);
    }

    private static float sampleEdgeFactor(@NotNull LocalSample local) {
        float horizontalEdge = smoothstep(0.48F, 1.10F, local.horizontalNorm());
        float baseEdge = 1.0F - smoothstep(0.05F, 0.22F, local.y01());
        float topEdge = smoothstep(0.78F, 1.04F, local.y01());
        return Mth.clamp(Math.max(horizontalEdge, Math.max(baseEdge, topEdge) * 0.45F), 0.0F, 1.0F);
    }

    private static boolean isSheet(CloudMorphologyFamily family) {
        return family == CloudMorphologyFamily.SHEET
                || family == CloudMorphologyFamily.CELLULAR_SHEET
                || family == CloudMorphologyFamily.FILAMENT;
    }

    private static boolean isTower(CloudMorphologyFamily family) {
        return family == CloudMorphologyFamily.TOWER
                || family == CloudMorphologyFamily.STORM_ANVIL
                || family == CloudMorphologyFamily.SPIRAL_STORM;
    }

    private static float ellipsoid(
            float x,
            float y,
            float z,
            float centerX,
            float centerY,
            float centerZ,
            float radiusX,
            float radiusY,
            float radiusZ
    ) {
        float qx = (x - centerX) / Math.max(radiusX, 0.001F);
        float qy = (y - centerY) / Math.max(radiusY, 0.001F);
        float qz = (z - centerZ) / Math.max(radiusZ, 0.001F);
        float distance = Mth.sqrt(qx * qx + qy * qy + qz * qz);
        return 1.0F - smoothstep(0.62F, 1.0F, distance);
    }

    private static float fbm(Vec3 position, int octaves) {
        float value = 0.0F;
        float amplitude = 0.5F;
        float frequency = 1.0F;
        int clampedOctaves = Mth.clamp(octaves, 1, 4);
        for (int i = 0; i < clampedOctaves; i++) {
            value += valueNoise(position.scale(frequency)) * amplitude;
            frequency *= 2.0F;
            amplitude *= 0.5F;
        }
        return Mth.clamp(value * 0.5F + 0.5F, 0.0F, 1.0F);
    }

    private static float valueNoise(Vec3 position) {
        int x0 = fastFloor(position.x());
        int y0 = fastFloor(position.y());
        int z0 = fastFloor(position.z());
        float fx = (float) (position.x() - x0);
        float fy = (float) (position.y() - y0);
        float fz = (float) (position.z() - z0);
        fx = fx * fx * (3.0F - 2.0F * fx);
        fy = fy * fy * (3.0F - 2.0F * fy);
        fz = fz * fz * (3.0F - 2.0F * fz);

        float n000 = hashGrid(x0, y0, z0);
        float n100 = hashGrid(x0 + 1, y0, z0);
        float n010 = hashGrid(x0, y0 + 1, z0);
        float n110 = hashGrid(x0 + 1, y0 + 1, z0);
        float n001 = hashGrid(x0, y0, z0 + 1);
        float n101 = hashGrid(x0 + 1, y0, z0 + 1);
        float n011 = hashGrid(x0, y0 + 1, z0 + 1);
        float n111 = hashGrid(x0 + 1, y0 + 1, z0 + 1);

        float x00 = Mth.lerp(fx, n000, n100);
        float x10 = Mth.lerp(fx, n010, n110);
        float x01 = Mth.lerp(fx, n001, n101);
        float x11 = Mth.lerp(fx, n011, n111);
        float y0Mix = Mth.lerp(fy, x00, x10);
        float y1Mix = Mth.lerp(fy, x01, x11);
        return Mth.lerp(fz, y0Mix, y1Mix) * 2.0F - 1.0F;
    }

    private static int fastFloor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static float hashGrid(int x, int y, int z) {
        int hash = x * 374761393 + y * 668265263 + z * 2147483647;
        hash = (hash ^ (hash >>> 13)) * 1274126177;
        hash ^= hash >>> 16;
        return (hash & 0x00FFFFFF) / (float) 0x01000000;
    }

    private static float hash01(int seed, int salt) {
        int hash = seed ^ (salt * 0x9E3779B9);
        hash ^= hash >>> 16;
        hash *= 0x7FEB352D;
        hash ^= hash >>> 15;
        hash *= 0x846CA68B;
        hash ^= hash >>> 16;
        return (hash & 0x00FFFFFF) / (float) 0x01000000;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        if (edge0 == edge1) {
            return value < edge0 ? 0.0F : 1.0F;
        }

        float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    public record DensityInputs(
            boolean valid,
            float lifecycleFactor,
            float effectiveDensity,
            float effectiveCoverage,
            float sheetness,
            float towerness,
            float stormDepth,
            float verticalThickness,
            float baseRadiusScale,
            float baseOffset,
            float topOffset,
            int lobeCount,
            float lobeStrength,
            float puffStrength
    ) {
        private static final DensityInputs EMPTY = new DensityInputs(
                false,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                0.0F,
                0.0F,
                0,
                0.0F,
                0.0F
        );
    }

    public record CloudDensitySample(
            float finalDensity,
            float unlitDensity,
            float primaryMass,
            float secondaryLobes,
            float tertiaryPuffs,
            float heightFactor,
            float edgeFactor,
            float coreFactor,
            float erosionFactor,
            float opticalDepthHint,
            float precipitationCoreFactor
    ) {
        private static final CloudDensitySample EMPTY = new CloudDensitySample(
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F
        );
    }

    private record LocalSample(boolean valid, float x, float y01, float z, float horizontalNorm) {
        private static final LocalSample INVALID = new LocalSample(false, 0.0F, 0.0F, 0.0F, 0.0F);
    }
}
