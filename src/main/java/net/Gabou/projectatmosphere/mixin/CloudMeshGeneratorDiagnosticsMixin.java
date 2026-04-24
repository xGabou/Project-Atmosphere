package net.Gabou.projectatmosphere.mixin;

import dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator;
import net.Gabou.projectatmosphere.client.render.SimpleCloudsRenderDiagnostics;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(value = CloudMeshGenerator.class, remap = false)
public abstract class CloudMeshGeneratorDiagnosticsMixin {
    @Unique
    private static final AtomicBoolean PROJECTATMOSPHERE$GEN_TICK_EMPTY_LOGGED = new AtomicBoolean();
    @Unique
    private static final AtomicBoolean PROJECTATMOSPHERE$GEN_TICK_ACTIVE_LOGGED = new AtomicBoolean();
    @Unique
    private static final AtomicBoolean PROJECTATMOSPHERE$FINALIZE_EMPTY_LOGGED = new AtomicBoolean();
    @Unique
    private static final AtomicBoolean PROJECTATMOSPHERE$FINALIZE_ACTIVE_LOGGED = new AtomicBoolean();

    @Inject(method = "genTick", at = @At("HEAD"))
    private void projectatmosphere$logGenTickStart(double originX, double originY, double originZ, net.minecraft.client.renderer.culling.Frustum frustum, float partialTick, CallbackInfo ci) {
        CloudMeshGeneratorDiagnosticsAccessor accessor = (CloudMeshGeneratorDiagnosticsAccessor)(Object)this;
        int queuedTasks = projectatmosphere$count(accessor.projectatmosphere$getChunkGenTasks());
        int completedTasks = projectatmosphere$count(accessor.projectatmosphere$getCompletedGenTasks());
        int tasksPerTick = accessor.projectatmosphere$getTasksPerTick();
        if ((queuedTasks > 0 || completedTasks > 0 || tasksPerTick > 0) && PROJECTATMOSPHERE$GEN_TICK_ACTIVE_LOGGED.compareAndSet(false, true)) {
            SimpleCloudsRenderDiagnostics.logPipelineStage(
                    "mesh_generator",
                    "genTick_active",
                    accessor.projectatmosphere$getChunks() == null ? 0 : accessor.projectatmosphere$getChunks().size(),
                    queuedTasks,
                    completedTasks,
                    true,
                    true,
                    accessor.projectatmosphere$getMeshGenStatus()
            );
        } else if (PROJECTATMOSPHERE$GEN_TICK_EMPTY_LOGGED.compareAndSet(false, true)) {
            SimpleCloudsRenderDiagnostics.logPipelineStage(
                    "mesh_generator",
                    "genTick_empty",
                    accessor.projectatmosphere$getChunks() == null ? 0 : accessor.projectatmosphere$getChunks().size(),
                    queuedTasks,
                    completedTasks,
                    true,
                    true,
                    accessor.projectatmosphere$getMeshGenStatus()
            );
        }
    }

    @Inject(method = "finalizeMeshGen", at = @At("RETURN"))
    private void projectatmosphere$logFinalizeMeshGen(CallbackInfoReturnable<Pair<CloudMeshGenerator.MeshGenStatus, CloudMeshGenerator.MeshGenStatus>> cir) {
        CloudMeshGeneratorDiagnosticsAccessor accessor = (CloudMeshGeneratorDiagnosticsAccessor)(Object)this;
        int completedTasks = projectatmosphere$count(accessor.projectatmosphere$getCompletedGenTasks());
        int opaqueElements = projectatmosphere$countElements(accessor.projectatmosphere$getChunks(), false);
        int transparentElements = projectatmosphere$countElements(accessor.projectatmosphere$getChunks(), true);
        boolean active = completedTasks > 0 || opaqueElements > 0 || transparentElements > 0;
        if (active && PROJECTATMOSPHERE$FINALIZE_ACTIVE_LOGGED.compareAndSet(false, true)) {
            SimpleCloudsRenderDiagnostics.logFinalizeMeshGen(
                    completedTasks,
                    opaqueElements,
                    transparentElements,
                    accessor.projectatmosphere$getOpaqueBufferSize(),
                    accessor.projectatmosphere$getTransparentBufferSize(),
                    cir.getReturnValue()
            );
        } else if (PROJECTATMOSPHERE$FINALIZE_EMPTY_LOGGED.compareAndSet(false, true)) {
            SimpleCloudsRenderDiagnostics.logFinalizeMeshGen(
                    completedTasks,
                    opaqueElements,
                    transparentElements,
                    accessor.projectatmosphere$getOpaqueBufferSize(),
                    accessor.projectatmosphere$getTransparentBufferSize(),
                    cir.getReturnValue()
            );
        }
    }

    @Unique
    private static int projectatmosphere$count(List<?> values) {
        return values == null ? 0 : values.size();
    }

    @Unique
    private static int projectatmosphere$count(Queue<?> values) {
        return values == null ? 0 : values.size();
    }

    @Unique
    private static int projectatmosphere$countElements(List<dev.nonamecrackers2.simpleclouds.client.mesh.chunk.MeshChunk> chunks, boolean transparent) {
        if (chunks == null) {
            return 0;
        }
        int total = 0;
        for (dev.nonamecrackers2.simpleclouds.client.mesh.chunk.MeshChunk chunk : chunks) {
            if (transparent) {
                total += chunk.getTransparentBuffers().map(dev.nonamecrackers2.simpleclouds.client.mesh.chunk.MeshChunk.BufferSet::getElementCount).orElse(0);
            } else {
                total += chunk.getOpaqueBuffers().getElementCount();
            }
        }
        return total;
    }
}
