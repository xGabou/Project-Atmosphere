package net.Gabou.projectatmosphere.mixin.client;

import dev.nonamecrackers2.simpleclouds.client.mesh.instancing.InstanceableMesh;
import net.Gabou.projectatmosphere.tools.debug.SimpleCloudsRenderDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InstanceableMesh.class, remap = false)
public abstract class InstanceableMeshDiagnosticsMixin {
    @Inject(method = "drawInstanced", at = @At("HEAD"))
    private void projectatmosphere$recordDrawCount(int count, CallbackInfo ci) {
        SimpleCloudsRenderDiagnostics.recordDraw("simpleclouds", count);
    }
}
