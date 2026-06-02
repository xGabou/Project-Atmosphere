package dev.protomanly.pmweather.weather;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.data.LevelSavedData;
import dev.protomanly.pmweather.interfaces.IWorldData;
import dev.protomanly.pmweather.util.Util;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

public abstract class WeatherHandler implements IWorldData {
   private List<Storm> storms = new ArrayList<Storm>();
   private ResourceKey<Level> dimension;
   public HashMap<Long, Storm> lookupStormByID = new HashMap<Long, Storm>();
   public long seed;

   public WeatherHandler(ResourceKey<Level> dimension) {
      super();
      this.dimension = dimension;
   }

   public void tick() {
      Level level = this.getWorld();
      if (level != null) {
         List<Storm> stormList = this.getStorms();

         for(int i = 0; i < stormList.size(); ++i) {
            Storm storm = stormList.get(i);
            if (this instanceof WeatherHandlerServer) {
               WeatherHandlerServer weatherHandlerServer = (WeatherHandlerServer)this;
               if (storm.dead) {
                  this.removeStorm(storm.ID);
                  weatherHandlerServer.syncStormRemove(storm);
                  continue;
               }
            }

            if (!storm.dead) {
               storm.tick();
            } else {
               this.removeStorm(storm.ID);
            }
         }
      }

   }

   public List<Storm> getStorms() {
      return this.storms;
   }

   public void addStorm(Storm storm) {
      if (!this.lookupStormByID.containsKey(storm.ID)) {
         this.storms.add(storm);
         this.lookupStormByID.put(storm.ID, storm);
      } else {
         PMWeather.LOGGER.warn("Tried to add a storm with existing ID: {}", storm.ID);
      }

   }

   public void removeStorm(long id) {
      Storm storm = this.lookupStormByID.get(id);
      if (storm != null) {
         storm.remove();
         this.storms.remove(storm);
         this.lookupStormByID.remove(id);
      } else {
         PMWeather.LOGGER.warn("Tried to remove a non-existent storm with ID: {}", id);
      }

   }

   public float getPrecipitation(Vec3 pos) {
      float precip = 0.0F;
      float cloudDensity = Clouds.getCloudDensity(this, new Vector2f((float)pos.x, (float)pos.z), 0.0F);
      if (cloudDensity > 0.15F) {
         precip += (cloudDensity - 0.15F) * 2.0F;
      }

      for(Storm storm : this.getStorms()) {
         if (!storm.visualOnly) {
            double dist = pos.distanceTo(new Vec3(storm.position.x, pos.y, storm.position.z));
            double perc = (double)0.0F;
            float smoothStage = (float)storm.stage + (float)storm.energy / 100.0F;
            if (storm.stage == 3) {
               smoothStage = 3.0F;
            }

            if (storm.stormType == 2) {
               Vec3 cPos = storm.position.multiply((double)1.0F, (double)0.0F, (double)1.0F);
               float intensity = (float)Math.pow((double)Math.clamp((float)storm.windspeed / 65.0F, 0.0F, 1.0F), (double)0.85F);
               Vec3 relPos = cPos.subtract(pos);
               double d = (double)((float)storm.maxWidth / (3.0F + (float)storm.windspeed / 12.0F));
               double d2 = (double)((float)storm.maxWidth / (1.15F + (float)storm.windspeed / 12.0F));
               double dE = (double)((float)storm.maxWidth * 0.65F / (1.75F + (float)storm.windspeed / 12.0F));
               double fac = (double)1.0F + Math.max((dist - (double)((float)storm.maxWidth * 0.2F)) / (double)storm.maxWidth, (double)0.0F) * (double)2.0F;
               d *= fac;
               d2 *= fac;
               double angle = Math.atan2(relPos.z, relPos.x) - dist / d;
               double angle2 = Math.atan2(relPos.z, relPos.x) - dist / d2;
               double angleE = Math.atan2(relPos.z, relPos.x) - dist / dE;
               float weak = 0.0F;
               float strong = 0.0F;
               float intense = 0.0F;
               float staticBands = (float)Math.sin(angle - (Math.PI / 2D));
               staticBands *= (float)Math.pow(Math.clamp(dist / (double)((float)storm.maxWidth * 0.25F), (double)0.0F, (double)1.0F), (double)0.1F);
               staticBands *= 1.25F * (float)Math.pow((double)intensity, (double)0.75F);
               if (staticBands < 0.0F) {
                  weak += Math.abs(staticBands);
               } else {
                  weak += Math.abs(staticBands) * (float)Math.pow((double)1.0F - Math.clamp(dist / (double)((float)storm.maxWidth * 0.65F), (double)0.0F, (double)1.0F), (double)0.5F);
                  weak *= Math.clamp(((float)storm.windspeed - 70.0F) / 40.0F, 0.0F, 1.0F);
               }

               float rotatingBands = (float)Math.sin((angle2 + Math.toRadians((double)((float)storm.tickCount / 8.0F))) * (double)6.0F);
               rotatingBands *= (float)Math.pow(Math.clamp(dist / (double)((float)storm.maxWidth * 0.25F), (double)0.0F, (double)1.0F), (double)0.1F);
               rotatingBands *= 1.25F * (float)Math.pow((double)intensity, (double)0.75F);
               strong += Mth.lerp(0.45F, Math.abs(rotatingBands) * 0.3F + 0.7F, weak);
               intense += Mth.lerp(0.3F, Math.abs(rotatingBands) * 0.2F + 0.8F, weak);
               weak = (Math.abs(rotatingBands) * 0.3F + 0.6F) * weak;
               float localRain = 0.0F;
               localRain += Mth.lerp(Math.clamp(((float)storm.windspeed - 120.0F) / 60.0F, 0.0F, 1.0F), Mth.lerp(Math.clamp(((float)storm.windspeed - 40.0F) / 90.0F, 0.0F, 1.0F), weak, strong), intense);
               float eye = (float)Math.sin((angleE + Math.toRadians((double)((float)storm.tickCount / 4.0F))) * (double)2.0F);
               float efc = Mth.lerp(Math.clamp(((float)storm.windspeed - 100.0F) / 50.0F, 0.0F, 1.0F), 0.15F, 0.4F);
               localRain = Math.max((float)Math.pow((double)1.0F - Math.clamp(dist / (double)((float)storm.maxWidth * efc), (double)0.0F, (double)1.0F), (double)0.5F) * (Math.abs(eye * 0.1F) + 0.9F) * 1.35F * intensity, localRain);
               localRain *= (float)Math.pow((double)1.0F - Math.clamp(dist / (double)storm.maxWidth, (double)0.0F, (double)1.0F), (double)0.5F);
               localRain *= Mth.lerp(0.5F + Math.clamp(((float)storm.windspeed - 65.0F) / 40.0F, 0.0F, 1.0F) * 0.5F, 1.0F, (float)Math.pow(Math.clamp(dist / (double)((float)storm.maxWidth * 0.1F), (double)0.0F, (double)1.0F), (double)2.0F));
               if (localRain > 0.6F) {
                  float dif = (localRain - 0.6F) / 2.5F;
                  localRain -= dif;
               }

               precip += Math.max(localRain - 0.15F, 0.0F) * 2.0F;
            }

            if (storm.stormType == 1) {
               Vec2 v2fWorldPos = new Vec2((float)pos.x, (float)pos.z);
               Vec2 stormVel = new Vec2((float)storm.velocity.x, (float)storm.velocity.z);
               Vec2 v2fStormPos = new Vec2((float)storm.position.x, (float)storm.position.z);
               Vec2 right = (new Vec2(stormVel.y, -stormVel.x)).normalized();
               Vec2 fwd = stormVel.normalized();
               Vec2 le = Util.mulVec2(right, -((float)ServerConfig.stormSize) * 5.0F);
               Vec2 ri = Util.mulVec2(right, (float)ServerConfig.stormSize * 5.0F);
               Vec2 off = Util.mulVec2(fwd, -((float)Math.pow(Mth.clamp(dist / (double)((float)ServerConfig.stormSize * 5.0F), (double)0.0F, (double)1.0F), (double)2.0F)) * (float)ServerConfig.stormSize * 1.5F);
               le = le.add(off);
               ri = ri.add(off);
               le = le.add(v2fStormPos);
               ri = ri.add(v2fStormPos);
               float d = Util.minimumDistance(le, ri, v2fWorldPos);
               if ((double)d > ServerConfig.stormSize * (double)16.0F) {
                  continue;
               }

               Vec2 nearPoint = Util.nearestPoint(le, ri, v2fWorldPos);
               Vec2 facing = v2fWorldPos.add(nearPoint.negated());
               float behind = -facing.dot(fwd);
               float sze = (float)ServerConfig.stormSize * 1.5F;
               sze *= Mth.lerp(Mth.clamp(smoothStage - 1.0F, 0.0F, 1.0F), 4.0F, 12.0F);
               behind += (float)ServerConfig.stormSize / 2.0F;
               if (behind > 0.0F) {
                  float p = Mth.clamp(Math.abs(behind) / sze, 0.0F, 1.0F);
                  float start = 0.06F;
                  if (p <= start) {
                     p /= start;
                  } else {
                     p = 1.0F - (p - start) / (1.0F - start);
                  }

                  perc = (double)((float)Math.pow((double)Mth.clamp(p, 0.0F, 1.0F), (double)3.0F));
               }

               if (storm.stage <= 0) {
                  perc = (double)0.0F;
               } else if (storm.stage == 1) {
                  perc *= (double)((float)storm.energy / 100.0F);
               }

               perc *= (double)Mth.sqrt(1.0F - Mth.clamp(d / sze, 0.0F, 1.0F));
            }

            if (storm.stormType == 0) {
               double coreDist = pos.distanceTo(new Vec3(storm.position.x + (double)2000.0F, pos.y, storm.position.z - (double)900.0F));
               if (Math.min(dist, coreDist) > ServerConfig.stormSize * (double)6.0F) {
                  continue;
               }

               perc = (double)1.0F - Math.clamp(dist / ServerConfig.stormSize, (double)0.0F, (double)1.0F);
               if (storm.stage == 0) {
                  perc *= (double)((float)storm.energy / 100.0F);
               }

               if (storm.stage >= 2) {
                  perc *= (double)Mth.lerp(Math.clamp(smoothStage - 2.0F, 0.0F, 1.0F), 1.0F, storm.occlusion * 0.5F + 0.5F);
               }

               double p = (double)1.0F - Math.clamp(coreDist / (ServerConfig.stormSize * (double)6.0F), (double)0.0F, (double)1.0F);
               if (storm.stage <= 1) {
                  p *= (double)0.0F;
               }

               if (storm.stage >= 2) {
                  p *= (double)Math.clamp((smoothStage - 2.0F) / 0.5F, 0.0F, 1.0F);
               }

               perc = Math.max(p, perc);
            }

            precip += (float)perc;
         }
      }

      return Math.clamp(precip * (float)ServerConfig.rainStrength, 0.0F, 1.0F);
   }

   public abstract Level getWorld();

   public CompoundTag save(CompoundTag data) {
      PMWeather.LOGGER.debug("WeatherHandler save");
      CompoundTag listStormsNBT = new CompoundTag();

      for(int i = 0; i < this.storms.size(); ++i) {
         Storm storm = this.storms.get(i);
         storm.getNBTCache().setUpdateForced(true);
         storm.write();
         storm.getNBTCache().setUpdateForced(false);
         listStormsNBT.put("storm_" + storm.ID, storm.getNBTCache().getNewNBT());
      }

      data.put("stormData", listStormsNBT);
      data.putLong("lastUsedIDStorm", Storm.LastUsedStormID);
      return null;
   }

   public void read() {
      LevelSavedData savedData = (LevelSavedData)((ServerLevel)this.getWorld()).getDataStorage().computeIfAbsent(LevelSavedData.factory(), "pmweather_weather_data");
      savedData.setDataHandler(this);
      PMWeather.LOGGER.debug("Weather Data: {}", savedData.getData());
      CompoundTag data = savedData.getData();
      Storm.LastUsedStormID = data.getLong("lastUsedIDStorm");
      CompoundTag storms = data.getCompound("stormData");

      for(String tagName : storms.getAllKeys()) {
         CompoundTag stormData = storms.getCompound(tagName);
         Storm storm = new Storm(this, this.getWorld(), (Float)null, stormData.getInt("stormType"));

         try {
            storm.getNBTCache().setNewNBT(stormData);
            storm.read();
            storm.getNBTCache().updateCacheFromNew();
         } catch (Exception e) {
            PMWeather.LOGGER.error(e.getMessage(), e);
         }

         this.addStorm(storm);
      }

   }
}
