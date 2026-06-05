package net.Gabou.projectatmosphere.util;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

@Deprecated
public record BiomeInstanceKey(ResourceLocation biomeType, BlockPos samplePos) {

    @Override
    public @NotNull String toString() {
        return biomeType.toString() + "@" + samplePos.toShortString();
    }

}


