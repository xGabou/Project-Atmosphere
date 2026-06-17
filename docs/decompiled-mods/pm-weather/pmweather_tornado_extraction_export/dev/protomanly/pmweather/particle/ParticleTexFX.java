package dev.protomanly.pmweather.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public class ParticleTexFX extends EntityRotFX {
   public ParticleTexFX(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, TextureAtlasSprite sprite) {
      super(level, x, y, z, xSpeed, ySpeed - (double)0.5F, zSpeed);
      this.setSprite(sprite);
      this.setColor(1.0F, 1.0F, 1.0F);
      this.gravity = 1.0F;
      this.quadSize = 0.15F;
      this.setLifetime(100);
      this.setCanCollide(false);
   }
}
