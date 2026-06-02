# Forecast Region Refactor Blueprint

Region-first migration plan that replaces biome-scoped runtime APIs with forecast regions, while keeping biomes only in generation and fallback. The design honors the eight-section aggregation and JSON fallback requirements and is structured for staged rollout.

## Goals and constraints
- Runtime APIs and storage are keyed by `ForecastRegionId` (no biome keys in gameplay paths).
- Biomes are used only for generation, reset, or recovery; biome snapshots are persisted for fallback and then dropped from memory.
- Each forecast region owns eight internal sections (biome-derived slices) with factors whose weighted sum reconstructs region curves for temperature, humidity, pressure, storm probability, wind, and related variables.
- Save migration and networking move from biome identifiers to region identifiers; legacy biome packets/adapters are temporary and deprecated.

## Phased refactor plan
1) Core types and mapping
- Add `ForecastRegionId`, `ForecastRegion`, `RegionForecastOrchestrator`, and a `RegionIndex` that maps chunks→regions and regions→biome members.
- Keep existing biome-facing public APIs as thin adapters that resolve a region id and delegate.

2) Generation layer
- Pipeline: biome slice generation → eight-section assembly → aggregate region curves → JSON fallback write. In-memory biome forecasts are discarded after the region is created and persisted.

3) Runtime swap
- Public sampling APIs accept `ForecastRegionId` (or `ForecastRegion`); biome APIs are deprecated adapters.
- Caches, registries, and services store data keyed by region id.

4) Module migrations
- Move temperature, humidity, pressure (incl. diffusion), clouds/SimpleClouds, wind, snow, instruments, orchestrator logic to region ids. Update networking payloads and serializers accordingly.

5) Cleanup
- Remove runtime biome access; keep biome keys only inside generation/fallback.
- Delete or replace dead/obsolete classes (see SynLightController and dead-code notes).

## Architecture (text diagram)
- RegionForecastOrchestrator
  - owns `Map<ForecastRegionId, ForecastRegion> activeRegions`
  - services: `resolveRegion(BlockPos/Vec3)`, `ensureRegionLoaded(id)`, `generateRegionFromBiomes(id)`, `loadFallback(id)`, `saveFallback(id)`, `tick(long time)`
  - inputs: biome map at load/reset, JSON fallback data
- ForecastRegion
  - fields: `ForecastRegionId id`; `List<BiomeInstanceKey> sourceBiomes` (gen/fallback only); `Section[8] sections { float factor; BiomeForecastSnapshot snapshot; }`; `RegionCurves curves` (temp/humidity/pressure/wind/storm/etc); `BiomeFallbackSnapshot fallbackSnapshot`
  - methods: sampling per variable; `clearBiomeForecasts()` drops in-memory biome snapshots after aggregation
- Runtime consumers
  - TemperatureSystem, HumiditySystem, Pressure (incl. diffusion), CloudController/SimpleClouds, WindSystem, SnowAccumulator, Instruments, SpikeManager, VariationGenerator — all consume region ids.
- JSON fallback layer
  - Per-region file containing eight biome-derived section snapshots plus the region aggregate; used only on load/reset/recovery.

## Key scaffolding (Java-style)

```java
package net.Gabou.projectatmosphere.modules.region;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record ForecastRegionId(int rx, int rz, ResourceKey<Level> dimension) {
    public static ForecastRegionId ofChunk(int chunkX, int chunkZ, ResourceKey<Level> dim) {
        // example: 8x8 chunks per forecast region (adjust if grid differs)
        return new ForecastRegionId(chunkX >> 3, chunkZ >> 3, dim);
    }

    public BlockPos toCenterBlockPos() {
        int blockX = (rx << 3) * 16 + 64;
        int blockZ = (rz << 3) * 16 + 64;
        return new BlockPos(blockX, 0, blockZ);
    }
}
```

```java
package net.Gabou.projectatmosphere.modules.region;

import java.util.List;
import javax.annotation.Nullable;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;

public final class ForecastRegion {
    private final ForecastRegionId id;
    private final List<BiomeInstanceKey> sourceBiomes; // generation/fallback only
    private final Section[] sections;                  // fixed-size 8
    private final RegionCurves curves;                 // temp, humidity, pressure, wind, storm, etc.
    private final BiomeFallbackSnapshot fallbackSnapshot;

    public ForecastRegion(ForecastRegionId id,
                          List<BiomeInstanceKey> sourceBiomes,
                          Section[] sections,
                          RegionCurves curves,
                          BiomeFallbackSnapshot fallbackSnapshot) {
        this.id = id;
        this.sourceBiomes = List.copyOf(sourceBiomes);
        this.sections = sections.clone();
        this.curves = curves;
        this.fallbackSnapshot = fallbackSnapshot;
    }

    public ForecastRegionId id() { return id; }
    public RegionCurves curves() { return curves; }
    public BiomeFallbackSnapshot fallbackSnapshot() { return fallbackSnapshot; }

    public TemperatureSample sampleTemperature(Vec3 inRegionPos, long gameTime) {
        return curves.temperature().sample(inRegionPos, gameTime, sections);
    }

    public HumiditySample sampleHumidity(Vec3 inRegionPos, long gameTime) {
        return curves.humidity().sample(inRegionPos, gameTime, sections);
    }

    public PressureSample samplePressure(long gameTime) {
        return curves.pressure().sample(gameTime, sections);
    }

    public WindSample sampleWind(long gameTime) {
        return curves.wind().sample(gameTime, sections);
    }

    public StormSample sampleStorm(long gameTime) {
        return curves.storm().sample(gameTime, sections);
    }

    public void clearBiomeForecasts() {
        for (Section s : sections) {
            s.clearSnapshot();
        }
    }

    public static final class Section {
        private final float factor;
        private BiomeForecastSnapshot snapshot; // nulled after clear

        public Section(float factor, BiomeForecastSnapshot snapshot) {
            this.factor = factor;
            this.snapshot = snapshot;
        }

        public float factor() { return factor; }
        public @Nullable BiomeForecastSnapshot snapshot() { return snapshot; }
        public void clearSnapshot() { this.snapshot = null; }
    }
}
```

```java
package net.Gabou.projectatmosphere.modules.region;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class RegionForecastOrchestrator {
    private final RegionIndex regionIndex; // chunk->region + biomes per region
    private final Map<ForecastRegionId, ForecastRegion> regions = new ConcurrentHashMap<>();
    private final RegionPersistence persistence;
    private final BiomeForecastGenerator biomeGenerator;

    public RegionForecastOrchestrator(RegionIndex regionIndex,
                                      RegionPersistence persistence,
                                      BiomeForecastGenerator biomeGenerator) {
        this.regionIndex = regionIndex;
        this.persistence = persistence;
        this.biomeGenerator = biomeGenerator;
    }

    public ForecastRegion resolve(BlockPos pos, ResourceKey<Level> dim) {
        return ensureLoaded(regionIndex.regionFor(pos, dim));
    }

    public ForecastRegion ensureLoaded(ForecastRegionId id) {
        return regions.computeIfAbsent(id, this::loadOrGenerate);
    }

    private ForecastRegion loadOrGenerate(ForecastRegionId id) {
        Optional<BiomeFallbackSnapshot> fb = persistence.loadFallback(id);
        if (fb.isPresent()) {
            return fromFallback(fb.get());
        }
        return generateFromBiomes(id);
    }

    private ForecastRegion fromFallback(BiomeFallbackSnapshot fb) {
        ForecastRegion.Section[] sections = fb.toSections();
        RegionCurves curves = aggregateSections(sections);
        ForecastRegion region = new ForecastRegion(fb.id(), fb.sourceBiomes(), sections, curves, fb);
        region.clearBiomeForecasts();
        return region;
    }

    private ForecastRegion generateFromBiomes(ForecastRegionId id) {
        List<BiomeInstanceKey> biomes = regionIndex.biomesFor(id);
        ForecastRegion.Section[] sections = sliceIntoEight(biomes);
        RegionCurves curves = aggregateSections(sections);
        BiomeFallbackSnapshot fb = persistence.saveFallback(id, sections, biomes);
        ForecastRegion region = new ForecastRegion(id, biomes, sections, curves, fb);
        region.clearBiomeForecasts();
        return region;
    }

    public void tick(long gameTime) {
        // advance region curves, manage diffusion, clouds, wind per region
    }

    private RegionCurves aggregateSections(ForecastRegion.Section[] sections) {
        WeightedCurve temp = WeightedCurve.empty();
        WeightedCurve humidity = WeightedCurve.empty();
        WeightedCurve pressure = WeightedCurve.empty();
        WeightedCurve wind = WeightedCurve.empty();
        WeightedCurve storm = WeightedCurve.empty();
        for (ForecastRegion.Section s : sections) {
            if (s.snapshot() == null) {
                continue;
            }
            temp.add(s.factor(), s.snapshot().temperatureCurve());
            humidity.add(s.factor(), s.snapshot().humidityCurve());
            pressure.add(s.factor(), s.snapshot().pressureCurve());
            wind.add(s.factor(), s.snapshot().windCurve());
            storm.add(s.factor(), s.snapshot().stormCurve());
        }
        return new DefaultRegionCurves(
            temp.normalize(),
            humidity.normalize(),
            pressure.normalize(),
            wind.normalize(),
            storm.normalize());
    }

    private ForecastRegion.Section[] sliceIntoEight(List<BiomeInstanceKey> biomes) {
        ForecastRegion.Section[] out = new ForecastRegion.Section[8];
        for (int i = 0; i < 8; i++) {
            BiomeForecastSnapshot snap = biomeGenerator.generateSlice(biomes, i);
            float factor = biomeGenerator.factorForSlice(biomes, i);
            out[i] = new ForecastRegion.Section(factor, snap);
        }
        return out;
    }
}
```

Supporting helpers (interfaces can live alongside the above):
- `RegionIndex`: chunk→region mapping, biome list per region, adjacency graph (for diffusion).
- `RegionPersistence`: loads/saves per-region fallback JSON and region saves.
- `BiomeForecastGenerator`: produces per-slice `BiomeForecastSnapshot` plus slice factors.
- `RegionCurves`: exposes per-variable curve accessors (`temperature()`, `humidity()`, `pressure()`, `wind()`, `storm()`).
- `WeightedCurve`: helper to accumulate weighted curves and normalize.
- `BiomeFallbackSnapshot`: serializable container for the eight section snapshots and region id; can reconstruct `Section[]`.

## Eight-section aggregation and fallback
- On load/reset/recovery:
  1. Collect biomes for the region (`RegionIndex.biomesFor`).
  2. Generate eight biome slices (`BiomeForecastGenerator.generateSlice`) with factors (area share or weighted by variance).
  3. Aggregate with weighted sums into region curves (temperature/humidity/pressure/wind/storm).
  4. Persist JSON fallback containing region id, source biomes, the eight section snapshots, and resulting aggregates.
  5. Call `clearBiomeForecasts()` so runtime holds only region curves.
- Interpolation: sampling methods may use `inRegionPos` to bias between sections (e.g., quadrant-based weights) but the public API stays region-focused.

## Migration guide by module (with before/after)

Temperature (weekly/daily, spikes, variation, storm prob):
```java
// Before
public double getTemperature(BiomeInstanceKey key, BlockPos pos, long time) {
    return tempCurves.get(key).sample(pos, time);
}

// After
public double getTemperature(ForecastRegionId regionId, Vec3 posInRegion, long time) {
    return regionRepo.get(regionId).sampleTemperature(posInRegion, time).value();
}

// Temporary adapter
@Deprecated
public double getTemperature(BiomeInstanceKey key, BlockPos pos, long time) {
    ForecastRegionId regionId = orchestrator.resolve(pos, level.dimension()).id();
    return getTemperature(regionId, toRegionLocal(pos), time);
}
```
- SpikeManager/VariationGenerator keyed by `ForecastRegionId`. Replace `Map<BiomeInstanceKey, SpikeState>` with `Map<ForecastRegionId, SpikeState>`.
- Storm probability curves stored per region; SimpleClouds/cyclones read region storm data.

Humidity:
- Sampling: `humidityRepo.get(regionId).sample(posInRegion, time);`
- Dew point: `dewPoint = DewPoint.compute(tempRegionSample, humidityRegionSample);`
- Remove biome parameters from all public/internal methods.

Pressure (plus chunk perturbations and diffusion):
```java
public PressureSample samplePressure(ForecastRegionId regionId, long time) {
    PressureSample base = regions.get(regionId).samplePressure(time);
    return chunkPerturbations.apply(regionId, base);
}
// Diffusion uses precomputed adjacency of region ids
diffusePressure(regionId, neighbors, diffusionRate);
```

Clouds and SimpleClouds:
```java
// Before
CloudState state = cloudTracker.getOrCreate(biomeKey);
// After
CloudState state = cloudTracker.getOrCreate(regionId);
```
- Cloud state save/load keyed by `ForecastRegionId`; cyclone/tornado rendering uses region storm/wind samples.

Wind:
- Wind vectors per region: `WindSample wind = windRepo.get(regionId).sample(time);`
- Wind particles and gust spikes keyed by region id.

Snow:
- `snowRegistry.update(regionId, tempSample, humiditySample, precipRate);`
- Accumulation/melt decisions use region values only.

Instruments:
```java
public void sendThermometer(Player player) {
    ForecastRegionId rid = orchestrator.resolve(player.blockPosition(), player.level().dimension()).id();
    double temp = temperatureService.getTemperature(rid, toRegionLocal(player.blockPosition()), player.level().getGameTime());
    network.send(new ThermometerPacket(rid, temp), player);
}
```
- Barometer/Humidimeter follow the same pattern; packets carry `ForecastRegionId`.

Forecast orchestrator:
- Legacy `ForecastOrchestrator` rewired/renamed to `RegionForecastOrchestrator`; exposes only region id or world-position-based APIs. Biome-facing methods are adapters marked `@Deprecated`.

## Networking and persistence updates
- Packets: replace biome ids with `ForecastRegionId` (rx, rz, dimension). Include converters for old payloads during migration; orchestrator translates legacy biome ids to regions on receipt.
- Saves: `RegionPersistence` writes per-region fallback (eight sections + aggregates) and runtime region saves. On load, try region save → fallback JSON → regeneration from biomes.
- Cached references: audit singletons and managers for `Map<BiomeInstanceKey, ?>` or fields named `biomeKey` and replace with region ids.

## Migration checklist (BiomeInstanceKey → ForecastRegionId)
- Storage: `Map<BiomeInstanceKey, Curve>` → `Map<ForecastRegionId, RegionCurves>`.
- Sampling: `getTemp(BiomeInstanceKey, Pos)` → `getTemp(ForecastRegionId, Vec3)`.
- Clouds: `cloudTracker.get(biomeKey)` → `cloudTracker.get(regionId)`.
- Instruments: `thermometer.read(biomeKey)` → `thermometer.read(regionId)`.
- Networking: payloads include `ForecastRegionId`; biome ids appear only in fallback sync.
- Generation-only: `BiomeForecastGenerator.generate(biomeKey)` is only called by the orchestrator.

## SynLightController and dead code
- SynLightController is likely unused because lighting/sky updates moved to region-based cloud/wind/sky systems while call sites still expect biome keys.
- If unique behavior (weather-driven light attenuation) is needed, reintroduce as `RegionLightController` invoked per region by the orchestrator, using region storm/cloud cover to compute light offsets.
- Otherwise deprecate/remove after confirming no unique effects remain.
- Dead/merge candidates: biome-specific caches/managers (temp/humidity/pressure), biome cloud trackers, legacy biome network packets, SynLightController. Role rule: orchestrator = lifecycle; region = data + sampling; systems = consumers; generators = biome-only inside generation/fallback.

## Final consistency check
- Runtime APIs accept `ForecastRegionId` and resolve via `RegionForecastOrchestrator.resolve(worldPos, dimension)`.
- `BiomeInstanceKey` appears only in generation (`generateFromBiomes`), fallback persistence (`saveFallback`/`loadFallback`), and reconstruction of section snapshots.
- JSON fallback stores eight biome-derived section snapshots; snapshots are cleared in memory after aggregation.
- Removing `BiomeInstanceKey` from runtime modules while retaining it in generation/fallback leaves gameplay functional because all sampling and state are region-based.

## Save migration hooks
- On load, orchestrator attempts region save → fallback JSON → regeneration from biomes; when recovering from old biome-only saves, map each biome to its region (`RegionIndex`), generate eight sections, aggregate, and persist the new region fallback before discarding biome snapshots.

## Notes for implementation ordering
- Start by dropping in the scaffolding types (even unused) and the deprecated biome adapters to prevent call-site churn.
- Migrate temperature/humidity/pressure first (core sampling), then wind/snow, then clouds/SimpleClouds and instruments; finish with networking payload swaps.
- Remove deprecated biome APIs only after all callers switch to region ids and save migration is validated.
