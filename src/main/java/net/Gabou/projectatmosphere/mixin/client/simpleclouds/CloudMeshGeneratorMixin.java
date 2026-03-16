package net.Gabou.projectatmosphere.mixin.client.simpleclouds;

import dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CloudMeshGenerator.class, remap = false)
public class CloudMeshGeneratorMixin
{
    @Inject(method = "onOffGen", at = @At("HEAD"), cancellable = true)
    private void projectatmosphere$cancelOnOffGenWhenVsyncDisabled(CallbackInfo ci)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null)
            return;

        if (!mc.options.enableVsync().get())
            ci.cancel();
        if(!mc.getWindow().isFullscreen())
            ci.cancel();
    }
}
