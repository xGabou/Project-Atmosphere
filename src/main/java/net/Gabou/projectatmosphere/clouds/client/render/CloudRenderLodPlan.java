package net.Gabou.projectatmosphere.clouds.client.render;

import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable render plan for one cloud snapshot after LOD and budget selection.
 */
public record CloudRenderLodPlan(
        @NotNull CloudRenderSnapshot snapshot,
        @NotNull CloudRenderProfile renderProfile,
        @NotNull CloudRenderLodTier tier,
        float distanceToCamera,
        float priority,
        float fadeAlpha
) {
}
