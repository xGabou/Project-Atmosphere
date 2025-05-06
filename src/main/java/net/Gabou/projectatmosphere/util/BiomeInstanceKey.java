package net.Gabou.projectatmosphere.util;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record BiomeInstanceKey(ResourceLocation biomeType, BlockPos samplePos) {

    @Override
    public @NotNull String toString() {
        return biomeType.toString() + "@" + samplePos.toShortString();
    }

    public static BiomeInstanceKey fromString(String input) {
        try {
            String[] parts = input.split("@");
            if (parts.length != 2) throw new IllegalArgumentException("Invalid format: missing '@' separator");

            ResourceLocation biome = new ResourceLocation(parts[0]);
            String[] posParts = parts[1].split("_");
            if (posParts.length != 3) throw new IllegalArgumentException("Invalid position format");

            int x = Integer.parseInt(posParts[0]);
            int y = Integer.parseInt(posParts[1]);
            int z = Integer.parseInt(posParts[2]);

            return new BiomeInstanceKey(biome, new BlockPos(x, y, z));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid BiomeInstanceKey format: " + input, e);
        }
    }
    public static BiomeInstanceKey fromJson(JsonObject obj) {
        ResourceLocation biome = new ResourceLocation(obj.get("biome").getAsString());
        BlockPos pos = AtmosphereUtils.deserializeBlockPos(obj.get("pos").getAsJsonObject());
        return new BiomeInstanceKey(biome, pos);
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("biome", biomeType.toString());
        obj.add("pos", AtmosphereUtils.serializeBlockPos(samplePos));
        return obj;
    }


}


