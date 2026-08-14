package net.Gabou.projectatmosphere.mixin;

import dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.client.shader.compute.ComputeShader;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.tools.debug.SimpleCloudsRenderDiagnostics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CloudMeshGenerator.class, remap = false)
public abstract class CloudMeshGeneratorShaderMixin {
    @Shadow
    @Final
    protected ResourceLocation meshShaderLoc;

    @ModifyArg(
            method = "createShader",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/nonamecrackers2/simpleclouds/client/shader/compute/ComputeShader;loadShader(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/server/packs/resources/ResourceProvider;IIILcom/google/common/collect/ImmutableMap;)Ldev/nonamecrackers2/simpleclouds/client/shader/compute/ComputeShader;"
            ),
            index = 0
    )
    // Keep this as a ModifyArg: Better Simple Clouds redirects the same invocation and
    // can safely receive this selected location, while two Redirect injectors collide.
    private ResourceLocation projectatmosphere$selectCubeMeshShader(ResourceLocation loc) {
        if (loc != null && "simpleclouds".equals(loc.getNamespace()) && "cube_mesh".equals(loc.getPath())) {
            return ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cube_mesh");
        }
        return loc;
    }

    @Inject(method = "createShader", at = @At("RETURN"))
    private void projectatmosphere$logSelectedCubeMeshShader(
            ResourceManager resourceManager,
            CallbackInfoReturnable<ComputeShader> cir
    ) {
        ResourceLocation shaderLoc = projectatmosphere$selectCubeMeshShader(this.meshShaderLoc);
        ComputeShader shader = cir.getReturnValue();
        SimpleCloudsRenderDiagnostics.logShaderLoad(
                "cube_mesh",
                this.meshShaderLoc,
                shaderLoc,
                shader == null ? "null" : shader.getName(),
                shader == null ? -1 : shader.getId(),
                shader != null && shader.isValid()
        );
    }
}
