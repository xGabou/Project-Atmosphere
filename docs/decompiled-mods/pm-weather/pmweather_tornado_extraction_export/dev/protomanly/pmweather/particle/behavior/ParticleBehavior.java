package dev.protomanly.pmweather.particle.behavior;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.particle.EntityRotFX;
import dev.protomanly.pmweather.particle.ParticleTexExtraRender;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class ParticleBehavior {
   public List<EntityRotFX> particles = new ArrayList<EntityRotFX>();
   public Vec3 coordSource;
   public Entity sourceEntity;
   public float rateDarken = 0.025F;
   public float rateBrighten = 0.01F;
   public float rateBrightenSlower = 0.003F;
   public float rateAlpha = 0.002F;
   public float rateScale = 0.1F;
   public int tickSmokifyTrigger = 40;
   float vanillaRainRed = 0.7F;
   float vanillaRainGreen = 0.7F;
   float vanillaRainBlue = 1.0F;

   public ParticleBehavior(Vec3 coordSource) {
      super();
      this.coordSource = coordSource;
   }

   public EntityRotFX initParticle(EntityRotFX particle) {
      particle.setPrevPosX(particle.getX());
      particle.setPrevPosY(particle.getY());
      particle.setPrevPosZ(particle.getZ());
      particle.setSize(0.01F, 0.01F);
      return particle;
   }

   public void initParticleRain(EntityRotFX particle, int extraRenderCount) {
      if (particle instanceof ParticleTexExtraRender particleTexExtraRender) {
         particleTexExtraRender.extraParticlesBaseAmount = extraRenderCount;
      }

      particle.killWhenUnderTopmostBlock = true;
      particle.setCanCollide(false);
      particle.killWhenUnderCameraAtLeast = 5;
      particle.dontRenderUnderTopmostBlock = true;
      particle.fastLight = true;
      particle.slantParticleToWind = true;
      particle.facePlayer = false;
      particle.setScale(0.3F);
      particle.isTransparent = true;
      particle.setGravity(1.8F);
      particle.setLifetime(50);
      particle.ticksFadeInMax = 5.0F;
      particle.ticksFadeOutMax = 5.0F;
      particle.ticksFadeOutMaxOnDeath = 3.0F;
      particle.setAlpha(0.0F);
      particle.rotationYaw = (float)PMWeather.RANDOM.nextInt(360) - 180.0F;
      particle.setMotionY((double)-0.5F);
      particle.setColor(this.vanillaRainRed, this.vanillaRainGreen, this.vanillaRainBlue);
      particle.spawnAsWeatherEffect();
   }

   public void initParticleGroundSplash(EntityRotFX particle) {
      particle.killWhenUnderTopmostBlock = true;
      particle.setCanCollide(false);
      particle.killWhenUnderCameraAtLeast = 5;
      particle.facePlayer = true;
      particle.setScale(0.2F + PMWeather.RANDOM.nextFloat() * 0.05F);
      particle.setLifetime(15);
      particle.setGravity(1.0F);
      particle.ticksFadeInMax = 3.0F;
      particle.ticksFadeOutMax = 4.0F;
      particle.setAlpha(0.0F);
      particle.renderOrder = 2;
      particle.rotationYaw = (float)PMWeather.RANDOM.nextInt(360) - 180.0F;
      particle.rotationPitch = 90.0F;
      particle.setMotionY((double)(PMWeather.RANDOM.nextFloat() * 0.2F));
      particle.setMotionX((double)((PMWeather.RANDOM.nextFloat() - 0.5F) * 0.01F));
      particle.setMotionZ((double)((PMWeather.RANDOM.nextFloat() - 0.5F) * 0.01F));
      particle.setColor(this.vanillaRainRed, this.vanillaRainGreen, this.vanillaRainBlue);
   }

   public void initParticleSnow(EntityRotFX particle, int extraRenderCount, float windSpeed) {
      if (particle instanceof ParticleTexExtraRender particleTexExtraRender) {
         particleTexExtraRender.extraParticlesBaseAmount = extraRenderCount;
      }

      float windScale = Math.max(0.1F, 1.0F - windSpeed);
      particle.setCanCollide(false);
      particle.ticksFadeOutMaxOnDeath = 5.0F;
      particle.dontRenderUnderTopmostBlock = true;
      particle.killWhenUnderTopmostBlock = true;
      particle.killWhenFarFromCameraAtLeast = 25;
      particle.setMotionX((double)0.0F);
      particle.setMotionY((double)0.0F);
      particle.setMotionZ((double)0.0F);
      particle.setScale(0.19500001F + PMWeather.RANDOM.nextFloat() * 0.05F);
      particle.setGravity(0.05F + PMWeather.RANDOM.nextFloat() * 0.1F);
      particle.setLifetime((int)(1440.0F * windScale));
      particle.facePlayer = true;
      particle.ticksFadeInMax = 40.0F * windScale;
      particle.ticksFadeOutMax = 40.0F * windScale;
      particle.ticksFadeOutMaxOnDeath = 10.0F;
      particle.setAlpha(0.0F);
      particle.rotationYaw = (float)PMWeather.RANDOM.nextInt(360) - 180.0F;
   }

   public void initParticleSleet(EntityRotFX particle, int extraRenderCount) {
      if (particle instanceof ParticleTexExtraRender particleTexExtraRender) {
         particleTexExtraRender.extraParticlesBaseAmount = extraRenderCount;
      }

      particle.setCanCollide(false);
      particle.ticksFadeOutMaxOnDeath = 5.0F;
      particle.dontRenderUnderTopmostBlock = true;
      particle.killWhenFarFromCameraAtLeast = 25;
      particle.setMotionX((double)0.0F);
      particle.setMotionY((double)0.0F);
      particle.setMotionZ((double)0.0F);
      particle.setScale(0.3F);
      particle.setGravity(1.2F);
      particle.setLifetime(50);
      particle.facePlayer = true;
      particle.ticksFadeInMax = 5.0F;
      particle.ticksFadeOutMax = 5.0F;
      particle.ticksFadeOutMaxOnDeath = 10.0F;
      particle.setAlpha(0.0F);
      particle.rotationYaw = (float)PMWeather.RANDOM.nextInt(360) - 180.0F;
   }

   public void initParticleHail(EntityRotFX particle) {
      particle.killWhenUnderTopmostBlock = false;
      particle.setCanCollide(true);
      particle.killOnCollide = true;
      particle.killWhenUnderCameraAtLeast = 5;
      particle.dontRenderUnderTopmostBlock = true;
      particle.rotationYaw = (float)PMWeather.RANDOM.nextInt(360);
      particle.rotationPitch = (float)PMWeather.RANDOM.nextInt(360);
      particle.fastLight = true;
      particle.slantParticleToWind = true;
      particle.windWeight = 1.5F;
      particle.ignoreWind = false;
      particle.spinFast = true;
      particle.spinFastRate = 10.0F;
      particle.facePlayer = false;
      particle.setScale(0.105000004F);
      particle.isTransparent = false;
      particle.setGravity(3.5F);
      particle.ticksFadeInMax = 5.0F;
      particle.ticksFadeOutMax = 5.0F;
      particle.ticksFadeOutMaxOnDeath = 50.0F;
      particle.fullAlphaTarget = 1.0F;
      particle.setAlpha(0.0F);
      particle.rotationYaw = (float)(PMWeather.RANDOM.nextInt(360) - 180);
      particle.setMotionY((double)-0.5F);
      particle.setColor(0.9F, 0.9F, 0.9F);
      particle.bounceOnVerticalImpact = true;
      particle.bounceOnVerticalImpactEnergy = 0.3F;
   }

   public void initParticleCube(EntityRotFX particle) {
      particle.killWhenUnderTopmostBlock = false;
      particle.setCanCollide(true);
      particle.killOnCollide = true;
      particle.killOnCollideActivateAtAge = 30;
      particle.killWhenUnderCameraAtLeast = 0;
      particle.dontRenderUnderTopmostBlock = true;
      particle.rotationPitch = (float)PMWeather.RANDOM.nextInt(360);
      particle.fastLight = true;
      particle.ignoreWind = true;
      particle.spinFast = true;
      particle.spinFastRate = 1.0F;
      particle.facePlayer = false;
      particle.setScale(0.45F);
      particle.isTransparent = false;
      particle.setGravity(0.5F);
      particle.setLifetime(400);
      particle.ticksFadeInMax = 5.0F;
      particle.ticksFadeOutMax = 5.0F;
      particle.ticksFadeOutMaxOnDeath = 20.0F;
      particle.fullAlphaTarget = 1.0F;
      particle.setAlpha(0.0F);
      particle.rotationYaw = (float)(PMWeather.RANDOM.nextInt(360) - 180);
      particle.vanillaMotionDampen = true;
      particle.bounceOnVerticalImpact = true;
      particle.bounceOnVerticalImpactEnergy = 0.3F;
   }
}
