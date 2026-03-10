package net.Gabou.projectatmosphere.util;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

/**
 * Identifies a fixed world region used for atmospheric forecasts.
 * Regions are laid out on a deterministic grid so any world position
 * maps to exactly one {@link RegionInstanceKey}.
 */
public record RegionInstanceKey(int regionX, int regionZ, int regionSize) {

    public static final int DEFAULT_REGION_SIZE = 2000;

    public RegionInstanceKey(int regionX, int regionZ) {
        this(regionX, regionZ, DEFAULT_REGION_SIZE);
    }

    /**
     * Maps a world position to a region key using the default grid size.
     */
    public static RegionInstanceKey from(BlockPos pos) {
        return from(pos, DEFAULT_REGION_SIZE);
    }

    /**
     * Maps a world position to a region key using the provided grid size.
     */
    public static RegionInstanceKey from(BlockPos pos, int regionSize) {
        if (pos == null) {
            throw new IllegalArgumentException("Cannot build RegionInstanceKey from null position");
        }
        int rx = Math.floorDiv(pos.getX(), regionSize);
        int rz = Math.floorDiv(pos.getZ(), regionSize);
        return new RegionInstanceKey(rx, rz, regionSize);
    }

    /**
     * Returns the center position of this region in block coordinates.
     * The y coordinate is always 0 and should be replaced with a meaningful altitude by callers.
     */
    public BlockPos center() {
        int minX = regionX * regionSize;
        int minZ = regionZ * regionSize;
        return new BlockPos(minX + regionSize / 2, 0, minZ + regionSize / 2);
    }

    /**
     * Checks whether the provided position falls inside this region.
     */
    public boolean contains(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        int minX = regionX * regionSize;
        int minZ = regionZ * regionSize;
        int maxX = minX + regionSize;
        int maxZ = minZ + regionSize;
        return pos.getX() >= minX && pos.getX() < maxX
                && pos.getZ() >= minZ && pos.getZ() < maxZ;
    }

    /**
     * Returns an adjacent region key offset by the given grid delta.
     */
    public RegionInstanceKey neighbor(int dx, int dz) {
        return new RegionInstanceKey(regionX + dx, regionZ + dz, regionSize);
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("regionX", regionX);
        obj.addProperty("regionZ", regionZ);
        obj.addProperty("regionSize", regionSize);
        return obj;
    }

    public static RegionInstanceKey fromJson(JsonObject obj) {
        int size = obj.has("regionSize") ? obj.get("regionSize").getAsInt() : DEFAULT_REGION_SIZE;
        int rx = obj.get("regionX").getAsInt();
        int rz = obj.get("regionZ").getAsInt();
        return new RegionInstanceKey(rx, rz, size);
    }

    @Override
    public @NotNull String toString() {
        return "region[" + regionX + "," + regionZ + "]@" + regionSize;
    }
}
