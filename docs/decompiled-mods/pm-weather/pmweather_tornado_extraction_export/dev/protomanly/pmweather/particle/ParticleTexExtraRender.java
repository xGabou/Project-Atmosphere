package dev.protomanly.pmweather.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.protomanly.pmweather.util.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class ParticleTexExtraRender extends ParticleTexFX {
   public int extraParticlesBaseAmount = 5;
   public boolean noExtraParticles = false;
   public float extraRandomSecondaryYawRotation = 360.0F;

   public ParticleTexExtraRender(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, TextureAtlasSprite sprite) {
      super(level, x, y, z, xSpeed, ySpeed, zSpeed, sprite);
   }

   public void tickExtraRotations() {
      if (this.slantParticleToWind) {
         double speed = this.xd * this.xd + this.zd * this.zd;
         this.rotationYaw = -((float)Math.toDegrees(Math.atan2(this.zd, this.xd))) - 90.0F;
         this.rotationPitch = Math.min(45.0F, (float)(speed * (double)120.0F));
         this.rotationPitch += (float)(this.entityID % 10 - 5);
      }

   }

   public void render(VertexConsumer buffer, Camera renderInfo, float partialTicks) {
      Vec3 vec3d = renderInfo.getPosition();
      float f = (float)(Mth.lerp((double)partialTicks, this.xo, this.x) - vec3d.x());
      float f1 = (float)(Mth.lerp((double)partialTicks, this.yo, this.y) - vec3d.y());
      float f2 = (float)(Mth.lerp((double)partialTicks, this.zo, this.z) - vec3d.z());
      Quaternionf quaternion;
      if (!this.facePlayer && (this.rotationPitch != 0.0F || this.rotationYaw != 0.0F)) {
         quaternion = new Quaternionf(0.0F, 0.0F, 0.0F, 1.0F);
         quaternion.mul(Axis.YP.rotationDegrees(this.rotationYaw));
         quaternion.mul(Axis.XP.rotationDegrees(this.rotationPitch));
         if (this.extraRandomSecondaryYawRotation > 0.0F) {
            quaternion.mul(Axis.YP.rotationDegrees((float)this.entityID % this.extraRandomSecondaryYawRotation));
         }
      } else {
         quaternion = renderInfo.rotation();
      }

      float u0 = this.getU0();
      float u1 = this.getU1();
      float v0 = this.getV0();
      float v1 = this.getV1();
      int renderAmount;
      if (this.noExtraParticles) {
         renderAmount = 1;
      } else {
         renderAmount = Math.min(1 + this.extraParticlesBaseAmount, Util.MAX_RAIN_DROPS);
      }

      for(int ii = 0; ii < renderAmount; ++ii) {
         double xx = (double)0.0F;
         double zz = (double)0.0F;
         double yy = (double)0.0F;
         if (ii != 0) {
            xx = Util.RAIN_POSITIONS[ii].x;
            yy = Util.RAIN_POSITIONS[ii].y;
            zz = Util.RAIN_POSITIONS[ii].z;
         }

         if (this.dontRenderUnderTopmostBlock) {
            int height2 = this.level.getHeightmapPos(Types.MOTION_BLOCKING, new BlockPos((int)(this.x + xx), (int)this.y, (int)(this.z + zz))).getY();
            if (this.y + yy < (double)height2) {
               continue;
            }
         }

         int i = this.getLightColor(partialTicks);
         if (i > 0) {
            this.lastNonZeroBrightness = i;
         } else {
            i = this.lastNonZeroBrightness;
         }

         Vector3f[] v3f = new Vector3f[]{new Vector3f(-1.0F, -1.0F, 0.0F), new Vector3f(-1.0F, 1.0F, 0.0F), new Vector3f(1.0F, 1.0F, 0.0F), new Vector3f(1.0F, -1.0F, 0.0F)};
         float scale = this.getQuadSize(partialTicks);

         for(int v = 0; v < 4; ++v) {
            Vector3f vector3f = v3f[v];
            vector3f.rotate(quaternion);
            vector3f.mul(scale);
            vector3f.add(f, f1, f2);
         }

         buffer.addVertex((float)(xx + (double)v3f[0].x), (float)(yy + (double)v3f[0].y), (float)(zz + (double)v3f[0].z)).setUv(u1, v1).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(i);
         buffer.addVertex((float)(xx + (double)v3f[1].x), (float)(yy + (double)v3f[1].y), (float)(zz + (double)v3f[1].z)).setUv(u1, v0).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(i);
         buffer.addVertex((float)(xx + (double)v3f[2].x), (float)(yy + (double)v3f[2].y), (float)(zz + (double)v3f[2].z)).setUv(u0, v0).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(i);
         buffer.addVertex((float)(xx + (double)v3f[3].x), (float)(yy + (double)v3f[3].y), (float)(zz + (double)v3f[3].z)).setUv(u0, v1).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(i);
      }

   }
}
