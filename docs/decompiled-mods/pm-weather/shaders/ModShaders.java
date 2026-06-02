//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package dev.protomanly.pmweather.shaders;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.compat.DistantHorizons;
import dev.protomanly.pmweather.config.ClientConfig;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.mixin.PostChainMixin;
import dev.protomanly.pmweather.weather.Lightning;
import dev.protomanly.pmweather.weather.Storm;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.WeatherHandler;
import dev.protomanly.pmweather.weather.WeatherHandlerClient;
import dev.protomanly.pmweather.weather.WindEngine;
import dev.protomanly.pmweather.weather.ThermodynamicEngine.Precipitation;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL32;

public class ModShaders {
    private static PostChain clouds;
    private static Vec2 lastScroll;
    public static Vec2 scroll;
    private static int lastWidth;
    private static int lastHeight;
    private static float snow;
    private static float lastSnow;
    private static float gameTime;
    private static boolean dhIntegrationInitialized;
    private static Integer noiseTextureID;

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        WeatherHandler weatherHandler = GameBusClientEvents.weatherHandler;
        if (player != null && weatherHandler != null && Minecraft.getInstance().level != null) {
            Vec3 wind = WindEngine.getWind(new Vec3(player.position().x, (double)minecraft.level.getMaxBuildHeight(), player.position().z), minecraft.level, true, true, false);
            Vec3 wind2 = wind.multiply(0.03, 0.03, 0.03);
            lastScroll = scroll;
            scroll = scroll.add(new Vec2(-((float)wind2.x), -((float)wind2.z)));

            for(Storm storm : weatherHandler.getStorms()) {
                if (storm.lastPosition == null) {
                    storm.lastPosition = storm.position;
                } else {
                    storm.lastPosition = storm.lastPosition.lerp(storm.position, (double)0.05F);
                }

                storm.lastSpin = storm.spin;
                storm.spin += storm.smoothWindspeed * 0.01F / Math.max(storm.smoothWidth, 20.0F);
            }

            ThermodynamicEngine.Precipitation precip = ThermodynamicEngine.getPrecipitationType(weatherHandler, player.position(), minecraft.level, 0);
            lastSnow = snow;
            if (precip != Precipitation.SNOW && precip != Precipitation.WINTRY_MIX) {
                snow = Mth.lerp(0.05F, snow, 0.0F);
            } else {
                float rain = weatherHandler.getPrecipitation(player.position());
                float snowBlindness = (float)Math.clamp(Math.pow(wind.length() / (double)60.0F, (double)2.0F) * (double)rain, (double)0.0F, (double)1.0F);
                snow = Mth.lerp(0.05F, snow, snowBlindness);
            }
        }

    }

    public static void renderShaders(float partialTicks, Camera camera, Matrix4f projMat, Matrix4f modelMat) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        WeatherHandler weatherHandler = GameBusClientEvents.weatherHandler;
        if (clouds != null && player != null && weatherHandler != null && Minecraft.getInstance().level != null && ServerConfig.validDimensions != null && ServerConfig.validDimensions.contains(Minecraft.getInstance().level.dimension())) {
            int width = minecraft.getWindow().getWidth();
            int height = minecraft.getWindow().getHeight();
            if (width != lastWidth || height != lastHeight) {
                lastWidth = width;
                lastHeight = height;
                updateShaderGroupSize(clouds);
            }

            float gameTime = (float)Blaze3D.getTime();
            RenderSystem.enableDepthTest();
            RenderSystem.resetTextureMatrix();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(false);
            PostChain var11 = clouds;
            if (var11 instanceof PostChainMixin) {
                PostChainMixin data = (PostChainMixin)var11;
                List<PostPass> passes = data.getPasses();
                EffectInstance effect = ((PostPass)passes.getFirst()).getEffect();
                effect.safeGetUniform("OutSize").set((float)width, (float)height);
                Vec3 camPos = camera.getPosition();
                effect.safeGetUniform("pos").set((float)camPos.x, (float)camPos.y, (float)camPos.z);
                effect.safeGetUniform("scroll").set(Mth.lerp(partialTicks, lastScroll.x, scroll.x), Mth.lerp(partialTicks, lastScroll.y, scroll.y));
                effect.safeGetUniform("maxSteps").set(800);
                effect.safeGetUniform("stepSize").set(0.01F);
                effect.safeGetUniform("fogStart").set(RenderSystem.getShaderFogStart() * 4.0F);
                effect.safeGetUniform("fogEnd").set(RenderSystem.getShaderFogEnd() * 4.0F);
                effect.safeGetUniform("proj").set(projMat.invert());
                effect.safeGetUniform("viewmat").set(modelMat.invert());
                effect.safeGetUniform("time").set((float)player.tickCount + partialTicks);
                long seed = 0L;
                if (GameBusClientEvents.weatherHandler != null) {
                    seed = GameBusClientEvents.weatherHandler.seed;
                }

                float gameTimeGoal = (float)player.level().getGameTime() + (float)seed / 1.0E14F + partialTicks;
                gameTime = Mth.lerp(0.3F, gameTime, gameTimeGoal);
                if (Math.abs(gameTime - gameTimeGoal) > 30.0F) {
                    gameTime = gameTimeGoal;
                }

                effect.safeGetUniform("worldTime").set(gameTime);
                effect.safeGetUniform("layer0height").set((float)ServerConfig.layer0Height);
                effect.safeGetUniform("layerCheight").set((float)ServerConfig.layerCHeight);
                effect.safeGetUniform("stormSize").set((float)ServerConfig.stormSize * 2.0F);
                float sunAng = Minecraft.getInstance().level.getSunAngle(partialTicks);
                Vec3 sunDir = new Vec3(-Math.sin((double)sunAng), Math.cos((double)sunAng), (double)0.0F);
                effect.safeGetUniform("sunDir").set((float)sunDir.x, (float)sunDir.y, (float)sunDir.z);
                effect.safeGetUniform("lightIntensity").set((float)Math.pow((Math.cos((double)sunAng) + (double)1.0F) / (double)2.0F, (double)3.0F));
                effect.safeGetUniform("downsample").set((float)ClientConfig.volumetricsDownsample);
                ((PostPass)passes.get(1)).getEffect().safeGetUniform("downsample").set((float)ClientConfig.volumetricsDownsample);
                ((PostPass)passes.get(1)).getEffect().safeGetUniform("glowFix").set(ClientConfig.glowFix ? 1.0F : 0.0F);
                ((PostPass)passes.get(1)).getEffect().safeGetUniform("doBlur").set(ClientConfig.volumetricsBlur ? 1.0F : 0.0F);
                effect.safeGetUniform("simpleLighting").set(ClientConfig.simpleLighting ? 0.0F : 1.0F);

                try {
                    if (DistantHorizons.isAvailable()) {
                        int depthTextureId = DistantHorizons.getDepthTextureId();
                        GL32.glActiveTexture(33991);
                        GL32.glBindTexture(3553, depthTextureId);
                        effect.safeGetUniform("dhDepthTex0").set(7);
                        effect.safeGetUniform("hasDHDepth").set(1.0F);
                        Matrix4f dhProj = DistantHorizons.getDhProjectionMatrix();
                        Matrix4f dhProjInv = (new Matrix4f(dhProj)).invert();
                        effect.safeGetUniform("dhProjection").set(dhProj);
                        effect.safeGetUniform("dhProjectionInverse").set(dhProjInv);
                        effect.safeGetUniform("dhViewmat").set(DistantHorizons.getDhModelViewMatrix());
                        effect.safeGetUniform("dhNearPlane").set(DistantHorizons.getNearPlane());
                        effect.safeGetUniform("dhFarPlane").set(DistantHorizons.getFarPlane());
                        effect.safeGetUniform("dhRenderDistance").set((float)DistantHorizons.getChunkRenderDistance() * 16.0F);
                        GL32.glActiveTexture(33984);
                    } else {
                        effect.safeGetUniform("hasDHDepth").set(0.0F);
                    }
                } catch (Exception var39) {
                    PMWeather.LOGGER.debug("DH Uniforms not available");
                    effect.safeGetUniform("hasDHDepth").set(0.0F);
                }

                if (weatherHandler instanceof WeatherHandlerClient) {
                    WeatherHandlerClient whc = (WeatherHandlerClient)weatherHandler;
                    effect.safeGetUniform("rain").set(whc.getPrecipitation());
                    effect.safeGetUniform("snow").set(Mth.lerp(partialTicks, lastSnow, snow));
                }

                List<Storm> storms = weatherHandler.getStorms();
                float[] stormPositions = new float[48];
                float[] stormVelocites = new float[32];
                float[] stormStages = new float[16];
                float[] stormEnergies = new float[16];
                float[] stormTypes = new float[16];
                float[] tornadoWindspeeds = new float[16];
                float[] tornadoWidths = new float[16];
                float[] tornadoTouchdownSpeeds = new float[16];
                float[] visualOnlys = new float[16];
                float[] stormSpins = new float[16];
                float[] stormDyings = new float[16];
                float[] tornadoShapes = new float[16];
                float[] stormOcclusions = new float[16];
                float[] lightningStrikes = new float[192];
                if (GameBusClientEvents.weatherHandler != null) {
                    float[] lightningBrightness = new float[64];
                    List<Lightning> lightnings = ((WeatherHandlerClient)GameBusClientEvents.weatherHandler).lightnings;

                    for(int i = 0; i < lightnings.size(); ++i) {
                        if (i < 64) {
                            Lightning lightning = (Lightning)lightnings.get(i);
                            lightningStrikes[i * 3] = (float)lightning.position.x;
                            lightningStrikes[i * 3 + 1] = (float)lightning.position.y;
                            lightningStrikes[i * 3 + 2] = (float)lightning.position.z;
                            float p = Math.clamp(((float)lightning.ticks + partialTicks) / (float)lightning.lifetime, 0.0F, 1.0F);
                            lightningBrightness[i] = (float)Math.abs(Math.cos(Math.sqrt((double)p) * Math.PI * (double)3.0F)) * (1.0F - p);
                        }
                    }

                    effect.safeGetUniform("lightningStrikes").set(lightningStrikes);
                    effect.safeGetUniform("lightningCount").set(lightnings.size());
                    effect.safeGetUniform("lightningBrightness").set(lightningBrightness);
                }

                int count = 0;

                for(int i = 0; i < storms.size(); ++i) {
                    if (i < 16) {
                        Storm storm = (Storm)storms.get(i);
                        if (!(storm.position.multiply((double)1.0F, (double)0.0F, (double)1.0F).distanceTo(camera.getPosition().multiply((double)1.0F, (double)0.0F, (double)1.0F)) > (double)32000.0F) && (storm.stage > 0 || storm.energy > 0 || storm.stormType == 2) && storm.lastPosition != null) {
                            Vec3 pos = storm.lastPosition;
                            stormPositions[count * 3] = (float)pos.x;
                            stormPositions[count * 3 + 1] = (float)pos.y;
                            stormPositions[count * 3 + 2] = (float)pos.z;
                            Vec3 vel = storm.velocity;
                            stormVelocites[count * 2] = (float)vel.x;
                            stormVelocites[count * 2 + 1] = (float)vel.z;
                            stormStages[count] = (float)storm.stage;
                            stormEnergies[count] = (float)storm.energy;
                            tornadoWindspeeds[count] = storm.smoothWindspeed;
                            tornadoWidths[count] = storm.smoothWidth;
                            tornadoTouchdownSpeeds[count] = (float)storm.touchdownSpeed;
                            stormSpins[count] = Mth.lerp(partialTicks, storm.lastSpin, storm.spin);
                            tornadoShapes[count] = storm.tornadoShape;
                            stormTypes[count] = (float)storm.stormType;
                            stormOcclusions[count] = storm.occlusion;
                            visualOnlys[count] = storm.visualOnly ? 1.0F : -1.0F;
                            stormDyings[count] = storm.isDying ? 1.0F : -1.0F;
                            ++count;
                        }
                    }
                }

                effect.safeGetUniform("stormCount").set(count);
                effect.safeGetUniform("stormPositions").set(stormPositions);
                effect.safeGetUniform("stormVelocities").set(stormVelocites);
                effect.safeGetUniform("stormStages").set(stormStages);
                effect.safeGetUniform("stormEnergies").set(stormEnergies);
                effect.safeGetUniform("stormTypes").set(stormTypes);
                effect.safeGetUniform("tornadoWindspeeds").set(tornadoWindspeeds);
                effect.safeGetUniform("tornadoWidths").set(tornadoWidths);
                effect.safeGetUniform("tornadoTouchdownSpeeds").set(tornadoTouchdownSpeeds);
                effect.safeGetUniform("visualOnlys").set(visualOnlys);
                effect.safeGetUniform("stormSpins").set(stormSpins);
                effect.safeGetUniform("stormDyings").set(stormDyings);
                effect.safeGetUniform("tornadoShapes").set(tornadoShapes);
                effect.safeGetUniform("stormOcclusions").set(stormOcclusions);
                effect.safeGetUniform("overcastPerc").set((float)ServerConfig.overcastPercent);
                effect.safeGetUniform("rainStrength").set((float)ServerConfig.rainStrength);
                Vec3 sampPos = camera.getPosition().multiply((double)1.0F, (double)0.0F, (double)1.0F).add((double)0.0F, ServerConfig.layer0Height, (double)0.0F);
                Vec3 lightingColor = new Vec3((double)1.0F, (double)1.0F, (double)1.0F);
                lightingColor = lightingColor.lerp(new Vec3(0.741, 0.318, 0.227), Math.pow((double)1.0F - sunDir.y, (double)2.5F));
                lightingColor = lightingColor.lerp(new Vec3(0.314, 0.408, 0.525), Math.clamp((sunDir.y + 0.1) / -0.1, (double)0.0F, (double)1.0F));
                Vec3 skyColor = Minecraft.getInstance().level.getSkyColor(sampPos, partialTicks);
                effect.safeGetUniform("lightingColor").set((float)lightingColor.x, (float)lightingColor.y, (float)lightingColor.z);
                effect.safeGetUniform("skyColor").set((float)skyColor.x, (float)skyColor.y, (float)skyColor.z);
                byte var10000;
                switch (ClientConfig.volumetricsQuality.ordinal()) {
                    case 0 -> var10000 = 0;
                    case 1 -> var10000 = 1;
                    case 2 -> var10000 = 2;
                    case 3 -> var10000 = 3;
                    case 4 -> var10000 = 4;
                    default -> throw new MatchException((String)null, (Throwable)null);
                }

                int quality = var10000;
                effect.safeGetUniform("quality").set(quality);
                effect.safeGetUniform("nearPlane").set(0.05F);
                effect.safeGetUniform("farPlane").set((float)(Integer)Minecraft.getInstance().options.renderDistance().get() * 4.0F * 16.0F);
                effect.safeGetUniform("renderDistance").set(6000.0F);
                clouds.process(partialTicks);
            }

            minecraft.getMainRenderTarget().bindWrite(false);
            RenderSystem.depthMask(true);
            projMat.invert();
            modelMat.invert();
        }

    }

    public static PostChain createShader(ResourceLocation resourceLocation) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            return new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(), minecraft.getMainRenderTarget(), resourceLocation);
        } catch (IOException e) {
            PMWeather.LOGGER.error("Failed to load shader: {}", resourceLocation, e);
        } catch (JsonSyntaxException e) {
            PMWeather.LOGGER.error("Failed to parse shader: {}", resourceLocation, e);
        }

        return null;
    }

    public static void createShaders() {
        if (clouds == null) {
            clouds = createShader(PMWeather.getPath("shaders/post/clouds.json"));
        }

    }

    public static void reload() {
        if (clouds != null) {
            clouds.close();
        }

        clouds = null;
        createShaders();
        updateShaderGroupSize(clouds);
        PMWeather.LOGGER.info("Loaded PMWeather Shaders");
    }

    private static void updateShaderGroupSize(PostChain shaderGroup) {
        if (shaderGroup != null) {
            Minecraft minecraft = Minecraft.getInstance();
            int width = minecraft.getWindow().getWidth();
            int height = minecraft.getWindow().getHeight();
            shaderGroup.resize(width, height);
        }

    }

    static {
        lastScroll = Vec2.ZERO;
        scroll = Vec2.ZERO;
        lastWidth = 0;
        lastHeight = 0;
        snow = 0.0F;
        lastSnow = 0.0F;
        gameTime = 0.0F;
        dhIntegrationInitialized = false;
    }

    public static enum Quality {
        POTATO,
        LOW,
        MEDIUM,
        HIGH,
        PC_KILLER;
    }
}
