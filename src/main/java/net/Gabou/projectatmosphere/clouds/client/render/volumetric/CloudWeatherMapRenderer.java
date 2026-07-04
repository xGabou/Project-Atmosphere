package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.client.render.shader.VolumetricCloudShaders;
import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderStateGuard;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL20;

import java.util.List;

/**
 * Rasterizes the interpolated cloud cells into the camera-following weather
 * map. The map is world-anchored and camera-snapped to its texel grid so
 * cloud silhouettes never swim as the camera moves.
 */
public final class CloudWeatherMapRenderer {
    public static final int MAX_CELLS = 96;
    public static final float WEATHER_EXTENT = 4096.0F;

    private static final float[] posRadiusArray = new float[MAX_CELLS * 4];
    private static final float[] shapeArray = new float[MAX_CELLS * 4];
    private static final float[] mediaArray = new float[MAX_CELLS * 4];

    private static double lastOriginX;
    private static double lastOriginZ;

    private CloudWeatherMapRenderer() {
    }

    public record Result(
            boolean rendered,
            double originX,
            double originZ,
            float slabBaseY,
            float slabTopY,
            int cellCount
    ) {
        public static final Result EMPTY = new Result(false, 0.0D, 0.0D, 120.0F, 320.0F, 0);
    }

    public static double lastOriginX() {
        return lastOriginX;
    }

    public static double lastOriginZ() {
        return lastOriginZ;
    }

    /**
     * Renders the weather map for this frame.
     *
     * @param cells interpolated render cells (already camera-filtered)
     * @param cameraX camera world X
     * @param cameraZ camera world Z
     * @param regionalCoverage 0..1 stratus/overcast layer amount
     * @param regionalEnergy 0..1 regional storminess
     * @param worldTime world time (ticks with partial)
     * @param mapSize weather map resolution from the quality profile
     * @return per-frame weather map metadata for downstream passes
     */
    public static Result render(
            List<VolumetricRenderCell> cells,
            double cameraX,
            double cameraZ,
            float regionalCoverage,
            float regionalEnergy,
            float worldTime,
            int mapSize
    ) {
        ShaderInstance shader = VolumetricCloudShaders.splatShader();
        if (shader == null) {
            return Result.EMPTY;
        }
        RenderTarget target = VolumetricCloudRenderTargets.prepareWeatherTarget(mapSize);
        if (target == null) {
            return Result.EMPTY;
        }

        // Camera-snapped origin: the map scrolls in whole texels only.
        float texelSize = WEATHER_EXTENT / mapSize;
        double originX = Math.floor((cameraX - WEATHER_EXTENT * 0.5D) / texelSize) * texelSize;
        double originZ = Math.floor((cameraZ - WEATHER_EXTENT * 0.5D) / texelSize) * texelSize;
        lastOriginX = originX;
        lastOriginZ = originZ;

        // Slab bounds: envelope of all cell layers plus the regional sheet.
        float slabBase = Float.MAX_VALUE;
        float slabTop = -Float.MAX_VALUE;
        int count = 0;
        for (VolumetricRenderCell cell : cells) {
            if (cell == null || count >= MAX_CELLS) {
                break;
            }
            slabBase = Math.min(slabBase, cell.baseY());
            slabTop = Math.max(slabTop, cell.topY());
            count++;
        }
        if (regionalCoverage > 0.01F || count == 0) {
            slabBase = Math.min(slabBase, 150.0F);
            slabTop = Math.max(slabTop, 260.0F);
        }
        slabBase -= 6.0F;
        slabTop += 12.0F;
        float slabSpan = Math.max(slabTop - slabBase, 8.0F);

        count = 0;
        for (VolumetricRenderCell cell : cells) {
            if (cell == null || count >= MAX_CELLS) {
                break;
            }
            int base = count * 4;
            posRadiusArray[base] = (float) cell.x();
            posRadiusArray[base + 1] = (float) cell.z();
            posRadiusArray[base + 2] = cell.radiusMajor();
            posRadiusArray[base + 3] = cell.radiusMinor();
            shapeArray[base] = cell.orientationRadians();
            shapeArray[base + 1] = Mth.clamp((cell.baseY() - slabBase) / slabSpan, 0.0F, 1.0F);
            shapeArray[base + 2] = Mth.clamp((cell.topY() - slabBase) / slabSpan, 0.0F, 1.0F);
            shapeArray[base + 3] = cell.edgeSoftness();
            mediaArray[base] = cell.density();
            mediaArray[base + 1] = cell.energy();
            mediaArray[base + 2] = cell.seed01();
            mediaArray[base + 3] = 0.0F;
            count++;
        }

        VolumetricCloudRenderTargets.clearAndBind(target);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);

        shader.safeGetUniform("WeatherOrigin").set((float) originX, (float) originZ);
        shader.safeGetUniform("WeatherExtent").set(WEATHER_EXTENT);
        shader.safeGetUniform("SlabBaseY").set(slabBase);
        shader.safeGetUniform("SlabTopY").set(slabTop);
        shader.safeGetUniform("RegionalCoverage").set(Mth.clamp(regionalCoverage, 0.0F, 1.0F));
        shader.safeGetUniform("RegionalEnergy").set(Mth.clamp(regionalEnergy, 0.0F, 1.0F));
        shader.safeGetUniform("WorldTime").set(worldTime);
        shader.safeGetUniform("CellCount").set(count);
        shader.apply();
        uploadCellArrays(shader, count);
        try {
            FullscreenQuad.draw(shader);
        } finally {
            shader.clear();
            CloudRenderStateGuard.restoreAfterCloudPass();
        }
        return new Result(true, originX, originZ, slabBase, slabTop, count);
    }

    /**
     * Uploads the per-cell arrays directly; the vanilla uniform system only
     * supports vec4-sized uniforms, so array uniforms go through raw GL.
     */
    private static void uploadCellArrays(ShaderInstance shader, int count) {
        int program = shader.getId();
        int safeCount = Math.max(1, Math.min(count, MAX_CELLS));
        uploadVec4Array(program, "CellPosRadius", posRadiusArray, safeCount);
        uploadVec4Array(program, "CellShape", shapeArray, safeCount);
        uploadVec4Array(program, "CellMedia", mediaArray, safeCount);
    }

    private static void uploadVec4Array(int program, String name, float[] values, int vec4Count) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location < 0) {
            location = GL20.glGetUniformLocation(program, name + "[0]");
        }
        if (location < 0) {
            return;
        }
        // Upload only the used prefix of the array.
        float[] slice = new float[vec4Count * 4];
        System.arraycopy(values, 0, slice, 0, slice.length);
        GL20.glUniform4fv(location, slice);
    }
}
