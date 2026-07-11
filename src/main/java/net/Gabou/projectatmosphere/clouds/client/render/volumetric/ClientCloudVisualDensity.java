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

        double warpedX = x;
        double warpedZ = z;
        if (frame.weatherMapModel()) {
            float warpX = fbm2((float) x * 0.010F + 3.7F, (float) z * 0.010F + 9.1F) - 0.5F;
            float warpZ = fbm2((float) x * 0.010F - 7.3F, (float) z * 0.010F + 1.9F) - 0.5F;
            warpedX += warpX * 42.0F;
            warpedZ += warpZ * 42.0F;
        }

        float slabSpan = Math.max(frame.slabTopY() - frame.slabBaseY(), 1.0F);
        float coverage = 0.0F;
        float baseAccum = 0.0F;
        float topAccum = 0.0F;
        float energyAccum = 0.0F;
        float weightAccum = 0.0F;

        int count = Math.min(CloudWeatherMapRenderer.MAX_CELLS, frame.cells().size());
        for (int i = 0; i < count; i++) {
            VolumetricRenderCell cell = frame.cells().get(i);
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
            double dx = warpedX - cell.x();
            double dz = warpedZ - cell.z();
            float cos = (float) Math.cos(-cell.orientationRadians());
            float sin = (float) Math.sin(-cell.orientationRadians());
            float localX = (float) (dx * cos - dz * sin) / radiusMajor;
            float localZ = (float) (dx * sin + dz * cos) / radiusMinor;
            float r = (float) Math.sqrt(localX * localX + localZ * localZ);
            float theta = (float) Math.atan2(localZ, localX);
            float seed = cell.seed01() * ((float) Math.PI * 2.0F);
            float lobes = 1.0F
                    + 0.16F * (float) Math.sin(theta * 2.0F + seed * 3.1F)
                    + 0.11F * (float) Math.sin(theta * 3.0F + seed * 7.7F)
                    + 0.07F * (float) Math.sin(theta * 5.0F + seed * 13.9F);
            r /= Math.max(lobes, 0.35F);

            float edgeStart = Mth.lerp(clamp01(cell.edgeSoftness()), 0.78F, 0.42F);
            float footprint = 1.0F - smoothstep(edgeStart, 1.0F, r);
            float cellCoverage = footprint * clamp01(cell.density());
            if (cellCoverage <= 0.002F) {
                continue;
            }
            coverage = 1.0F - (1.0F - coverage) * (1.0F - cellCoverage);
            float weight = cellCoverage * cellCoverage * cellCoverage;
            float base01 = clamp01((cell.baseY() - frame.slabBaseY()) / slabSpan);
            float top01 = clamp01((cell.topY() - frame.slabBaseY()) / slabSpan);
            float edge01 = 1.0F - footprint;
            float cellSpan = Math.max(top01 - base01, 2.0F / slabSpan);
            baseAccum += (base01 + edge01 * cellSpan * 0.42F) * weight;
            topAccum += (top01 - edge01 * cellSpan * 0.42F) * weight;
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
        float baseY = frame.slabBaseY() + clamp01(baseAccum / weightAccum) * slabSpan;
        float topY = frame.slabBaseY() + clamp01(topAccum / weightAccum) * slabSpan;
        float h01 = (float) ((y - baseY) / Math.max(topY - baseY, 2.0F));
        if (h01 <= -0.02F || h01 >= 1.02F) {
            return 0.0F;
        }
        float normalizedCoverage = smoothstep(0.012F, 0.42F, clamp01(coverage * frame.coverageMul()));
        float energy = clamp01(energyAccum / weightAccum);
        float height = smoothstep(0.0F, 0.10F, clamp01(h01))
                * (1.0F - smoothstep(0.62F + energy * 0.22F, 1.0F, clamp01(h01)));
        // Noise textures cannot be evaluated on the CPU; this is the exact
        // weather-map envelope and vertical profile consumed by the shader.
        return clamp01(normalizedCoverage * height * frame.densityMul()
                * smoothstep(0.012F, 0.095F, normalizedCoverage));
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
