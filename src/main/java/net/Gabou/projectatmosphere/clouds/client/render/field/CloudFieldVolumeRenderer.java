package net.Gabou.projectatmosphere.clouds.client.render.field;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.Gabou.projectatmosphere.client.render.mesh.VolumeBoxMesh;
import net.Gabou.projectatmosphere.client.render.shader.CloudFieldVolumeShaders;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldRendererInput;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSourceKind;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws synced CloudField snapshots as simple bounded volumetric prototype
 * clouds. The renderer reads only CloudFieldRendererInput and does not query
 * backend or weather systems.
 */
public final class CloudFieldVolumeRenderer {
    private static final int MAX_FIELDS_PER_FRAME = 16;
    private static final VolumeBoxMesh VOLUME_BOX = new VolumeBoxMesh();

    private CloudFieldVolumeRenderer() {
    }

    /**
     * Renders all valid CloudField snapshots for the current client dimension.
     *
     * @param input current renderer input from ClientCloudFieldCache
     * @param poseStack render pose stack already translated to world space
     * @param projectionMatrix current projection matrix
     * @param dimensionId current client dimension id
     * @param cachedSnapshots raw client snapshot cache size
     * @return diagnostics for command inspection
     */
    public static CloudFieldVolumeRenderStats render(
            CloudFieldRendererInput input,
            PoseStack poseStack,
            Matrix4f projectionMatrix,
            String dimensionId,
            int cachedSnapshots
    ) {
        CloudFieldVolumeRenderMode mode = CloudFieldVolumeRenderConfig.mode();
        CloudFieldVolumeRenderFilter filter = CloudFieldVolumeRenderConfig.filter();
        ShaderInstance shader = CloudFieldVolumeShaders.getShader();
        if (shader == null) {
            return CloudFieldVolumeRenderStats.idle(true, false, mode, filter, "shader_unavailable", cachedSnapshots);
        }
        if (input == null || input.fields().isEmpty()) {
            return CloudFieldVolumeRenderStats.idle(true, true, mode, filter, "no_snapshots", cachedSnapshots);
        }

        Stats stats = new Stats(dimensionId, input.worldTime(), cachedSnapshots, input.fields().size(), mode, filter);
        List<FieldDraw> candidates = new ArrayList<>();
        for (CloudFieldSnapshot snapshot : input.fields()) {
            if (snapshot == null) {
                stats.invalidGeometrySkipped++;
                stats.lastSkipReason = "null_snapshot";
                continue;
            }
            if (!dimensionMatches(snapshot, dimensionId)) {
                stats.wrongDimensionSkipped++;
                stats.lastSkipReason = "wrong_dimension";
                continue;
            }
            if (!snapshot.hasVisibleClouds()) {
                stats.notVisibleSkipped++;
                stats.lastSkipReason = "not_visible";
                continue;
            }
            Bounds bounds = boundsFor(snapshot);
            if (!isValid(bounds)) {
                stats.invalidGeometrySkipped++;
                stats.lastSkipReason = "invalid_geometry";
                continue;
            }
            stats.visibleFields++;
            candidates.add(new FieldDraw(snapshot, bounds, bounds.center().distanceToSqr(input.cameraPosition())));
        }

        stats.fieldsBeforeFilter = candidates.size();
        candidates = applyFilter(candidates, filter);
        stats.filterSkipped += Math.max(0, stats.fieldsBeforeFilter - candidates.size());
        if (stats.filterSkipped > 0) {
            stats.lastSkipReason = "filtered_out";
        }
        stats.visibleFields = candidates.size();

        candidates.sort(Comparator.comparingDouble(FieldDraw::distanceSqr).reversed());
        if (candidates.size() > MAX_FIELDS_PER_FRAME) {
            stats.maxFieldLimitSkipped += candidates.size() - MAX_FIELDS_PER_FRAME;
            stats.lastSkipReason = "max_field_limit";
            candidates = new ArrayList<>(candidates.subList(0, MAX_FIELDS_PER_FRAME));
        }

        if (candidates.isEmpty()) {
            return stats.toRenderStats();
        }

        Matrix4f modelViewMat = poseStack.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> shader);

        try {
            for (FieldDraw candidate : candidates) {
                if (candidate.bounds().contains(input.cameraPosition())) {
                    RenderSystem.disableCull();
                } else {
                    RenderSystem.enableCull();
                }

                CloudFieldVolumeUniformUploader.apply(
                        shader,
                        input,
                        candidate.snapshot(),
                        candidate.bounds(),
                        modelViewMat,
                        projectionMatrix
                );
                shader.apply();
                VOLUME_BOX.draw(shader, modelViewMat, projectionMatrix);
                shader.clear();
                stats.renderedFields++;
                stats.recordRendered(candidate.snapshot());
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }

        return stats.toRenderStats();
    }

    private static boolean dimensionMatches(CloudFieldSnapshot snapshot, String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return true;
        }
        return dimensionId.equals(snapshot.dimensionId());
    }

    private static Bounds boundsFor(CloudFieldSnapshot snapshot) {
        Vec3 center = snapshot.center();
        float radius = Math.max(1.0F, snapshot.radius());
        float baseY = snapshot.baseY();
        float topY = Math.max(baseY + 1.0F, snapshot.topY());
        float horizontalPadding = Math.max(2.0F, radius * 0.06F);
        float verticalPadding = Math.max(2.0F, (topY - baseY) * 0.06F);
        return new Bounds(
                new Vec3(center.x() - radius - horizontalPadding, baseY - verticalPadding, center.z() - radius - horizontalPadding),
                new Vec3(center.x() + radius + horizontalPadding, topY + verticalPadding, center.z() + radius + horizontalPadding)
        );
    }

    private static boolean isValid(Bounds bounds) {
        return bounds != null
                && Double.isFinite(bounds.min().x())
                && Double.isFinite(bounds.min().y())
                && Double.isFinite(bounds.min().z())
                && Double.isFinite(bounds.max().x())
                && Double.isFinite(bounds.max().y())
                && Double.isFinite(bounds.max().z())
                && bounds.max().x() > bounds.min().x()
                && bounds.max().y() > bounds.min().y()
                && bounds.max().z() > bounds.min().z();
    }

    private static List<FieldDraw> applyFilter(List<FieldDraw> candidates, CloudFieldVolumeRenderFilter filter) {
        if (candidates.isEmpty() || filter == CloudFieldVolumeRenderFilter.ALL) {
            return candidates;
        }
        if (filter == CloudFieldVolumeRenderFilter.FIRST) {
            return List.of(candidates.get(0));
        }
        if (filter == CloudFieldVolumeRenderFilter.NEAREST) {
            FieldDraw nearest = candidates.stream()
                    .min(Comparator.comparingDouble(FieldDraw::distanceSqr))
                    .orElse(null);
            return nearest == null ? List.of() : List.of(nearest);
        }

        List<FieldDraw> filtered = new ArrayList<>();
        for (FieldDraw candidate : candidates) {
            CloudFieldSourceKind sourceKind = candidate.snapshot().sourceKind();
            if (filter == CloudFieldVolumeRenderFilter.MANUAL && sourceKind == CloudFieldSourceKind.MANUAL_DEBUG) {
                filtered.add(candidate);
            } else if (filter == CloudFieldVolumeRenderFilter.WEATHER && sourceKind == CloudFieldSourceKind.WEATHER_SUMMARY) {
                filtered.add(candidate);
            }
        }
        return filtered;
    }

    private static String sourceLabel(CloudFieldSnapshot snapshot) {
        return snapshot.sourceKind().serializedName();
    }

    private static String shortId(CloudFieldSnapshot snapshot) {
        String id = snapshot.fieldId().toString();
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    /**
     * World-space bounds for one CloudField draw.
     */
    public record Bounds(Vec3 min, Vec3 max) {
        public Bounds {
            min = min == null ? Vec3.ZERO : min;
            max = max == null ? min : max;
        }

        public Vec3 center() {
            return new Vec3(
                    (min.x() + max.x()) * 0.5D,
                    (min.y() + max.y()) * 0.5D,
                    (min.z() + max.z()) * 0.5D
            );
        }

        public boolean contains(Vec3 position) {
            if (position == null) {
                return false;
            }
            return Mth.clamp(position.x(), min.x(), max.x()) == position.x()
                    && Mth.clamp(position.y(), min.y(), max.y()) == position.y()
                    && Mth.clamp(position.z(), min.z(), max.z()) == position.z();
        }
    }

    private record FieldDraw(CloudFieldSnapshot snapshot, Bounds bounds, double distanceSqr) {
    }

    private static final class Stats {
        private final String dimensionId;
        private final long worldTime;
        private final int cachedSnapshots;
        private final int rendererInputFields;
        private final CloudFieldVolumeRenderMode mode;
        private final CloudFieldVolumeRenderFilter filter;
        private final List<String> renderedFieldLabels = new ArrayList<>();
        private int fieldsBeforeFilter;
        private int visibleFields;
        private int renderedFields;
        private int wrongDimensionSkipped;
        private int invalidGeometrySkipped;
        private int notVisibleSkipped;
        private int filterSkipped;
        private int maxFieldLimitSkipped;
        private String lastSkipReason = "none";

        private Stats(
                String dimensionId,
                long worldTime,
                int cachedSnapshots,
                int rendererInputFields,
                CloudFieldVolumeRenderMode mode,
                CloudFieldVolumeRenderFilter filter
        ) {
            this.dimensionId = dimensionId == null ? "unknown" : dimensionId;
            this.worldTime = worldTime;
            this.cachedSnapshots = cachedSnapshots;
            this.rendererInputFields = rendererInputFields;
            this.mode = mode;
            this.filter = filter;
        }

        private void recordRendered(CloudFieldSnapshot snapshot) {
            if (renderedFieldLabels.size() >= 4) {
                return;
            }
            renderedFieldLabels.add(shortId(snapshot) + ":" + sourceLabel(snapshot));
        }

        private CloudFieldVolumeRenderStats toRenderStats() {
            int skipped = wrongDimensionSkipped + invalidGeometrySkipped + notVisibleSkipped + filterSkipped + maxFieldLimitSkipped;
            return new CloudFieldVolumeRenderStats(
                    true,
                    true,
                    mode,
                    filter,
                    dimensionId,
                    worldTime,
                    cachedSnapshots,
                    rendererInputFields,
                    fieldsBeforeFilter,
                    visibleFields,
                    renderedFields,
                    skipped,
                    0,
                    0,
                    0,
                    wrongDimensionSkipped,
                    invalidGeometrySkipped,
                    notVisibleSkipped,
                    filterSkipped,
                    maxFieldLimitSkipped,
                    renderedFieldLabels.isEmpty() ? "none" : String.join(",", renderedFieldLabels),
                    lastSkipReason
            );
        }
    }
}
