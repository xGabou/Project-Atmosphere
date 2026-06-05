package net.Gabou.projectatmosphere.util;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

@Deprecated(forRemoval = true, since = "10.0.0")
public record BiomeInstanceKey(ResourceLocation biomeType, BlockPos samplePos) {

    @Override
    public @NotNull String toString() {
        return biomeType.toString() + "@" + samplePos.toShortString();
    }

}


