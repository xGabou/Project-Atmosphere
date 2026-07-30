package net.Gabou.projectatmosphere.clouds.field;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Render boundary for the future CloudField renderer. It contains snapshots
 * only, never mutable simulation objects.
 */
public record CloudFieldRendererInput(
        List<CloudFieldSnapshot> fields,
        long worldTime,
        float partialTick,
        Vec3 cameraPosition
) {
    public CloudFieldRendererInput {
        fields = fields == null ? List.of() : List.copyOf(fields);
        partialTick = Float.isFinite(partialTick) ? partialTick : 0.0F;
        cameraPosition = cameraPosition == null ? Vec3.ZERO : cameraPosition;
    }

    public static CloudFieldRendererInput empty(long worldTime, float partialTick, Vec3 cameraPosition) {
        return new CloudFieldRendererInput(List.of(), worldTime, partialTick, cameraPosition);
    }

    public List<CloudFieldSnapshot> visibleFields() {
        return fields.stream()
                .filter(CloudFieldSnapshot::hasVisibleClouds)
                .toList();
    }

    public int dynamicCloudletCount() {
        int total = 0;
        for (CloudFieldSnapshot field : fields) {
            total += field.dynamicCloudletCount();
        }
        return total;
    }

    public boolean hasDynamicCloudlets() {
        return dynamicCloudletCount() > 0;
    }
}
