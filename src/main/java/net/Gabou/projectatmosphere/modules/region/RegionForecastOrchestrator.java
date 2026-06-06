package net.Gabou.projectatmosphere.modules.region;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class RegionForecastOrchestrator {
    private final ServerLevel level;
    private final RegionIndex regionIndex;
    private final RegionPersistence persistence;
    private final Map<RegionInstanceKey, ForecastRegion> regions = new ConcurrentHashMap<>();

    public RegionForecastOrchestrator(ServerLevel level,
                                      RegionIndex regionIndex,
                                      RegionPersistence persistence) {
        this.level = level;
        this.regionIndex = regionIndex;
        this.persistence = persistence;
    }

    public ForecastRegion resolve(BlockPos pos, ResourceKey<Level> dimension) {
        RegionInstanceKey id = regionIndex.regionFor(pos, dimension);
        return ensureLoaded(id);
    }

    public ForecastRegion ensureLoaded(RegionInstanceKey id) {
        return regions.computeIfAbsent(id, this::loadOrGenerate);
    }

    private ForecastRegion loadOrGenerate(RegionInstanceKey id) {
        Optional<ForecastRegion> persisted = persistence.loadRegion(id);
        if (persisted.isPresent()) {
            ForecastRegion region = persisted.get();
            RegionForecastCorruptionValidator.CorruptionReport report = RegionForecastCorruptionValidator.detect(region);
            if (!report.corrupted()) {
                return region;
            }
            ProjectAtmosphere.LOGGER.warn(
                    "[Atmosphere] Region {} save appears corrupted ({}). Regenerating region forecast.",
                    id,
                    report.message()
            );
        }

        ForecastRegion generated = ForecastGenerator.generateForecastForRegionKey(id, level);
        persistence.saveRegion(generated);
        return generated;
    }

    public void tick(long gameTime) {
        // Hook for per-tick advancement keyed by region id.
    }

    public Vec3 toRegionLocal(BlockPos pos) {
        RegionInstanceKey regionKey = RegionInstanceKey.from(pos);
        return RegionAdapters.toRegionLocal(pos, regionKey);
    }
}
