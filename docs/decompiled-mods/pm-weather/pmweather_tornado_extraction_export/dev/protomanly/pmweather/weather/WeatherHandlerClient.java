package dev.protomanly.pmweather.weather;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.config.ClientConfig;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.particle.ParticleCube;
import dev.protomanly.pmweather.sound.ModSounds;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class WeatherHandlerClient extends WeatherHandler {
   public List<Lightning> lightnings = new ArrayList<Lightning>();

   public WeatherHandlerClient(ResourceKey<Level> dimension) {
      super(dimension);
   }

   public Level getWorld() {
      return Minecraft.getInstance().level;
   }

   public float getHail() {
      Player player = Minecraft.getInstance().player;
      if (player == null) {
         return 0.0F;
      } else {
         float precip = 0.0F;

         for(Storm storm : this.getStorms()) {
            if (!storm.visualOnly) {
               double dist = player.position().distanceTo(new Vec3(storm.position.x + (double)2000.0F, player.position().y, storm.position.z - (double)900.0F));
               if (!(dist > ServerConfig.stormSize * (double)4.0F)) {
                  double perc = (double)0.0F;
                  if (storm.stormType == 0) {
                     perc = (double)1.0F - Math.clamp(dist / (ServerConfig.stormSize * (double)6.0F), (double)0.0F, (double)1.0F);
                     if (storm.stage == 2) {
                        perc *= (double)((float)storm.energy / 100.0F);
                     }

                     if (storm.stage > 2) {
                        perc *= (double)1.0F;
                     }

                     if (storm.stage < 2) {
                        perc *= (double)0.0F;
                     }
                  }

                  precip += (float)perc;
               }
            }
         }

         return Math.clamp(precip, 0.0F, 1.0F);
      }
   }

   public float getPrecipitation() {
      Player player = Minecraft.getInstance().player;
      return player == null ? 0.0F : this.getPrecipitation(player.position());
   }

   public void strike(Vec3 pos) {
      Lightning lightning = new Lightning(pos, this.getWorld());
      this.lightnings.add(lightning);
      Player player = Minecraft.getInstance().player;
      if (player != null) {
         double dist = player.position().multiply((double)1.0F, (double)0.0F, (double)1.0F).distanceTo(pos.multiply((double)1.0F, (double)0.0F, (double)1.0F));
         if (dist > (double)256.0F) {
            this.getWorld().playLocalSound(pos.x, pos.y, pos.z, (SoundEvent)ModSounds.THUNDER_FAR.value(), SoundSource.WEATHER, 5000.0F, PMWeather.RANDOM.nextFloat(0.8F, 1.0F), true);
         } else {
            this.getWorld().playLocalSound(pos.x, pos.y, pos.z, (SoundEvent)ModSounds.THUNDER_NEAR.value(), SoundSource.WEATHER, 5000.0F, PMWeather.RANDOM.nextFloat(0.8F, 1.0F), true);
         }
      }

   }

   public void tick() {
      super.tick();
      Iterator<Lightning> iterator = this.lightnings.iterator();

      while(iterator.hasNext()) {
         Lightning lightning = iterator.next();
         if (!lightning.dead && lightning.level == this.getWorld()) {
            lightning.tick();
         } else {
            iterator.remove();
         }
      }

   }

   public void nbtSyncFromServer(CompoundTag compoundTag) {
      String command = compoundTag.getString("command");
      if (command.equals("syncStormNew")) {
         CompoundTag stormCompoundTag = compoundTag.getCompound("data");
         long ID = stormCompoundTag.getLong("ID");
         PMWeather.LOGGER.debug("syncStormNew, ID: {}", ID);
         Storm storm = new Storm(this, this.getWorld(), (Float)null, stormCompoundTag.getInt("stormType"));
         storm.getNBTCache().setNewNBT(stormCompoundTag);
         storm.nbtSyncFromServer();
         storm.getNBTCache().updateCacheFromNew();
         this.addStorm(storm);
      } else if (command.equals("syncStormRemove")) {
         CompoundTag stormCompoundTag = compoundTag.getCompound("data");
         long ID = stormCompoundTag.getLong("ID");
         Storm storm = this.lookupStormByID.get(ID);
         if (storm != null) {
            this.removeStorm(ID);
         }
      } else if (command.equals("syncStormUpdate")) {
         CompoundTag stormCompoundTag = compoundTag.getCompound("data");
         long ID = stormCompoundTag.getLong("ID");
         Storm storm = this.lookupStormByID.get(ID);
         if (storm != null) {
            storm.getNBTCache().setNewNBT(stormCompoundTag);
            storm.nbtSyncFromServer();
            storm.getNBTCache().updateCacheFromNew();
         }
      } else if (command.equals("syncBlockParticleNew")) {
         if ((double)PMWeather.RANDOM.nextFloat() > ClientConfig.debrisParticleDensity) {
            return;
         }

         CompoundTag nbt = compoundTag.getCompound("data");
         Vec3 pos = new Vec3((double)nbt.getInt("positionX"), (double)(nbt.getInt("positionY") + 1), (double)nbt.getInt("positionZ"));
         BlockState state = NbtUtils.readBlockState(this.getWorld().holderLookup(Registries.BLOCK), nbt.getCompound("blockstate"));
         long stormID = nbt.getLong("stormID");
         Storm storm = this.lookupStormByID.get(stormID);
         if (storm != null) {
            ParticleCube debris = new ParticleCube((ClientLevel)this.getWorld(), pos.x + (double)((PMWeather.RANDOM.nextFloat() - PMWeather.RANDOM.nextFloat()) * 3.0F), pos.y + (double)((PMWeather.RANDOM.nextFloat() - PMWeather.RANDOM.nextFloat()) * 3.0F), pos.z + (double)((PMWeather.RANDOM.nextFloat() - PMWeather.RANDOM.nextFloat()) * 3.0F), (double)0.0F, (double)0.0F, (double)0.0F, state);
            GameBusClientEvents.particleBehavior.initParticleCube(debris);
            storm.listParticleDebris.add(debris);
            debris.ignoreWind = true;
            debris.renderRange = 256.0F;
            debris.spawnAsDebrisEffect();
         }
      } else if (command.equals("syncLightningNew")) {
         CompoundTag nbt = compoundTag.getCompound("data");
         this.strike(new Vec3(nbt.getDouble("positionX"), nbt.getDouble("positionY"), nbt.getDouble("positionZ")));
      }

   }
}
