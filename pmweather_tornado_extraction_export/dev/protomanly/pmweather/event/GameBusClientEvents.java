package dev.protomanly.pmweather.event;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.config.ClientConfig;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.interfaces.ParticleData;
import dev.protomanly.pmweather.networking.ModNetworking;
import dev.protomanly.pmweather.particle.EntityRotFX;
import dev.protomanly.pmweather.particle.ParticleCube;
import dev.protomanly.pmweather.particle.ParticleManager;
import dev.protomanly.pmweather.particle.ParticleRegistry;
import dev.protomanly.pmweather.particle.ParticleTexExtraRender;
import dev.protomanly.pmweather.particle.ParticleTexFX;
import dev.protomanly.pmweather.particle.behavior.ParticleBehavior;
import dev.protomanly.pmweather.shaders.ModShaders;
import dev.protomanly.pmweather.sound.ModSounds;
import dev.protomanly.pmweather.util.ChunkCoordinatesBlock;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.WeatherHandler;
import dev.protomanly.pmweather.weather.WeatherHandlerClient;
import dev.protomanly.pmweather.weather.WindEngine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;

@EventBusSubscriber(
   modid = "pmweather",
   bus = Bus.GAME,
   value = {Dist.CLIENT}
)
public class GameBusClientEvents {
   public static Level lastLevel;
   public static WeatherHandler weatherHandler;
   public static ParticleManager particleManager;
   public static ParticleManager particleManagerDebris;
   public static ParticleBehavior particleBehavior = new ParticleBehavior((Vec3)null);
   public static List<Block> LEAVES_BLOCKS = new ArrayList<Block>() {
      {
         this.add(Blocks.ACACIA_LEAVES);
         this.add(Blocks.AZALEA_LEAVES);
         this.add(Blocks.BIRCH_LEAVES);
         this.add(Blocks.DARK_OAK_LEAVES);
         this.add(Blocks.CHERRY_LEAVES);
         this.add(Blocks.FLOWERING_AZALEA_LEAVES);
         this.add(Blocks.MANGROVE_LEAVES);
         this.add(Blocks.OAK_LEAVES);
         this.add(Blocks.JUNGLE_LEAVES);
         this.add(Blocks.SPRUCE_LEAVES);
      }
   };
   public static ArrayList<ChunkCoordinatesBlock> soundLocations = new ArrayList<ChunkCoordinatesBlock>();
   public static HashMap<ChunkCoordinatesBlock, Long> soundTimeLocations = new HashMap<ChunkCoordinatesBlock, Long>();
   public static long lastAmbientTick;
   public static long lastAmbientTickThreaded;
   public static long lastWindSoundTick;

   public GameBusClientEvents() {
      super();
   }

   @SubscribeEvent
   public static void fogEvent(ViewportEvent.RenderFog event) {
      Minecraft minecraft = Minecraft.getInstance();
      Level level = minecraft.level;
      if (level != null && ClientConfig.baseGameFog) {
         RenderSystem.setShaderFogStart(10000.0F);
         RenderSystem.setShaderFogEnd(40000.0F);
      }

   }

   @SubscribeEvent
   public static void onStageRenderTick(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_PARTICLES && weatherHandler != null) {
         particleManagerDebris.render(event.getPoseStack(), (MultiBufferSource.BufferSource)null, Minecraft.getInstance().gameRenderer.lightTexture(), event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false), event.getFrustum());
      }

   }

   public static void doSnowParticles(float precip, Minecraft minecraft, Level level) {
      int spawnsNeeded = (int)(precip * 80.0F);
      int spawns = 0;
      int spawnAreaSize = 50;

      for(int i = 0; i < ClientConfig.rainParticleDensity; ++i) {
         BlockPos pos = minecraft.player.blockPosition().offset(PMWeather.RANDOM.nextInt(spawnAreaSize) - spawnAreaSize / 2, -5 + PMWeather.RANDOM.nextInt(25), PMWeather.RANDOM.nextInt(spawnAreaSize) - spawnAreaSize / 2);
         if (canPrecipitateAt(level, pos)) {
            TextureAtlasSprite var10000;
            switch (PMWeather.RANDOM.nextInt(4)) {
               case 1 -> var10000 = ParticleRegistry.snow1;
               case 2 -> var10000 = ParticleRegistry.snow2;
               case 3 -> var10000 = ParticleRegistry.snow3;
               default -> var10000 = ParticleRegistry.snow;
            }

            TextureAtlasSprite particle = var10000;
            ParticleTexExtraRender snow = new ParticleTexExtraRender((ClientLevel)level, (double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), (double)0.0F, (double)0.0F, (double)0.0F, particle);
            snow.fullAlphaTarget = 1.0F;
            snow.renderOrder = 3;
            particleBehavior.initParticleSnow(snow, Math.max((int)(5.0F * precip), 1), (float)(WindEngine.getWind(pos, level, false, false, true).length() / (double)45.0F));
            snow.setScale(Math.max(precip * 0.08F + (PMWeather.RANDOM.nextFloat() - PMWeather.RANDOM.nextFloat()) * 0.02F, 0.01F));
            snow.windWeight = 0.15F;
            snow.renderOrder = 3;
            snow.spawnAsWeatherEffect();
            ++spawns;
            if (spawns > spawnsNeeded) {
               break;
            }
         }
      }

   }

   public static void doSleetParticles(float precip, Minecraft minecraft, Level level) {
      int spawnsNeeded = (int)(precip * 300.0F);
      int spawns = 0;
      int spawnAreaSize = 30;

      for(int i = 0; i < ClientConfig.rainParticleDensity; ++i) {
         BlockPos pos = minecraft.player.blockPosition().offset(PMWeather.RANDOM.nextInt(spawnAreaSize) - spawnAreaSize / 2, -5 + PMWeather.RANDOM.nextInt(25), PMWeather.RANDOM.nextInt(spawnAreaSize) - spawnAreaSize / 2);
         if (canPrecipitateAt(level, pos)) {
            ParticleTexExtraRender sleet = new ParticleTexExtraRender((ClientLevel)level, (double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), (double)0.0F, (double)0.0F, (double)0.0F, ParticleRegistry.sleet);
            sleet.fullAlphaTarget = 1.0F;
            sleet.renderOrder = 3;
            particleBehavior.initParticleSleet(sleet, Math.max((int)(20.0F * precip), 1));
            sleet.setScale(Math.max(precip * 0.08F + (PMWeather.RANDOM.nextFloat() - PMWeather.RANDOM.nextFloat()) * 0.02F, 0.02F) * 0.3F);
            sleet.renderOrder = 3;
            sleet.spawnAsWeatherEffect();
            ++spawns;
            if (spawns > spawnsNeeded) {
               break;
            }
         }
      }

   }

   public static void doRainParticles(float precip, Minecraft minecraft, Level level) {
      int spawnsNeeded = (int)(precip * 300.0F);
      int spawns = 0;
      int spawnAreaSize = 30;
      double windspeed = (double)0.0F;
      if (weatherHandler != null) {
         windspeed = WindEngine.getWind(minecraft.player.position(), level, false, false, false, true).length();
      }

      for(int i = 0; i < ClientConfig.rainParticleDensity; ++i) {
         BlockPos pos = minecraft.player.blockPosition().offset(PMWeather.RANDOM.nextInt(spawnAreaSize) - spawnAreaSize / 2, -5 + PMWeather.RANDOM.nextInt(25), PMWeather.RANDOM.nextInt(spawnAreaSize) - spawnAreaSize / 2);
         if (canPrecipitateAt(level, pos)) {
            ParticleTexExtraRender rain = new ParticleTexExtraRender((ClientLevel)level, (double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), (double)0.0F, (double)0.0F, (double)0.0F, ParticleRegistry.rain);
            rain.fullAlphaTarget = Mth.lerp(precip, 0.3F, 1.0F);
            rain.renderOrder = 3;
            particleBehavior.initParticleRain(rain, Math.max((int)(20.0F * precip), 1));
            if (windspeed > (double)50.0F && i < ClientConfig.rainParticleDensity / 3) {
               float strength = precip * (float)Math.clamp((windspeed - (double)50.0F) / (double)50.0F, (double)0.0F, (double)1.0F);
               ParticleTexExtraRender mist = new ParticleTexExtraRender((ClientLevel)level, (double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), (double)0.0F, (double)0.0F, (double)0.0F, ParticleRegistry.mist);
               mist.fullAlphaTarget = Mth.lerp(strength, 0.3F, 1.0F);
               mist.renderOrder = 4;
               particleBehavior.initParticleRain(mist, Math.max((int)(5.0F * strength), 1));
               mist.setScale(0.5F + strength);
               mist.setColor(0.9F, 0.9F, 0.9F);
               mist.setGravity(0.5F);
            }

            ++spawns;
            if (spawns > spawnsNeeded) {
               break;
            }
         }
      }

      spawnAreaSize = 40;

      for(int i = 0; (float)i < (float)(ClientConfig.rainParticleDensity * 3) * precip; ++i) {
         BlockPos pos = minecraft.player.blockPosition().offset(PMWeather.RANDOM.nextInt(spawnAreaSize) - spawnAreaSize / 2, -5 + PMWeather.RANDOM.nextInt(25), PMWeather.RANDOM.nextInt(spawnAreaSize) - spawnAreaSize / 2);
         pos = level.getHeightmapPos(Types.MOTION_BLOCKING, pos).below();
         BlockState state = level.getBlockState(pos);
         double maxY = (double)0.0F;
         double minY = (double)0.0F;
         VoxelShape shape = state.getShape(level, pos);
         if (!shape.isEmpty()) {
            minY = shape.bounds().minY;
            maxY = shape.bounds().maxY;
         }

         if (!(pos.distSqr(minecraft.player.blockPosition()) > (double)spawnAreaSize / (double)2.0F * ((double)spawnAreaSize / (double)2.0F)) && canPrecipitateAt(level, pos.above())) {
            if (level.getBlockState(pos).getBlock().defaultMapColor() == MapColor.WATER) {
               pos = pos.offset(0, 1, 0);
            }

            ParticleTexFX rain = new ParticleTexFX((ClientLevel)level, (double)((float)pos.getX() + PMWeather.RANDOM.nextFloat()), (double)pos.getY() + 0.01 + maxY, (double)((float)pos.getZ() + PMWeather.RANDOM.nextFloat()), (double)0.0F, (double)0.0F, (double)0.0F, ParticleRegistry.splash);
            rain.fullAlphaTarget = Mth.lerp(precip, 0.2F, 0.8F) / 2.0F;
            rain.renderOrder = 5;
            particleBehavior.initParticleGroundSplash(rain);
            rain.spawnAsWeatherEffect();
         }
      }

   }

   @SubscribeEvent
   public static void onTick(ClientTickEvent.Pre event) {
      Minecraft minecraft = Minecraft.getInstance();
      Level level = minecraft.level;
      if (level != null && !minecraft.isPaused()) {
         getClientWeather();
         tryAmbientSounds();
         trySounds();
         weatherHandler.tick();
         particleManager.tick();
         particleManagerDebris.tick();
         ModShaders.tick();
         WeatherHandlerClient weatherHandlerClient = (WeatherHandlerClient)weatherHandler;
         if (minecraft.player != null) {
            Entity entity = minecraft.player;
            Vec3 w = WindEngine.getWind(entity.getPosition(1.0F), level, false, true, false);
            if (w.length() > (double)60.0F && !minecraft.player.isCreative() && !minecraft.player.isSpectator()) {
               double factor = Mth.lerp(Math.clamp(w.length() / (double)125.0F, (double)0.0F, (double)1.0F), 0.005, 0.02);
               float mult = 0.65F;
               if (!entity.onGround()) {
                  mult = 0.15F;
               }

               entity.addDeltaMovement(w.multiply((double)0.05F, (double)0.0F, (double)0.05F).multiply(factor, (double)0.0F, factor).multiply((double)mult, (double)mult, (double)mult));
            }

            minecraft.particleEngine.iterateParticles((particle) -> {
               if (particle instanceof ParticleData particleData) {
                  boolean affect = true;
                  if (particle instanceof EntityRotFX entityRotFX) {
                     affect = !entityRotFX.ignoreWind;
                  }

                  if (affect) {
                     Vec3 wind = WindEngine.getWind(particle.getPos(), level, false, false, false);
                     particleData.addVelocity(wind.multiply((double)0.05F, (double)0.05F, (double)0.05F).multiply((double)0.04F, (double)0.04F, (double)0.04F));
                     double l = wind.length() * 0.01;
                     if (particleData.getVelocity().length() < l) {
                        particleData.setVelocity(particleData.getVelocity().normalize().multiply(l, l, l));
                     }
                  }
               }

            });
            particleManager.getParticles().forEach((particleRenderType, particles) -> {
               for(Particle particle : particles) {
                  if (particle instanceof ParticleData particleData) {
                     float affect = 1.0F;
                     if (particle instanceof EntityRotFX entityRotFX) {
                        if (entityRotFX.ignoreWind) {
                           affect = 0.0F;
                        } else {
                           affect = entityRotFX.windWeight;
                        }
                     }

                     if (affect > 0.0F) {
                        Vec3 wind = WindEngine.getWind(particle.getPos(), level, false, false, false);
                        particleData.addVelocity(wind.multiply((double)0.05F, (double)0.05F, (double)0.05F).multiply((double)0.04F, (double)0.04F, (double)0.04F).multiply((double)affect, (double)affect, (double)affect));
                        double l = wind.length() * 0.01;
                        if (particleData.getVelocity().length() < l) {
                           particleData.setVelocity(particleData.getVelocity().normalize().multiply(l, l, l));
                        }
                     }
                  }
               }

            });
            particleManagerDebris.getParticles().forEach((particleRenderType, particles) -> {
               for(Particle particle : particles) {
                  if (particle instanceof ParticleData particleData) {
                     float affect = 1.0F;
                     if (particle instanceof EntityRotFX entityRotFX) {
                        if (entityRotFX.ignoreWind) {
                           affect = 0.0F;
                        } else {
                           affect = entityRotFX.windWeight;
                        }
                     }

                     if (affect > 0.0F) {
                        Vec3 wind = WindEngine.getWind(particle.getPos(), level, false, false, false);
                        particleData.addVelocity(wind.multiply((double)0.05F, (double)0.05F, (double)0.05F).multiply((double)0.04F, (double)0.04F, (double)0.04F).multiply((double)affect, (double)affect, (double)affect));
                     }
                  }
               }

            });
            float hail = weatherHandlerClient.getHail();
            float precip = weatherHandlerClient.getPrecipitation();
            if (precip > 0.0F) {
               ThermodynamicEngine.Precipitation precipType = ThermodynamicEngine.getPrecipitationType(weatherHandlerClient, minecraft.player.position(), level, 0);
               if (precipType == ThermodynamicEngine.Precipitation.RAIN || precipType == ThermodynamicEngine.Precipitation.FREEZING_RAIN || precipType == ThermodynamicEngine.Precipitation.WINTRY_MIX) {
                  doRainParticles(precip, minecraft, level);
               }

               if (precipType == ThermodynamicEngine.Precipitation.SLEET || precipType == ThermodynamicEngine.Precipitation.WINTRY_MIX) {
                  doSleetParticles(precip, minecraft, level);
               }

               if (precipType == ThermodynamicEngine.Precipitation.SNOW || precipType == ThermodynamicEngine.Precipitation.WINTRY_MIX) {
                  doSnowParticles(precip, minecraft, level);
               }
            }

            if (hail > 0.0F) {
               int spawnsNeeded = (int)(hail * 80.0F);
               int spawns = 0;
               int spawnAreaSize = 30;

               for(int i = 0; i < 15; ++i) {
                  BlockPos pos = minecraft.player.blockPosition().offset(PMWeather.RANDOM.nextInt(spawnAreaSize) - spawnAreaSize / 2, -5 + PMWeather.RANDOM.nextInt(25), PMWeather.RANDOM.nextInt(spawnAreaSize) - spawnAreaSize / 2);
                  if (canPrecipitateAt(level, pos)) {
                     ParticleCube hailP = new ParticleCube((ClientLevel)level, (double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), (double)0.0F, (double)0.0F, (double)0.0F, Blocks.PACKED_ICE.defaultBlockState());
                     particleBehavior.initParticleHail(hailP);
                     hailP.setScale(0.01F + PMWeather.RANDOM.nextFloat() * hail * 0.08F);
                     hailP.renderOrder = 3;
                     hailP.spawnAsDebrisEffect();
                     ++spawns;
                     if (spawns >= spawnsNeeded) {
                        break;
                     }
                  }
               }
            }
         }
      }

   }

   public static boolean canPrecipitateAt(Level level, BlockPos pos) {
      if ((double)pos.getY() > ServerConfig.layer0Height) {
         return false;
      } else {
         return level.getHeightmapPos(Types.MOTION_BLOCKING, pos).getY() <= pos.getY();
      }
   }

   public static void resetClientWeather() {
      weatherHandler = null;
   }

   public static WeatherHandlerClient getClientWeather() {
      try {
         Level level = Minecraft.getInstance().level;
         if (weatherHandler == null || level != lastLevel) {
            init(level);
         }
      } catch (Exception e) {
         PMWeather.LOGGER.error(e.getMessage(), e);
      }

      return (WeatherHandlerClient)weatherHandler;
   }

   public static void trySounds() {
      try {
         Minecraft minecraft = Minecraft.getInstance();
         Level level = minecraft.level;
         Player player = minecraft.player;
         if (player == null || level == null) {
            return;
         }

         float hail = ((WeatherHandlerClient)weatherHandler).getHail();
         if (hail > 0.0F) {
            int chance = (int)Mth.lerp(hail, 20.0F, 2.0F);
            if (PMWeather.RANDOM.nextInt(chance) == 0) {
               BlockPos pos = player.blockPosition().offset(PMWeather.RANDOM.nextInt(-15, 16), 15, PMWeather.RANDOM.nextInt(-15, 16));
               pos = level.getHeightmapPos(Types.MOTION_BLOCKING, pos);
               if (canPrecipitateAt(level, pos) && pos.distSqr(player.blockPosition()) < (double)225.0F) {
                  level.playLocalSound(pos, (SoundEvent)ModSounds.HAIL.value(), SoundSource.WEATHER, hail * 3.5F, 2.0F + PMWeather.RANDOM.nextFloat() * 0.5F, false);
               }
            }
         }

         if (lastWindSoundTick < System.currentTimeMillis()) {
            lastWindSoundTick = System.currentTimeMillis() + 4000L + (long)PMWeather.RANDOM.nextInt(0, 3000);
            Vec3 wind = WindEngine.getWind(player.position(), level);
            double windspeed = wind.length();
            if (windspeed > (double)55.0F) {
               ModSounds.playPlayerLockedSound(player.position(), (SoundEvent)ModSounds.WIND_STRONG.value(), (float)(windspeed / (double)200.0F), 0.9F + PMWeather.RANDOM.nextFloat() * 0.2F);
            }

            if (windspeed > (double)35.0F) {
               ModSounds.playPlayerLockedSound(player.position(), (SoundEvent)ModSounds.WIND_MED.value(), (float)(windspeed / (double)200.0F), 0.9F + PMWeather.RANDOM.nextFloat() * 0.2F);
            }

            if (windspeed > (double)5.0F) {
               ModSounds.playPlayerLockedSound(player.position(), (SoundEvent)ModSounds.WIND_CALM.value(), Math.min((float)(windspeed / (double)100.0F), 0.1F), 0.9F + PMWeather.RANDOM.nextFloat() * 0.2F);
            }
         }

         if (lastAmbientTick < System.currentTimeMillis()) {
            lastAmbientTick = System.currentTimeMillis() + 500L;
            int size = 32;
            int hSize = size / 2;
            BlockPos curBlockPos = player.blockPosition();

            for(int i = 0; i < soundLocations.size(); ++i) {
               ChunkCoordinatesBlock chunkCoord = soundLocations.get(i);
               if (Math.sqrt(chunkCoord.distSqr(curBlockPos)) > (double)size) {
                  soundLocations.remove(i--);
                  soundTimeLocations.remove(chunkCoord);
               } else {
                  Block block = level.getBlockState(chunkCoord).getBlock();
                  if (block != null && (block.defaultMapColor() == MapColor.WATER || block.defaultMapColor() == MapColor.PLANT)) {
                     long lastPlayTime = 0L;
                     float soundMuffle = 0.6F;
                     if (soundTimeLocations.containsKey(chunkCoord)) {
                        lastPlayTime = (Long)soundTimeLocations.get(chunkCoord);
                     }

                     float maxLeavesVolume = 1.0F;
                     soundMuffle *= (float)ClientConfig.leavesVolume;
                     if (lastPlayTime < System.currentTimeMillis() && LEAVES_BLOCKS.contains(chunkCoord.block)) {
                        Vec3 wind = WindEngine.getWind(curBlockPos, level, false, false, false);
                        double windspeed = wind.length();
                        soundTimeLocations.put(chunkCoord, System.currentTimeMillis() + 12000L + (long)PMWeather.RANDOM.nextInt(50));
                        minecraft.level.playLocalSound(chunkCoord, (SoundEvent)ModSounds.CALM_AMBIENCE.value(), SoundSource.AMBIENT, (float)Math.min((double)maxLeavesVolume, windspeed * (double)soundMuffle * (double)0.05F), 0.9F + PMWeather.RANDOM.nextFloat() * 0.2F, false);
                     }
                  } else {
                     soundLocations.remove(i);
                     soundTimeLocations.remove(chunkCoord);
                  }
               }
            }
         }
      } catch (Exception e) {
         PMWeather.LOGGER.error(e.getMessage(), e);
      }

   }

   public static void tryAmbientSounds() {
      Minecraft minecraft = Minecraft.getInstance();
      Level level = minecraft.level;
      Player player = minecraft.player;
      if (lastAmbientTickThreaded < System.currentTimeMillis() && ClientConfig.leavesVolume > (double)0.0F) {
         lastAmbientTickThreaded = System.currentTimeMillis() + 500L;
         int size = 32;
         int hSize = size / 2;
         BlockPos curBlockPos = player.blockPosition();

         for(int x = curBlockPos.getX() - hSize; x < curBlockPos.getX() + hSize; ++x) {
            for(int y = curBlockPos.getY() - hSize; y < curBlockPos.getY() + hSize; ++y) {
               for(int z = curBlockPos.getZ() - hSize; z < curBlockPos.getZ() + hSize; ++z) {
                  Block block = level.getBlockState(new BlockPos(x, y, z)).getBlock();
                  if (block.defaultMapColor() == MapColor.PLANT) {
                     boolean proxFail = false;

                     for(ChunkCoordinatesBlock soundLocation : soundLocations) {
                        if (Math.sqrt(soundLocation.distSqr(new BlockPos(x, y, z))) < (double)15.0F) {
                           proxFail = true;
                           break;
                        }
                     }

                     if (!proxFail) {
                        soundLocations.add(new ChunkCoordinatesBlock(x, y, z, block));
                     }
                  }
               }
            }
         }
      }

   }

   public static void init(Level level) {
      lastLevel = level;
      if (level != null) {
         weatherHandler = new WeatherHandlerClient(level.dimension());
         Minecraft minecraft = Minecraft.getInstance();
         if (particleManager == null) {
            particleManager = new ParticleManager(minecraft.level, minecraft.getTextureManager());
         } else {
            particleManager.setLevel((ClientLevel)level);
         }

         if (particleManagerDebris == null) {
            particleManagerDebris = new ParticleManager(minecraft.level, minecraft.getTextureManager());
         } else {
            particleManagerDebris.setLevel((ClientLevel)level);
         }

         CompoundTag data = new CompoundTag();
         data.putString("command", "syncFull");
         data.putString("packetCommand", "WeatherData");
         ModNetworking.clientSendToSever(data);
      }
   }
}
