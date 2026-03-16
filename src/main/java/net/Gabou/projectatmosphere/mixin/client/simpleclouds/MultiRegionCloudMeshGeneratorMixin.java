package net.Gabou.projectatmosphere.mixin.client.simpleclouds;

import dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.MultiRegionCloudMeshGenerator;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MultiRegionCloudMeshGenerator.class, remap = false)
public class MultiRegionCloudMeshGeneratorMixin
{
    @Inject(method = "onOffGen", at = @At("HEAD"), cancellable = true)
    private void projectatmosphere$cancelOnOffGenUnlessFullscreenVsync(CallbackInfo ci)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null)
            return;

        boolean vsync = mc.options.enableVsync().get();
        boolean fullscreen = mc.getWindow() != null && mc.getWindow().isFullscreen();

        if (!(vsync && fullscreen))
            ci.cancel();
    }
}

