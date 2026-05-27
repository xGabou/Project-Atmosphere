package net.Gabou.projectatmosphere.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.client.renderer.pipeline.ShaderSupportPipeline;
import net.Gabou.projectatmosphere.client.render.SimpleCloudsTornadoRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ShaderSupportPipeline.class, remap = false)
public abstract class ShaderSupportPipelineTornadoMixin {
    @Inject(
            method = "afterLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/nonamecrackers2/simpleclouds/client/renderer/SimpleCloudsRenderer;getCloudTransparencyTarget()Ldev/nonamecrackers2/simpleclouds/client/framebuffer/WeightedBlendingTarget;"
            ),
            require = 0
    )
    private void projectatmosphere$renderTornadoOpaque(Minecraft mc, SimpleCloudsRenderer renderer,
                                                       PoseStack stack, Matrix4f projMat, float partialTick,
                                                       double camX, double camY, double camZ, Frustum frustum,
                                                       CallbackInfo ci) {
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        boolean pathLog = SimpleCloudsTornadoRenderer.shouldPathLog(level);
        if (pathLog) {
            SimpleCloudsTornadoRenderer.path(
                    "ShaderSupportPipeline hook entered gameTime={} dhLoaded={} mainDepth={} cloudDepth={} transparencyDepth={}",
                    level.getGameTime(),
                    SimpleCloudsMod.dhLoaded(),
                    mc.getMainRenderTarget().getDepthTextureId(),
                    renderer.getCloudTarget().getDepthTextureId(),
                    renderer.getCloudTransparencyTarget().getDepthTextureId()
            );
        }
        if (SimpleCloudsMod.dhLoaded()) {
            if (pathLog) {
                SimpleCloudsTornadoRenderer.path("ShaderSupportPipeline skipped: SimpleClouds reports DH loaded");
            }
            return;
        }
        float[] cloudColor = renderer.getCloudColor(partialTick);
        mc.getProfiler().push("projectatmosphere_tornado_opaque");
        SimpleCloudsTornadoRenderer.INSTANCE.prepareFrame(level, partialTick);
        boolean hasPreparedTornado = SimpleCloudsTornadoRenderer.INSTANCE.hasPreparedTornadoes();
        if (pathLog) {
            SimpleCloudsTornadoRenderer.path(
                    "ShaderSupportPipeline prepared tornadoes={} hasPrepared={} frustumGate=disabled",
                    SimpleCloudsTornadoRenderer.INSTANCE.preparedTornadoCount(),
                    hasPreparedTornado
            );
        }
        if (!hasPreparedTornado) {
            if (pathLog) {
                SimpleCloudsTornadoRenderer.path("ShaderSupportPipeline skipped: no prepared tornado");
            }
            mc.getProfiler().pop();
            return;
        }
        boolean downsampled = SimpleCloudsTornadoRenderer.INSTANCE.usesDownsamplePath();
        if (!downsampled) {
            renderer.copyDepthFromCloudsToTransparency();
        }
        int primaryDepth = downsampled ? renderer.getCloudTarget().getDepthTextureId() : renderer.getCloudTransparencyTarget().getDepthTextureId();
        int secondaryDepth = -1;
        if (pathLog) {
            SimpleCloudsTornadoRenderer.path(
                    "ShaderSupportPipeline drawing downsampled={} primaryDepth={} secondaryDepth={} cloudTarget={}x{} transparencyTarget={}x{}",
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
