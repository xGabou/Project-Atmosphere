package dev.protomanly.pmweather.weather;

import dev.protomanly.pmweather.PMWeather;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class Vorticy {
   public float windspeedMult = 0.0F;
   public float maxWindspeedMult;
   public float widthPerc;
   public float distancePerc;
   public float angle;
   public int lifetime;
   public int tickCount;
   public boolean dead = false;
   private Storm storm;

   public Vorticy(Storm storm, float maxWindspeedMult, float widthPerc, float distancePerc, int lifetime) {
      super();
      this.storm = storm;
      this.maxWindspeedMult = maxWindspeedMult;
      this.distancePerc = distancePerc;
      this.widthPerc = widthPerc;
      this.lifetime = lifetime;
      this.angle = PMWeather.RANDOM.nextFloat() * ((float)Math.PI * 2F);
   }

   public void tick() {
      if (!this.dead) {
         ++this.tickCount;
         float lifeDelta = (float)this.tickCount / (float)this.lifetime;
         float wind = (float)this.storm.windspeed * (1.0F - this.distancePerc);
         float angleAdd = (float)Math.toRadians((double)(wind / 300.0F));
         if ((double)lifeDelta > (double)0.5F) {
            this.windspeedMult = Mth.lerp((lifeDelta - 0.5F) * 2.0F, this.maxWindspeedMult, 0.0F);
         } else {
            this.windspeedMult = Mth.lerp(lifeDelta * 2.0F, 0.0F, this.maxWindspeedMult);
         }

         if (this.tickCount > this.lifetime) {
            this.dead = true;
         }

         if (this.storm.stormType == 2) {
            angleAdd *= 0.1F;
         }

         this.angle += angleAdd;
         if (this.angle > ((float)Math.PI * 2F)) {
            this.angle = 0.0F;
         }

      }
   }

   public float getWidth() {
      return this.storm.stormType == 2 ? this.widthPerc * (float)this.storm.maxWidth : this.widthPerc * this.storm.width;
   }

   public float getDistance() {
      return this.storm.stormType == 2 ? this.distancePerc * (float)this.storm.maxWidth : this.distancePerc * this.storm.width;
   }

   public Vec3 getPosition() {
      float realDist = this.getDistance();
      return this.storm.position.add(new Vec3(Math.sin((double)this.angle) * (double)realDist, (double)0.0F, Math.cos((double)this.angle) * (double)realDist));
   }
}
