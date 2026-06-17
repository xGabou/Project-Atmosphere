package net.Gabou.projectatmosphere.tools.debug;

import dev.nonamecrackers2.simpleclouds.common.cloud.CloudType;
import dev.nonamecrackers2.simpleclouds.common.noise.NoiseSettings;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SimpleCloudsRenderDiagnostics {
    private static final Logger LOGGER = LogManager.getLogger("ProjectAtmosphere/SimpleCloudsRender");
    private static final boolean ENABLED = Boolean.getBoolean("projectatmosphere.simpleclouds.debugRender");
    private static final ThreadLocal<PassStats> CURRENT_PASS = ThreadLocal.withInitial(PassStats::new);
    private static final AtomicBoolean PLAYER_SAMPLE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean SHADER_LOAD_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean REGION_UPLOAD_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean CHUNK_DECISION_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean PREPARE_MESH_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean PIPELINE_STAGE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean ALPHA_FALLBACK_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean FINALIZE_MESH_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean PASS_SUMMARY_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean DH_PIPELINE_FALLBACK_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean DH_PASS_SUMMARY_LOGGED = new AtomicBoolean();
    private static final ThreadLocal<Boolean> DH_PIPELINE_ACTIVE = ThreadLocal.withInitial(() -> false);

    private SimpleCloudsRenderDiagnostics() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static void beginPass(String passName, int totalChunks, int opaqueBytes, int transparentBytes, int opaqueElements, int transparentElements, boolean canRender, boolean transparencyEnabled, Object meshStatus) {
        if (Boolean.TRUE.equals(DH_PIPELINE_ACTIVE.get())) {
            return;
        }
        setPassStats(passName, totalChunks, opaqueBytes, transparentBytes, opaqueElements, transparentElements, canRender, transparencyEnabled, meshStatus);
    }

    private static void setPassStats(String passName, int totalChunks, int opaqueBytes, int transparentBytes, int opaqueElements, int transparentElements, boolean canRender, boolean transparencyEnabled, Object meshStatus) {
        PassStats stats = CURRENT_PASS.get();
        stats.passName = passName;
        stats.totalChunks = totalChunks;
        stats.opaqueBytes = opaqueBytes;
        stats.transparentBytes = transparentBytes;
        stats.opaqueElements = opaqueElements;
        stats.transparentElements = transparentElements;
        stats.canRender = canRender;
        stats.transparencyEnabled = transparencyEnabled;
        stats.meshStatus = meshStatus;
        stats.drawCalls = 0;
        stats.totalElements = 0;
        stats.alphaFallbacks = 0;
    }

    public static void beginDhPipelinePass(int totalChunks, int opaqueBytes, int transparentBytes, int opaqueElements, int transparentElements, boolean canRender, boolean transparencyEnabled, Object meshStatus) {
        DH_PIPELINE_ACTIVE.set(true);
        setPassStats(
                "dh_after_distant_horizons_render",
                totalChunks,
                opaqueBytes,
                transparentBytes,
                opaqueElements,
                transparentElements,
                canRender,
                transparencyEnabled,
                meshStatus
        );
    }

    public static void recordDraw(String passName, int elementCount) {
        PassStats stats = CURRENT_PASS.get();
        if (stats.passName == null || "unknown".equals(stats.passName)) {
            stats.passName = passName;
        }
        stats.drawCalls++;
        stats.totalElements += Math.max(0, elementCount);
    }

    public static void noteAlphaFallback(int elementCount, int ticksSinceLastGen) {
        PassStats stats = CURRENT_PASS.get();
        stats.alphaFallbacks++;
        if (ENABLED || ALPHA_FALLBACK_LOGGED.compareAndSet(false, true)) {
            LOGGER.info(
                    "[SimpleCloudsRender] alpha fallback triggered elementCount={} ticksSinceLastGen={} pass={} drawCalls={} totalElements={}",
                    elementCount,
                    ticksSinceLastGen,
                    stats.passName,
                    stats.drawCalls,
                    stats.totalElements
            );
        }
    }
    public static void logPlayerSample(Level level, double x, double z) {
        logPlayerSample(CloudManager.get(level), x, z);
    }

    public static void logPlayerSample(CloudManager<?> manager, double playerX, double playerZ) {
        if (manager == null) {
            return;
        }

        var sample = manager.getCloudTypeAtPosition((float)playerX, (float)playerZ);
        CloudType type = sample.getLeft();
        float fade = sample.getRight();
        float coverage = 1.0F - fade;
        NoiseSettings noise = type != null ? type.noiseConfig() : null;
        float[] packedNoise = noise != null ? noise.packForShader() : new float[0];
        if (ENABLED || PLAYER_SAMPLE_LOGGED.compareAndSet(false, true)) {
            LOGGER.info(
                    "[SimpleCloudsRender] playerSample world=({}, {}) cloudHeight={} cloudMode={} cloudCount={} selectedType={} coverage={} fade={} weather={} stormStart={} noiseStartHeight={} noiseEndHeight={} noisePacked={}",
                    fmt(playerX),
                    fmt(playerZ),
                    manager.getCloudHeight(),
                    manager.getCloudMode(),
                    manager.getClouds().size(),
                    type != null ? type.id() : "null",
                    fmt(coverage),
                    fmt(fade),
                    type != null ? type.weatherType() : "null",
                    type != null ? fmt(type.stormStart()) : "null",
                    noise != null ? noise.getStartHeight() : -1,
                    noise != null ? noise.getEndHeight() : -1,
                    Arrays.toString(packedNoise)
            );
        }
    }

    public static void logShaderLoad(String stage, ResourceLocation requested, ResourceLocation actual, String shaderName, int shaderId, boolean valid) {
        if (ENABLED || SHADER_LOAD_LOGGED.compareAndSet(false, true)) {
            LOGGER.info(
                    "[SimpleCloudsRender] shaderLoad stage={} requested={} actual={} shaderName={} shaderId={} valid={}",
                    stage,
                    requested,
                    actual,
                    shaderName,
                    shaderId,
                    valid
            );
        }
    }

    public static void logRegionUpload(int cloudRegions, int filteredRegions, int uploadedRegions, int cachedTypes, int shaderId, String shaderName) {
        if (ENABLED || REGION_UPLOAD_LOGGED.compareAndSet(false, true)) {
            LOGGER.info(
                    "[SimpleCloudsRender] regionUpload cloudRegions={} filteredRegions={} uploadedRegions={} cachedTypes={} shaderId={} shaderName={}",
                    cloudRegions,
                    filteredRegions,
                    uploadedRegions,
                    cachedTypes,
                    shaderId,
                    shaderName
            );
        }
    }

    public static void logChunkGenDecision(float minX, float minZ, float maxX, float maxZ, List<String> cornerSamples, String resultDescription) {
        if (ENABLED || CHUNK_DECISION_LOGGED.compareAndSet(false, true)) {
            LOGGER.info(
                    "[SimpleCloudsRender] chunkGenDecision bounds=({}, {}) -> ({}, {}) samples={} result={}",
                    fmt(minX),
                    fmt(minZ),
                    fmt(maxX),
                    fmt(maxZ),
                    cornerSamples,
                    resultDescription
            );
        }
    }

    public static void logPrepareMeshGen(int queuedTasks, int chunkCount, int tasksPerTick, int meshGenInterval, double originX, double originY, double originZ, float meshOffsetX, float meshOffsetZ, boolean frustumCulled) {
        if (ENABLED || PREPARE_MESH_LOGGED.compareAndSet(false, true)) {
            LOGGER.info(
                    "[SimpleCloudsRender] prepareMeshGen queuedTasks={} chunkCount={} tasksPerTick={} meshGenInterval={} origin=({}, {}, {}) meshOffset=({}, {}) frustumCulled={}",
                    queuedTasks,
                    chunkCount,
                    tasksPerTick,
                    meshGenInterval,
                    fmt(originX),
                    fmt(originY),
                    fmt(originZ),
                    fmt(meshOffsetX),
                    fmt(meshOffsetZ),
                    frustumCulled
            );
        }
    }

    public static void logPipelineStage(String pipelineName, String stage, int chunkCount, int queuedTasks, int completedTasks, boolean canRender, boolean transparencyEnabled, Object meshStatus) {
        if (ENABLED || PIPELINE_STAGE_LOGGED.compareAndSet(false, true)) {
            LOGGER.info(
                    "[SimpleCloudsRender] pipeline stage={} pipeline={} chunks={} queuedTasks={} completedTasks={} canRender={} transparencyEnabled={} meshStatus={}",
                    stage,
                    pipelineName,
                    chunkCount,
                    queuedTasks,
                    completedTasks,
                    canRender,
                    transparencyEnabled,
                    meshStatus
            );
        }
    }

    public static void logDhPipelineFallback(String selectedPipeline, int chunkCount, int queuedTasks, int completedTasks, boolean canRender, boolean transparencyEnabled, Object meshStatus) {
        if (ENABLED || DH_PIPELINE_FALLBACK_LOGGED.compareAndSet(false, true)) {
            LOGGER.info(
                    "[SimpleCloudsRender] dhPipelineFallback selectedPipeline={} chunks={} queuedTasks={} completedTasks={} canRender={} transparencyEnabled={} meshStatus={}",
                    selectedPipeline,
                    chunkCount,
                    queuedTasks,
                    completedTasks,
                    canRender,
                    transparencyEnabled,
                    meshStatus
            );
        }
    }

    public static void logFinalizeMeshGen(int completedTasks, int opaqueElements, int transparentElements, int opaqueBytes, int transparentBytes, Object meshStatus) {
        if (ENABLED || FINALIZE_MESH_LOGGED.compareAndSet(false, true)) {
            LOGGER.info(
                    "[SimpleCloudsRender] finalizeMeshGen completedTasks={} opaqueElements={} transparentElements={} opaqueBytes={} transparentBytes={} meshStatus={}",
                    completedTasks,
                    opaqueElements,
                    transparentElements,
                    opaqueBytes,
                    transparentBytes,
                    meshStatus
            );
        }
    }

    public static void endPass() {
        if (Boolean.TRUE.equals(DH_PIPELINE_ACTIVE.get())) {
            return;
        }
        PassStats stats = CURRENT_PASS.get();
        if (ENABLED || PASS_SUMMARY_LOGGED.compareAndSet(false, true)) {
            LOGGER.info(
                    "[SimpleCloudsRender] pass={} totalChunks={} drawCalls={} totalElements={} alphaFallbacks={} opaqueBytes={} transparentBytes={} opaqueElements={} transparentElements={} canRender={} transparencyEnabled={} meshStatus={}",
                    stats.passName,
                    stats.totalChunks,
                    stats.drawCalls,
                    stats.totalElements,
                    stats.alphaFallbacks,
                    stats.opaqueBytes,
                    stats.transparentBytes,
                    stats.opaqueElements,
                    stats.transparentElements,
                    stats.canRender,
                    stats.transparencyEnabled,
                    stats.meshStatus
            );
        }
    }

    public static void endDhPipelinePass() {
        PassStats stats = CURRENT_PASS.get();
        if (ENABLED || DH_PASS_SUMMARY_LOGGED.compareAndSet(false, true)) {
            LOGGER.info(
                    "[SimpleCloudsRender] dhPass totalChunks={} drawCalls={} totalElements={} alphaFallbacks={} opaqueBytes={} transparentBytes={} opaqueElements={} transparentElements={} canRender={} transparencyEnabled={} meshStatus={}",
                    stats.totalChunks,
                    stats.drawCalls,
                    stats.totalElements,
                    stats.alphaFallbacks,
                    stats.opaqueBytes,
                    stats.transparentBytes,
                    stats.opaqueElements,
                    stats.transparentElements,
                    stats.canRender,
                    stats.transparencyEnabled,
                    stats.meshStatus
            );
        }
        DH_PIPELINE_ACTIVE.set(false);
    }

    public static boolean isDhPipelineActive() {
        return Boolean.TRUE.equals(DH_PIPELINE_ACTIVE.get());
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static final class PassStats {
        private String passName = "unknown";
        private int totalChunks;
        private int opaqueBytes;
        private int transparentBytes;
        private int opaqueElements;
        private int transparentElements;
        private boolean canRender;
        private boolean transparencyEnabled;
        private Object meshStatus;
        private int drawCalls;
        private int totalElements;
        private int alphaFallbacks;
    }
}
