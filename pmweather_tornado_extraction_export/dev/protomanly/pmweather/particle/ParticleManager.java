package dev.protomanly.pmweather.particle;

import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.config.ClientConfig;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleDescription;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TrackingEmitter;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.client.ClientHooks;
import org.apache.commons.compress.utils.Lists;
import org.joml.Matrix4fStack;

public class ParticleManager implements PreparableReloadListener {
   private static final FileToIdConverter PARTICLE_LISTER = FileToIdConverter.json("particles");
   private static final ResourceLocation PARTICLES_ATLAS_INFO = ResourceLocation.withDefaultNamespace("particles");
   private static final List<ParticleRenderType> RENDER_ORDER;
   protected ClientLevel level;
   private final TextureAtlas textureAtlas;
   private final TextureManager textureManager;
   private final Map<ResourceLocation, ParticleProvider<?>> providers = new HashMap<ResourceLocation, ParticleProvider<?>>();
   private final Map<ResourceLocation, MutableSpriteSet> spriteSets = Maps.newHashMap();
   private final Queue<Particle> particlesToAdd = Queues.newArrayDeque();
   private final Queue<TrackingEmitter> trackingEmitters = Queues.newArrayDeque();
   private final Map<ParticleRenderType, Queue<Particle>> particles;
   private final Object2IntOpenHashMap<ParticleGroup> trackedParticleCounts;

   public ParticleManager(ClientLevel level, TextureManager textureManager) {
      super();
      this.particles = Maps.newTreeMap(ClientHooks.makeParticleRenderTypeComparator(RENDER_ORDER));
      this.trackedParticleCounts = new Object2IntOpenHashMap();
      this.textureAtlas = new TextureAtlas(TextureAtlas.LOCATION_PARTICLES);
      this.level = level;
      this.textureManager = textureManager;
   }

   public CompletableFuture<Void> reload(PreparableReloadListener.PreparationBarrier preparationBarrier, ResourceManager resourceManager, ProfilerFiller profilerFiller, ProfilerFiller profilerFiller1, Executor executor, Executor executor1) {
      CompletableFuture<List<ParticleDefinition>> completableFuture = CompletableFuture.supplyAsync(() -> PARTICLE_LISTER.listMatchingResources(resourceManager), executor).thenCompose((locationResourceMap) -> {
         List<CompletableFuture<ParticleDefinition>> list = new ArrayList<CompletableFuture<ParticleDefinition>>(locationResourceMap.size());
         locationResourceMap.forEach((k, v) -> {
            ResourceLocation resourceLocation = PARTICLE_LISTER.fileToId(k);
            list.add(CompletableFuture.supplyAsync(() -> {
               record ParticleDefinition(ResourceLocation resourceLocation, Optional<List<ResourceLocation>> sprites) {
                  ParticleDefinition {
                     super();
                  }
               }

               return new ParticleDefinition(resourceLocation, this.loadParticleDescription(resourceLocation, v));
            }, executor));
         });
         return Util.sequence(list);
      });
      CompletableFuture<SpriteLoader.Preparations> completableFuture1 = SpriteLoader.create(this.textureAtlas).loadAndStitch(resourceManager, PARTICLES_ATLAS_INFO, 0, executor).thenCompose(SpriteLoader.Preparations::waitForUpload);
      CompletableFuture var10000 = CompletableFuture.allOf(completableFuture1, completableFuture);
      Objects.requireNonNull(preparationBarrier);
      return var10000.thenCompose(preparationBarrier::wait).thenAcceptAsync((v) -> {
         this.clearParticles();
         profilerFiller1.startTick();
         profilerFiller1.push("upload");
         SpriteLoader.Preparations preparations = completableFuture1.join();
         this.textureAtlas.upload(preparations);
         profilerFiller1.popPush("bindSpriteSets");
         Set<ResourceLocation> set = new HashSet<ResourceLocation>();
         TextureAtlasSprite textureAtlasSprite = preparations.missing();
         ((List)completableFuture.join()).forEach((particleDefinition) -> {
            Optional<List<ResourceLocation>> optionalResourceLocations = particleDefinition.sprites();
            if (!optionalResourceLocations.isEmpty()) {
               List<TextureAtlasSprite> textureAtlasSprites = new ArrayList<TextureAtlasSprite>();

               for(ResourceLocation resourceLocation : optionalResourceLocations.get()) {
                  TextureAtlasSprite textureAtlasSprite1 = (TextureAtlasSprite)preparations.regions().get(resourceLocation);
                  if (textureAtlasSprite1 == null) {
                     set.add(resourceLocation);
                     textureAtlasSprites.add(textureAtlasSprite);
                  } else {
                     textureAtlasSprites.add(textureAtlasSprite1);
                  }
               }

               if (textureAtlasSprites.isEmpty()) {
                  textureAtlasSprites.add(textureAtlasSprite);
               }

               (this.spriteSets.get(particleDefinition.resourceLocation())).rebind(textureAtlasSprites);
            }

         });
         if (!set.isEmpty()) {
            PMWeather.LOGGER.warn("Missing particle sprites: {}", set.stream().sorted().map(ResourceLocation::toString).collect(Collectors.joining(",")));
         }

         profilerFiller1.pop();
         profilerFiller1.endTick();
      }, executor1);
   }

   private Optional<List<ResourceLocation>> loadParticleDescription(ResourceLocation resourceLocation, Resource resource) {
      if (!this.spriteSets.containsKey(resourceLocation)) {
         PMWeather.LOGGER.debug("Redundant texture list for particle: {}", resourceLocation);
         return Optional.<List<ResourceLocation>>empty();
      } else {
         try (Reader reader = resource.openAsReader()) {
            ParticleDescription particleDescription = ParticleDescription.fromJson(GsonHelper.parse(reader));
            return Optional.<List<ResourceLocation>>of(particleDescription.getTextures());
         } catch (IOException e) {
            throw new IllegalStateException("Failed to load description for particle " + String.valueOf((Object)resourceLocation), e);
         }
      }
   }

   @Nullable
   private <T extends ParticleOptions> Particle makeParticle(T particleOptions, double x, double y, double z, double xMotion, double yMotion, double zMotion) {
      ParticleProvider<T> particleProvider = this.providers.get(BuiltInRegistries.PARTICLE_TYPE.getKey(particleOptions.getType()));
      return particleProvider == null ? null : particleProvider.createParticle(particleOptions, this.level, x, y, z, xMotion, yMotion, zMotion);
   }

   public void add(Particle particle) {
      Optional<ParticleGroup> optional = particle.getParticleGroup();
      if (optional.isPresent()) {
         if (this.hasSpaceInParticleLimit(optional.get())) {
            this.particlesToAdd.add(particle);
            this.updateCount(optional.get(), 1);
         }
      } else {
         this.particlesToAdd.add(particle);
      }

   }

   public void tick() {
      this.level.getProfiler().push("pmweather_particle_tick");
      this.particles.forEach((particleRenderType, particles1) -> {
         this.level.getProfiler().push("pmweather_particle_tick_" + particleRenderType.toString());
         this.tickParticleList(particles1);
         this.level.getProfiler().pop();
      });
      if (!this.trackingEmitters.isEmpty()) {
         List<TrackingEmitter> list = Lists.newArrayList();

         for(TrackingEmitter trackingEmitter : this.trackingEmitters) {
            trackingEmitter.tick();
            if (!trackingEmitter.isAlive()) {
               list.add(trackingEmitter);
            }
         }

         this.trackingEmitters.removeAll(list);
      }

      Particle particle;
      if (!this.particlesToAdd.isEmpty()) {
         while((particle = this.particlesToAdd.poll()) != null) {
            (this.particles.computeIfAbsent(particle.getRenderType(), (particleRenderType) -> EvictingQueue.create(32768))).add(particle);
         }
      }

      this.level.getProfiler().pop();
   }

   private void tickParticleList(Collection<Particle> particles) {
      if (!particles.isEmpty()) {
         Iterator<Particle> iterator = particles.iterator();

         while(iterator.hasNext()) {
            Particle particle = iterator.next();
            this.tickParticle(particle);
            if (!particle.isAlive()) {
               particle.getParticleGroup().ifPresent((particleGroup) -> this.updateCount(particleGroup, -1));
               iterator.remove();
            }
         }
      }

   }

   private void updateCount(ParticleGroup particleGroup, int count) {
      this.trackedParticleCounts.addTo(particleGroup, count);
   }

   private void tickParticle(Particle particle) {
      try {
         particle.tick();
      } catch (Throwable throwable) {
         CrashReport crashReport = CrashReport.forThrowable(throwable, "Ticking Particle");
         CrashReportCategory crashReportCategory = crashReport.addCategory("Particle being ticked");
         Objects.requireNonNull(particle);
         crashReportCategory.setDetail("Particle", particle::toString);
         ParticleRenderType var10002 = particle.getRenderType();
         Objects.requireNonNull(var10002);
         crashReportCategory.setDetail("Particle Type", var10002::toString);
         throw new ReportedException(crashReport);
      }
   }

   public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, LightTexture lightTexture, Camera camera, float partialTicks, @Nullable Frustum frustum) {
      this.level.getProfiler().push("pmweather_particle_render");
      float fogStart = RenderSystem.getShaderFogStart();
      float fogEnd = RenderSystem.getShaderFogEnd();
      RenderSystem.setShaderFogStart(fogStart);
      RenderSystem.setShaderFogEnd(fogEnd * 2.0F);
      lightTexture.turnOnLightLayer();
      RenderSystem.enableDepthTest();
      RenderSystem.activeTexture(33986);
      RenderSystem.activeTexture(33984);
      Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
      matrix4fStack.pushMatrix();
      matrix4fStack.mul(poseStack.last().pose());
      RenderSystem.applyModelViewMatrix();
      RenderSystem.disableCull();
      int particleCount = 0;

      for(ParticleRenderType particleRenderType : this.particles.keySet()) {
         this.level.getProfiler().push(particleRenderType.toString());
         if (particleRenderType != ParticleRenderType.NO_RENDER) {
            Iterable<Particle> iterable = this.particles.get(particleRenderType);
            if (iterable != null) {
               RenderSystem.setShader(GameRenderer::getParticleShader);
               Tesselator tesselator = Tesselator.getInstance();
               BufferBuilder bufferBuilder = particleRenderType.begin(tesselator, this.textureManager);
               Map<Integer, List<Particle>> sortedList = new HashMap<Integer, List<Particle>>();
               int maxRenderOrder = 0;

               for(Particle particle : iterable) {
                  int renderOrder = 10;
                  if (particle instanceof EntityRotFX) {
                     EntityRotFX entityRotFX = (EntityRotFX)particle;
                     renderOrder = entityRotFX.renderOrder;
                  }

                  if (renderOrder > maxRenderOrder) {
                     maxRenderOrder = renderOrder;
                  }

                  if (sortedList.containsKey(renderOrder)) {
                     (sortedList.get(renderOrder)).add(particle);
                  } else {
                     List<Particle> list = new ArrayList<Particle>();
                     list.add(particle);
                     sortedList.put(renderOrder, list);
                  }
               }

               for(int i = 0; i <= maxRenderOrder; ++i) {
                  if (sortedList.containsKey(i)) {
                     List<Particle> particlesSorted = sortedList.get(i);
                     particlesSorted.sort((p1, p2) -> {
                        double d1 = p1.getPos().distanceToSqr(camera.getPosition());
                        double d2 = p2.getPos().distanceToSqr(camera.getPosition());
                        return Double.compare(d2, d1);
                     });

                     for(Particle particle : particlesSorted) {
                        if (particle instanceof EntityRotFX) {
                           EntityRotFX entityRotFX = (EntityRotFX)particle;
                           if (camera.getPosition().distanceToSqr(particle.getPos()) > (double)(entityRotFX.renderRange * entityRotFX.renderRange) || frustum != null && !frustum.isVisible(entityRotFX.getBoundingBoxForRender())) {
                              continue;
                           }
                        } else if (camera.getPosition().distanceToSqr(particle.getPos()) > (double)65536.0F || frustum != null && !frustum.isVisible(particle.getRenderBoundingBox(partialTicks))) {
                           continue;
                        }

                        if (!(camera.getPosition().distanceToSqr(particle.getPos()) > (double)(ClientConfig.maxParticleSpawnDistanceFromPlayer * ClientConfig.maxParticleSpawnDistanceFromPlayer))) {
                           try {
                              particle.render(bufferBuilder, camera, partialTicks);
                           } catch (Throwable throwable) {
                              CrashReport crashReport = CrashReport.forThrowable(throwable, "Rendering Particle");
                              CrashReportCategory crashReportCategory = crashReport.addCategory("Particle being rendered");
                              Objects.requireNonNull(particle);
                              crashReportCategory.setDetail("Particle", particle::toString);
                              Objects.requireNonNull(particleRenderType);
                              crashReportCategory.setDetail("Particle Type", particleRenderType::toString);
                              throw new ReportedException(crashReport);
                           }
                        }
                     }
                  }
               }

               MeshData meshData = bufferBuilder.build();
               if (meshData != null) {
                  BufferUploader.drawWithShader(meshData);
               }
            }

            this.level.getProfiler().pop();
         }
      }

      matrix4fStack.popMatrix();
      RenderSystem.applyModelViewMatrix();
      RenderSystem.depthMask(true);
      RenderSystem.disableBlend();
      lightTexture.turnOffLightLayer();
      RenderSystem.setShaderFogStart(fogStart);
      RenderSystem.setShaderFogEnd(fogEnd);
      this.level.getProfiler().pop();
   }

   public void setLevel(@Nullable ClientLevel level) {
      this.level = level;
      this.clearParticles();
      this.trackingEmitters.clear();
   }

   private boolean hasSpaceInParticleLimit(ParticleGroup particleGroup) {
      return this.trackedParticleCounts.getInt(particleGroup) < particleGroup.getLimit();
   }

   public void clearParticles() {
      this.particles.clear();
      this.particlesToAdd.clear();
      this.trackingEmitters.clear();
      this.trackedParticleCounts.clear();
   }

   public Map<ParticleRenderType, Queue<Particle>> getParticles() {
      return this.particles;
   }

   static {
      RENDER_ORDER = ImmutableList.of(ParticleRenderType.TERRAIN_SHEET, ParticleRenderType.PARTICLE_SHEET_OPAQUE, ParticleRenderType.PARTICLE_SHEET_LIT, ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT, ParticleRenderType.CUSTOM, EntityRotFX.SORTED_OPAQUE_BLOCK, EntityRotFX.SORTED_TRANSLUCENT);
   }

   static class MutableSpriteSet implements SpriteSet {
      private List<TextureAtlasSprite> sprites;

      MutableSpriteSet() {
         super();
      }

      public TextureAtlasSprite get(int i, int i1) {
         return this.sprites.get(i * (this.sprites.size() - 1) / i1);
      }

      public TextureAtlasSprite get(RandomSource randomSource) {
         return this.sprites.get(randomSource.nextInt(this.sprites.size()));
      }

      public void rebind(List<TextureAtlasSprite> textureAtlasSprites) {
         this.sprites = ImmutableList.copyOf(textureAtlasSprites);
      }
   }
}
