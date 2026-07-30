package net.Gabou.projectatmosphere.clouds.analytics;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.cell.CloudCell;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.CloudWeatherMapRenderer;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL43;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * GL 4.3 compute pass that measures per-cell shape metrics against the
 * rendered weather map: integrated coverage (mass proxy), actual footprint vs
 * nominal radius, centroid drift, column top, best-neighbor overlap, and a
 * split score (density gap along the major axis).
 *
 * Readback is strictly fence-gated: the result buffer is only mapped after
 * the fence has signaled, several frames later. No stall, ever - a lesson
 * from the removed CPU-upload shadow path (documented NVidia crash history).
 * Analytics are advisory; any failure silently degrades to the CPU analytic
 * fallback in the server simulation.
 */
public final class CloudCellAnalyticsPass {
    private static final int MAX_CELLS = CloudWeatherMapRenderer.MAX_CELLS;
    private static final int FLOATS_PER_CELL = 8;
    private static final long RUN_INTERVAL_TICKS = 15L;

    private static boolean initAttempted;
    private static boolean supported;
    private static int programId = -1;
    private static int resultBufferId = -1;

    private static long pendingFence;
    private static List<UUID> pendingCellIds = List.of();
    private static long lastRunGameTime = Long.MIN_VALUE;
    private static volatile List<CloudCellAnalyticsReport> latestReports = List.of();
    private static volatile String statusText = "not_initialized";
    private static Consumer<List<CloudCellAnalyticsReport>> reportSink;

    private CloudCellAnalyticsPass() {
    }

    public static String status() {
        return statusText;
    }

    public static List<CloudCellAnalyticsReport> latestReports() {
        return latestReports;
    }

    /** Sink invoked on the render thread whenever new reports are read back. */
    public static void setReportSink(Consumer<List<CloudCellAnalyticsReport>> sink) {
        reportSink = sink;
    }

    /**
     * Called once per frame while analytics are enabled. Dispatches a new
     * measurement every ~15 ticks and polls the previous fence.
     */
    public static void tick(
            List<CloudCell> cells,
            RenderTarget weatherTarget,
            CloudWeatherMapRenderer.Result weather,
            long gameTime
    ) {
        if (!RenderSystem.isOnRenderThread() || weatherTarget == null || !weather.rendered()) {
            return;
        }
        if (!ensureInitialized()) {
            return;
        }

        pollPendingReadback();

        if (pendingFence != 0L || gameTime - lastRunGameTime < RUN_INTERVAL_TICKS) {
            return;
        }
        if (cells == null || cells.isEmpty()) {
            return;
        }
        lastRunGameTime = gameTime;
        dispatch(cells, weatherTarget, weather);
    }

    public static void shutdown() {
        if (programId > 0) {
            GL20.glDeleteProgram(programId);
            programId = -1;
        }
        if (resultBufferId > 0) {
            GL15.glDeleteBuffers(resultBufferId);
            resultBufferId = -1;
        }
        if (pendingFence != 0L) {
            GL32.glDeleteSync(pendingFence);
            pendingFence = 0L;
        }
        initAttempted = false;
        supported = false;
    }

    // -----------------------------------------------------------------

    private static boolean ensureInitialized() {
        if (initAttempted) {
            return supported;
        }
        initAttempted = true;
        try {
            if (!GL.getCapabilities().OpenGL43) {
                statusText = "unsupported_no_gl43";
                return false;
            }
            int shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
            GL20.glShaderSource(shader, COMPUTE_SOURCE);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                String log = GL20.glGetShaderInfoLog(shader, 4096);
                GL20.glDeleteShader(shader);
                statusText = "compile_failed";
                ProjectAtmosphere.LOGGER.warn("[CloudAnalytics] compute compile failed: {}", log);
                return false;
            }
            programId = GL20.glCreateProgram();
            GL20.glAttachShader(programId, shader);
            GL20.glLinkProgram(programId);
            GL20.glDeleteShader(shader);
            if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                String log = GL20.glGetProgramInfoLog(programId, 4096);
                GL20.glDeleteProgram(programId);
                programId = -1;
                statusText = "link_failed";
                ProjectAtmosphere.LOGGER.warn("[CloudAnalytics] compute link failed: {}", log);
                return false;
            }

            resultBufferId = GL15.glGenBuffers();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, resultBufferId);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,
                    (long) MAX_CELLS * FLOATS_PER_CELL * Float.BYTES, GL15.GL_DYNAMIC_READ);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);

            supported = true;
            statusText = "ready";
            ProjectAtmosphere.LOGGER.info("[CloudAnalytics] GL4.3 compute analytics initialized");
            return true;
        } catch (Throwable throwable) {
            statusText = "init_exception";
            supported = false;
            ProjectAtmosphere.LOGGER.warn("[CloudAnalytics] init failed; using CPU fallback", throwable);
            return false;
        }
    }

    private static void dispatch(List<CloudCell> cells, RenderTarget weatherTarget, CloudWeatherMapRenderer.Result weather) {
        try {
            int count = Math.min(cells.size(), MAX_CELLS);
            float[] cellData = new float[MAX_CELLS * 8];
            List<UUID> ids = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                CloudCell cell = cells.get(i);
                ids.add(cell.id());
                int base = i * 8;
                cellData[base] = (float) cell.x();
                cellData[base + 1] = (float) cell.z();
                cellData[base + 2] = cell.radiusMajor();
                cellData[base + 3] = cell.radiusMinor();
                cellData[base + 4] = cell.orientationRadians();
                cellData[base + 5] = 0.0F;
                cellData[base + 6] = 0.0F;
                cellData[base + 7] = 0.0F;
            }

            int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            GL20.glUseProgram(programId);

            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, weatherTarget.getColorTextureId());
            GL20.glUniform1i(GL20.glGetUniformLocation(programId, "WeatherMap"), 0);
            GL20.glUniform2f(GL20.glGetUniformLocation(programId, "WeatherOrigin"),
                    (float) weather.originX(), (float) weather.originZ());
            GL20.glUniform1f(GL20.glGetUniformLocation(programId, "WeatherExtent"),
                    CloudWeatherMapRenderer.WEATHER_EXTENT);
            GL20.glUniform1i(GL20.glGetUniformLocation(programId, "CellCount"), count);
            int cellsLocation = GL20.glGetUniformLocation(programId, "Cells");
            if (cellsLocation < 0) {
                cellsLocation = GL20.glGetUniformLocation(programId, "Cells[0]");
            }
            if (cellsLocation >= 0) {
                // Two vec4 per cell packed sequentially.
                GL20.glUniform4fv(cellsLocation, cellData);
            }

            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, resultBufferId);
            GL43.glDispatchCompute(count, 1, 1);
            GL43.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);

            pendingFence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            pendingCellIds = ids;

            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL20.glUseProgram(previousProgram);
        } catch (Throwable throwable) {
            supported = false;
            statusText = "dispatch_exception";
            ProjectAtmosphere.LOGGER.warn("[CloudAnalytics] dispatch failed; disabling analytics", throwable);
        }
    }

    private static void pollPendingReadback() {
        if (pendingFence == 0L) {
            return;
        }
        try {
            int signaled = GL32.glClientWaitSync(pendingFence, 0, 0L);
            if (signaled != GL32.GL_ALREADY_SIGNALED && signaled != GL32.GL_CONDITION_SATISFIED) {
                return; // still in flight; poll again next frame
            }
            GL32.glDeleteSync(pendingFence);
            pendingFence = 0L;

            int count = pendingCellIds.size();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, resultBufferId);
            ByteBuffer mapped = GL30.glMapBufferRange(
                    GL43.GL_SHADER_STORAGE_BUFFER,
                    0,
                    (long) count * FLOATS_PER_CELL * Float.BYTES,
                    GL30.GL_MAP_READ_BIT
            );
            if (mapped == null) {
                GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
                return;
            }
            FloatBuffer data = mapped.asFloatBuffer();
            List<CloudCellAnalyticsReport> reports = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int base = i * FLOATS_PER_CELL;
                float integrated = data.get(base);
                float footprintRatio = data.get(base + 1);
                float centroidX = data.get(base + 2);
                float centroidZ = data.get(base + 3);
                float maxTop01 = data.get(base + 4);
                int peerIndex = Math.round(data.get(base + 5));
                float overlapScore = data.get(base + 6);
                float splitScore = data.get(base + 7);
                UUID peer = peerIndex >= 0 && peerIndex < count && peerIndex != i
                        ? pendingCellIds.get(peerIndex)
                        : null;
                reports.add(new CloudCellAnalyticsReport(
                        pendingCellIds.get(i),
                        integrated,
                        footprintRatio,
                        centroidX,
                        centroidZ,
                        maxTop01,
                        peer,
                        overlapScore,
                        splitScore
                ));
            }
            GL15.glUnmapBuffer(GL43.GL_SHADER_STORAGE_BUFFER);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);

            latestReports = reports;
            statusText = "ready reports=" + reports.size();
            Consumer<List<CloudCellAnalyticsReport>> sink = reportSink;
            if (sink != null) {
                sink.accept(reports);
            }
        } catch (Throwable throwable) {
            supported = false;
            statusText = "readback_exception";
            if (pendingFence != 0L) {
                GL32.glDeleteSync(pendingFence);
                pendingFence = 0L;
            }
            ProjectAtmosphere.LOGGER.warn("[CloudAnalytics] readback failed; disabling analytics", throwable);
        }
    }

    // Cells uniform packs two vec4 per cell: [x, z, radiusMajor, radiusMinor],
    // [orientation, 0, 0, 0].
    private static final String COMPUTE_SOURCE = """
            #version 430
            layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;

            uniform sampler2D WeatherMap;
            uniform vec2 WeatherOrigin;
            uniform float WeatherExtent;
            uniform int CellCount;
            uniform vec4 Cells[192]; // 2 vec4 per cell, up to 96 cells

            layout(std430, binding = 0) buffer Results {
                float results[];
            };

            vec4 cellPos(int i) { return Cells[i * 2]; }
            vec4 cellShape(int i) { return Cells[i * 2 + 1]; }

            float coverageAt(vec2 worldXZ) {
                vec2 uv = (worldXZ - WeatherOrigin) / WeatherExtent;
                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                    return 0.0;
                }
                return textureLod(WeatherMap, uv, 0.0).r;
            }

            float topAt(vec2 worldXZ) {
                vec2 uv = clamp((worldXZ - WeatherOrigin) / WeatherExtent, vec2(0.0), vec2(1.0));
                return textureLod(WeatherMap, uv, 0.0).b;
            }

            void main() {
                int cellIndex = int(gl_WorkGroupID.x);
                if (cellIndex >= CellCount) {
                    return;
                }
                vec4 pos = cellPos(cellIndex);
                vec4 shape = cellShape(cellIndex);
                vec2 center = pos.xy;
                float radius = max(pos.z, pos.w);
                float sampleExtent = radius * 1.6;

                // 24x24 grid over the cell bounding box.
                float integrated = 0.0;
                float covered = 0.0;
                vec2 centroidSum = vec2(0.0);
                float maxTop = 0.0;
                const int N = 24;
                for (int gz = 0; gz < N; gz++) {
                    for (int gx = 0; gx < N; gx++) {
                        vec2 offset = (vec2(float(gx), float(gz)) + 0.5) / float(N) * 2.0 - 1.0;
                        vec2 p = center + offset * sampleExtent;
                        float c = coverageAt(p);
                        integrated += c;
                        if (c > 0.10) {
                            covered += 1.0;
                            centroidSum += p;
                            maxTop = max(maxTop, topAt(p));
                        }
                    }
                }
                float total = float(N * N);
                integrated /= total;
                float texArea = (2.0 * sampleExtent) * (2.0 * sampleExtent) / total;
                float actualArea = covered * texArea;
                float nominalArea = 3.14159265 * pos.z * pos.w;
                float footprintRatio = nominalArea > 1.0 ? actualArea / nominalArea : 0.0;
                vec2 centroidOffset = covered > 0.5 ? centroidSum / covered - center : vec2(0.0);

                // Best-neighbor overlap: bridge coverage at the midpoint,
                // weighted by proximity of footprints.
                float bestOverlap = 0.0;
                int bestPeer = -1;
                for (int j = 0; j < CellCount; j++) {
                    if (j == cellIndex) {
                        continue;
                    }
                    vec4 other = cellPos(j);
                    float distance = length(other.xy - center);
                    float combined = radius + max(other.z, other.w);
                    if (distance > combined * 1.3) {
                        continue;
                    }
                    vec2 mid = (center + other.xy) * 0.5;
                    float bridge = coverageAt(mid);
                    float proximity = 1.0 - clamp(distance / max(combined, 1.0), 0.0, 1.0);
                    float overlap = bridge * (0.4 + 0.6 * proximity);
                    if (overlap > bestOverlap) {
                        bestOverlap = overlap;
                        bestPeer = j;
                    }
                }

                // Split score: a coverage gap in the middle of the major axis
                // while both ends stay covered suggests two lobes.
                vec2 axis = vec2(cos(shape.x), sin(shape.x));
                float endA = 0.0;
                float endB = 0.0;
                float middle = 1.0;
                for (int s = 0; s < 5; s++) {
                    float t = (float(s) + 0.5) / 5.0;
                    endA = max(endA, coverageAt(center - axis * pos.z * (0.4 + 0.5 * t)));
                    endB = max(endB, coverageAt(center + axis * pos.z * (0.4 + 0.5 * t)));
                    middle = min(middle, coverageAt(center + axis * pos.z * (t - 0.5) * 0.5));
                }
                float splitScore = clamp(min(endA, endB) - middle, 0.0, 1.0);

                int base = cellIndex * 8;
                results[base] = integrated;
                results[base + 1] = footprintRatio;
                results[base + 2] = centroidOffset.x;
                results[base + 3] = centroidOffset.y;
                results[base + 4] = maxTop;
                results[base + 5] = float(bestPeer);
                results[base + 6] = bestOverlap;
                results[base + 7] = splitScore;
            }
            """;
}
