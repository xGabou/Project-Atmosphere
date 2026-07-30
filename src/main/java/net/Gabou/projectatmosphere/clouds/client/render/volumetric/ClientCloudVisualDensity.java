package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Canonical client density source. It is published only after the matching
 * cloud representation was actually rendered, so gameplay and camera effects
 * cannot accidentally query a parallel simulation.
 */
public final class ClientCloudVisualDensity {
    public enum Source {
        NONE,
        VOLUMETRIC_FIELDS,
        VOLUMETRIC_CELLS,
        FIELD_FALLBACK
    }

    private static volatile Frame current = Frame.EMPTY;

    private ClientCloudVisualDensity() {
    }

    public static void publishVolumetric(
            String dimensionId,
            Source source,
            List<VolumetricRenderCell> renderCells,
            CloudWeatherMapRenderer.Result weather,
            float regionalCoverage,
            float regionalEnergy,
            boolean includeRegionalLayer,
            float worldTime,
            int weatherMapSize,
            VolumetricCloudRenderer.Tuning tuning
    ) {
        if (weather == null || !weather.rendered()) {
            clear();
            return;
        }
        VolumetricCloudRenderer.Tuning safeTuning = tuning == null
                ? VolumetricCloudRenderer.Tuning.CELLS
                : tuning;
        current = new Frame(
                normalizeDimension(dimensionId),
                source == null ? Source.NONE : source,
                renderCells == null ? List.of() : List.copyOf(renderCells),
                weather.originX(), weather.originZ(), CloudWeatherMapRenderer.WEATHER_EXTENT,
                weather.slabBaseY(), weather.slabTopY(),
                includeRegionalLayer ? Mth.clamp(regionalCoverage, 0.0F, 1.0F) : 0.0F,
                Mth.clamp(regionalEnergy, 0.0F, 1.0F),
                worldTime,
                Math.max(1, weatherMapSize),
                safeTuning.coverageMul(), safeTuning.densityMul(),
                true
        );
    }

    public static void publishFieldFallback(
            String dimensionId,
            List<CloudFieldSnapshot> snapshots,
            long worldTime
    ) {
        List<VolumetricRenderCell> fields = new ArrayList<>();
        float slabBase = Float.MAX_VALUE;
        float slabTop = -Float.MAX_VALUE;
        if (snapshots != null) {
            for (CloudFieldSnapshot snapshot : snapshots) {
                if (snapshot == null || !snapshot.hasVisibleClouds()
                        || !normalizeDimension(dimensionId).equals(snapshot.dimensionId())) {
                    continue;
                }
                VolumetricRenderCell cell = VolumetricRenderCell.fromFieldSnapshot(snapshot);
                fields.add(cell);
                slabBase = Math.min(slabBase, cell.baseY());
                slabTop = Math.max(slabTop, cell.topY());
            }
        }
        if (fields.isEmpty()) {
            clear();
            return;
        }
        current = new Frame(
                normalizeDimension(dimensionId), Source.FIELD_FALLBACK, List.copyOf(fields),
                0.0D, 0.0D, 0.0F,
                slabBase, slabTop,
                0.0F, 0.0F,
                worldTime, 1,
                1.0F, 1.0F,
                false
        );
    }

    public static float densityAt(Level level, double x, double y, double z) {
        if (level == null) {
            return 0.0F;
        }
        Frame frame = current;
        if (frame.source() == Source.NONE
                || !frame.dimensionId().equals(level.dimension().location().toString())) {
            return 0.0F;
        }
        return sample(frame, x, y, z);
    }

    public static float densityAt(String dimensionId, Vec3 position) {
        if (position == null) {
            return 0.0F;
        }
        Frame frame = current;
        if (frame.source() == Source.NONE
                || !frame.dimensionId().equals(normalizeDimension(dimensionId))) {
            return 0.0F;
        }
        return sample(frame, position.x(), position.y(), position.z());
    }

    public static Source source() {
        return current.source();
    }

    public static boolean hasRenderedData() {
        return current.source() != Source.NONE;
    }

    public static void clear() {
        current = Frame.EMPTY;
    }

    private static float sample(Frame frame, double x, double y, double z) {
        float edgeFade = 1.0F;
        if (frame.weatherMapModel()) {
            float u = (float) ((x - frame.originX()) / frame.extent());
            float v = (float) ((z - frame.originZ()) / frame.extent());
            if (u < 0.0F || u > 1.0F || v < 0.0F || v > 1.0F) {
                return 0.0F;
            }
            float edgeDistance = Math.min(Math.min(u, 1.0F - u), Math.min(v, 1.0F - v));
            edgeFade = smoothstep(0.0F, 0.055F, edgeDistance);
        }

        float slabSpan = Math.max(frame.slabTopY() - frame.slabBaseY(), 1.0F);
        float coverage = 0.0F;
        float baseAccum = 0.0F;
        float topAccum = 0.0F;
        float energyAccum = 0.0F;
        float weightAccum = 0.0F;
        float cumulusBaseMin = 1.0F;
        float cumulusTopMax = 0.0F;
        float cumulusDominantWeight = -1.0F;
        float cumulusDominantBase = 1.0F;
        float cumulusDominantTop = 0.0F;
        float cumulusEnergyAccum = 0.0F;
        float cumulusWeightAccum = 0.0F;
        float stratusSurfaceWeight = 0.0F;
        float stratusSurfaceBaseAccum = 0.0F;
        float stratusSurfaceTopAccum = 0.0F;
        float dominantCategoryWeight = -1.0F;
        int dominantCategoryProfile = 0;

        int count = Math.min(CloudWeatherMapRenderer.MAX_CELLS, frame.cells().size());
        for (int i = 0; i < count; i++) {
            VolumetricRenderCell cell = frame.cells().get(i);
            int profile = cell.cloudProfile();

            if (frame.weatherMapModel() && cell.macroCarrier() && profile == 1) {
                double anchorDx = x - cell.x();
                double anchorDz = z - cell.z();
                float anchorCos = (float) Math.cos(-cell.orientationRadians());
                float anchorSin = (float) Math.sin(-cell.orientationRadians());
                float anchorLocalX = (float) (anchorDx * anchorCos - anchorDz * anchorSin);
                float anchorLocalZ = (float) (anchorDx * anchorSin + anchorDz * anchorCos);
                float anchorRadiusX = Math.max(cell.radiusMajor() * 1.35F, 1.0F);
                float anchorRadiusZ = Math.max(cell.radiusMinor() * 1.35F, 1.0F);
                float anchorNx = anchorLocalX / anchorRadiusX;
                float anchorNz = anchorLocalZ / anchorRadiusZ;
                float anchorR = (float) Math.sqrt(anchorNx * anchorNx + anchorNz * anchorNz);
                float anchorSupport = 1.0F - smoothstep(0.78F, 1.0F, anchorR);
                if (anchorSupport > 0.001F) {
                    float seedOffsetX = 11.3F + cell.seed01() * 173.0F;
                    float seedOffsetZ = -7.1F + cell.seed01() * 97.0F;
                    float broadBand = fbm2(
                            anchorLocalX * 0.0032F + seedOffsetX,
                            anchorLocalZ * 0.0095F + seedOffsetZ
                    );
                    float crossBand = valueNoise(
                            anchorLocalX * 0.0054F + anchorLocalZ * 0.0017F
                                    + seedOffsetX * 1.73F + 31.7F,
                            anchorLocalZ * 0.0046F - anchorLocalX * 0.0013F
                                    + seedOffsetZ * 1.73F - 18.9F
                    );
                    float baseSignal = clamp01(broadBand * 0.72F + crossBand * 0.28F);
                    float topSignal = clamp01(broadBand * 0.34F + crossBand * 0.66F);
                    float macroBase = clamp01((cell.baseY() - frame.slabBaseY()) / slabSpan);
                    float macroTop = clamp01((cell.topY() - frame.slabBaseY()) / slabSpan);
                    float macroSpan = Math.max(macroTop - macroBase, 2.0F / slabSpan);
                    float surfaceBase = macroBase + macroSpan * Mth.lerp(baseSignal, 0.12F, 0.32F);
                    float surfaceTop = macroBase + macroSpan * Mth.lerp(topSignal, 0.65F, 0.90F);
                    surfaceTop = Math.max(surfaceTop, surfaceBase + macroSpan * 0.34F);
                    float surfaceWeight = anchorSupport * anchorSupport;
                    stratusSurfaceBaseAccum += surfaceBase * surfaceWeight;
                    stratusSurfaceTopAccum += surfaceTop * surfaceWeight;
                    stratusSurfaceWeight += surfaceWeight;
                }
            }

            float footprintScale = 1.0F;
            if (frame.weatherMapModel()) {
                float texelSize = frame.extent() / frame.weatherMapSize();
                float averageRadius = (cell.radiusMajor() + cell.radiusMinor()) * 0.5F;
                footprintScale = CloudWeatherMapRenderer.adaptiveFootprintScale(
                        averageRadius / Math.max(texelSize, 0.001F),
                        VolumetricCloudDebugConfig.adaptiveWeatherFootprintEnabled()
                ) * Math.max(VolumetricCloudDebugConfig.weatherCoverageScale(), 0.001F);
            }
            float radiusMajor = Math.max(cell.radiusMajor() * footprintScale, 1.0F);
            float radiusMinor = Math.max(cell.radiusMinor() * footprintScale, 1.0F);
            // Match the GPU's analytic macro footprint. The former CPU-only
            // 42-block warp made whiteout/fog disagree with the cloud mass
            // actually rendered on screen.
            double dx = x - cell.x();
            double dz = z - cell.z();
            float cos = (float) Math.cos(-cell.orientationRadians());
            float sin = (float) Math.sin(-cell.orientationRadians());
            float localX = (float) (dx * cos - dz * sin) / radiusMajor;
            float localZ = (float) (dx * sin + dz * cos) / radiusMinor;
            float r = (float) Math.sqrt(localX * localX + localZ * localZ);
            float theta = (float) Math.atan2(localZ, localX);
            float seed = cell.seed01() * ((float) Math.PI * 2.0F);
            float lobeStrength = frame.weatherMapModel()
                    ? switch (profile) {
                        case 1, 5 -> 0.20F;
                        case 2 -> 0.72F;
                        case 3 -> 0.22F;
                        case 4, 7 -> 0.76F;
                        case 6 -> 0.34F;
                        default -> 1.0F;
                    }
                    : 1.0F;
            float lobes = 1.0F
                    + 0.16F * lobeStrength * (float) Math.sin(theta * 2.0F + seed * 3.1F)
                    + 0.11F * lobeStrength * (float) Math.sin(theta * 3.0F + seed * 7.7F)
                    + 0.07F * lobeStrength * (float) Math.sin(theta * 5.0F + seed * 13.9F);
            r /= Math.max(lobes, 0.35F);

            float edgeStart = Mth.lerp(clamp01(cell.edgeSoftness()), 0.78F, 0.42F);
            if (frame.weatherMapModel()) {
                if (profile == 1 || profile == 5) {
                    edgeStart = 0.76F;
                } else if (profile == 2) {
                    edgeStart = 0.62F;
                } else if (profile == 6) {
                    edgeStart = 0.58F;
                }
            }
            float footprint = 1.0F - smoothstep(edgeStart, 1.0F, r);
            boolean envelopeOnly = cell.macroCarrier()
                    && cell.envelopeRole() == VolumetricRenderCell.EnvelopeRole.CARRIER_ONLY;
            float lifecycle = clamp01(cell.lifecycleStage());
            float lifecycleEnvelope = frame.weatherMapModel()
                    ? (lifecycle < 0.5F
                        ? Mth.lerp(lifecycle * 2.0F, 0.30F, 1.0F)
                        : Mth.lerp((lifecycle - 0.5F) * 2.0F, 1.0F, 0.30F))
                    : 1.0F;
            float precipitationPacking = frame.weatherMapModel()
                    ? 1.0F + clamp01(cell.precipitationIntensity()) * 0.16F
                    : 1.0F;
            float categoricalCoverage = envelopeOnly
                    ? 0.0F
                    : footprint * clamp01(cell.density()) * lifecycleEnvelope;
            float categoryWeight = categoricalCoverage * categoricalCoverage * categoricalCoverage;
            if (categoryWeight > dominantCategoryWeight) {
                dominantCategoryWeight = categoryWeight;
                dominantCategoryProfile = profile;
            }
            float cellCoverage = footprint * clamp01(cell.density())
                    * lifecycleEnvelope * precipitationPacking;
            if (cellCoverage <= 0.002F) {
                continue;
            }
            float visibleCellCoverage = envelopeOnly ? 0.0F : cellCoverage;
            coverage = 1.0F - (1.0F - coverage) * (1.0F - visibleCellCoverage);
            float weight = frame.weatherMapModel()
                    ? cellCoverage * cellCoverage
                    : cellCoverage * cellCoverage * cellCoverage;
            if (frame.weatherMapModel() && cell.macroCarrier()) {
                float carrierBoost = switch (profile) {
                    case 1, 5 -> 7.0F;
                    case 2 -> 5.0F;
                    case 3 -> 2.0F;
                    case 4, 7 -> 10.0F;
                    case 6 -> 6.0F;
                    default -> 1.0F;
                };
                weight *= carrierBoost;
            }
            float base01 = clamp01((cell.baseY() - frame.slabBaseY()) / slabSpan);
            float top01 = clamp01((cell.topY() - frame.slabBaseY()) / slabSpan);
            float edge01 = 1.0F - footprint;
            float cellSpan = Math.max(top01 - base01, 2.0F / slabSpan);
            float baseCollapse = 0.42F;
            float topCollapse = 0.42F;
            if (frame.weatherMapModel()) {
                switch (profile) {
                    case 1 -> {
                        baseCollapse = 0.012F;
                        topCollapse = 0.18F;
                    }
                    case 2 -> {
                        baseCollapse = 0.035F;
                        topCollapse = 0.28F;
                    }
                    case 3 -> {
                        baseCollapse = 0.025F;
                        topCollapse = 0.48F;
                    }
                    case 5 -> {
                        baseCollapse = 0.012F;
                        topCollapse = 0.14F;
                    }
                    case 6 -> {
                        baseCollapse = 0.22F;
                        topCollapse = 0.22F;
                    }
                    default -> {
                        baseCollapse = 0.08F;
                        topCollapse = 0.36F;
                    }
                }
            }
            float localBase = base01 + edge01 * cellSpan * baseCollapse;
            float localTop = top01 - edge01 * cellSpan * topCollapse;
            if (frame.weatherMapModel() && profile == 3) {
                float radial01 = clamp01(r);
                float baseEdge = smoothstep(0.72F, 1.0F, radial01);
                float radialDome = (float) Math.pow(radial01, 1.60D);
                localBase = base01 + cellSpan * 0.025F * baseEdge;
                float roleTopCollapse = switch (cell.envelopeRole()) {
                    case BASE -> 0.28F;
                    case CORE -> 0.50F;
                    case TOWER -> 0.62F;
                    case MACRO -> 0.70F;
                    case CARRIER_ONLY -> 0.40F;
                    default -> 0.46F;
                };
                localTop = top01 - cellSpan * roleTopCollapse * radialDome;
            }
            if (envelopeOnly) {
                continue;
            }
            if (frame.weatherMapModel() && profile == 3) {
                float cumulusWeight = cellCoverage * cellCoverage * cellCoverage;
                if (cumulusWeight > cumulusDominantWeight) {
                    cumulusDominantWeight = cumulusWeight;
                    cumulusDominantBase = localBase;
                    cumulusDominantTop = localTop;
                }
                float footprintCore = smoothstep(0.08F, 0.46F, footprint);
                float massGate = smoothstep(0.002F, 0.025F, clamp01(cellCoverage));
                float unionSupport = footprintCore * massGate;
                if (unionSupport > 0.001F) {
                    float supportedBase = Mth.lerp(unionSupport, 1.0F, localBase);
                    float supportedTop = Mth.lerp(unionSupport, 0.0F, localTop);
                    cumulusBaseMin = Math.min(cumulusBaseMin, supportedBase);
                    cumulusTopMax = Math.max(cumulusTopMax, supportedTop);
                }
                cumulusEnergyAccum += clamp01(cell.energy()) * cumulusWeight;
                cumulusWeightAccum += cumulusWeight;
            }
            baseAccum += localBase * weight;
            topAccum += localTop * weight;
            energyAccum += clamp01(cell.energy()) * weight;
            weightAccum += weight;
        }

        if (frame.regionalCoverage() > 0.01F) {
            float sheetNoise = fbm2(
                    (float) x * 0.0011F + frame.worldTime() * 0.00021F,
                    (float) z * 0.0011F + frame.worldTime() * 0.00013F
            );
            float sheet = clamp01((sheetNoise - (1.0F - frame.regionalCoverage() * 0.9F)) * 3.2F)
                    * frame.regionalCoverage();
            if (sheet > 0.002F) {
                float sheetBase = 0.30F + sheetNoise * 0.05F;
                float sheetTop = sheetBase + 0.12F + frame.regionalCoverage() * 0.08F;
                coverage = 1.0F - (1.0F - coverage) * (1.0F - sheet * 0.85F);
                float sheetWeight = sheet * 0.6F;
                float weight = sheetWeight * sheetWeight * sheetWeight;
                if (weight > dominantCategoryWeight) {
                    dominantCategoryWeight = weight;
                    dominantCategoryProfile = 1;
                }
                baseAccum += sheetBase * weight;
                topAccum += sheetTop * weight;
                energyAccum += frame.regionalEnergy() * weight;
                weightAccum += weight;
            }
        }

        coverage *= edgeFade;
        if (weightAccum <= 0.0F || coverage <= 0.002F) {
            return 0.0F;
        }
        boolean useCumulusEnvelope = frame.weatherMapModel()
                && dominantCategoryProfile == 3
                && cumulusWeightAccum > 0.0F;
        float base01;
        float top01;
        if (useCumulusEnvelope) {
            base01 = clamp01(Math.min(cumulusDominantBase, cumulusBaseMin));
            top01 = clamp01(Math.max(cumulusDominantTop, cumulusTopMax));
        } else {
            base01 = clamp01(baseAccum / weightAccum);
            top01 = clamp01(topAccum / weightAccum);
        }
        if (frame.weatherMapModel() && dominantCategoryProfile == 1
                && stratusSurfaceWeight > 0.0F) {
            float surfaceInfluence = smoothstep(0.02F, 0.25F, stratusSurfaceWeight);
            base01 = Mth.lerp(
                    surfaceInfluence,
                    base01,
                    stratusSurfaceBaseAccum / stratusSurfaceWeight
            );
            top01 = Mth.lerp(
                    surfaceInfluence,
                    top01,
                    stratusSurfaceTopAccum / stratusSurfaceWeight
            );
        }
        top01 = Math.max(top01, base01 + 2.0F / slabSpan);
        float baseY = frame.slabBaseY() + base01 * slabSpan;
        float topY = frame.slabBaseY() + clamp01(top01) * slabSpan;
        float h01 = (float) ((y - baseY) / Math.max(topY - baseY, 2.0F));
        if (h01 <= -0.02F || h01 >= 1.02F) {
            return 0.0F;
        }
        float normalizedCoverage = smoothstep(0.012F, 0.42F, clamp01(coverage * frame.coverageMul()));
        float energy = clamp01(useCumulusEnvelope
                ? cumulusEnergyAccum / cumulusWeightAccum
                : energyAccum / weightAccum);
        float clampedHeight = clamp01(h01);
        boolean renderedStratus = frame.weatherMapModel() && dominantCategoryProfile == 1;
        float envelopeCoverage = renderedStratus
                ? (float) Math.sqrt(Math.max(normalizedCoverage, 0.0F))
                        * smoothstep(0.008F, 0.12F, normalizedCoverage)
                : normalizedCoverage;
        float baseEnd = renderedStratus ? 0.020F : 0.10F;
        float topStart = renderedStratus
                ? 0.76F
                : 0.62F + energy * 0.22F;
        float height = smoothstep(0.0F, baseEnd, clampedHeight)
                * (1.0F - smoothstep(topStart, 1.0F, clampedHeight));
        float familyDensityScale = renderedStratus ? 0.62F : 1.0F;
        // The CPU cannot sample the 3-D material textures, but it now matches
        // the rendered weather envelope, lifecycle, invisible-carrier rule and
        // stratus vertical bounds. This keeps camera fog/whiteout spatially
        // aligned with the volume without pretending to reproduce fine noise.
        return clamp01(envelopeCoverage * height * frame.densityMul() * familyDensityScale
                * smoothstep(0.010F, 0.080F, normalizedCoverage));
    }

    private static float fbm2(float x, float y) {
        return valueNoise(x, y) * 0.65F + valueNoise(x * 2.13F + 19.7F, y * 2.13F + 19.7F) * 0.35F;
    }

    private static float valueNoise(float x, float y) {
        float ix = (float) Math.floor(x);
        float iy = (float) Math.floor(y);
        float fx = fract(x);
        float fy = fract(y);
        fx = fx * fx * (3.0F - 2.0F * fx);
        fy = fy * fy * (3.0F - 2.0F * fy);
        float a = hash12(ix, iy);
        float b = hash12(ix + 1.0F, iy);
        float c = hash12(ix, iy + 1.0F);
        float d = hash12(ix + 1.0F, iy + 1.0F);
        return Mth.lerp(fy, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
    }

    private static float hash12(float x, float y) {
        float p3x = fract(x * 0.1031F);
        float p3y = fract(y * 0.1031F);
        float p3z = fract(x * 0.1031F);
        float dot = p3x * (p3y + 33.33F)
                + p3y * (p3z + 33.33F)
                + p3z * (p3x + 33.33F);
        p3x += dot;
        p3y += dot;
        p3z += dot;
        return fract((p3x + p3y) * p3z);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        if (edge0 == edge1) {
            return value < edge0 ? 0.0F : 1.0F;
        }
        float t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0F - 2.0F * t);
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private static float clamp01(float value) {
        return Mth.clamp(value, 0.0F, 1.0F);
    }

    private static String normalizeDimension(String dimensionId) {
        return dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
    }

    private record Frame(
            String dimensionId,
            Source source,
            List<VolumetricRenderCell> cells,
            double originX,
            double originZ,
            float extent,
            float slabBaseY,
            float slabTopY,
            float regionalCoverage,
            float regionalEnergy,
            float worldTime,
            int weatherMapSize,
            float coverageMul,
            float densityMul,
            boolean weatherMapModel
    ) {
        private static final Frame EMPTY = new Frame(
                "minecraft:overworld", Source.NONE, List.of(),
                0.0D, 0.0D, 0.0F,
                0.0F, 0.0F,
                0.0F, 0.0F,
                0.0F, 1, 1.0F, 1.0F, false
        );
    }
}
