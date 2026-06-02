package dev.protomanly.pmweather.compat;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.DhApi.Delayed;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiAfterDhInitEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderPassEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderSetupEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import dev.protomanly.pmweather.PMWeather;
import org.joml.Matrix4f;

public class DistantHorizonsHandler {
   private boolean dhReady = false;
   private int dhDepthTextureId = -1;
   private Matrix4f dhProjectionMatrix = new Matrix4f();
   private Matrix4f dhModelViewMatrix = new Matrix4f();
   private float dhNearPlane = 0.05F;
   private float dhFarPlane = 1024.0F;
   private int dhRenderDistance = 256;

   public DistantHorizonsHandler() {
      super();
   }

   public void initialize() {
      try {
         this.registerEventHandlers();
      } catch (Exception e) {
         PMWeather.LOGGER.error("Failed to register DH event handlers", e);
         throw e;
      }
   }

   private void registerEventHandlers() {
      DhApi.events.bind(DhApiAfterDhInitEvent.class, new DhApiAfterDhInitEvent() {
         public void afterDistantHorizonsInit(DhApiEventParam<Void> event) {
            PMWeather.LOGGER.info("Distant Horizons initialized");
            DistantHorizonsHandler.this.dhReady = true;

            try {
               DistantHorizonsHandler.this.dhRenderDistance = (Integer)Delayed.configs.graphics().chunkRenderDistance().getValue();
            } catch (Exception e) {
               PMWeather.LOGGER.warn("Failed to get DH render distance, using default", e);
               DistantHorizonsHandler.this.dhRenderDistance = 256;
            }

         }
      });
      DhApi.events.bind(DhApiBeforeRenderPassEvent.class, new DhApiBeforeRenderPassEvent() {
         public void beforeRender(DhApiEventParam<DhApiRenderParam> event) {
            DistantHorizonsHandler.this.captureRenderState((DhApiRenderParam)event.value);
         }
      });
      DhApi.events.bind(DhApiBeforeRenderSetupEvent.class, new DhApiBeforeRenderSetupEvent() {
         public void beforeSetup(DhApiEventParam<DhApiRenderParam> event) {
            DistantHorizonsHandler.this.captureRenderState((DhApiRenderParam)event.value);
         }
      });
   }

   private void captureRenderState(DhApiRenderParam param) {
      try {
         DhApiResult<Integer> depthResult = Delayed.renderProxy.getDhDepthTextureId();
         if (depthResult.success && (Integer)depthResult.payload > 0) {
            this.dhDepthTextureId = (Integer)depthResult.payload;
            PMWeather.LOGGER.debug("Captured DH depth texture: " + this.dhDepthTextureId);
         }

         float[] projValues = param.dhProjectionMatrix.getValuesAsArray();
         float[] mvValues = param.dhModelViewMatrix.getValuesAsArray();
         this.dhProjectionMatrix.set(projValues[0], projValues[4], projValues[8], projValues[12], projValues[1], projValues[5], projValues[9], projValues[13], projValues[2], projValues[6], projValues[10], projValues[14], projValues[3], projValues[7], projValues[11], projValues[15]);
         this.dhModelViewMatrix.set(mvValues[0], mvValues[4], mvValues[8], mvValues[12], mvValues[1], mvValues[5], mvValues[9], mvValues[13], mvValues[2], mvValues[6], mvValues[10], mvValues[14], mvValues[3], mvValues[7], mvValues[11], mvValues[15]);
         this.dhNearPlane = param.nearClipPlane;
         this.dhFarPlane = param.farClipPlane;

         try {
            this.dhRenderDistance = (Integer)Delayed.configs.graphics().chunkRenderDistance().getValue();
         } catch (Exception var6) {
         }
      } catch (Exception e) {
         PMWeather.LOGGER.error("Failed to capture DH render state", e);
      }

   }

   public boolean isReady() {
      return this.dhReady && this.dhDepthTextureId > 0;
   }

   public int getDepthTextureId() {
      return this.dhDepthTextureId;
   }

   public Matrix4f getDhProjectionMatrix() {
      return new Matrix4f(this.dhProjectionMatrix);
   }

   public Matrix4f getDhModelViewMatrix() {
      return new Matrix4f(this.dhModelViewMatrix);
   }

   public float getNearPlane() {
      return this.dhNearPlane;
   }

   public float getFarPlane() {
      return this.dhFarPlane;
   }

   public int getChunkRenderDistance() {
      return this.dhRenderDistance;
   }
}
