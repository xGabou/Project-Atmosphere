package net.Gabou.projectatmosphere.util;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public class AtmosphereUtils {

    /**
     * Serialize a BlockPos to a JsonObject.
     */
    public static JsonObject serializeBlockPos(BlockPos pos) {
        JsonObject obj = new JsonObject();
        obj.addProperty("x", pos.getX());
        obj.addProperty("y", pos.getY());
        obj.addProperty("z", pos.getZ());
        return obj;
    }

    /**
     * Deserialize a BlockPos from a JsonObject.
     */
    public static BlockPos deserializeBlockPos(JsonObject obj) {
        int x = obj.get("x").getAsInt();
        int y = obj.get("y").getAsInt();
        int z = obj.get("z").getAsInt();
        return new BlockPos(x, y, z);
    }

    /**
     * Gets the biome key at a given position in a level.
     */
    public static BiomeInstanceKey getBiomeKey(ServerLevel level, BlockPos pos) {
        ResourceLocation biomeId = level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                .getKey(level.getBiome(pos).value());

        return new BiomeInstanceKey(biomeId, pos);
    }
    public static ResourceLocation getBiomeLocation(BlockPos pos, Level world) {
        return world.getBiome(pos).unwrapKey().get().location();
    }


}
