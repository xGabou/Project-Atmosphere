package dev.protomanly.pmweather.render;

import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.particle.ParticleManager;
import dev.protomanly.pmweather.shaders.ModShaders;
import dev.protomanly.pmweather.weather.Lightning;
import dev.protomanly.pmweather.weather.WeatherHandlerClient;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;

@EventBusSubscriber(
   modid = "pmweather",
   bus = Bus.GAME,
   value = {Dist.CLIENT}
)
public class RenderEvents {
   public static List<Color> lightningColors = new ArrayList<Color>() {
      {
         this.add(new Color(16777215));
         this.add(new Color(15587698));
         this.add(new Color(13041578));
         this.add(new Color(10778102));
         this.add(new Color(15106690));
         this.add(new Color(7203548));
      }
   };

   public RenderEvents() {
      super();
   }

   @SubscribeEvent
   public static void render(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_WEATHER) {
         RadarRenderer.RenderedRadars = 0;
         float partialTicks = event.getPartialTick().getGameTimeDeltaTicks();
         WeatherHandlerClient weatherHandlerClient = (WeatherHandlerClient)GameBusClientEvents.weatherHandler;
         if (weatherHandlerClient != null) {
            List<Lightning> lightnings = weatherHandlerClient.lightnings;

            for(int i = 0; i < lightnings.size(); ++i) {
               Lightning lightning = lightnings.get(i);
               if (lightning != null) {
                  Random rand = new Random(lightning.seed);
                  Color color = lightningColors.get(rand.nextInt(lightningColors.size()));
                  float p = Math.clamp(((float)lightning.ticks + partialTicks) / (float)lightning.lifetime, 0.0F, 1.0F);
                  float alpha = (float)Math.abs(Math.cos(Math.sqrt((double)p) * Math.PI * (double)3.0F)) * (1.0F - p);
                  color = new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.clamp((long)((int)(alpha * 255.0F)), 0, 255));
                  CustomLightningRenderer.render(lightning.position, lightning.seed, event.getCamera(), color);
               }
            }

            ModShaders.renderShaders(event.getPartialTick().getGameTimeDeltaTicks(), event.getCamera(), event.getProjectionMatrix(), event.getModelViewMatrix());
            ParticleManager pm = GameBusClientEvents.particleManager;
            if (pm != null) {
               pm.render(event.getPoseStack(), (MultiBufferSource.BufferSource)null, Minecraft.getInstance().gameRenderer.lightTexture(), event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false), event.getFrustum());
            }
         }
      }

   }
}
