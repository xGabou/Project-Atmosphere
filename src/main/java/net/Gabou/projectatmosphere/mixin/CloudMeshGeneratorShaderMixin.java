package net.Gabou.projectatmosphere.mixin;

import com.google.common.collect.ImmutableMap;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.client.shader.compute.ComputeShader;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.client.render.SimpleCloudsRenderDiagnostics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;

@Mixin(value = CloudMeshGenerator.class, remap = false)
public abstract class CloudMeshGeneratorShaderMixin {
    @Redirect(
            method = "createShader",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/nonamecrackers2/simpleclouds/client/shader/compute/ComputeShader;loadShader(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/server/packs/resources/ResourceProvider;IIILcom/google/common/collect/ImmutableMap;)Ldev/nonamecrackers2/simpleclouds/client/shader/compute/ComputeShader;"
            )
    )
    private ComputeShader projectatmosphere$useProjectAtmosphereCubeMeshShader(ResourceLocation loc, ResourceProvider provider, int localX, int localY, int localZ, ImmutableMap<String, String> parameters) throws IOException {
        ResourceLocation shaderLoc = loc;
        if (loc != null && "simpleclouds".equals(loc.getNamespace()) && "cube_mesh".equals(loc.getPath())) {
            shaderLoc = ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cube_mesh");
        }
        ComputeShader shader = ComputeShader.loadShader(shaderLoc, provider, localX, localY, localZ, parameters);
        SimpleCloudsRenderDiagnostics.logShaderLoad(
                "cube_mesh",
                loc,
                shaderLoc,
                shader == null ? "null" : shader.getName(),
                shader == null ? -1 : shader.getId(),
                shader != null && shader.isValid()
        );
        return shader;
    }
}
