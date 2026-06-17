package net.Gabou.projectatmosphere.mixin;

import dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.client.shader.compute.ComputeShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CloudMeshGenerator.class, remap = false)
public interface CloudMeshGeneratorAccessor {
    @Accessor("shader")
    ComputeShader projectatmosphere$getShader();
}
