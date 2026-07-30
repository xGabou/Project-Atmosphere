package net.Gabou.projectatmosphere.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.client.renderer.pipeline.DefaultPipeline;
import net.Gabou.projectatmosphere.client.render.SimpleCloudsTornadoRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DefaultPipeline.class, remap = false)
public abstract class DefaultPipelineTornadoMixin {
    @Inject(
            method = "afterSky",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/nonamecrackers2/simpleclouds/client/renderer/SimpleCloudsRenderer;copyDepthFromCloudsToMain()V"
            ),
            require = 0
    )
    private void projectatmosphere$renderTornadoOpaque(Minecraft mc, SimpleCloudsRenderer renderer,
                                                       Matrix4f modelViewMat, Matrix4f projMat, float partialTick,
                                                       double camX, double camY, double camZ, Frustum frustum,
                                                       CallbackInfo ci) {
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        boolean pathLog = SimpleCloudsTornadoRenderer.shouldPathLog(level);
        if (pathLog) {
            SimpleCloudsTornadoRenderer.path(
                    "DefaultPipeline hook entered gameTime={} dhLoaded={} mainDepth={} cloudDepth={} transparencyDepth={}",
                    level.getGameTime(),
                    SimpleCloudsMod.dhLoaded(),
                    mc.getMainRenderTarget().getDepthTextureId(),
                    renderer.getCloudTarget().getDepthTextureId(),
                    renderer.getCloudTransparencyTarget().getDepthTextureId()
            );
        }
        if (SimpleCloudsMod.dhLoaded()) {
            if (pathLog) {
                SimpleCloudsTornadoRenderer.path("DefaultPipeline skipped: SimpleClouds reports DH loaded");
            }
            return;
        }
        float[] cloudColor = renderer.getCloudColor(partialTick);
        mc.getProfiler().push("projectatmosphere_tornado_opaque");
        SimpleCloudsTornadoRenderer.INSTANCE.prepareFrame(level, partialTick);
        boolean hasPreparedTornado = SimpleCloudsTornadoRenderer.INSTANCE.hasPreparedTornadoes();
        if (pathLog) {
            SimpleCloudsTornadoRenderer.path(
                    "DefaultPipeline prepared tornadoes={} hasPrepared={} frustumGate=disabled",
                    SimpleCloudsTornadoRenderer.INSTANCE.preparedTornadoCount(),
                    hasPreparedTornado
            );
        }
        if (!hasPreparedTornado) {
            if (pathLog) {
                SimpleCloudsTornadoRenderer.path("DefaultPipeline skipped: no prepared tornado");
            }
            mc.getProfiler().pop();
            return;
        }
        boolean downsampled = SimpleCloudsTornadoRenderer.INSTANCE.usesDownsamplePath();
        renderer.copyDepthFromCloudsToTransparency();
        int primaryDepth = renderer.getCloudTransparencyTarget().getDepthTextureId();
        int secondaryDepth = mc.getMainRenderTarget().getDepthTextureId();
        if (pathLog) {
            SimpleCloudsTornadoRenderer.path(
                    "DefaultPipeline drawing downsampled={} copiedTransparencyDepth=true primaryDepth={} secondaryDepth={} cloudTarget={}x{} transparencyTarget={}x{}",
                    downsampled,
                    primaryDepth,
                    secondaryDepth,
                    renderer.getCloudTarget().width,
                    renderer.getCloudTarget().height,
                    renderer.getCloudTransparencyTarget().width,
                    renderer.getCloudTransparencyTarget().height
            );
        }
        renderer.getCloudTarget().bindWrite(false);
        PoseStack stack = new PoseStack();
        stack.last().pose().set(modelViewMat);
        SimpleCloudsTornadoRenderer.INSTANCE.renderOpaque(
                renderer, stack, projMat, partialTick, cloudColor[0], cloudColor[1], cloudColor[2],
                null,
                primaryDepth,
                secondaryDepth,
                true
        );
        mc.getProfiler().pop();
    }
}
