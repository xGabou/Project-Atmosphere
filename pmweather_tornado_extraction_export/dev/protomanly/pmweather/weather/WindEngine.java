package dev.protomanly.pmweather.weather;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.util.Util;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class WindEngine {
   public static SimplexNoise simplexNoise;

   public WindEngine() {
      super();
   }

   public static void init(WeatherHandler weatherHandler) {
      simplexNoise = new SimplexNoise(new LegacyRandomSource(weatherHandler.seed));
   }

   public static double FBM(Vec3 pos, int octaves, float lacunarity, float gain, float amplitude) {
      double y = (double)0.0F;
      if (simplexNoise != null) {
         for(int i = 0; i < Math.max(octaves, 1); ++i) {
            y += (double)amplitude * simplexNoise.getValue(pos.x, pos.y, pos.z);
            pos = pos.multiply((double)lacunarity, (double)lacunarity, (double)lacunarity);
            amplitude *= gain;
         }
      }

      return y;
   }

   public static float getSwirl(Vec3 position, Level level, float sampleSize) {
      Vec3 sample1Z = getWind(position.add((double)0.0F, (double)0.0F, (double)sampleSize), level).normalize();
      Vec3 sample2Z = getWind(position.add((double)0.0F, (double)0.0F, (double)(-sampleSize)), level).normalize();
      Vec3 sample1X = getWind(position.add((double)(-sampleSize), (double)0.0F, (double)0.0F), level).normalize();
      Vec3 sample2X = getWind(position.add((double)sampleSize, (double)0.0F, (double)0.0F), level).normalize();
      double compZ = (-sample1Z.dot(sample2Z) + (double)1.0F) / (double)2.0F;
      double compX = (-sample1X.dot(sample2X) + (double)1.0F) / (double)2.0F;
      return (float)(compZ * compX);
   }

   public static Vec3 getWind(Vec3 position, Level level) {
      return getWind(position, level, false, false, true, false);
   }

   public static Vec3 getWind(Vec3 position, Level level, boolean ignoreStorms, boolean ignoreTornadoes, boolean windCheck) {
      return getWind(position, level, ignoreStorms, ignoreTornadoes, windCheck, false);
   }

   public static Vec3 getWind(Vec3 position, Level level, boolean ignoreStorms, boolean ignoreTornadoes, boolean windCheck, boolean windAnyway) {
      Vec3 wind = Vec3.ZERO;
      Vec3 rawWind = Vec3.ZERO;
      BlockPos blockPos = new BlockPos((int)position.x, (int)position.y, (int)position.z);
      List<Storm> tornadicStorms = new ArrayList<Storm>();
      if (level == null) {
         PMWeather.LOGGER.warn("Level is null");
         return wind;
      } else {
         int worldHeight = level.getHeightmapPos(Types.MOTION_BLOCKING, blockPos).getY();
         if (windCheck && !windAnyway) {
            if (!Util.canWindAffect(position, level)) {
               return wind;
            }
         } else if (!windAnyway && position.y < (double)worldHeight) {
            return wind;
         }

         if (simplexNoise != null) {
            float timeScale = 20000.0F;
            float scale = 12000.0F;
            double ang = FBM(new Vec3(position.x / (double)(scale * 3.0F), position.z / (double)(scale * 3.0F), (double)((float)level.getGameTime() / (timeScale * 6.0F))), 5, 2.0F, 0.1F, 1.0F);
            ang *= Math.PI;
            Vec3 dir = (new Vec3(Math.cos(ang), (double)0.0F, Math.sin(ang))).normalize();
            double speed = Math.max(simplexNoise.getValue(-position.z / (double)scale, -position.x / (double)scale, (double)(-((float)level.getGameTime()) / timeScale)) + (double)1.0F, (double)0.0F) * (double)10.0F;
            wind = wind.add(dir.multiply(speed, speed, speed));
            WeatherHandler weatherHandler;
            if (level.isClientSide()) {
               weatherHandler = GameBusClientEvents.weatherHandler;
            } else {
               weatherHandler = GameBusEvents.MANAGERS.get(level.dimension());
            }

            if (weatherHandler != null && !ignoreStorms) {
               for(Storm storm : weatherHandler.getStorms()) {
                  if (!storm.visualOnly) {
                     if (storm.stage >= 3 && storm.stormType == 0) {
                        tornadicStorms.add(storm);
                     }

                     double distance = position.multiply((double)1.0F, (double)0.0F, (double)1.0F).distanceTo(storm.position.multiply((double)1.0F, (double)0.0F, (double)1.0F));
                     if (storm.stormType == 2) {
                        Vec3 relativePos = position.subtract(storm.position);
                        Vec3 inward = (new Vec3(-relativePos.x, (double)0.0F, -relativePos.z)).normalize();
                        Vec3 rotational = (new Vec3(relativePos.z, (double)0.0F, -relativePos.x)).normalize();
                        double pullStrngth = (double)((float)storm.windspeed * 0.3F);
                        double rotStrngth = (double)((float)storm.windspeed * 0.7F);
                        float mult = (float)Math.pow((double)1.0F - Math.clamp(distance / (double)storm.maxWidth, (double)0.0F, (double)1.0F), (double)3.0F);
                        if (distance < (double)((float)storm.maxWidth * 0.1F)) {
                           mult += (float)Math.pow((double)1.0F - Math.clamp(distance / (double)((float)storm.maxWidth * 0.1F), (double)0.0F, (double)1.0F), (double)0.75F) * 0.15F;
                           mult = Math.clamp(mult, 0.0F, 1.0F);
                        }

                        double d = (double)((float)storm.maxWidth / (1.5F + (float)storm.windspeed / 30.0F));
                        float effect = Math.clamp((float)distance / (float)storm.maxWidth, 0.0F, 1.0F);
                        float noiseX = (float)FBM(new Vec3(position.x / (double)((float)storm.maxWidth * 0.5F), position.z / (double)((float)storm.maxWidth * 0.5F), (double)((float)level.getGameTime() / timeScale)), 5, 2.0F, 0.2F, 1.0F);
                        float noiseZ = (float)FBM(new Vec3(position.z / (double)((float)storm.maxWidth * 0.5F), position.x / (double)((float)storm.maxWidth * 0.5F), (double)((float)level.getGameTime() / timeScale)), 5, 2.0F, 0.2F, 1.0F);
                        relativePos = relativePos.add(new Vec3((double)(noiseX * (float)storm.maxWidth * 0.3F * effect), (double)0.0F, (double)(noiseZ * (float)storm.maxWidth * 0.3F * effect)));
                        double angle = Math.atan2(relativePos.z, relativePos.x) - distance / d;
                        float bands = (float)Math.sin((angle + Math.toRadians((double)((float)storm.tickCount / 8.0F))) * (double)4.0F);
                        mult += Mth.lerp(1.0F - Math.clamp((float)distance / ((float)storm.maxWidth * 0.35F), 0.0F, 1.0F), (float)Math.pow((double)Math.abs(bands), (double)2.0F) * 0.5F * mult, 0.5F * mult);
                        float noise = (float)FBM(new Vec3(position.x / (double)((float)storm.maxWidth * 0.5F), position.z / (double)((float)storm.maxWidth * 0.5F), (double)((float)level.getGameTime() / timeScale)), 5, 2.0F, 0.2F, 1.0F);
                        mult *= Math.clamp(noise, 0.0F, 1.0F) * 0.2F + 0.8F;
                        float noise2 = (float)FBM(new Vec3(position.x / (double)((float)storm.maxWidth * 0.1F), position.z / (double)((float)storm.maxWidth * 0.1F), (double)((float)level.getGameTime() / timeScale)), 5, 2.0F, 0.2F, 1.0F);
                        mult *= Math.clamp(noise2, 0.0F, 1.0F) * 0.1F + 0.9F;
                        mult *= 1.15F + (float)Math.pow((double)1.0F - Math.clamp((distance - (double)((float)storm.maxWidth * 0.1F)) / (double)((float)storm.maxWidth * 0.1F), (double)0.0F, (double)1.0F), (double)2.5F) * 0.35F;
                        float eye = (float)Math.pow(Math.clamp(distance / (double)((float)storm.maxWidth * 0.1F), (double)0.0F, (double)1.0F), (double)Mth.lerp(Math.clamp((float)storm.windspeed / 120.0F, 0.0F, 1.0F), 0.5F, 4.0F));
                        mult *= Mth.lerp((float)Math.pow((double)Math.clamp((float)storm.windspeed / 65.0F, 0.0F, 1.0F), (double)2.0F), 1.0F, eye);
                        Vec3 vec = inward.multiply(pullStrngth, (double)0.0F, pullStrngth).add(rotational.multiply(rotStrngth, (double)0.0F, rotStrngth)).multiply((double)mult, (double)0.0F, (double)mult);
                        if (vec.length() > (double)storm.windspeed) {
                           double dif = vec.length() - (double)storm.windspeed;
                           vec = vec.subtract(new Vec3(dif, (double)0.0F, dif));
                        }

                        rawWind = rawWind.add(vec);

                        for(Vorticy vorticy : storm.vorticies) {
                           Vec3 pos = vorticy.getPosition();
                           Vec3 rPos = position.subtract(pos);
                           Vec3 in = (new Vec3(-rPos.x, (double)0.0F, -rPos.z)).normalize();
                           Vec3 rot = (new Vec3(rPos.z, (double)0.0F, -rPos.x)).normalize();
                           float width = vorticy.getWidth();
                           double dist = position.multiply((double)1.0F, (double)0.0F, (double)1.0F).distanceTo(pos.multiply((double)1.0F, (double)0.0F, (double)1.0F));
                           double pullStrn = (double)(vorticy.windspeedMult * (float)storm.windspeed * 0.3F);
                           double rotStrn = (double)(vorticy.windspeedMult * (float)storm.windspeed * 0.7F);
                           float m = (float)Math.pow((double)(1.0F - Math.clamp((float)dist / width, 0.0F, 1.0F)), (double)3.75F);
                           m *= Math.clamp((float)dist / (width * 0.1F), 0.0F, 1.0F);
                           m *= 7.0F;
                           Vec3 v = in.multiply(pullStrn, (double)0.0F, pullStrn).add(rot.multiply(rotStrn, (double)0.0F, rotStrn)).multiply((double)m, (double)0.0F, (double)m);
                           rawWind = rawWind.add(v);
                        }
                     }

                     if (storm.stormType == 1) {
                        Vec2 v2fWorldPos = new Vec2((float)position.x, (float)position.z);
                        Vec2 stormVel = new Vec2((float)storm.velocity.x, (float)storm.velocity.z);
                        Vec2 v2fStormPos = new Vec2((float)storm.position.x, (float)storm.position.z);
                        Vec2 right = (new Vec2(stormVel.y, -stormVel.x)).normalized();
                        Vec2 fwd = stormVel.normalized();
                        Vec2 le = Util.mulVec2(right, -((float)ServerConfig.stormSize) * 5.0F);
                        Vec2 ri = Util.mulVec2(right, (float)ServerConfig.stormSize * 5.0F);
                        Vec2 off = Util.mulVec2(fwd, -((float)Math.pow(Mth.clamp(distance / (double)((float)ServerConfig.stormSize * 5.0F), (double)0.0F, (double)1.0F), (double)2.0F)) * (float)ServerConfig.stormSize * 1.5F);
                        le = le.add(off);
                        ri = ri.add(off);
                        le = le.add(v2fStormPos);
                        ri = ri.add(v2fStormPos);
                        float d = Util.minimumDistance(le, ri, v2fWorldPos);
                        Vec2 nearPoint = Util.nearestPoint(le, ri, v2fWorldPos);
                        Vec2 facing = v2fWorldPos.add(nearPoint.negated());
                        float behind = -facing.dot(fwd);
                        behind += (float)FBM(new Vec3(position.x / (ServerConfig.stormSize * (double)2.0F), position.z / (ServerConfig.stormSize * (double)2.0F), (double)((float)level.getGameTime() / timeScale)), 5, 2.0F, 0.2F, 1.0F) * (float)ServerConfig.stormSize * 0.25F;
                        float perc = 0.0F;
                        float sze = (float)ServerConfig.stormSize * 4.0F;
                        behind += (float)ServerConfig.stormSize;
                        if (behind > 0.0F) {
                           float p = Mth.clamp(Math.abs(behind) / sze, 0.0F, 1.0F);
                           float start = 0.06F;
                           if (storm.stage >= 3) {
                              start = Mth.lerp((float)storm.energy / 100.0F, start, start * 2.5F);
                           }

                           if (p <= start) {
                              p /= start;
                           } else {
                              p = 1.0F - (p - start) / (1.0F - start);
                              if (storm.stage >= 3) {
                                 p = (float)Math.pow((double)p, (double)Mth.lerp((float)storm.energy / 100.0F, 1.0F, 0.75F));
                              }
                           }

                           perc = Mth.clamp(p, 0.0F, 1.0F);
                        }

                        if (storm.stage < 1) {
                           perc *= (float)storm.energy / 100.0F;
                        } else if (storm.stage == 1) {
                           perc *= (float)storm.energy / 125.0F + 1.0F;
                        } else if (storm.stage == 2) {
                           perc *= (float)storm.energy / 200.0F + 1.8F;
                        } else {
                           perc *= (float)storm.energy / 100.0F + 2.3F;
                        }

                        float gustNoise = (float)FBM(new Vec3(position.z / (ServerConfig.stormSize * (double)2.0F), position.x / (ServerConfig.stormSize * (double)2.0F), (double)((float)level.getGameTime() / timeScale)), 7, 2.0F, 0.4F, 1.0F);
                        if (storm.stage >= 3) {
                           float p = (float)storm.energy / 100.0F;
                           gustNoise *= 1.0F - p;
                           perc *= 1.0F + p * 0.3F;
                        }

                        perc *= Mth.lerp(Mth.clamp(behind / ((float)ServerConfig.stormSize * 3.0F), 0.0F, 1.0F), (float)Math.pow((double)(0.8F + gustNoise * 0.5F), (double)1.5F), 0.5F);
                        perc *= Mth.sqrt(1.0F - Mth.clamp(d / sze, 0.0F, 1.0F));
                        wind = wind.add(storm.velocity.multiply((double)(perc * 13.0F) * ServerConfig.squallStrengthMultiplier, (double)0.0F, (double)(perc * 13.0F) * ServerConfig.squallStrengthMultiplier));
                     }

                     if (storm.stormType == 0) {
                        Vec3 relativePos = position.subtract(storm.position);
                        Vec3 inward = (new Vec3(-relativePos.x, (double)0.0F, -relativePos.z)).normalize();
                        Vec3 rotational = (new Vec3(relativePos.z, (double)0.0F, -relativePos.x)).normalize();
                        double pullStrngth = (double)1.0F - Math.clamp(distance / (ServerConfig.stormSize * (double)4.0F), (double)0.0F, (double)1.0F);
                        double rotStrngth = (double)1.0F - Math.clamp(distance / ServerConfig.stormSize, (double)0.0F, (double)1.0F);
                        if (storm.stage < 1) {
                           pullStrngth *= (double)0.5F;
                           pullStrngth *= (double)((float)storm.energy / 100.0F);
                           rotStrngth *= (double)0.0F;
                        } else if (storm.stage == 1) {
                           pullStrngth *= (double)((float)storm.energy / 200.0F + 0.5F);
                           rotStrngth *= (double)((float)storm.energy / 100.0F * 0.1F);
                        } else if (storm.stage == 2) {
                           pullStrngth *= (double)(1.0F + (float)storm.energy / 100.0F);
                           rotStrngth *= (double)(0.1F + (float)storm.energy / 100.0F * 0.4F);
                        } else {
                           pullStrngth *= (double)(2.0F + (float)storm.windspeed / 400.0F);
                           rotStrngth *= (double)(0.5F + (float)storm.windspeed / 400.0F);
                        }

                        pullStrngth *= (double)0.5F;
                        rotStrngth *= (double)6.0F;
                        Vec3 vec = inward.multiply(pullStrngth, (double)0.0F, pullStrngth).add(rotational.multiply(rotStrngth, (double)0.0F, rotStrngth)).multiply((double)20.0F, (double)20.0F, (double)20.0F);
                        wind = wind.add(vec);
                     }
                  }
               }
            }
         }

         if (wind.length() > (double)30.0F) {
            double over = wind.length() - (double)40.0F;
            double val = (double)30.0F + over / (double)3.0F;
            wind = wind.normalize().multiply(val, val, val);
         }

         if (blockPos.getY() > 85) {
            float val = Math.clamp((float)(blockPos.getY() - 85) / 40.0F, 0.0F, 1.0F) / 3.0F + 1.0F;
            wind = wind.multiply((double)val, (double)val, (double)val);
         }

         wind = wind.add(rawWind);
         int heightAbove = blockPos.getY() - worldHeight;
         if (heightAbove > 0) {
            float val = Math.clamp((float)heightAbove / 15.0F, 0.0F, 1.0F) / 3.0F + 1.0F;
            wind = wind.multiply((double)val, (double)val, (double)val);
         }

         float tornadicEffect = 0.0F;
         Vec3 tornadicWind = Vec3.ZERO;
         if (!ignoreStorms && !ignoreTornadoes) {
            for(Storm tornadicStorm : tornadicStorms) {
               Vec3 relativePos = position.subtract(tornadicStorm.position);
               Vec3 inward = (new Vec3(-relativePos.x, (double)0.0F, -relativePos.z)).normalize();
               Vec3 rotational = (new Vec3(relativePos.z, (double)0.0F, -relativePos.x)).normalize();
               double distance = position.distanceTo(tornadicStorm.position);
               if (!(distance > (double)(tornadicStorm.width * 2.0F))) {
                  double windEffect = (double)tornadicStorm.getWind(position);
                  tornadicEffect = Math.clamp((float)windEffect / (float)Math.max(tornadicStorm.windspeed, 30), tornadicEffect, 1.0F);
                  if (Float.isNaN(tornadicEffect)) {
                     tornadicEffect = 0.0F;
                  }

                  double inPerc = 0.35;
                  tornadicWind = tornadicWind.add(inward.multiply(windEffect * inPerc, windEffect * inPerc, windEffect * inPerc)).add(rotational.add(windEffect * ((double)1.0F - inPerc), windEffect * ((double)1.0F - inPerc), windEffect * ((double)1.0F - inPerc)));
               }
            }
         }

         return wind.lerp(tornadicWind, (double)tornadicEffect);
      }
   }

   public static Vec3 getWind(BlockPos position, Level level, boolean ignoreStorms, boolean ignoreTornadoes, boolean windCheck) {
      return getWind(new Vec3((double)position.getX(), (double)(position.getY() + 1), (double)position.getZ()), level, ignoreStorms, ignoreTornadoes, windCheck, false);
   }

   public static Vec3 getWind(BlockPos position, Level level) {
      return getWind(new Vec3((double)position.getX(), (double)(position.getY() + 1), (double)position.getZ()), level, false, false, true, false);
   }
}
