package net.Gabou.projectatmosphere.mixin;

import dev.nonamecrackers2.simpleclouds.client.mesh.chunk.MeshChunk;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = CloudMeshGenerator.class, remap = false)
public interface CloudMeshGeneratorDiagnosticsAccessor {
    @Accessor("chunks")
    List<MeshChunk> projectatmosphere$getChunks();

    @Accessor("opaqueBufferSize")
    int projectatmosphere$getOpaqueBufferSize();

    @Accessor("transparentBufferSize")
    int projectatmosphere$getTransparentBufferSize();

    @Accessor("meshGenStatus")
    Pair<CloudMeshGenerator.MeshGenStatus, CloudMeshGenerator.MeshGenStatus> projectatmosphere$getMeshGenStatus();
}
