package net.Gabou.projectatmosphere.mixin;

import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudSpawningConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = CloudSpawningConfig.Info.class, remap = false)
public abstract class InfoMixin {
    /**
     * Force all CloudSpawningConfig.Info entries to report
     * movesToPlayer = false, regardless of what was set.
     * @author Gabou
     * @reason Clouds should not move to player in Project Atmosphere
     */
    @Overwrite
    public boolean movesToPlayer() {
        return false;
    }
}
