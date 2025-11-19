package net.Gabou.projectatmosphere.mixin;

import dev.nonamecrackers2.simpleclouds.api.common.cloud.region.ITornadoRegion;
import dev.nonamecrackers2.simpleclouds.api.common.cloud.region.ScAPICloudRegion;
import dev.nonamecrackers2.simpleclouds.api.common.cloud.region.TornadoDescriptor;
import dev.nonamecrackers2.simpleclouds.api.common.event.CloudRegionTickEvent;
import dev.nonamecrackers2.simpleclouds.api.common.event.CloudRegionTickEventTornadoAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Collections;
import java.util.List;

@Mixin(value = CloudRegionTickEvent.class, remap = false)
public abstract class CloudRegionTickEventMixin implements CloudRegionTickEventTornadoAccess {

    @Shadow public abstract ScAPICloudRegion getCloudRegion();

    @Override
    public List<TornadoDescriptor> getTornadoes() {
        ScAPICloudRegion region = this.getCloudRegion();
        if (region instanceof ITornadoRegion tornadoRegion) {
            return tornadoRegion.getTornadoesView();
        }
        return Collections.emptyList();
    }
}
