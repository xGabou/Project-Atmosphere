package dev.protomanly.pmweather.weather;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.block.ModBlocks;
import dev.protomanly.pmweather.block.entity.RadarBlockEntity;
import dev.protomanly.pmweather.config.Config;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.entity.ModEntities;
import dev.protomanly.pmweather.entity.MovingBlock;
import dev.protomanly.pmweather.interfaces.ParticleData;
import dev.protomanly.pmweather.particle.EntityRotFX;
import dev.protomanly.pmweather.sound.ModSounds;
import dev.protomanly.pmweather.sound.MovingSoundStreamingSource;
import dev.protomanly.pmweather.util.CachedNBTTagCompound;
import dev.protomanly.pmweather.util.Util;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.common.Tags.Blocks;

public class Storm {
   public static long LastUsedStormID = 0L;
   private static final float resistance = 0.985F;
   public static final float tickConversion = 0.05F;
   @OnlyIn(Dist.CLIENT)
   public MovingSoundStreamingSource tornadicWind;
   @OnlyIn(Dist.CLIENT)
   public MovingSoundStreamingSource tornadicDamage;
   @OnlyIn(Dist.CLIENT)
   public MovingSoundStreamingSource supercellWind;
   @OnlyIn(Dist.CLIENT)
   public MovingSoundStreamingSource eyewallWind;
   @OnlyIn(Dist.CLIENT)
   public MovingSoundStreamingSource undergroundWind;
   public long ID;
   public WeatherHandler weatherHandler;
   public Vec3 position;
   public Vec3 lastPosition;
   public Vec3 velocity;
   public int windspeed;
   public float cycloneWindspeed = 0.0F;
   public float smoothWindspeed = 0.0F;
   public float width = 15.0F;
   public float smoothWidth = 15.0F;
   public float tornadoShape;
   public float spin;
   public float lastSpin;
   public int energy;
   public int stormType;
   public int stage;
   public int tickCount;
   public int tornadoOnGroundTicks;
   public boolean dead;
   public Level level;
   private final CachedNBTTagCompound nbtCache;
   public SimplexNoise simplexNoise;
   public float rankineFactor;
   public List<EntityRotFX> listParticleDebris;
   private final List<ChunkPos> forceLoadedChunks;
   public int maxStage;
   public int maxProgress;
   public boolean isDying;
   public int growthSpeed;
   public int maxWindspeed;
   public int maxWidth;
   public int ticksSinceDying;
   public int touchdownSpeed;
   public boolean onWater;
   public float occlusion;
   public boolean visualOnly;
   public boolean cirus;
   public boolean aimedAtPlayer;
   public int maxColdEnergy;
   public int coldEnergy;
   public List<Vorticy> vorticies;

   public double FBM(Vec3 pos, int octaves, float lacunarity, float gain, float amplitude) {
      double y = (double)0.0F;

      for(int i = 0; i < Math.max(octaves, 1); ++i) {
         y += (double)amplitude * this.simplexNoise.getValue(pos.x, pos.y, pos.z);
         pos = pos.multiply((double)lacunarity, (double)lacunarity, (double)lacunarity);
         amplitude *= gain;
      }

      return y;
   }

   public Vec3 rotateV3(Vec3 x, double angle) {
      double rx = x.x * Math.cos(angle) - x.z * Math.sin(angle);
      double rz = x.x * Math.sin(angle) + x.z * Math.cos(angle);
      return new Vec3(rx, x.y, rz);
   }

   public Storm(WeatherHandler weatherHandler, Level level, @Nullable Float risk, int stormType) {
      super();
      this.tornadoShape = PMWeather.RANDOM.nextFloat() * 10.0F + 6.0F;
      this.spin = 0.0F;
      this.lastSpin = 0.0F;
      this.tickCount = 0;
      this.tornadoOnGroundTicks = 0;
      this.dead = false;
      this.rankineFactor = 4.5F;
      this.forceLoadedChunks = new ArrayList<ChunkPos>();
      this.maxStage = 0;
      this.maxProgress = 0;
      this.isDying = false;
      this.growthSpeed = 20;
      this.maxWindspeed = 0;
      this.maxWidth = 15;
      this.ticksSinceDying = 0;
      this.touchdownSpeed = PMWeather.RANDOM.nextInt(65, 120);
      this.onWater = false;
      this.occlusion = 0.0F;
      this.visualOnly = false;
      this.cirus = false;
      this.aimedAtPlayer = false;
      this.maxColdEnergy = 300;
      this.coldEnergy = 0;
      this.vorticies = new ArrayList<Vorticy>();
      this.weatherHandler = weatherHandler;
      this.level = level;
      this.stormType = stormType;
      this.simplexNoise = new SimplexNoise(new LegacyRandomSource(weatherHandler.seed));
      this.nbtCache = new CachedNBTTagCompound();
      if (level.isClientSide()) {
         this.listParticleDebris = new ArrayList<EntityRotFX>();
      } else {
         this.maxStage = 0;
         this.maxProgress = PMWeather.RANDOM.nextInt(25, 99);
         float stage1Chance = 1.0F / (float)ServerConfig.chanceInOneStage1;
         float stage2Chance = 1.0F / (float)ServerConfig.chanceInOneStage2;
         float stage3Chance = 1.0F / (float)ServerConfig.chanceInOneStage3;
         if (risk != null && ServerConfig.environmentSystem && stormType == 0) {
            stage1Chance *= risk * 1.75F + 0.05F;
            stage2Chance *= risk;
            stage3Chance *= risk * 0.75F;
            PMWeather.LOGGER.debug("Readjusted stage chances: 1: {} 2: {} 3: {}", new Object[]{stage1Chance, stage2Chance, stage3Chance});
         }

         if (PMWeather.RANDOM.nextFloat() <= stage1Chance) {
            this.maxStage = 1;
         }

         if (PMWeather.RANDOM.nextFloat() <= stage2Chance) {
            this.maxStage = 2;
         }

         if (PMWeather.RANDOM.nextFloat() <= stage3Chance) {
            this.maxStage = 3;
         }

         if (this.maxStage == 3 && stormType == 0) {
            this.maxProgress = 100;
            float mW;
            if (risk != null && ServerConfig.environmentSystem) {
               mW = risk * 80.0F;
            } else {
               mW = 125.0F;
            }

            mW += 55.0F;
            this.maxWindspeed = Math.min((int)Mth.lerp(PMWeather.RANDOM.nextFloat(), 55.0F, mW), 220);
            this.touchdownSpeed = PMWeather.RANDOM.nextInt(75, Math.max(25 + (int)((float)this.maxWindspeed * 1.1F), 100));
         }

         this.growthSpeed = PMWeather.RANDOM.nextInt(30, 80);
         if (stormType == 1) {
            this.growthSpeed = PMWeather.RANDOM.nextInt(40, 70);
         }

         this.maxWidth = PMWeather.RANDOM.nextInt(15, 25 + (int)(Math.pow((double)((float)this.maxWindspeed / 220.0F), (double)1.75F) * (ServerConfig.maxTornadoWidth - (double)25.0F)));
         PMWeather.LOGGER.debug("Max Stage: {}, Max Energy: {}, Max Windspeed: {}, Max Width: {}, Touchdown Speed: {}", new Object[]{this.maxStage, this.maxProgress, this.maxWindspeed, this.maxWidth, this.touchdownSpeed});
      }

   }

   public void recalc(@Nullable Float risk) {
      if (this.maxStage == 3 && this.stormType == 0) {
         this.maxProgress = 100;
         float mW;
         if (risk != null && ServerConfig.environmentSystem) {
            mW = risk * 80.0F;
         } else {
            mW = 125.0F;
         }

         mW += 55.0F;
         this.maxWindspeed = Math.min((int)Mth.lerp(PMWeather.RANDOM.nextFloat(), 55.0F, mW), 220);
         this.touchdownSpeed = PMWeather.RANDOM.nextInt(75, Math.max(25 + (int)((float)this.maxWindspeed * 1.1F), 100));
      }

      this.growthSpeed = PMWeather.RANDOM.nextInt(30, 80);
      if (this.stormType == 1) {
         this.growthSpeed = PMWeather.RANDOM.nextInt(40, 70);
      }

      this.maxWidth = PMWeather.RANDOM.nextInt(15, 25 + (int)(Math.pow((double)((float)this.maxWindspeed / 220.0F), (double)1.75F) * (ServerConfig.maxTornadoWidth - (double)25.0F)));
      PMWeather.LOGGER.debug("Max Stage: {}, Max Energy: {}, Max Windspeed: {}, Max Width: {}, Touchdown Speed: {}", new Object[]{this.maxStage, this.maxProgress, this.maxWindspeed, this.maxWidth, this.touchdownSpeed});
   }

   public void aimAtPlayer() {
      if (this.stormType != 1) {
         Player nearest = this.level.getNearestPlayer(this.position.x, this.position.y, this.position.z, (double)4096.0F, false);
         if (nearest != null) {
            Vec3 aimPos = nearest.position().add(new Vec3((double)(PMWeather.RANDOM.nextFloat() - 0.5F) * ServerConfig.aimAtPlayerOffset, (double)0.0F, (double)(PMWeather.RANDOM.nextFloat() - 0.5F) * ServerConfig.aimAtPlayerOffset));
            if (this.position.distanceTo(aimPos) >= ServerConfig.aimAtPlayerOffset) {
               Vec3 toward = this.position.subtract(new Vec3(aimPos.x, this.position.y, aimPos.z)).multiply((double)1.0F, (double)0.0F, (double)1.0F).normalize();
               double speed = PMWeather.RANDOM.nextDouble() * (double)5.0F + (double)1.0F;
               this.velocity = toward.multiply(-speed, (double)0.0F, -speed);
            }

            this.aimedAtPlayer = true;
         }

      }
   }

   public void tick() {
      ++this.tickCount;
      Iterator<Vorticy> vorts = this.vorticies.iterator();

      while(vorts.hasNext()) {
         Vorticy vorticy = vorts.next();
         vorticy.tick();
         if (vorticy.dead) {
            vorts.remove();
         }
      }

      float vorticySpawnChance = 0.05F;
      if (this.isDying) {
         vorticySpawnChance = 0.25F;
      }

      vorticySpawnChance += Mth.clamp((float)Math.pow((double)(((float)this.windspeed - 100.0F) / 200.0F), (double)2.0F), 0.0F, 0.5F);
      if ((float)this.windspeed >= 39.0F && this.stormType == 2) {
         vorticySpawnChance *= 2.0F;
         if (!this.level.isClientSide && PMWeather.RANDOM.nextFloat() < vorticySpawnChance * 0.05F && this.vorticies.size() < 10) {
            Vorticy vorticy = new Vorticy(this, (float)Math.pow((double)PMWeather.RANDOM.nextFloat(), (double)0.75F) * 0.1F, PMWeather.RANDOM.nextFloat() * 0.15F + 0.05F, 0.075F, PMWeather.RANDOM.nextInt(900, 3000));
            this.vorticies.add(vorticy);
         }
      }

      if (this.stage == 3 && (float)this.windspeed >= 40.0F && this.stormType == 0) {
         ++this.tornadoOnGroundTicks;
         if (!this.level.isClientSide && PMWeather.RANDOM.nextFloat() < vorticySpawnChance * 0.05F && this.vorticies.size() < 10) {
            Vorticy vorticy = new Vorticy(this, (float)Math.pow((double)PMWeather.RANDOM.nextFloat(), (double)0.75F) * 0.4F, PMWeather.RANDOM.nextFloat() * 0.3F + 0.05F, 1.0F / this.rankineFactor * 0.5F, PMWeather.RANDOM.nextInt(35, 120));
            this.vorticies.add(vorticy);
         }
      }

      if (this.isDying) {
         ++this.ticksSinceDying;
      }

      BlockPos blockPos = new BlockPos((int)this.position.x, (int)this.position.y, (int)this.position.z);
      if (!this.level.isClientSide() && this.stage >= 2 && this.stormType == 0) {
         float y = 0.0F;
         int count = 0;

         for(int x = -1; x <= 1; ++x) {
            for(int z = -1; z <= 1; ++z) {
               float r = Math.max(this.width, 45.0F);
               Vec3 samplePos = this.position.add((double)((float)x * r * 0.5F), (double)0.0F, (double)((float)z * r * 0.5F));
               BlockPos sample = this.level.getHeightmapPos(Types.WORLD_SURFACE_WG, new BlockPos((int)samplePos.x, this.level.getMaxBuildHeight(), (int)samplePos.z));
               y += (float)sample.getY();
               ++count;
            }
         }

         y /= (float)count;
         blockPos = new BlockPos((int)this.position.x, (int)y, (int)this.position.z);
         this.position = new Vec3(this.position.x, Mth.lerp((double)0.01F, this.position.y, (double)y), this.position.z);
      }

      if (this.tickCount % 20 == 0 && !this.level.isClientSide()) {
         Level iterator = this.level;
         if (iterator instanceof ServerLevel) {
            ServerLevel serverLevel = (ServerLevel)iterator;
            if (this.windspeed > 40 && this.stormType == 0) {
               ChunkPos cChunkPos = new ChunkPos(blockPos);

               for(int x = -((int)this.width); (float)x <= this.width; x += 16) {
                  for(int z = -((int)this.width); (float)z <= this.width; z += 16) {
                     ChunkPos chunkPos = new ChunkPos(blockPos.offset(x, 0, z));
                     if (!serverLevel.hasChunk(chunkPos.x, chunkPos.z) && !this.forceLoadedChunks.contains(chunkPos) && serverLevel.isInWorldBounds(blockPos)) {
                        this.forceLoadedChunks.add(chunkPos);
                        serverLevel.setChunkForced(chunkPos.x, chunkPos.z, true);
                     }
                  }
               }

               Iterator<ChunkPos> iterator = this.forceLoadedChunks.iterator();

               while(iterator.hasNext()) {
                  ChunkPos cpos = iterator.next();
                  double dist = Math.sqrt((double)cpos.distanceSquared(cChunkPos));
                  if (dist > (double)(this.width * 2.0F / 16.0F)) {
                     iterator.remove();
                     serverLevel.setChunkForced(cpos.x, cpos.z, false);
                  }
               }
            } else {
               Iterator<ChunkPos> iterator = this.forceLoadedChunks.iterator();

               while(iterator.hasNext()) {
                  ChunkPos cpos = iterator.next();
                  iterator.remove();
                  serverLevel.setChunkForced(cpos.x, cpos.z, false);
               }
            }
         }
      }

      if (this.tickCount % 10 == 0 && !this.level.isClientSide()) {
         Level targetProgress = this.level;
         if (targetProgress instanceof ServerLevel) {
            ServerLevel serverLevel = (ServerLevel)targetProgress;
            float lightningChance = 0.0F;
            if (this.stage == 1) {
               lightningChance = (float)this.energy / 100.0F;
            } else if (this.stage == 2) {
               lightningChance = 1.0F + (float)this.energy / 100.0F;
            } else if (this.stage > 2) {
               lightningChance = 2.0F;
            }

            if (this.visualOnly) {
               lightningChance = 0.0F;
            }

            lightningChance = Math.min(lightningChance * 0.035F, 0.1F);
            if (this.stormType == 1) {
               lightningChance *= 3.0F;
            }

            if (PMWeather.RANDOM.nextFloat() <= lightningChance * 0.5F) {
               Vec3 lPos = this.position.add((double)(PMWeather.RANDOM.nextFloat((float)(-ServerConfig.stormSize), (float)ServerConfig.stormSize) / 2.0F), (double)0.0F, (double)(PMWeather.RANDOM.nextFloat((float)(-ServerConfig.stormSize), (float)ServerConfig.stormSize) / 2.0F));
               if (this.stormType == 1) {
                  Vec2 stormVel = new Vec2((float)this.velocity.x, (float)this.velocity.z);
                  Vec2 right = (new Vec2(stormVel.y, -stormVel.x)).normalized();
                  Vec2 fwd = stormVel.normalized();
                  right = Util.mulVec2(right, PMWeather.RANDOM.nextFloat((float)(-ServerConfig.stormSize), (float)ServerConfig.stormSize) * 5.0F);
                  fwd = Util.mulVec2(fwd, PMWeather.RANDOM.nextFloat((float)(-ServerConfig.stormSize), (float)ServerConfig.stormSize) / 2.0F);
                  lPos = this.position.add(new Vec3((double)right.x, (double)0.0F, (double)right.y)).add(new Vec3((double)fwd.x, (double)0.0F, (double)fwd.y));
               }

               int height = this.level.getHeightmapPos(Types.MOTION_BLOCKING, new BlockPos((int)lPos.x, (int)lPos.y, (int)lPos.z)).getY();
               ((WeatherHandlerServer)this.weatherHandler).syncLightningNew(new Vec3(lPos.x, (double)height, lPos.z));
            }
         }
      }

      int gs = this.growthSpeed / 2;
      if (this.stormType == 0 && this.stage < 3) {
         gs = (int)((float)gs / 1.5F);
      }

      if (this.tickCount % gs == 0) {
         if (this.stormType == 2 && !this.level.isClientSide()) {
            this.stage = 0;
            if (this.windspeed >= 15) {
               this.stage = 1;
            }

            if (this.windspeed >= 25) {
               this.stage = 2;
            }

            if (this.windspeed >= 40) {
               this.stage = 3;
            }

            Float sst = ThermodynamicEngine.GetSST(this.weatherHandler, this.position, this.level, (RadarBlockEntity)null, 0);
            if (sst != null && this.tickCount <= 48000) {
               if (sst > 32.0F) {
                  sst = 32.0F;
               }

               float v = 24.0F;
               if (this.cycloneWindspeed > 60.0F) {
                  v += (this.cycloneWindspeed - 60.0F) / 18.5F;
               }

               float growth = (sst - v) / 3.5F;
               if ((float)this.windspeed > 165.0F) {
                  growth -= ((float)this.windspeed - 165.0F) / 15.0F;
               }

               if (growth < 0.0F) {
                  growth = Math.max(growth, -1.5F);
               } else {
                  growth *= 1.25F;
                  growth = Math.min(growth, 3.0F);
               }

               this.cycloneWindspeed += growth;
            } else {
               float death = 1.0F;
               death += Math.max((this.cycloneWindspeed - 75.0F) / 100.0F, 0.0F);
               this.cycloneWindspeed -= death * 0.25F;
            }

            this.windspeed = Math.round(this.cycloneWindspeed);
            if (this.windspeed < -5) {
               this.dead = true;
            }
         } else if (!this.isDying) {
            int targetProgress = this.maxProgress;
            if (this.maxStage > this.stage) {
               targetProgress = 100;
            }

            if (this.energy < targetProgress) {
               ++this.energy;
               if (this.stormType == 1) {
                  this.coldEnergy = Math.clamp((long)(this.coldEnergy + 1), 0, this.maxColdEnergy);
               }
            }

            if (this.stage >= 3 && this.stormType == 0) {
               if (this.windspeed < this.maxWindspeed) {
                  ++this.windspeed;
                  this.occlusion = Math.clamp(this.occlusion - 0.025F, 0.0F, 1.0F);
               }

               if (this.windspeed >= this.maxWindspeed) {
                  this.isDying = true;
                  this.growthSpeed = PMWeather.RANDOM.nextInt(20, 70);
               }
            } else if (this.stage >= this.maxStage && this.energy >= targetProgress) {
               this.isDying = true;
               this.growthSpeed = PMWeather.RANDOM.nextInt(40, 80);
               if (PMWeather.RANDOM.nextInt(2) == 0 || this.maxWidth > 200) {
                  this.maxWidth = Math.min(this.maxWidth, PMWeather.RANDOM.nextInt(5, 35));
               }
            }

            if (this.energy >= 100) {
               this.energy = 0;
               if (this.stormType == 0) {
                  if (this.stage < 3 && this.stage < this.maxStage) {
                     ++this.stage;
                     if (this.stage == 3) {
                        this.windspeed = 0;
                     }
                  }
               } else if (this.stage < this.maxStage) {
                  ++this.stage;
               }
            }
         } else if (this.ticksSinceDying > (this.stormType == 1 ? 2400 : 1200)) {
            if (this.stage >= 3 && this.stormType == 0) {
               if (this.windspeed < 85 && this.windspeed > 15) {
                  if (PMWeather.RANDOM.nextInt(2) == 0 && !this.level.isClientSide()) {
                     --this.windspeed;
                  }
               } else {
                  --this.windspeed;
               }

               this.occlusion = Math.clamp(this.occlusion + 0.015F, 0.0F, 1.0F);
               if (this.windspeed <= 0) {
                  this.windspeed = 0;
                  --this.stage;
                  this.energy = 100;
               }
            } else {
               --this.energy;
               if (this.energy <= 0) {
                  this.energy = 100;
                  --this.stage;
                  if (this.stage < 0) {
                     this.energy = 0;
                     this.stage = 0;
                     if (this.coldEnergy > 0) {
                        --this.coldEnergy;
                     } else {
                        this.dead = true;
                     }
                  }
               }
            }
         }

         if (Config.DEBUG) {
            PMWeather.LOGGER.debug("Stage: {}, Energy: {}, Windspeed: {}, Width: {}", new Object[]{this.stage, this.energy, this.windspeed, this.width});
         }
      }

      if (this.stormType == 0) {
         this.width = Mth.lerp(0.025F, this.width, Math.max(5.0F, Math.clamp((float)this.windspeed / (float)this.maxWindspeed, 0.1F, 1.0F) * (float)this.maxWidth));
      } else if (this.stormType == 2) {
         this.width = (float)this.maxWidth;
      }

      Vec3 vel = this.velocity.multiply((double)0.05F, (double)0.05F, (double)0.05F).multiply((double)2.0F, (double)0.0F, (double)2.0F);
      if (!this.aimedAtPlayer) {
         vel = vel.add((new Vec3((double)0.0F, (double)0.0F, (double)-3.0F)).multiply((double)(0.05F * this.occlusion), (double)(0.05F * this.occlusion), (double)(0.05F * this.occlusion)));
      }

      this.position = this.position.add(vel);
      if (!this.aimedAtPlayer) {
         if (this.stormType != 1) {
            this.velocity = this.velocity.multiply((double)0.985F, (double)0.985F, (double)0.985F);
            Vec3 baseWind = WindEngine.getWind(new Vec3(this.position.x, (double)(this.level.getMaxBuildHeight() + 1), this.position.z), this.level, true, true, false, false);
            float factor = 0.018181818F;
            if (this.stormType == 2) {
               factor = 0.05F;
            }

            Vec3 velAdd = (new Vec3(baseWind.x, (double)0.0F, baseWind.z)).multiply((double)factor, (double)0.0F, (double)factor);
            this.velocity = this.velocity.add(velAdd.multiply((double)0.05F, (double)0.05F, (double)0.05F));
         }

         if (!this.level.isClientSide() && this.stage >= 3 && ServerConfig.aimAtPlayer && this.stormType == 0) {
            this.aimAtPlayer();
         }
      }

      if (!this.level.isClientSide() && this.tickCount % this.getUpdateRate() == 0) {
         WeatherHandlerServer weatherHandlerServer = (WeatherHandlerServer)this.weatherHandler;
         weatherHandlerServer.syncStormUpdate(this);
      }

      if (this.level.isClientSide()) {
         this.tickClient();
      } else if (this.stage >= 3 && this.stormType == 0) {
         if (this.windspeed >= 40) {
            AABB aabb = new AABB(this.position.x, this.position.y, this.position.z, this.position.x, this.position.y, this.position.z);
            aabb = aabb.inflate((double)this.width / (double)2.0F, (double)85.0F, (double)this.width / (double)2.0F);

            for(Entity entity : this.level.getEntities((Entity)null, aabb)) {
               if (entity instanceof Player) {
                  Player player = (Player)entity;
                  if (!player.isCreative() && !player.isSpectator()) {
                     this.pull(entity, 2.5F);
                     continue;
                  }
               }

               if (!(entity instanceof Player)) {
                  this.pull(entity, 2.5F);
               }
            }

            boolean dd = this.tickCount % 5 == 0 || !ServerConfig.damageEvery5thTick;
            if (dd) {
               int windfieldWidth = Math.max((int)this.width, 40);
               int numBlocks = Math.min(windfieldWidth * Math.max(windfieldWidth / 2, 20) + this.windspeed * 3 + 300, ServerConfig.maxBlocksDamagedPerTick);
               Map<Vec3i, Boolean> checkedMap = new HashMap<Vec3i, Boolean>();
               Map<ChunkPos, LevelChunk> chunkMap = new HashMap<ChunkPos, LevelChunk>();
               int damaged = 0;
               int damageMax = (500 + (int)this.width) / 3;

               for(int i = 0; i < numBlocks && damaged < damageMax; ++i) {
                  int x = (int)(PMWeather.RANDOM.nextFloat() * (float)windfieldWidth * 2.0F - (float)windfieldWidth);
                  int z = (int)(PMWeather.RANDOM.nextFloat() * (float)windfieldWidth * 2.0F - (float)windfieldWidth);
                  Vec3i off = new Vec3i(x, 0, z);
                  if (!checkedMap.containsKey(off)) {
                     checkedMap.put(off, true);
                     double dist = off.distSqr(Vec3i.ZERO);
                     if (!(dist > (double)(windfieldWidth * windfieldWidth))) {
                        float percAdj = 16.0F;
                        if (ServerConfig.damageEvery5thTick) {
                           percAdj *= 5.0F;
                        }

                        BlockPos bPos = blockPos.offset(off.getX(), 60, off.getZ());
                        if (this.level.isInWorldBounds(bPos)) {
                           BlockPos blockPosTop = this.level.getHeightmapPos(Types.MOTION_BLOCKING, bPos).below();
                           double windEffect = (double)this.getWind(blockPosTop.getCenter());
                           if (!(windEffect < (double)40.0F)) {
                              ChunkPos chunkPos = new ChunkPos(SectionPos.blockToSectionCoord(blockPosTop.getX()), SectionPos.blockToSectionCoord(blockPosTop.getZ()));
                              LevelChunk chunk;
                              if (chunkMap.containsKey(chunkPos)) {
                                 chunk = chunkMap.get(chunkPos);
                              } else {
                                 PMWeather.LOGGER.debug("{}", chunkPos);
                                 chunk = this.level.getChunk(chunkPos.x, chunkPos.z);
                                 chunkMap.put(chunkPos, chunk);
                              }

                              this.doDamage(chunk, blockPosTop, windEffect, percAdj, windfieldWidth);
                           }
                        }
                     }
                  }
               }
            }
         }

      }
   }

   public void doDamage(LevelChunk chunk, BlockPos blockPosTop, double windEffect, float percAdj, int windfieldWidth) {
      BlockState state = chunk.getBlockState(blockPosTop);
      BlockPos randomDown = blockPosTop.below(PMWeather.RANDOM.nextInt(10));
      BlockState stateDown = chunk.getBlockState(randomDown);
      boolean downBlacklisted = false;

      for(TagKey<Block> tag : ServerConfig.blacklistedBlockTags) {
         if (stateDown.is(tag)) {
            downBlacklisted = true;
            break;
         }
      }

      if (!downBlacklisted && !ServerConfig.blacklistedBlocks.contains(stateDown.getBlock())) {
         if (stateDown.is(Blocks.GLASS_BLOCKS) || stateDown.is(Blocks.GLASS_PANES)) {
            double percChance = Math.clamp((windEffect - (double)75.0F) / (double)15.0F, (double)0.0F, (double)1.0F);
            if ((double)PMWeather.RANDOM.nextFloat() <= percChance * (double)(0.3F * percAdj) && Util.canWindAffect(randomDown.getCenter(), this.level)) {
               this.level.removeBlock(randomDown, false);
               this.level.playSound((Player)null, randomDown, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, PMWeather.RANDOM.nextFloat(0.8F, 1.2F));
            }
         }

         if (stateDown.is(BlockTags.LOGS) && !stateDown.is(Blocks.STRIPPED_LOGS) && ServerConfig.doDebarking) {
            double percChance = Math.clamp((windEffect - (double)140.0F) / (double)20.0F, (double)0.0F, (double)1.0F);
            if ((double)PMWeather.RANDOM.nextFloat() <= percChance * (double)(0.5F * percAdj) && Util.canWindAffect(randomDown.getCenter(), this.level)) {
               Block replacement = Util.STRIPPED_VARIANTS.getOrDefault(stateDown.getBlock(), net.minecraft.world.level.block.Blocks.STRIPPED_OAK_LOG);
               this.level.setBlockAndUpdate(randomDown, (BlockState)replacement.defaultBlockState().trySetValue(BlockStateProperties.AXIS, (Direction.Axis)stateDown.getOptionalValue(BlockStateProperties.AXIS).orElse(Axis.Y)));
            }
         }
      }

      BlockState aboveState = chunk.getBlockState(blockPosTop.above());
      if (!aboveState.isAir()) {
         Block aboveBlock = aboveState.getBlock();
         float blockStrength = getBlockStrength(aboveBlock, this.level, blockPosTop.above());
         double percChance = Math.clamp(Math.pow(Math.clamp(Math.max(windEffect - (double)blockStrength, (double)0.0F) / (double)20.0F, (double)0.0F, (double)1.0F), (double)4.0F) + 0.02, (double)0.0F, (double)1.0F) * 0.05 * (double)percAdj;
         if (windEffect < (double)blockStrength) {
            percChance = (double)0.0F;
         }

         if (aboveBlock.defaultDestroyTime() < 0.05F && aboveBlock.defaultDestroyTime() >= 0.0F && !ServerConfig.blacklistedBlocks.contains(aboveBlock) && (double)PMWeather.RANDOM.nextFloat() <= percChance) {
            this.level.removeBlock(blockPosTop.above(), false);
            return;
         }

         boolean blacklisted = false;

         for(TagKey<Block> tag : ServerConfig.blacklistedBlockTags) {
            if (aboveBlock.defaultBlockState().is(tag)) {
               blacklisted = true;
               break;
            }
         }

         if (windEffect >= (double)blockStrength && aboveBlock.defaultDestroyTime() > 0.0F && !ServerConfig.blacklistedBlocks.contains(aboveBlock) && !blacklisted && state.getFluidState().isEmpty() && (double)PMWeather.RANDOM.nextFloat() <= percChance) {
            this.level.removeBlock(blockPosTop.above(), false);
         }
      }

      if (!state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) && !state.is((Block)ModBlocks.SCOURED_GRASS.get())) {
         if (state.is(net.minecraft.world.level.block.Blocks.DIRT)) {
            double percChance = Math.clamp((windEffect - (double)170.0F) / (double)40.0F, (double)0.0F, (double)1.0F);
            if ((double)PMWeather.RANDOM.nextFloat() <= percChance * (double)(0.02F * percAdj)) {
               this.level.setBlockAndUpdate(blockPosTop, ((Block)ModBlocks.MEDIUM_SCOURING.get()).defaultBlockState());
            }

         } else if (state.is((Block)ModBlocks.MEDIUM_SCOURING.get())) {
            double percChance = Math.clamp((windEffect - (double)200.0F) / (double)30.0F, (double)0.0F, (double)1.0F);
            if ((double)PMWeather.RANDOM.nextFloat() <= percChance * (double)(0.02F * percAdj)) {
               this.level.setBlockAndUpdate(blockPosTop, ((Block)ModBlocks.HEAVY_SCOURING.get()).defaultBlockState());
            }

         } else {
            Block block = state.getBlock();
            float blockStrength = getBlockStrength(block, this.level, blockPosTop);
            if (state.is(Blocks.STRIPPED_LOGS)) {
               blockStrength *= 2.0F;
            }

            if (ServerConfig.blockStrengths.containsKey(block)) {
               blockStrength = (Float)ServerConfig.blockStrengths.get(block);
            }

            double stretch = (double)35.0F;
            if (state.is(BlockTags.LEAVES)) {
               stretch = (double)70.0F;
            } else if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) {
               stretch = (double)50.0F;
            }

            double percChance = Math.clamp(Math.pow(Math.clamp(Math.max(windEffect - (double)blockStrength, (double)0.0F) / stretch, (double)0.0F, (double)1.0F), (double)4.0F) + 0.02, (double)0.0F, (double)1.0F) * 0.05 * (double)percAdj;
            if (windEffect < (double)blockStrength) {
               percChance = (double)0.0F;
            }

            if (block.defaultDestroyTime() < 0.05F && block.defaultDestroyTime() >= 0.0F && !ServerConfig.blacklistedBlocks.contains(block) && (double)PMWeather.RANDOM.nextFloat() <= percChance) {
               this.level.removeBlock(blockPosTop, false);
            } else {
               boolean blacklisted = false;

               for(TagKey<Block> tag : ServerConfig.blacklistedBlockTags) {
                  if (block.defaultBlockState().is(tag)) {
                     blacklisted = true;
                     break;
                  }
               }

               if (windEffect >= (double)blockStrength && block.defaultDestroyTime() > 0.0F && !ServerConfig.blacklistedBlocks.contains(block) && !blacklisted && state.getFluidState().isEmpty() && (double)PMWeather.RANDOM.nextFloat() <= percChance) {
                  MovingBlock movingBlock = (MovingBlock)((EntityType)ModEntities.MOVING_BLOCK.get()).create(this.level);
                  if (movingBlock != null) {
                     movingBlock.setStartPos(blockPosTop);
                     movingBlock.setBlockState(state);
                     movingBlock.setPos((double)blockPosTop.getX(), (double)blockPosTop.getY(), (double)blockPosTop.getZ());
                     this.level.removeBlock(blockPosTop, false);
                     Player nearest = this.level.getNearestPlayer((double)blockPosTop.getX(), (double)blockPosTop.getY(), (double)blockPosTop.getZ(), (double)128.0F, false);
                     if (PMWeather.RANDOM.nextInt(Math.max(1, windfieldWidth / 10)) == 0 && nearest != null && nearest.position().distanceTo(blockPosTop.getCenter()) < (double)128.0F) {
                        if (this.level.isLoaded(blockPosTop)) {
                           this.level.addFreshEntity(movingBlock);
                        } else {
                           movingBlock.discard();
                        }
                     } else {
                        movingBlock.discard();
                        ((WeatherHandlerServer)this.weatherHandler).syncBlockParticleNew(blockPosTop, state, this);
                     }
                  }
               }

            }
         }
      } else {
         double percChance = Math.clamp((windEffect - (double)140.0F) / (double)80.0F, (double)0.0F, (double)1.0F);
         if ((double)PMWeather.RANDOM.nextFloat() <= percChance * (double)(0.02F * percAdj)) {
            this.level.setBlockAndUpdate(blockPosTop, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
         }

      }
   }

   public float getRankine(double dist, int windfieldWidth) {
      float rankineWidth = (float)windfieldWidth / this.rankineFactor;
      float perc = 0.0F;
      if (dist <= (double)(rankineWidth / 2.0F)) {
         perc = (float)dist / (rankineWidth / 2.0F);
      } else if (dist <= (double)((float)windfieldWidth * 2.0F)) {
         perc = Math.clamp((float)Math.pow((double)1.0F - (dist - (double)(rankineWidth / 2.0F)) / (double)(((float)windfieldWidth * 2.0F - rankineWidth) / 2.0F), (double)1.5F), 0.0F, 1.0F);
      }

      if (Float.isNaN(perc)) {
         perc = 0.0F;
      }

      return perc;
   }

   public float getWind(Vec3 pos) {
      int windfieldWidth = Math.max((int)this.width, 40);
      double dist = this.position.multiply((double)1.0F, (double)0.0F, (double)1.0F).distanceTo(pos.multiply((double)1.0F, (double)0.0F, (double)1.0F));
      float perc = this.getRankine(dist, windfieldWidth);
      float affectPerc = (float)Math.sqrt((double)1.0F - dist / (double)((float)windfieldWidth * 2.0F));
      Vec3 relativePos = pos.subtract(this.position);
      Vec3 rotational = (new Vec3(relativePos.z, (double)0.0F, -relativePos.x)).normalize();
      Vec3 rPosNoise = this.rotateV3(relativePos, (double)this.tickCount / (double)60.0F);
      double wNoise = this.FBM(new Vec3(rPosNoise.x / (double)100.0F, rPosNoise.z / (double)100.0F, (double)this.tickCount / (double)200.0F), 5, 2.0F, 0.5F, 1.0F);
      double realWind = (double)this.windspeed * ((double)1.0F + wNoise * 0.1);
      Vec3 motion = rotational.multiply(realWind * (double)perc, (double)0.0F, realWind * (double)perc);
      motion = motion.add(this.velocity.multiply((double)(15.0F * affectPerc), (double)0.0F, (double)(15.0F * affectPerc)));

      for(Vorticy vorticy : this.vorticies) {
         double d = vorticy.getPosition().multiply((double)1.0F, (double)0.0F, (double)1.0F).distanceTo(pos.multiply((double)1.0F, (double)0.0F, (double)1.0F));
         Vec3 rPos = pos.subtract(vorticy.getPosition());
         Vec3 rot = (new Vec3(rPos.z, (double)0.0F, -rPos.x)).normalize();
         int windWid = (int)((float)windfieldWidth * vorticy.widthPerc);
         float p = this.getRankine(d, windWid);
         float wind = vorticy.windspeedMult * (float)this.windspeed;
         motion = motion.add(rot.multiply((double)(wind * p), (double)0.0F, (double)(wind * p)));
      }

      return (float)motion.length();
   }

   public void initFirstTime() {
      this.ID = (long)(LastUsedStormID++);
   }

   public void pull(Particle particle, float multiplier) {
      int windfieldWidth = Math.max((int)this.width, 40);
      BlockPos blockPos = new BlockPos((int)particle.getPos().x, (int)particle.getPos().y, (int)particle.getPos().z);
      int worldHeight = this.level.getHeightmapPos(Types.MOTION_BLOCKING, blockPos).getY();
      if (worldHeight <= blockPos.getY()) {
         double dist = particle.getPos().distanceTo(new Vec3(this.position.x, particle.getPos().y, this.position.z));
         if (!(dist > (double)windfieldWidth)) {
            Vec3 relativePos = particle.getPos().subtract(this.position);
            double heightDifference = particle.getPos().y - this.position.y;
            if (!(Math.abs(heightDifference) > (double)150.0F)) {
               Vec3 inward = (new Vec3(-relativePos.x, (double)0.0F, -relativePos.z)).normalize();
               Vec3 rotational = (new Vec3(relativePos.z, (double)0.0F, -relativePos.x)).normalize();
               double windEffect = (double)this.getWind(particle.getPos());
               double effectStrength = Math.clamp(windEffect / (double)Math.max((float)this.windspeed, 130.0F), (double)0.0F, (double)1.0F) * (double)multiplier;
               double pullFactor = (double)4.0F;
               pullFactor -= Math.max(heightDifference, (double)0.0F) / (double)100.0F * (double)3.0F;
               pullFactor /= (double)Math.max(this.width / 100.0F, 1.0F);
               if (dist <= (double)(this.width / (this.rankineFactor * 2.0F))) {
                  pullFactor = (double)-1.5F;
               }

               Vec3 add = inward.multiply(effectStrength * pullFactor, effectStrength * pullFactor, effectStrength * pullFactor).add(rotational.multiply(effectStrength, effectStrength, effectStrength));
               add = add.add(new Vec3((double)0.0F, effectStrength, (double)0.0F));
               if (particle instanceof ParticleData) {
                  ParticleData particleData = (ParticleData)particle;
                  particleData.addVelocity(add.multiply((double)0.05F, (double)0.05F, (double)0.05F));
               }

            }
         }
      }
   }

   public void pull(Entity entity, float multiplier) {
      int windfieldWidth = Math.max((int)this.width, 40);
      int worldHeight = this.level.getHeightmapPos(Types.MOTION_BLOCKING, entity.blockPosition()).getY();
      if (worldHeight <= entity.blockPosition().getY()) {
         double dist = entity.position().distanceTo(new Vec3(this.position.x, entity.position().y, this.position.z));
         if (!(dist > (double)windfieldWidth)) {
            Vec3 relativePos = entity.position().subtract(this.position);
            double heightDifference = entity.position().y - this.position.y;
            if (!(Math.abs(heightDifference) > (double)150.0F)) {
               Vec3 inward = (new Vec3(-relativePos.x, (double)0.0F, -relativePos.z)).normalize();
               Vec3 rotational = (new Vec3(relativePos.z, (double)0.0F, -relativePos.x)).normalize();
               double windEffect = (double)this.getWind(entity.position());
               if (!(windEffect < (double)60.0F)) {
                  double effectStrength = Math.clamp((windEffect - (double)60.0F) / (double)Math.max((float)this.windspeed * 1.2F, 130.0F), (double)0.0F, (double)1.0F) * (double)multiplier * (double)1.5F;
                  double pullFactor = (double)4.0F;
                  pullFactor -= Math.max(heightDifference, (double)0.0F) / (double)65.0F * (double)3.0F;
                  if (dist <= (double)(this.width / this.rankineFactor)) {
                     pullFactor = (double)-1.5F;
                  }

                  Vec3 add = inward.multiply(effectStrength * pullFactor, effectStrength * pullFactor, effectStrength * pullFactor).add(rotational.multiply(effectStrength, effectStrength, effectStrength));
                  add = add.add(new Vec3((double)0.0F, effectStrength, (double)0.0F));
                  entity.addDeltaMovement(add.multiply((double)0.05F, (double)0.05F, (double)0.05F));
                  Vec3 motion = entity.getDeltaMovement();
                  if (motion.y > (double)-0.25F) {
                     entity.fallDistance = 0.0F;
                  }

               }
            }
         }
      }
   }

   @OnlyIn(Dist.CLIENT)
   public void tickClient() {
      Player player = Minecraft.getInstance().player;
      if (player != null && (this.undergroundWind == null || this.undergroundWind.isStopped()) && !this.dead) {
         this.undergroundWind = new MovingSoundStreamingSource(this, (SoundEvent)ModSounds.UNDERGROUND_WIND.value(), SoundSource.WEATHER, 0.1F, 1.0F, (float)this.maxWidth, true, 3);
         Minecraft.getInstance().getSoundManager().play(this.undergroundWind);
      }

      if (player != null && this.stormType == 2) {
         this.smoothWidth = this.width;
         this.smoothWindspeed = Mth.lerp(0.1F, this.smoothWindspeed, (float)this.windspeed);
         if ((this.eyewallWind == null || this.eyewallWind.isStopped()) && !this.dead) {
            this.eyewallWind = new MovingSoundStreamingSource(this, (SoundEvent)ModSounds.EYEWALL_WIND.value(), SoundSource.WEATHER, 0.1F, 1.0F, (float)this.maxWidth, true, 2);
            Minecraft.getInstance().getSoundManager().play(this.eyewallWind);
         }
      }

      if (player != null && this.stormType == 0) {
         this.smoothWindspeed = Mth.lerp(0.1F, this.smoothWindspeed, (float)this.windspeed);
         this.smoothWidth = Mth.lerp(0.05F, this.smoothWidth, this.width);
         if (this.stage >= 3) {
            if ((this.tornadicWind == null || this.tornadicWind.isStopped()) && !this.dead) {
               this.tornadicWind = new MovingSoundStreamingSource(this, (SoundEvent)ModSounds.TORNADIC_WIND.value(), SoundSource.WEATHER, 0.1F, 1.0F, this.width, true, 1);
               Minecraft.getInstance().getSoundManager().play(this.tornadicWind);
            }

            if ((this.tornadicDamage == null || this.tornadicDamage.isStopped()) && !this.dead) {
               this.tornadicDamage = new MovingSoundStreamingSource(this, (SoundEvent)ModSounds.TORNADIC_DAMAGE.value(), SoundSource.WEATHER, 0.1F, 1.0F, this.width, true, 4);
               Minecraft.getInstance().getSoundManager().play(this.tornadicDamage);
            }

            if (this.windspeed >= 40 && !player.isCreative() && !player.isSpectator()) {
               this.pull((Entity)player, 2.5F);
            }
         }

         if (this.stage >= 2 && (this.supercellWind == null || this.supercellWind.isStopped()) && !this.dead) {
            this.supercellWind = new MovingSoundStreamingSource(this, (SoundEvent)ModSounds.SUPERCELL_WIND.value(), SoundSource.WEATHER, 0.1F, 1.0F, this.width, true, 0);
            Minecraft.getInstance().getSoundManager().play(this.supercellWind);
         }

         if (this.stage < 3 && this.tornadicWind != null) {
            this.tornadicWind.stopPlaying();
            this.tornadicWind = null;
         }

         if (this.stage < 2 && this.supercellWind != null) {
            this.supercellWind.stopPlaying();
            this.supercellWind = null;
         }

         for(int i = 0; i < this.listParticleDebris.size(); ++i) {
            EntityRotFX debris = this.listParticleDebris.get(i);
            if (!debris.isAlive()) {
               this.listParticleDebris.remove(debris);
            } else {
               this.pull((Particle)debris, 1.0F);
            }
         }
      }

   }

   public void remove() {
      this.dead = true;
      if (EffectiveSide.get().equals(LogicalSide.CLIENT)) {
         this.cleanupClient();
      }

      this.cleanup();
   }

   public void cleanup() {
      this.weatherHandler = null;
      if (!this.level.isClientSide()) {
         for(ChunkPos chunkPos : this.forceLoadedChunks) {
            ((ServerLevel)this.level).setChunkForced(chunkPos.x, chunkPos.z, false);
         }
      }

   }

   @OnlyIn(Dist.CLIENT)
   public void cleanupClient() {
      if (this.tornadicWind != null) {
         this.tornadicWind.stopPlaying();
         this.tornadicWind = null;
      }

      if (this.tornadicDamage != null) {
         this.tornadicDamage.stopPlaying();
         this.tornadicDamage = null;
      }

      if (this.supercellWind != null) {
         this.supercellWind.stopPlaying();
         this.supercellWind = null;
      }

      if (this.eyewallWind != null) {
         this.eyewallWind.stopPlaying();
         this.eyewallWind = null;
      }

      if (this.undergroundWind != null) {
         this.undergroundWind.stopPlaying();
         this.undergroundWind = null;
      }

   }

   public void read() {
      this.nbtSyncFromServer();
   }

   public void write() {
      this.nbtSyncForClient();
   }

   public int getUpdateRate() {
      return this.stormType == 0 && this.stage >= 3 ? 2 : 40;
   }

   public void nbtSyncFromServer() {
      CachedNBTTagCompound nbt = this.getNBTCache();
      this.ID = nbt.getLong("ID");
      this.onWater = nbt.getBoolean("onWater");
      this.position = new Vec3(nbt.getDouble("positionX"), nbt.getDouble("positionY"), nbt.getDouble("positionZ"));
      this.velocity = new Vec3(nbt.getDouble("velocityX"), nbt.getDouble("velocityY"), nbt.getDouble("velocityZ"));
      this.windspeed = nbt.getInt("windspeed");
      this.cycloneWindspeed = (float)this.windspeed;
      this.width = nbt.getFloat("width");
      this.energy = nbt.getInt("energy");
      this.coldEnergy = nbt.getInt("coldEnergy");
      this.stormType = nbt.getInt("stormType");
      this.stage = nbt.getInt("stage");
      this.dead = nbt.getBoolean("dead");
      this.isDying = nbt.getBoolean("isDying");
      this.maxWidth = nbt.getInt("maxWidth");
      this.maxWindspeed = nbt.getInt("maxWindspeed");
      this.maxStage = nbt.getInt("maxStage");
      this.maxProgress = nbt.getInt("maxProgress");
      this.ticksSinceDying = nbt.getInt("ticksSinceDying");
      this.growthSpeed = nbt.getInt("growthSpeed");
      this.visualOnly = nbt.getBoolean("visualOnly");
      this.aimedAtPlayer = nbt.getBoolean("aimedAtPlayer");
      this.cirus = nbt.getBoolean("cirus");
      this.touchdownSpeed = nbt.getInt("touchdownSpeed");
      this.occlusion = nbt.getFloat("occlusion");
      CompoundTag vorticiesData = nbt.get("vorticies");
      int vorticyCount = vorticiesData.getInt("vorticyCount");
      this.vorticies.clear();

      for(int i = 0; i < vorticyCount; ++i) {
         CompoundTag vorticyData = vorticiesData.getCompound("vorticy" + i);
         Vorticy vorticy = new Vorticy(this, vorticyData.getFloat("maxWindspeedMult"), vorticyData.getFloat("widthPerc"), vorticyData.getFloat("distancePerc"), vorticyData.getInt("lifetime"));
         vorticy.dead = vorticyData.getBoolean("dead");
         vorticy.angle = vorticyData.getFloat("angle");
         vorticy.tickCount = vorticyData.getInt("tickCount");
         vorticy.windspeedMult = vorticyData.getFloat("windspeedMult");
         this.vorticies.add(vorticy);
      }

   }

   public void nbtSyncForClient() {
      CachedNBTTagCompound nbt = this.getNBTCache();
      CompoundTag vorticiesData = new CompoundTag();
      vorticiesData.putInt("vorticyCount", this.vorticies.size());

      for(int i = 0; i < this.vorticies.size(); ++i) {
         Vorticy vorticy = this.vorticies.get(i);
         CompoundTag vorticyData = new CompoundTag();
         vorticyData.putBoolean("dead", vorticy.dead);
         vorticyData.putFloat("windspeedMult", vorticy.windspeedMult);
         vorticyData.putFloat("maxWindspeedMult", vorticy.maxWindspeedMult);
         vorticyData.putFloat("widthPerc", vorticy.widthPerc);
         vorticyData.putFloat("distancePerc", vorticy.distancePerc);
         vorticyData.putFloat("angle", vorticy.angle);
         vorticyData.putInt("lifetime", vorticy.lifetime);
         vorticyData.putInt("tickCount", vorticy.tickCount);
         vorticiesData.put("vorticy" + i, vorticyData);
      }

      nbt.put("vorticies", vorticiesData);
      nbt.putBoolean("onWater", this.onWater);
      nbt.putInt("touchdownSpeed", this.touchdownSpeed);
      nbt.putBoolean("cirus", this.cirus);
      nbt.putBoolean("aimedAtPlayer", this.aimedAtPlayer);
      nbt.putBoolean("visualOnly", this.visualOnly);
      nbt.putBoolean("isDying", this.isDying);
      nbt.putInt("maxWidth", this.maxWidth);
      nbt.putInt("maxWindspeed", this.maxWindspeed);
      nbt.putInt("maxStage", this.maxStage);
      nbt.putInt("maxProgress", this.maxProgress);
      nbt.putInt("ticksSinceDying", this.ticksSinceDying);
      nbt.putInt("growthSpeed", this.growthSpeed);
      nbt.putFloat("occlusion", this.occlusion);
      nbt.putDouble("positionX", this.position.x);
      nbt.putDouble("positionY", this.position.y);
      nbt.putDouble("positionZ", this.position.z);
      nbt.putDouble("velocityX", this.velocity.x);
      nbt.putDouble("velocityY", this.velocity.y);
      nbt.putDouble("velocityZ", this.velocity.z);
      nbt.putLong("ID", this.ID);
      nbt.getNewNBT().putLong("ID", this.ID);
      nbt.putInt("windspeed", this.windspeed);
      nbt.putFloat("width", this.width);
      nbt.putInt("energy", this.energy);
      nbt.putInt("coldEnergy", this.coldEnergy);
      nbt.putInt("stormType", this.stormType);
      nbt.putInt("stage", this.stage);
      nbt.putBoolean("dead", this.dead);
   }

   public CachedNBTTagCompound getNBTCache() {
      return this.nbtCache;
   }

   public static float getBlockStrength(Block block, Level level, @Nullable BlockPos blockPos) {
      ItemStack item = new ItemStack(Items.IRON_AXE);
      float destroySpeed = block.defaultBlockState().getDestroySpeed(level, blockPos != null ? blockPos : BlockPos.ZERO);

      try {
         destroySpeed /= item.getDestroySpeed(block.defaultBlockState());
      } catch (Exception e) {
         PMWeather.LOGGER.warn(e.getMessage());
      }

      return 60.0F + Mth.sqrt(destroySpeed) * 60.0F;
   }
}
