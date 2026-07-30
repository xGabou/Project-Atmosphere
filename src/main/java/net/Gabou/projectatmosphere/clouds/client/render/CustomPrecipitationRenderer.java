package net.Gabou.projectatmosphere.clouds.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.Gabou.projectatmosphere.clouds.AtmosphereCloudPolicy;
import net.Gabou.projectatmosphere.clouds.client.ClientLocalizedWeatherState;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.modules.weather.PrecipitationTier;
import net.Gabou.projectatmosphere.modules.weather.SnowTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

public final class CustomPrecipitationRenderer {
    private static final ResourceLocation RAIN_LOCATION = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/environment/rain.png");
    private static final ResourceLocation SNOW_LOCATION = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/environment/snow.png");
    private static volatile PrecipitationVisualState lastState = PrecipitationVisualState.NONE;

    private CustomPrecipitationRenderer() {
    }

    public static boolean renderSnowAndRain(
            @Nullable ClientLevel level,
            LightTexture lightTexture,
            float partialTick,
            double camX,
            double camY,
            double camZ
    ) {
        lastState = resolveState(level, partialTick);
        if (level == null || !lastState.hasCustomVisualWeather()) {
            return false;
        }

        renderLocalPrecipitation(level, lightTexture, lastState, partialTick, camX, camY, camZ);
        return true;
    }

    public static PrecipitationVisualState getLastState() {
        return lastState;
    }

    public static PrecipitationVisualState resolveState(@Nullable ClientLevel level, float partialTick) {
        if (level == null || !isEnabled() || !AtmosphereCloudPolicy.shouldOwnWeather(level)) {
            return PrecipitationVisualState.NONE;
        }

        ClientLocalizedWeatherState.Diagnostics diagnostics = ClientLocalizedWeatherState.getDiagnostics();
        float rainIntensity = Mth.clamp(ClientLocalizedWeatherState.getRainLevel(level, partialTick), 0.0F, 1.0F);
        float thunderIntensity = Mth.clamp(ClientLocalizedWeatherState.getThunderLevel(level, partialTick), 0.0F, 1.0F);
        PrecipitationTier rainTier = PrecipitationTier.fromRainIntensity(rainIntensity + thunderIntensity * 0.18F);
        BlockPos samplePos = diagnostics.samplePos();
        SnowTier snowTier = diagnostics.sample().snowing()
                ? SnowTier.resolve(-2.0F, Math.max(0.35F, rainIntensity), resolveWindProxy(rainIntensity, thunderIntensity), rainIntensity)
                : SnowTier.NONE;
        float windProxy = resolveWindProxy(rainIntensity, thunderIntensity);
        float slant = Mth.clamp(windProxy / 24.0F, 0.0F, 1.0F);
        float fogBoost = Math.max(rainTier.getFogBoost(), snowTier.getWhiteoutStrength() * 0.65F);
        float splashIntensity = snowTier == SnowTier.NONE ? rainTier.getSplashIntensity() : 0.0F;

        return new PrecipitationVisualState(
                snowTier == SnowTier.NONE ? rainTier : PrecipitationTier.NONE,
                snowTier,
                rainIntensity,
                thunderIntensity,
                slant * 0.55F,
                slant * 0.22F,
                fogBoost,
                splashIntensity,
                samplePos
        );
    }

    private static boolean isEnabled() {
        try {
            return AtmoCommonConfig.ENABLE_CUSTOM_PRECIPITATION_RENDERING.get();
        } catch (IllegalStateException exception) {
            return true;
        }
    }

    private static float resolveWindProxy(float rainIntensity, float thunderIntensity) {
        return 4.0F + rainIntensity * 8.0F + thunderIntensity * 12.0F;
    }

    private static void renderLocalPrecipitation(
            ClientLevel level,
            LightTexture lightTexture,
            PrecipitationVisualState state,
            float partialTick,
            double camX,
            double camY,
            double camZ
    ) {
        float intensity = Mth.clamp(Math.max(state.rainIntensity(), state.snowTier().getWhiteoutStrength()), 0.0F, 1.0F);
        if (intensity <= 0.001F) {
            return;
        }

        lightTexture.turnOnLightLayer();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = null;
        int cameraX = Mth.floor(camX);
        int cameraY = Mth.floor(camY);
        int cameraZ = Mth.floor(camZ);
        int radius = Minecraft.useFancyGraphics() ? 10 : 5;
        int activeLayer = -1;
        float ticks = (float) level.getGameTime() + partialTick;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        RenderSystem.setShader(GameRenderer::getParticleShader);

        try {
            for (int z = cameraZ - radius; z <= cameraZ + radius; z++) {
                for (int x = cameraX - radius; x <= cameraX + radius; x++) {
                    double relX = (double) x + 0.5D - camX;
                    double relZ = (double) z + 0.5D - camZ;
                    float distance = (float) Math.sqrt(relX * relX + relZ * relZ);
                    if (distance > radius) {
                        continue;
                    }

                    int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    int minY = Math.max(cameraY - radius, groundY);
                    int maxY = Math.max(cameraY + radius, groundY);
                    if (minY == maxY) {
                        continue;
                    }

                    boolean snow = state.snowTier() != SnowTier.NONE;
                    int layer = snow ? 1 : 0;
                    if (activeLayer != layer) {
                        if (activeLayer >= 0) {
                            BufferUploader.drawWithShader(buffer.buildOrThrow());
                        }
                        activeLayer = layer;
                        RenderSystem.setShaderTexture(0, snow ? SNOW_LOCATION : RAIN_LOCATION);
                        buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                    }

                    double normalScale = distance > 0.001F ? 0.5D / distance : 0.0D;
                    double quadX = -relZ * normalScale;
                    double quadZ = relX * normalScale;
                    float distanceFade = ((1.0F - (distance / (float) radius) * (distance / (float) radius)) * (snow ? 0.30F : 0.50F) + 0.50F);
                    float alpha = Mth.clamp(distanceFade * intensity, 0.0F, 1.0F);
                    mutablePos.set(x, Math.max(minY, groundY), z);
                    int light = LevelRenderer.getLightColor(level, mutablePos);
                    RandomSource random = RandomSource.create((long) (x * x * 3121 + x * 45238971 ^ z * z * 418711 + z * 13761));

                    if (snow) {
                        addSnowColumn(buffer, random, ticks, partialTick, x, z, minY, maxY, camX, camY, camZ, quadX, quadZ, alpha, light);
                    } else {
                        addRainColumn(buffer, level.getGameTime(), random, partialTick, x, z, minY, maxY, camX, camY, camZ, quadX, quadZ, alpha, light, state);
                    }
                }
            }

            if (activeLayer >= 0) {
                BufferUploader.drawWithShader(buffer.buildOrThrow());
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            lightTexture.turnOffLightLayer();
        }
    }

    private static void addRainColumn(
            BufferBuilder buffer,
            long gameTime,
            RandomSource random,
            float partialTick,
            int x,
            int z,
            int minY,
            int maxY,
            double camX,
            double camY,
            double camZ,
            double quadX,
            double quadZ,
            float alpha,
            int light,
            PrecipitationVisualState state
    ) {
        int offset = (int) (gameTime + x * x * 3121L + x * 45238971L + z * z * 418711L + z * 13761L) & 31;
        float vOffset = -((float) offset + partialTick) / 32.0F * (3.0F + random.nextFloat());
        double slantX = state.windSlantX() * 0.08D;
        double slantZ = state.windSlantZ() * 0.08D;
        addColumnQuad(buffer, x, z, minY, maxY, camX, camY, camZ, quadX, quadZ, slantX, slantZ, vOffset, 0.25F, alpha, light);
    }

    private static void addSnowColumn(
            BufferBuilder buffer,
            RandomSource random,
            float ticks,
            float partialTick,
            int x,
            int z,
            int minY,
            int maxY,
            double camX,
            double camY,
            double camZ,
            double quadX,
            double quadZ,
            float alpha,
            int light
    ) {
        float vOffset = -((ticks % 512.0F) + partialTick) / 512.0F;
        float uOffset = (float) (random.nextDouble() + (double) ticks * 0.01D * random.nextGaussian());
        float vDrift = (float) (random.nextDouble() + (double) (ticks * random.nextGaussian()) * 0.001D);
        addColumnQuad(buffer, x, z, minY, maxY, camX, camY, camZ, quadX, quadZ, 0.0D, 0.0D, vOffset + vDrift, 0.25F, Mth.clamp(alpha * 0.86F, 0.0F, 1.0F), light, uOffset);
    }

    private static void addColumnQuad(
            BufferBuilder buffer,
            int x,
            int z,
            int minY,
            int maxY,
            double camX,
            double camY,
            double camZ,
            double quadX,
            double quadZ,
            double slantX,
            double slantZ,
            float vOffset,
            float vScale,
            float alpha,
            int light
    ) {
        addColumnQuad(buffer, x, z, minY, maxY, camX, camY, camZ, quadX, quadZ, slantX, slantZ, vOffset, vScale, alpha, light, 0.0F);
    }

    private static void addColumnQuad(
            BufferBuilder buffer,
            int x,
            int z,
            int minY,
            int maxY,
            double camX,
            double camY,
            double camZ,
            double quadX,
            double quadZ,
            double slantX,
            double slantZ,
            float vOffset,
            float vScale,
            float alpha,
            int light,
            float uOffset
    ) {
        double baseX = (double) x + 0.5D - camX;
        double baseZ = (double) z + 0.5D - camZ;
        double topX = baseX + slantX;
        double topZ = baseZ + slantZ;
        float vMin = (float) minY * vScale + vOffset;
        float vMax = (float) maxY * vScale + vOffset;

        buffer.addVertex((float) (topX - quadX), (float) (maxY - camY), (float) (topZ - quadZ)).setUv(uOffset, vMin).setColor(1.0F, 1.0F, 1.0F, alpha).setLight(light);
        buffer.addVertex((float) (topX + quadX), (float) (maxY - camY), (float) (topZ + quadZ)).setUv(1.0F + uOffset, vMin).setColor(1.0F, 1.0F, 1.0F, alpha).setLight(light);
        buffer.addVertex((float) (baseX + quadX), (float) (minY - camY), (float) (baseZ + quadZ)).setUv(1.0F + uOffset, vMax).setColor(1.0F, 1.0F, 1.0F, alpha).setLight(light);
        buffer.addVertex((float) (baseX - quadX), (float) (minY - camY), (float) (baseZ - quadZ)).setUv(uOffset, vMax).setColor(1.0F, 1.0F, 1.0F, alpha).setLight(light);
    }
}
