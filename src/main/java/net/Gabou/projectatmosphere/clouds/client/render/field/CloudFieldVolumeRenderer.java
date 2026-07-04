package net.Gabou.projectatmosphere.clouds.client.render.field;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.Gabou.projectatmosphere.client.render.mesh.VolumeBoxMesh;
import net.Gabou.projectatmosphere.client.render.shader.CloudFieldVolumeShaders;
import net.Gabou.projectatmosphere.clouds.client.render.CloudGpuTimer;
import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderStateGuard;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldRendererInput;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSourceKind;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Draws synced CloudField snapshots as bounded volumetric clouds. The renderer
 * reads only CloudFieldRendererInput and does not query backend or weather
 * systems directly.
 */
public final class CloudFieldVolumeRenderer {
    private static final VolumeBoxMesh VOLUME_BOX = new VolumeBoxMesh();
    private static final CloudGpuTimer GPU_TIMER = new CloudGpuTimer();

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
            int cachedSnapshots,
            Frustum frustum,
            RenderTarget outputTarget,
            int sceneDepthTextureId,
            boolean downscaleApplied
    ) {
        CloudFieldVolumeRenderMode mode = CloudFieldVolumeRenderConfig.mode();
        CloudFieldVolumeRenderFilter filter = CloudFieldVolumeRenderConfig.filter();
        ShaderInstance shader = CloudFieldVolumeShaders.getShader();
        boolean debugMode = mode != CloudFieldVolumeRenderMode.NORMAL;
        GPU_TIMER.poll();
        if (shader == null) {
            return CloudFieldVolumeRenderStats.idle(true, false, mode, filter, "shader_unavailable", cachedSnapshots);
        }
        if (input == null || input.fields().isEmpty()) {
            return CloudFieldVolumeRenderStats.idle(true, true, mode, filter, "no_snapshots", cachedSnapshots);
        }

        Stats stats = new Stats(dimensionId, input.worldTime(), cachedSnapshots, input.fields().size(), mode, filter);
        RenderTarget target = outputTarget == null ? Minecraft.getInstance().getMainRenderTarget() : outputTarget;
        // Sampling main depth is safe only while drawing into the dedicated
        // downscaled target. Native rendering relies on framebuffer depth and
        // avoids sampling an attached texture.
        boolean sceneDepthClip = !debugMode && downscaleApplied && sceneDepthTextureId > 0;
        boolean compositeOcclusion = !debugMode && downscaleApplied;
        stats.recordTarget(target, downscaleApplied, sceneDepthClip, compositeOcclusion);
        List<FieldDraw> candidates = new ArrayList<>();
        double maxRenderDistance = Math.max(100.0D, AtmoCommonConfig.CLOUD_RENDER_DISTANCE.get());
        double maxRenderDistanceSqr = maxRenderDistance * maxRenderDistance;
        for (CloudFieldSnapshot snapshot : input.fields()) {
            if (snapshot == null) {
                stats.invalidGeometrySkipped++;
                stats.lastSkipReason = "null_snapshot";
                stats.recordRawField("null result=skipped:null_snapshot");
                continue;
            }
            Bounds bounds = boundsFor(snapshot);
            if (!dimensionMatches(snapshot, dimensionId)) {
                stats.wrongDimensionSkipped++;
                stats.lastSkipReason = "wrong_dimension";
                stats.recordField(snapshot, bounds, input.cameraPosition(), "skipped:wrong_dimension", "dimension_mismatch");
                continue;
            }
            if (!snapshot.hasVisibleClouds()) {
                stats.notVisibleSkipped++;
                stats.lastSkipReason = "not_visible";
                stats.recordField(snapshot, bounds, input.cameraPosition(), "skipped:not_visible", "snapshot_visible=false");
                continue;
            }
            if (!isValid(bounds)) {
                stats.invalidGeometrySkipped++;
                stats.lastSkipReason = "invalid_geometry";
                stats.recordField(snapshot, bounds, input.cameraPosition(), "skipped:invalid_geometry", "invalid_bounds");
                continue;
            }
            if (!debugMode && frustum != null && !frustum.isVisible(bounds.toAabb())) {
                stats.frustumSkipped++;
                stats.lastSkipReason = "frustum";
                stats.recordField(snapshot, bounds, input.cameraPosition(), "skipped:frustum", "visible=false frustum=culled");
                continue;
            }
            double distanceSqr = bounds.distanceToSqr(input.cameraPosition());
            if (!debugMode && distanceSqr > maxRenderDistanceSqr) {
                stats.distanceSkipped++;
                stats.lastSkipReason = "distance";
                stats.recordField(snapshot, bounds, input.cameraPosition(), "skipped:distance", "visible=false distance_culled");
                continue;
            }
            stats.visibleFields++;
            candidates.add(new FieldDraw(snapshot, bounds, distanceSqr, fieldPriority(snapshot, bounds, input.cameraPosition())));
        }

        stats.fieldsBeforeFilter = candidates.size();
        List<FieldDraw> filteredCandidates = applyFilter(candidates, filter);
        for (FieldDraw candidate : candidates) {
            if (!containsField(filteredCandidates, candidate)) {
                stats.recordField(candidate.snapshot(), candidate.bounds(), input.cameraPosition(), "skipped:filtered_out", "visible=true frustum=passed");
            }
        }
        candidates = filteredCandidates;
        stats.filterSkipped += Math.max(0, stats.fieldsBeforeFilter - filteredCandidates.size());
        if (stats.filterSkipped > 0) {
            stats.lastSkipReason = "filtered_out";
        }
        stats.visibleFields = candidates.size();

        int maxFieldsPerFrame = CloudFieldVolumeRenderConfig.maxRenderedFields();
        if (candidates.size() > maxFieldsPerFrame) {
            candidates.sort(Comparator.comparingDouble(FieldDraw::priority).reversed());
            stats.maxFieldLimitSkipped += candidates.size() - maxFieldsPerFrame;
            stats.lastSkipReason = "max_field_limit";
            for (int i = maxFieldsPerFrame; i < candidates.size(); i++) {
                FieldDraw skipped = candidates.get(i);
                stats.recordField(skipped.snapshot(), skipped.bounds(), input.cameraPosition(), "skipped:max_field_limit", "visible=true frustum=passed");
            }
            candidates = new ArrayList<>(candidates.subList(0, maxFieldsPerFrame));
        }
        candidates.sort(Comparator.comparingDouble(FieldDraw::distanceSqr).reversed());

        if (candidates.isEmpty()) {
            return stats.toRenderStats();
        }

        try {
            Matrix4f modelViewMat = poseStack.last().pose();
            if (target != null) {
                target.bindWrite(true);
            }
            RenderSystem.disableScissor();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(debugMode ? GL11.GL_ALWAYS : GL11.GL_LEQUAL);
            RenderSystem.depthMask(downscaleApplied && !debugMode);
            RenderSystem.setShader(() -> shader);

            GPU_TIMER.begin();
            try {
                for (FieldDraw candidate : candidates) {
                    if (candidate.bounds().contains(input.cameraPosition())) {
                        RenderSystem.enableCull();
                        GL11.glCullFace(GL11.GL_FRONT);
                    } else {
                        RenderSystem.enableCull();
                        GL11.glCullFace(GL11.GL_BACK);
                    }

                    CloudFieldVolumeUniformUploader.apply(
                            shader,
                            input,
                            candidate.snapshot(),
                            candidate.bounds(),
                            modelViewMat,
                            projectionMatrix,
                            target,
                            sceneDepthTextureId,
                            raymarchStepsFor(candidate),
                            sceneDepthClip
                    );
                    shader.apply();
                    try {
                        VOLUME_BOX.draw(shader, modelViewMat, projectionMatrix);
                    } finally {
                        shader.clear();
                    }
                    stats.renderedFields++;
                    stats.recordRendered(candidate.snapshot());
                    stats.recordField(candidate.snapshot(), candidate.bounds(), input.cameraPosition(), "rendered", "visible=true frustum=passed");
                }
            } finally {
                GPU_TIMER.end();
                stats.recordGpuTimer(GPU_TIMER);
            }
        } finally {
            restoreRenderState();
        }

        return stats.toRenderStats();
    }

    /**
     * Restores the render state touched by the CloudField volume pass. The hook
     * also calls this defensively when it catches a render exception.
     */
    public static void restoreRenderState() {
        CloudRenderStateGuard.restoreAfterCloudPass();
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

    private static boolean containsField(List<FieldDraw> candidates, FieldDraw target) {
        for (FieldDraw candidate : candidates) {
            if (candidate.snapshot().fieldId().equals(target.snapshot().fieldId())) {
                return true;
            }
        }
        return false;
    }

    private static double fieldPriority(CloudFieldSnapshot snapshot, Bounds bounds, Vec3 cameraPosition) {
        if (bounds.contains(cameraPosition)) {
            return Double.MAX_VALUE * 0.25D;
        }
        double distanceSqr = Math.max(1.0D, bounds.distanceToSqr(cameraPosition));
        double radius = Math.max(1.0D, snapshot.radius());
        double projectedContribution = (radius * radius) / distanceSqr;
        return projectedContribution
                * Math.max(0.10D, sourceVisualMultiplier(snapshot.sourceKind()))
                * Math.max(0.10D, snapshot.effectiveDensity())
                * Math.max(0.10D, snapshot.effectiveCoverage());
    }

    private static int raymarchStepsFor(FieldDraw draw) {
        int baseSteps = CloudFieldVolumeRenderConfig.raymarchSteps();
        return Math.max(8, baseSteps);
    }

    private static String sourceLabel(CloudFieldSnapshot snapshot) {
        return snapshot.sourceKind().serializedName();
    }

    private static float sourceVisualMultiplier(CloudFieldSourceKind sourceKind) {
        return switch (sourceKind == null ? CloudFieldSourceKind.UNKNOWN : sourceKind) {
            case MANUAL_DEBUG -> 0.92F;
            case WEATHER_SUMMARY -> 0.38F;
            case PA_CLUSTER -> 1.0F;
            case PA_REGION -> 0.78F;
            case UNKNOWN -> 0.55F;
        };
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

        public AABB toAabb() {
            return new AABB(min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
        }

        public double distanceToSqr(Vec3 position) {
            Vec3 safePosition = position == null ? Vec3.ZERO : position;
            double dx = axisDistance(safePosition.x(), min.x(), max.x());
            double dy = axisDistance(safePosition.y(), min.y(), max.y());
            double dz = axisDistance(safePosition.z(), min.z(), max.z());
            return dx * dx + dy * dy + dz * dz;
        }

        private static double axisDistance(double value, double min, double max) {
            if (value < min) {
                return min - value;
            }
            if (value > max) {
                return value - max;
            }
            return 0.0D;
        }
    }

    private record FieldDraw(CloudFieldSnapshot snapshot, Bounds bounds, double distanceSqr, double priority) {
    }

    private static final class Stats {
        private final String dimensionId;
        private final long worldTime;
        private final int cachedSnapshots;
        private final int rendererInputFields;
        private final CloudFieldVolumeRenderMode mode;
        private final CloudFieldVolumeRenderFilter filter;
        private final List<String> renderedFieldLabels = new ArrayList<>();
        private final List<String> fieldDiagnostics = new ArrayList<>();
        private int fieldsBeforeFilter;
        private int visibleFields;
        private int renderedFields;
        private int wrongDimensionSkipped;
        private int invalidGeometrySkipped;
        private int notVisibleSkipped;
        private int filterSkipped;
        private int maxFieldLimitSkipped;
        private int frustumSkipped;
        private int distanceSkipped;
        private String targetDiagnostics = "none";
        private String performanceDiagnostics = "raymarchGpuMs=unavailable";
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

        private void recordTarget(
                RenderTarget target,
                boolean downscaleApplied,
                boolean sceneDepthClip,
                boolean compositeOcclusion
        ) {
            if (target == null) {
                targetDiagnostics = "missing";
                return;
            }
            RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
            String targetKind = target == mainTarget ? "main" : "cloud_downscale";
            targetDiagnostics = targetKind
                    + " size=" + target.width + "x" + target.height
                    + " downscaleApplied=" + downscaleApplied
                    + " sceneDepthClip=" + sceneDepthClip
                    + " compositeOcclusion=" + compositeOcclusion
                    + " depth=" + (target.getDepthTextureId() > 0);
        }

        private void recordGpuTimer(CloudGpuTimer gpuTimer) {
            if (gpuTimer == null || !gpuTimer.isSupported()) {
                performanceDiagnostics = "raymarchGpuMs=unsupported";
                return;
            }
            if (!gpuTimer.hasResult()) {
                performanceDiagnostics = "raymarchGpuMs=pending pendingRaymarchQueries=" + gpuTimer.getPendingQueries();
                return;
            }
            performanceDiagnostics = "raymarchGpuMs=" + CloudFieldVolumeRenderStats.format(gpuTimer.getLastMilliseconds())
                    + " raymarchAgeFrames=" + gpuTimer.getLastResultAgeFrames()
                    + " pendingRaymarchQueries=" + gpuTimer.getPendingQueries();
        }

        private void recordRawField(String detail) {
            if (fieldDiagnostics.size() >= 8) {
                return;
            }
            fieldDiagnostics.add(detail);
        }

        private void recordField(
                CloudFieldSnapshot snapshot,
                Bounds bounds,
                Vec3 cameraPosition,
                String result,
                String visibility
        ) {
            if (fieldDiagnostics.size() >= 8) {
                return;
            }
            Vec3 center = snapshot.center();
            boolean validBounds = isValid(bounds);
            boolean cameraInside = validBounds && bounds.contains(cameraPosition);
            Vec3 distanceAnchor = validBounds ? bounds.center() : center;
            double cameraDistance = Math.sqrt(distanceAnchor.distanceToSqr(cameraPosition));
            Vec3 min = validBounds ? bounds.min() : Vec3.ZERO;
            Vec3 max = validBounds ? bounds.max() : Vec3.ZERO;
            fieldDiagnostics.add(String.format(
                    Locale.ROOT,
                    "%s:%s sourceVisual=%.2f center=%.1f,%.1f,%.1f radius=%.1f baseTop=%.1f/%.1f volumeMin=%.1f,%.1f,%.1f volumeMax=%.1f,%.1f,%.1f density=%.3f coverage=%.3f hydration=%.3f vertical=%.3f cloudlets=%d/%d cloudletBudget=%d cameraDistance=%.1f cameraInsideVolume=%s %s result=%s",
                    shortId(snapshot),
                    sourceLabel(snapshot),
                    sourceVisualMultiplier(snapshot.sourceKind()),
                    center.x(),
                    center.y(),
                    center.z(),
                    snapshot.radius(),
                    snapshot.baseY(),
                    snapshot.topY(),
                    min.x(),
                    min.y(),
                    min.z(),
                    max.x(),
                    max.y(),
                    max.z(),
                    snapshot.density(),
                    snapshot.coverage(),
                    snapshot.hydrationProgress(),
                    snapshot.verticalDevelopment(),
                    snapshot.activeCloudletCount(),
                    snapshot.targetCloudletCount(),
                    CloudFieldVolumeRenderConfig.cloudletBudget(),
                    cameraDistance,
                    cameraInside,
                    visibility,
                    result
            ));
        }

        private CloudFieldVolumeRenderStats toRenderStats() {
            int skipped = wrongDimensionSkipped
                    + invalidGeometrySkipped
                    + notVisibleSkipped
                    + filterSkipped
                    + maxFieldLimitSkipped
                    + frustumSkipped
                    + distanceSkipped;
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
                    frustumSkipped,
                    distanceSkipped,
                    renderedFieldLabels.isEmpty() ? "none" : String.join(",", renderedFieldLabels),
                    fieldDiagnostics.isEmpty() ? "none" : String.join("\n", fieldDiagnostics),
                    targetDiagnostics,
                    performanceDiagnostics,
                    "none",
                    lastSkipReason
            );
        }
    }
}
