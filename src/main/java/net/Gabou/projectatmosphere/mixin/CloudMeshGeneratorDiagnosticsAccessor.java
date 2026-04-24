package net.Gabou.projectatmosphere.mixin;

import dev.nonamecrackers2.simpleclouds.client.mesh.chunk.MeshChunk;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Queue;

@Mixin(value = CloudMeshGenerator.class, remap = false)
public interface CloudMeshGeneratorDiagnosticsAccessor {
    @Accessor("chunks")
    List<MeshChunk> projectatmosphere$getChunks();

    @Accessor("completedGenTasks")
    List<?> projectatmosphere$getCompletedGenTasks();

    @Accessor("chunkGenTasks")
    Queue<?> projectatmosphere$getChunkGenTasks();

    @Accessor("tasksPerTick")
    int projectatmosphere$getTasksPerTick();

    @Accessor("opaqueBufferSize")
    int projectatmosphere$getOpaqueBufferSize();

    @Accessor("transparentBufferSize")
    int projectatmosphere$getTransparentBufferSize();

    @Accessor("meshGenStatus")
    Pair<CloudMeshGenerator.MeshGenStatus, CloudMeshGenerator.MeshGenStatus> projectatmosphere$getMeshGenStatus();
}
