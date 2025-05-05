//package net.Gabou.projectatmosphere.modules.pressure.system;
//
//import net.Gabou.projectatmosphere.modules.pressure.util.PressureGenerator;
//import net.Gabou.projectatmosphere.modules.humidity.manager.HumidityManager;
//import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureProfileManager;
//import net.minecraft.core.BlockPos;
//import net.minecraft.resources.ResourceLocation;
//import net.minecraft.server.level.ServerLevel;
//import net.minecraft.world.level.Level;
//import net.minecraft.world.level.levelgen.Heightmap;
//
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//
//public class PressureSystemManager {
//    private static final float DIFFUSION_RATE = 0.1f;
//    private static final Map<CellPos, Float> pressureGrid = new ConcurrentHashMap<>();
//    private static final Map<CellPos, Float> nextPressureGrid = new ConcurrentHashMap<>();
//
//    public static void init(ServerLevel world, BlockPos center, int radiusBlocks) {
//        pressureGrid.clear();
//        int centerCX = center.getX() >> 4;
//        int centerCZ = center.getZ() >> 4;
//        int rChunks = radiusBlocks / 16;
//        ResourceLocation dim = world.dimension().location();
//
//        for (int dx = -rChunks; dx <= rChunks; dx++) {
//            for (int dz = -rChunks; dz <= rChunks; dz++) {
//                int cx = centerCX + dx, cz = centerCZ + dz;
//                CellPos cell = new CellPos(dim, cx, cz);
//                BlockPos sample = new BlockPos((cx << 4) + 8, world.getHeight(Heightmap.Types.WORLD_SURFACE, (cx << 4) + 8, (cz << 4) + 8), (cz << 4) + 8);
//                ResourceLocation biome = world.getBiome(sample).unwrapKey().get().location();
//
//                float[][] week = PressureGenerator.generateWeekForecast(world, sample, biome);
//                float todayAvg = (week[0][0] + week[0][1]) * 0.5f;
//
//                pressureGrid.put(cell, todayAvg);
//            }
//        }
//    }
//
//    public static void tick(ServerLevel world) {
//        nextPressureGrid.clear();
//        for (CellPos cell : pressureGrid.keySet()) {
//            float current = pressureGrid.get(cell);
//            float diffused = diffuse(cell, current);
//            float thermal = computeThermalOffset(world, cell);
//            nextPressureGrid.put(cell, diffused + thermal);
//        }
//        pressureGrid.clear();
//        pressureGrid.putAll(nextPressureGrid);
//    }
//
//    private static float diffuse(CellPos cell, float current) {
//        List<CellPos> neighbors = List.of(
//                new CellPos(cell.dimension(), cell.chunkX() + 1, cell.chunkZ()),
//                new CellPos(cell.dimension(), cell.chunkX() - 1, cell.chunkZ()),
//                new CellPos(cell.dimension(), cell.chunkX(), cell.chunkZ() + 1),
//                new CellPos(cell.dimension(), cell.chunkX(), cell.chunkZ() - 1)
//        );
//
//        float sum = 0;
//        int count = 0;
//        for (CellPos n : neighbors) {
//            Float p = pressureGrid.get(n);
//            if (p != null) {
//                sum += p;
//                count++;
//            }
//        }
//
//        return count > 0 ? current + DIFFUSION_RATE * ((sum / count) - current) : current;
//    }
//
//    private static float computeThermalOffset(ServerLevel world, CellPos cell) {
//        BlockPos sample = new BlockPos((cell.chunkX() << 4) + 8,
//                world.getHeight(Heightmap.Types.WORLD_SURFACE, (cell.chunkX() << 4) + 8, (cell.chunkZ() << 4) + 8),
//                (cell.chunkZ() << 4) + 8);
//
//        ResourceLocation biome = world.getBiome(sample).unwrapKey().get().location();
//        float[][] tempWeek = TemperatureProfileManager.getWeeklyForecast(biome);
//        float Tavg = (tempWeek == null) ? 15f : (tempWeek[0][0] + tempWeek[0][1]) * 0.5f;
//        float deltaT = -0.5f * (Tavg - 15f);
//
//        float RH = HumidityManager.getAverageHumidity(biome, 0);
//        float deltaH = -0.05f * RH;
//
//        return deltaT + deltaH;
//    }
//
//    public static float getPressure(ServerLevel world, BlockPos pos) {
//        CellPos cell = new CellPos(world.dimension().location(), pos.getX() >> 4, pos.getZ() >> 4);
//        return pressureGrid.getOrDefault(cell, 1013.25f);
//    }
//
//    public static Map<CellPos, Float> getLiveGrid() {
//        return Collections.unmodifiableMap(pressureGrid);
//    }
//}
