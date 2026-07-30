package net.Gabou.projectatmosphere.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class StorageUtils {
    // ---------------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------------
    public static void clearCache(ServerLevel world, String fileName) {
        try {
            Files.deleteIfExists(getPerWorldSavePath(world, fileName));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ---------------------------------------------------------------------
    // Saving
    // ---------------------------------------------------------------------
    /**
     * Saves the temperature forecasts to the JSON file.
     * This method is called asynchronously to avoid blocking the main thread.
     */

    // ---------------------------------------------------------------------
    // Paths
    // ---------------------------------------------------------------------
    public static Path getPerWorldSavePath(ServerLevel world, String fileName) {
        String dimensionPath = world.dimension().location().getNamespace().equals("minecraft")
                ? world.dimension().location().getPath()
                : world.dimension().location().getNamespace() + "_" + world.dimension().location().getPath();

        return world.getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve(dimensionPath)
                .resolve("data")
                .resolve("projectatmosphere")
                .resolve(fileName);
    }
}
