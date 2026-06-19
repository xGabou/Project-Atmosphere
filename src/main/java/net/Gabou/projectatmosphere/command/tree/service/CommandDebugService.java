package net.Gabou.projectatmosphere.command.tree.service;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandSupport;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericSupportEvaluator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericUpdateScheduler;
import net.Gabou.projectatmosphere.modules.atmosphere.CloudWaterExchange;
import net.Gabou.projectatmosphere.modules.atmosphere.CloudWaterService;
import net.Gabou.projectatmosphere.modules.atmosphere.CycloneManager;
import net.Gabou.projectatmosphere.modules.atmosphere.CycloneSnapshot;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.atmosphere.SeasonalAtmosphericDrift;
import net.Gabou.projectatmosphere.modules.atmosphere.WeakLowManager;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.ocean.OceanBasinManager;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.modules.weather.RegionalWeatherPhase;
import net.Gabou.projectatmosphere.modules.weather.ServerWeatherStateResolver;
import net.Gabou.projectatmosphere.modules.weathercell.WeatherCellManager;
import net.Gabou.projectatmosphere.telemetry.verification.VerificationCollector;
import net.Gabou.projectatmosphere.telemetry.verification.VerificationFormatter;
import net.Gabou.projectatmosphere.telemetry.verification.VerificationReport;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.Gabou.projectatmosphere.util.StorageUtils;
import net.Gabou.projectatmosphere.util.UnitFormatter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CommandDebugService {
    private static final int VERIFICATION_PAGE_CHAR_LIMIT = 220;

    private CommandDebugService() {
    }

    public static int setDebugMode(CommandSourceStack source, boolean enabled) {
        ProjectAtmosphere.DEBUG_MODE = enabled;
        PaCommandMessages.success(
                source,
                true,
                "Debug mode updated",
                "Result: " + (enabled ? "on" : "off")
        );
        return 1;
    }

    public static int runVerification(CommandSourceStack source, boolean snapshot) {
        ServerLevel level = source.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            PaCommandMessages.failure(source, "Verification", "Verification is only available in the Overworld.");
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        VerificationReport report = VerificationCollector.collect(level, pos);
        String output = snapshot
                ? VerificationFormatter.formatSnapshot(report)
                : VerificationFormatter.formatFull(report);

        try {
            Path reportPath = writeReportFile(level, output, snapshot, pos);
            MutableComponent message = Component.literal("Verification report saved to " + reportPath)
                    .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, reportPath.toString())));
            source.sendSuccess(() -> message, false);
            source.sendSuccess(() -> Component.literal("Use /pa debug verify page <n> for chat pages."), false);
        } catch (IOException e) {
            ProjectAtmosphere.LOGGER.error("Failed to write verification report", e);
            PaCommandMessages.failure(source, "Verification", "Failed to write verification report to disk.", e.getMessage());
        }
        return 1;
    }

    public static int runVerificationPage(CommandSourceStack source, boolean snapshot, int page) {
        ServerLevel level = source.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            PaCommandMessages.failure(source, "Verification", "Verification is only available in the Overworld.");
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        VerificationReport report = VerificationCollector.collect(level, pos);
        String output = snapshot
                ? VerificationFormatter.formatSnapshot(report)
                : VerificationFormatter.formatFull(report);
        List<String> pages = paginate(output);
        if (pages.isEmpty()) {
            PaCommandMessages.failure(source, "Verification", "Verification report was empty.");
            return 0;
        }

        if (page > 0) {
            if (page > pages.size()) {
                PaCommandMessages.failure(
                        source,
                        "Verification",
                        "Requested page " + page + " is out of range.",
                        "Available pages: 1-" + pages.size()
                );
                return 0;
            }
            sendPage(source, pages.get(page - 1), page, pages.size());
            return 1;
        }

        for (int i = 0; i < pages.size(); i++) {
            sendPage(source, pages.get(i), i + 1, pages.size());
        }
        return 1;
    }

    public static int runCycloneCurrent(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!PaCommandSupport.requireOverworld(source, level, "Cyclone debug is only available in the Overworld.")) {
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        RegionAtmosphereState state = liveStateAt(pos);
        if (state == null) {
            PaCommandMessages.failure(source, "Cyclone Debug", "No live atmosphere state is available near this position.");
            return 0;
        }

        long gameTime = level.getGameTime();
        long dayTime = level.getDayTime();
        ForecastRegion region = ForecastOrchestrator.getRegionForecast(level, pos);
        AtmosphericSupportEvaluator.Support support = AtmosphericSupportEvaluator.evaluate(state.getRegionId(), state);
        CycloneManager.CycloneSupport cycloneSupport = CycloneManager.evaluateCycloneSupport(state, gameTime);
        CycloneManager.SeedSpawnCheck spawnCheck = CycloneManager.evaluateSeedSpawn(state, gameTime, dayTime);
        WeakLowManager.WeakLowSnapshot nearestLow = WeakLowManager.nearestSnapshot(pos);
        CycloneSnapshot nearest = CycloneManager.nearestCyclone(pos);
        double nearestDistance = CycloneManager.distanceTo(nearest, pos);
        boolean cycloneActive = nearest != null && nearestDistance <= nearest.radius();
        float cycloneInfluence = CycloneManager.estimatePressureDelta(state, gameTime);
        float forecastPressure = region == null ? 0f : region.samplePressure(gameTime);
        float pressureTarget = state.getTargetPressure(gameTime);
        float oceanFlux = OceanBasinManager.estimateHumidityFlux(state.getRegionId(), state.getHumidity());
        float oceanPressure = OceanBasinManager.estimatePressureDelta(state.getRegionId(), state.getPressure());
        ResourceLocation biome = state.getDominantBiome();

        PaCommandMessages.success(
                source,
                false,
                "Cyclone Debug: Current Region",
                "Region: " + state.getRegionId(),
                "Biome: " + (biome == null ? "unknown" : biome),
                "Live Pressure: " + UnitFormatter.formatPressure(state.getPressure()),
                "Forecast Pressure: " + UnitFormatter.formatPressure(forecastPressure),
                "Pressure Target: " + UnitFormatter.formatPressure(pressureTarget),
                "Pressure Anomaly To Normal: " + formatSigned(1013.25f - state.getPressure(), " hPa"),
                "Pressure Anomaly To Forecast: " + formatSigned(forecastPressure - state.getPressure(), " hPa"),
                "Cyclone Active: " + yesNo(cycloneActive),
                "Cyclone Id: " + (nearest == null ? "none" : nearest.id()),
                "Cyclone Distance: " + formatDistance(nearestDistance),
                "Cyclone Pressure Influence: " + formatSigned(cycloneInfluence, " hPa"),
                "Cyclone Seed Eligible: " + yesNo(cycloneSupport.seedEligible()),
                "Cyclone Seed Support: " + fmt(cycloneSupport.seedSupport()),
                "Cyclone Intensification Support: " + fmt(cycloneSupport.intensificationSupport()),
                "Cyclone Severe Support: " + fmt(cycloneSupport.severeSupport()),
                "Weak Low Support: " + fmt(cycloneSupport.weakLowSupport()),
                "Nearest Weak Low: " + (nearestLow == null ? "none" : nearestLow.id()),
                "Humidity: " + fmt(state.getHumidity()),
                "Cloud Water: " + fmt(state.getCloudWater()),
                "Rain Intensity: " + fmt(state.getRainIntensity()),
                "Storm Pressure Support: " + fmt(support.stormPressureSupport()),
                "Thunderstorm Support: " + fmt(support.thunderstormSupport()),
                "Supercell Support: " + fmt(support.supercellSupport()),
                "Convergence: " + fmt(support.windConvergence()),
                "Ocean Flux: " + fmt(oceanFlux),
                "Ocean Pressure Influence: " + formatSigned(oceanPressure, " hPa"),
                "Spawn Cooldown: " + CycloneManager.spawnCooldownRemainingTicks(dayTime) + " ticks",
                "Regional Cyclone Cap: " + CycloneManager.getActiveCycloneSnapshots().size() + " / " + CycloneManager.maxActiveCyclones(),
                "Nearest Cyclone Distance: " + formatDistance(nearestDistance),
                "Can Spawn Seed: " + yesNo(spawnCheck.canSpawn()),
                "Blocked Reason: " + spawnCheck.blockedReasonSummary()
        );
        return 1;
    }

    public static int runWeakLows(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!PaCommandSupport.requireOverworld(source, level, "Weak-low debug is only available in the Overworld.")) {
            return 0;
        }

        List<WeakLowManager.WeakLowSnapshot> lows = WeakLowManager.getActiveSnapshots();
        if (lows.isEmpty()) {
            WeakLowManager.WeakLowCandidate best = WeakLowManager.bestCandidate(level);
            PaCommandMessages.success(
                    source,
                    false,
                    "Weak Lows: Active",
                    "Active Weak Lows: 0",
                    "Best Candidate Region: " + (best == null ? "none" : best.regionKey()),
                    "Best Candidate Support: " + (best == null ? "n/a" : fmt(best.supportScore())),
                    "Blocked Reason: " + (best == null ? "no candidate regions" : best.blockedReason())
            );
            return 1;
        }

        List<String> lines = new ArrayList<>();
        lines.add("Active Weak Lows: " + lows.size());
        for (WeakLowManager.WeakLowSnapshot low : lows) {
            appendWeakLowSummary(lines, low);
        }
        PaCommandMessages.success(source, false, "Weak Lows: Active", lines.toArray(String[]::new));
        return 1;
    }

    public static int runWeakLowCandidates(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!PaCommandSupport.requireOverworld(source, level, "Weak-low debug is only available in the Overworld.")) {
            return 0;
        }

        WeakLowManager.WeakLowCandidateDiagnostics diagnostics = WeakLowManager.evaluateCandidateDiagnostics(level);
        List<WeakLowManager.WeakLowCandidate> candidates = diagnostics.candidates();
        if (candidates.isEmpty()) {
            PaCommandMessages.success(
                    source,
                    false,
                    "Weak Lows: Candidates",
                    "Scan Radius: " + diagnostics.scanRadiusRegions() + " forecast regions",
                    "Max Regions Per Tick: " + diagnostics.maxRegionsPerTick(),
                    "Checked Regions: " + diagnostics.checkedRegionCount(),
                    "Loaded Regions: " + diagnostics.loadedRegionCount(),
                    "Forecast-only Regions: " + diagnostics.forecastOnlyRegionCount(),
                    "Skipped Regions: " + diagnostics.skippedRegionCount(),
                    "Duplicate Regions Skipped: " + diagnostics.duplicateRegionSkippedCount(),
                    "Active Players Included: " + diagnostics.activePlayersIncluded(),
                    "No weak-low candidate regions.",
                    "Blocked Reasons: " + formatReasonCounts(diagnostics.blockedReasonCounts()),
                    "Last Weak Low Spawn Tick: " + formatTick(diagnostics.lastWeakLowSpawnTick()),
                    "Next Scan Tick: " + diagnostics.nextScanTick()
            );
            return 1;
        }

        List<String> lines = new ArrayList<>();
        WeakLowManager.WeakLowCandidate best = candidates.get(0);
        lines.add("Scan Radius: " + diagnostics.scanRadiusRegions() + " forecast regions");
        lines.add("Max Regions Per Tick: " + diagnostics.maxRegionsPerTick());
        lines.add("Checked Regions: " + diagnostics.checkedRegionCount());
        lines.add("Loaded Regions: " + diagnostics.loadedRegionCount());
        lines.add("Forecast-only Regions: " + diagnostics.forecastOnlyRegionCount());
        lines.add("Skipped Regions: " + diagnostics.skippedRegionCount());
        lines.add("Duplicate Regions Skipped: " + diagnostics.duplicateRegionSkippedCount());
        lines.add("Active Players Included: " + diagnostics.activePlayersIncluded());
        lines.add("Candidate Weak Lows: " + candidates.size());
        lines.add("Best Candidate Region: " + best.regionKey());
        lines.add("Best Candidate Support: " + fmt(best.supportScore()));
        lines.add("Best Candidate Blocked Reason: " + best.blockedReason());
        lines.add("Blocked Reasons: " + formatReasonCounts(diagnostics.blockedReasonCounts()));
        lines.add("Last Weak Low Spawn Tick: " + formatTick(diagnostics.lastWeakLowSpawnTick()));
        lines.add("Next Scan Tick: " + diagnostics.nextScanTick());
        for (WeakLowManager.WeakLowCandidate candidate : candidates.subList(0, Math.min(10, candidates.size()))) {
            appendWeakLowCandidate(lines, candidate);
        }
        PaCommandMessages.success(source, false, "Weak Lows: Candidates", lines.toArray(String[]::new));
        return 1;
    }

    public static int runWeatherCellCandidates(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!PaCommandSupport.requireOverworld(source, level, "WeatherCell debug is only available in the Overworld.")) {
            return 0;
        }

        WeatherCellManager.WeatherCellCandidateDiagnostics diagnostics = WeatherCellManager.evaluateCandidateDiagnostics(level);
        List<WeatherCellManager.WeatherCellCandidateDebug> candidates = diagnostics.candidates();
        List<String> lines = new ArrayList<>();
        lines.add("Checked Regions: " + diagnostics.checkedRegionCount());
        lines.add("Candidate WeatherCells: " + candidates.size());
        lines.add("Blocked Reasons: " + formatReasonCounts(diagnostics.blockedReasonCounts()));
        lines.add("Last Formation Attempt Tick: " + formatTick(diagnostics.lastFormationAttemptTick()));
        lines.add("Last WeatherCell Spawn Tick: " + formatTick(diagnostics.lastWeatherCellSpawnTick()));
        lines.add("Next Formation Tick: " + diagnostics.nextFormationTick());
        if (!candidates.isEmpty()) {
            WeatherCellManager.WeatherCellCandidateDebug best = candidates.get(0);
            lines.add("Best Candidate Region: " + best.regionKey());
            lines.add("Best Candidate Score: " + fmt(best.score()));
            lines.add("Best Candidate Blocked Reason: " + best.blockedReason());
            for (WeatherCellManager.WeatherCellCandidateDebug candidate : candidates.subList(0, Math.min(10, candidates.size()))) {
                appendWeatherCellCandidate(lines, candidate);
            }
        }
        PaCommandMessages.success(source, false, "WeatherCells: Candidates", lines.toArray(String[]::new));
        return 1;
    }

    public static int runStormBridge(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!PaCommandSupport.requireOverworld(source, level, "Storm bridge debug is only available in the Overworld.")) {
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        RegionAtmosphereState state = liveStateAt(pos);
        if (state == null) {
            PaCommandMessages.failure(source, "Storm Bridge", "No live atmosphere state is available near this position.");
            return 0;
        }

        long gameTime = level.getGameTime();
        ForecastRegion region = ForecastOrchestrator.getRegionForecast(level, pos);
        AtmosphericSupportEvaluator.Support support = AtmosphericSupportEvaluator.evaluate(state.getRegionId(), state);
        WeakLowManager.WeakLowCandidateDiagnostics lowDiagnostics = WeakLowManager.evaluateCandidateDiagnostics(level);
        WeatherCellManager.WeatherCellCandidateDiagnostics cellDiagnostics = WeatherCellManager.evaluateCandidateDiagnostics(level);
        List<CycloneManager.CycloneCandidateDebug> cycloneCandidates = CycloneManager.evaluateCycloneCandidates(level);
        WeakLowManager.WeakLowCandidate bestLow = lowDiagnostics.candidates().isEmpty() ? null : lowDiagnostics.candidates().get(0);
        WeatherCellManager.WeatherCellCandidateDebug bestCell = cellDiagnostics.candidates().isEmpty() ? null : cellDiagnostics.candidates().get(0);
        CycloneManager.CycloneCandidateDebug bestCyclone = cycloneCandidates.isEmpty() ? null : cycloneCandidates.get(0);
        RegionalWeatherPhase phase = ServerWeatherStateResolver.resolve(level, state.getRegionId(), gameTime);

        PaCommandMessages.success(
                source,
                false,
                "Storm Bridge",
                "Current Region: " + state.getRegionId(),
                "Weather Phase: " + phase,
                "Forecast StormWeek: " + stormWeekSummary(region, gameTime),
                "Live Storm Chance: " + fmt(ForecastOrchestrator.getCurrentStormChance(state.getRegionId(), gameTime)),
                "Pressure Anomaly: " + fmt(Math.max(1013.25F - state.getPressure(), state.getTargetPressure(gameTime) - state.getPressure())) + " hPa",
                "Humidity: " + fmt(state.getHumidity()),
                "Cloud Water: " + fmt(state.getCloudWater()),
                "Cloud Cover: " + fmt(state.getCloudCover()),
                "Rain Intensity: " + fmt(state.getRainIntensity()),
                "Convergence: " + fmt(support.windConvergence()),
                "Humidity Transport: " + fmt(support.humidityTransport()),
                "Weak Low Best: " + summarizeWeakLow(bestLow),
                "WeatherCell Best: " + summarizeWeatherCell(bestCell),
                "Cyclone Seed Best: " + summarizeCyclone(bestCyclone),
                "Weak Low Reasons: " + formatReasonCounts(lowDiagnostics.blockedReasonCounts()),
                "WeatherCell Reasons: " + formatReasonCounts(cellDiagnostics.blockedReasonCounts()),
                "Last Weak Low Spawn Tick: " + formatTick(lowDiagnostics.lastWeakLowSpawnTick()),
                "Last WeatherCell Spawn Tick: " + formatTick(cellDiagnostics.lastWeatherCellSpawnTick()),
                "Last Cyclone Seed Tick: " + formatTick(CycloneManager.lastCycloneSeedTick())
        );
        return 1;
    }

    public static int runCondensation(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!PaCommandSupport.requireOverworld(source, level, "Condensation debug is only available in the Overworld.")) {
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        RegionAtmosphereState state = liveStateAt(pos);
        if (state == null) {
            PaCommandMessages.failure(source, "Condensation", "No live atmosphere state is available near this position.");
            return 0;
        }

        long gameTime = level.getGameTime();
        float targetHumidity = state.getTargetHumidity(gameTime);
        float humiditySurplus = state.getHumidity() - Math.max(targetHumidity, 0.62F);
        CloudWaterExchange exchange = CloudWaterService.compute(
                state.getHumidity(),
                targetHumidity,
                state.getCloudWater(),
                state.getCloudCover(),
                state.getRainIntensity()
        );
        String blockedReason = humiditySurplus <= 0.0F
                ? "humidity not above target/saturation floor"
                : exchange.condensation() <= 0.0F ? "condensation potential rounded to zero" : "none";

        PaCommandMessages.success(
                source,
                false,
                "Condensation",
                "Region: " + state.getRegionId(),
                "Humidity: " + fmt(state.getHumidity()),
                "Target Humidity: " + fmt(targetHumidity),
                "Saturation Floor: " + fmt(Math.max(targetHumidity, 0.62F)),
                "Humidity Surplus: " + fmt(humiditySurplus),
                "Cloud Water: " + fmt(state.getCloudWater()),
                "Cloud Cover: " + fmt(state.getCloudCover()),
                "Rain Intensity: " + fmt(state.getRainIntensity()),
                "Condensation Potential: " + fmt(exchange.condensation()),
                "Re-Evaporation: " + fmt(exchange.reEvaporation()),
                "Precipitation Draw: " + fmt(exchange.precipitationDraw()),
                "Cloud Water Delta: " + fmt(exchange.cloudWaterDelta()),
                "Humidity Delta: " + fmt(exchange.humidityDelta()),
                "Blocked Reason: " + blockedReason
        );
        return 1;
    }

    public static int runCycloneCandidates(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!PaCommandSupport.requireOverworld(source, level, "Cyclone candidate debug is only available in the Overworld.")) {
            return 0;
        }

        CycloneManager.CycloneCandidateDiagnostics diagnostics = CycloneManager.evaluateCycloneCandidateDiagnostics(level);
        List<CycloneManager.CycloneCandidateDebug> candidates = diagnostics.candidates();
        if (candidates.isEmpty()) {
            PaCommandMessages.success(
                    source,
                    false,
                    "Cyclone Debug: Candidates",
                    "No cyclone candidate regions.",
                    "Scan Radius: " + diagnostics.scanRadiusRegions() + " forecast regions",
                    "Checked Regions: " + diagnostics.checkedRegions(),
                    "Loaded Regions: " + diagnostics.loadedRegions(),
                    "Forecast-only Regions: " + diagnostics.forecastOnlyRegions(),
                    "Skipped Regions: " + diagnostics.skippedRegions(),
                    "Duplicate Regions Skipped: " + diagnostics.duplicateRegionsSkipped(),
                    "Active Players Included: " + diagnostics.activePlayersIncluded(),
                    "Last Cyclone Seed Tick: " + formatTick(diagnostics.lastCycloneSeedTick()),
                    "Last Weak Low Tick: " + formatTick(diagnostics.lastWeakLowTick())
            );
            return 1;
        }

        List<String> lines = new ArrayList<>();
        CycloneManager.CycloneCandidateDebug best = candidates.get(0);
        lines.add("Scan Radius: " + diagnostics.scanRadiusRegions() + " forecast regions");
        lines.add("Max Regions Per Tick: " + diagnostics.maxRegionsPerTick());
        lines.add("Checked Regions: " + diagnostics.checkedRegions());
        lines.add("Loaded Regions: " + diagnostics.loadedRegions());
        lines.add("Forecast-only Regions: " + diagnostics.forecastOnlyRegions());
        lines.add("Skipped Regions: " + diagnostics.skippedRegions());
        lines.add("Duplicate Regions Skipped: " + diagnostics.duplicateRegionsSkipped());
        lines.add("Candidate Regions: " + diagnostics.checkedRegions());
        lines.add("Top Candidate Rows: " + candidates.size());
        lines.add("Active Players Included: " + diagnostics.activePlayersIncluded());
        lines.add("Best Candidate Globally: " + best.regionKey());
        lines.add("Best Candidate Support: " + fmt(best.seedSupport()));
        lines.add("Best Candidate Can Spawn: " + yesNo(best.canSpawn()));
        lines.add("Best Candidate Blocked Reason: " + best.blockedReason());
        lines.add("Most Common Blocked Reasons: " + formatReasonCounts(diagnostics.blockedReasonCounts()));
        lines.add("Last Cyclone Seed Tick: " + formatTick(diagnostics.lastCycloneSeedTick()));
        lines.add("Last Weak Low Tick: " + formatTick(diagnostics.lastWeakLowTick()));
        lines.add("Top 5 Candidates:");
        for (CycloneManager.CycloneCandidateDebug candidate : candidates.subList(0, Math.min(5, candidates.size()))) {
            appendCycloneCandidate(lines, candidate);
        }
        PaCommandMessages.success(source, false, "Cyclone Debug: Candidates", lines.toArray(String[]::new));
        return 1;
    }

    public static int runCycloneNearest(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!PaCommandSupport.requireOverworld(source, level, "Cyclone debug is only available in the Overworld.")) {
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        RegionAtmosphereState state = liveStateAt(pos);
        CycloneSnapshot nearest = CycloneManager.nearestCyclone(pos);
        if (nearest == null) {
            PaCommandMessages.success(source, false, "Cyclone Debug: Nearest", "Nearest Cyclone: none");
            return 1;
        }
        double distance = CycloneManager.distanceTo(nearest, pos);
        float influence = state == null ? 0f : CycloneManager.estimatePressureDelta(state, level.getGameTime());
        PaCommandMessages.success(
                source,
                false,
                "Cyclone Debug: Nearest",
                "Nearest Cyclone: " + nearest.id(),
                "Distance: " + formatDistance(distance),
                "Influence at player: " + fmt(influenceRadius(nearest, distance)),
                "Pressure influence: " + formatSigned(influence, " hPa"),
                "Stage: " + cycloneStage(nearest),
                "Intensity: " + fmt(nearest.intensity())
        );
        return 1;
    }

    public static int runCycloneList(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!PaCommandSupport.requireOverworld(source, level, "Cyclone debug is only available in the Overworld.")) {
            return 0;
        }

        List<CycloneSnapshot> cyclones = CycloneManager.getActiveCycloneSnapshots();
        if (cyclones.isEmpty()) {
            PaCommandMessages.success(source, false, "Cyclone Debug: List", "No active cyclone systems.");
            return 1;
        }

        List<String> lines = new ArrayList<>();
        lines.add("Total Active Cyclones: " + cyclones.size());
        cyclones.stream()
                .sorted(Comparator.comparing(CycloneSnapshot::id))
                .forEach(snapshot -> appendCycloneSummary(lines, level, snapshot));
        PaCommandMessages.success(source, false, "Cyclone Debug: List", lines.toArray(String[]::new));
        return 1;
    }

    public static int runPressureCurrent(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!PaCommandSupport.requireOverworld(source, level, "Pressure debug is only available in the Overworld.")) {
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        RegionAtmosphereState state = liveStateAt(pos);
        ForecastRegion region = ForecastOrchestrator.getRegionForecast(level, pos);
        if (state == null || region == null) {
            PaCommandMessages.failure(source, "Pressure Debug", "No live atmosphere or forecast region is available here.");
            return 0;
        }

        long gameTime = level.getGameTime();
        long dayTime = level.getDayTime();
        RegionAtmosphereState.PressureTargetDebug target = state.pressureTargetDebug(gameTime);
        boolean active = AtmosphericStateRegistry.getActiveStates().contains(state.getRegionId());
        AtmosphericUpdateScheduler.PressureDiagnostics pressure = AtmosphericUpdateScheduler.estimatePressureDiagnostics(state, gameTime, active);
        AtmosphericSupportEvaluator.Support support = AtmosphericSupportEvaluator.evaluate(state.getRegionId(), state);
        float forecastPressure = region.samplePressure(gameTime);
        float seasonPressure = SeasonalAtmosphericDrift.currentPressureOffsetHpa();
        float windPressure = WindVector.estimatePressureTransport(state.getRegionId());
        float oceanPressure = OceanBasinManager.estimatePressureDelta(state.getRegionId(), state.getPressure());
        float cyclonePressure = CycloneManager.estimatePressureDelta(state, gameTime);
        float[][] pressureWeek = region.getPressure();
        int pressureWeekLength = pressureWeek == null ? 0 : pressureWeek.length;
        String forecastDay = pressureWeekLength <= 0
                ? "unavailable"
                : String.valueOf((gameTime / 24000L) % pressureWeekLength);

        PaCommandMessages.success(
                source,
                false,
                "Pressure Debug: Current Region",
                "Region: " + state.getRegionId(),
                "Live Pressure: " + UnitFormatter.formatPressure(state.getPressure()),
                "Forecast Pressure: " + UnitFormatter.formatPressure(forecastPressure),
                "Forecast Pressure Current Sample: " + UnitFormatter.formatPressure(target.forecastPressureCurrentSample()),
                "Pressure Target: " + UnitFormatter.formatPressure(target.effectiveTargetPressure()),
                "Raw Pressure Target: " + UnitFormatter.formatPressure(target.rawTargetPressure()),
                "Live State Raw Pressure Target: " + UnitFormatter.formatPressure(target.rawTargetPressure()),
                "Effective Pressure Target: " + UnitFormatter.formatPressure(target.effectiveTargetPressure()),
                "Target Source: " + target.source(),
                "Target Day Index: " + target.targetDayIndex(),
                "Current Forecast Day Index: " + target.currentForecastDayIndex(),
                "Target Uses Current Forecast Day: " + yesNo(target.targetUsesCurrentForecastDay()),
                "Day-0 Target Profile Active: " + yesNo(target.day0TargetProfileActive()),
                "Stale Target Detected: " + yesNo(target.staleTargetDetected()),
                "Stale Target Correction Delta: " + formatSigned(target.staleTargetCorrectionDelta(), " hPa"),
                "Pressure Anomaly Classification: " + classifyPressureAnomaly(target, pressure, windPressure, oceanPressure, cyclonePressure, support),
                "Normal Pressure Reference: " + UnitFormatter.formatPressure(1013.25f),
                "Base Pressure: " + UnitFormatter.formatPressure(state.getBasePressure()),
                "Season Pressure Offset: " + formatSigned(seasonPressure, " hPa"),
                "Pressure Target Source: " + target.source(),
                "Target Curve Index: " + target.lowerIndex(),
                "Target Curve Previous Point: " + UnitFormatter.formatPressure(target.previousPoint()),
                "Target Curve Next Point: " + UnitFormatter.formatPressure(target.nextPoint()),
                "Target Curve Interpolation Factor: " + fmt(target.interpolation()),
                "Pressure Target Support Gated: no",
                "Scheduler Pressure Delta: " + formatSigned(pressure.schedulerPressureDelta(), " hPa"),
                "Recovery Pressure Delta: " + formatSigned(pressure.forecastRecoveryDelta(), " hPa"),
                "Pressure Guard Delta: " + formatSigned(pressure.pressureGuardDelta(), " hPa"),
                "Base Relax Pressure Delta: " + formatSigned(pressure.baseRelaxDelta(), " hPa"),
                "Rain Pressure Delta: " + formatSigned(pressure.rainPressureDelta(), " hPa"),
                "Wind Pressure Mix Delta: " + formatSigned(windPressure, " hPa"),
                "Ocean Pressure Influence: " + formatSigned(oceanPressure, " hPa"),
                "Cyclone Pressure Influence: " + formatSigned(cyclonePressure, " hPa"),
                "Unsupported Low Recovery Delta: " + formatSigned(pressure.unsupportedLowRecoveryDelta(), " hPa"),
                "Pressure Recovery Eligible: " + yesNo(pressure.supportResistance() < 0.65f && state.getPressure() < 1013.25f),
                "Unsupported Low Recovery Active: " + yesNo(pressure.unsupportedLowRecoveryActive()),
                "Support Resistance: " + fmt(pressure.supportResistance()),
                "Forecast/Target Note: Forecast Pressure samples weekly forecast day " + forecastDay
                        + "; Effective Pressure Target uses the current weekly forecast day plus the day-0 diurnal shape offset."
        );
        return 1;
    }

    private static RegionAtmosphereState liveStateAt(BlockPos pos) {
        RegionInstanceKey key = RegionInstanceKey.from(pos);
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
        return state == null ? AtmosphericStateRegistry.findNearest(pos.getX(), pos.getZ()) : state;
    }

    private static void appendCycloneSummary(List<String> lines, ServerLevel level, CycloneSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        BlockPos center = BlockPos.containing(snapshot.centerX(), 0, snapshot.centerZ());
        RegionAtmosphereState centerState = AtmosphericStateRegistry.findNearest(snapshot.centerX(), snapshot.centerZ());
        RegionInstanceKey centerRegion = centerState == null ? RegionInstanceKey.from(center) : centerState.getRegionId();
        CycloneManager.CycloneSupport support = CycloneManager.evaluateCycloneSupport(centerState, level.getGameTime());
        String humidity = centerState == null ? "n/a" : fmt(centerState.getHumidity());
        String cloudWater = centerState == null ? "n/a" : fmt(centerState.getCloudWater());
        lines.add("Cyclone: " + snapshot.id());
        lines.add("  Center Region: " + centerRegion);
        lines.add("  Center Position: x=" + fmt(snapshot.centerX()) + ", z=" + fmt(snapshot.centerZ()));
        lines.add("  Age: " + snapshot.ageTicks() + " ticks");
        lines.add("  Stage: " + cycloneStage(snapshot));
        lines.add("  Intensity: " + fmt(snapshot.intensity()));
        lines.add("  Pressure Drop: " + formatSigned(-snapshot.corePressureDrop(), " hPa core"));
        lines.add("  Radius: " + fmt(snapshot.radius()) + " blocks");
        lines.add("  Movement Vector: dx=" + fmt(snapshot.movementX()) + ", dz=" + fmt(snapshot.movementZ()));
        lines.add("  Humidity Support: " + humidity);
        lines.add("  Cloud Water Support: " + cloudWater);
        lines.add("  Seed Support: " + fmt(support.seedSupport()));
        lines.add("  Intensification Support: " + fmt(support.intensificationSupport()));
        lines.add("  Severe Support: " + fmt(support.severeSupport()));
        lines.add("  Weak Low Support: " + fmt(support.weakLowSupport()));
        lines.add("  Remaining Lifetime: " + snapshot.lifetimeTicks() + " ticks");
    }

    private static void appendWeakLowSummary(List<String> lines, WeakLowManager.WeakLowSnapshot low) {
        lines.add("Weak Low: " + low.id());
        lines.add("  Region: " + low.regionKey());
        lines.add("  Center: x=" + fmt((float) low.centerX()) + ", z=" + fmt((float) low.centerZ()));
        lines.add("  Support Score: " + fmt(low.supportScore()));
        lines.add("  Pressure Anomaly: " + fmt(low.pressureAnomaly()) + " hPa");
        lines.add("  Humidity: " + fmt(low.humidity()));
        lines.add("  Cloud Water: " + fmt(low.cloudWater()));
        lines.add("  Cloud Cover: " + fmt(low.cloudCover()));
        lines.add("  Convergence/Shear: " + fmt(low.convergence()) + " / " + fmt(low.shear()));
        lines.add("  Instability: " + fmt(low.instability()));
        lines.add("  Cumulonimbus Support: " + fmt(low.cumulonimbusSupport()));
        lines.add("  Nimbostratus/Rain Support: " + fmt(low.nimbostratusSupport()));
        lines.add("  WeatherCell Support: " + fmt(low.weatherCellSupport()));
        lines.add("  Age: " + low.ageTicks() + " ticks");
        lines.add("  Lifetime: " + low.lifetimeTicks() + " ticks");
        lines.add("  Intensity: " + fmt(low.intensity()));
        lines.add("  Decay Reason: " + low.decayReason());
        lines.add("  Promoted To Cyclone Seed: " + yesNo(low.promotedToCycloneSeed()));
    }

    private static void appendWeakLowCandidate(List<String> lines, WeakLowManager.WeakLowCandidate candidate) {
        lines.add("Candidate: " + candidate.regionKey());
        lines.add("  Position: " + formatPosition(candidate.position()));
        lines.add("  Eligible: " + yesNo(candidate.eligible()));
        lines.add("  Support Score: " + fmt(candidate.supportScore()));
        lines.add("  Pressure Anomaly: " + fmt(candidate.pressureAnomaly()) + " hPa");
        lines.add("  Humidity: " + fmt(candidate.humidity()));
        lines.add("  Cloud Water: " + fmt(candidate.cloudWater()));
        lines.add("  Cloud Cover: " + fmt(candidate.cloudCover()));
        lines.add("  Convergence/Shear: " + fmt(candidate.convergence()) + " / " + fmt(candidate.shear()));
        lines.add("  Instability: " + fmt(candidate.instability()));
        lines.add("  Cumulonimbus Support: " + fmt(candidate.cumulonimbusSupport()));
        lines.add("  Nimbostratus/Rain Support: " + fmt(candidate.nimbostratusSupport()));
        lines.add("  WeatherCell Support: " + fmt(candidate.weatherCellSupport()));
        lines.add("  Humidity Transport Support: " + fmt(candidate.humidityTransportSupport()));
        lines.add("  Ocean Support: " + fmt(candidate.oceanSupport()));
        lines.add("  Incipient Low Bridge: " + yesNo(candidate.incipientLowBridge()));
        lines.add("  Blocked Reason: " + candidate.blockedReason());
    }

    private static void appendWeatherCellCandidate(List<String> lines, WeatherCellManager.WeatherCellCandidateDebug candidate) {
        lines.add("Candidate: " + candidate.regionKey());
        lines.add("  Position: " + formatPosition(candidate.position()));
        lines.add("  Eligible: " + yesNo(candidate.eligible()));
        lines.add("  Score: " + fmt(candidate.score()));
        lines.add("  Formation Chance: " + fmt(candidate.formationChance()));
        lines.add("  Pressure Anomaly: " + fmt(candidate.pressureAnomaly()) + " hPa");
        lines.add("  Humidity: " + fmt(candidate.humidity()) + " / min " + fmt(candidate.minimumHumidity()));
        lines.add("  Cloud Water: " + fmt(candidate.cloudWater()) + " / min " + fmt(candidate.minimumCloudWater()));
        lines.add("  Cloud Cover: " + fmt(candidate.cloudCover()));
        lines.add("  Cell Coverage: " + fmt(candidate.coverage()));
        lines.add("  Convergence: " + fmt(candidate.convergence()));
        lines.add("  Humidity Transport: " + fmt(candidate.humidityTransport()));
        lines.add("  Weak Low Organization: " + fmt(candidate.weakLowOrganization()));
        lines.add("  Local Active Cells: " + candidate.localActiveCells());
        lines.add("  Blocked Reason: " + candidate.blockedReason());
    }

    private static void appendCycloneCandidate(List<String> lines, CycloneManager.CycloneCandidateDebug candidate) {
        lines.add("Candidate: " + candidate.regionKey());
        lines.add("  Position: " + formatPosition(candidate.position()));
        lines.add("  Can Spawn Seed: " + yesNo(candidate.canSpawn()));
        lines.add("  Seed Support: " + fmt(candidate.seedSupport()));
        lines.add("  Intensification Support: " + fmt(candidate.intensificationSupport()));
        lines.add("  Severe Support: " + fmt(candidate.severeSupport()));
        lines.add("  Pressure Anomaly: " + fmt(candidate.pressureAnomalyHpa()) + " hPa");
        lines.add("  Humidity: " + fmt(candidate.humidity()));
        lines.add("  Cloud Water: " + fmt(candidate.cloudWater()));
        lines.add("  Cloud Cover: " + fmt(candidate.cloudCover()));
        lines.add("  Convergence: " + fmt(candidate.convergenceSupport()));
        lines.add("  Ocean Moisture: " + fmt(candidate.oceanMoistureBonus()));
        lines.add("  Thunderstorm Support: " + fmt(candidate.thunderstormSupport()));
        lines.add("  Supercell Support: " + fmt(candidate.supercellSupport()));
        lines.add("  Weak Low Support: " + fmt(candidate.weakLowSupport()));
        lines.add("  Blocked Reason: " + candidate.blockedReason());
    }

    private static String cycloneStage(CycloneSnapshot snapshot) {
        if (snapshot == null) {
            return "none";
        }
        if (snapshot.intensity() >= 0.75f) {
            return "SEVERE";
        }
        if (snapshot.intensity() >= 0.45f) {
            return "ORGANIZED";
        }
        return "SEED";
    }

    private static float influenceRadius(CycloneSnapshot snapshot, double distance) {
        if (snapshot == null || snapshot.radius() <= 0f || !Double.isFinite(distance)) {
            return 0f;
        }
        return Mth.clamp((float) (1d - distance / snapshot.radius()), 0f, 1f);
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatSigned(float value, String suffix) {
        String sign = value >= 0f ? "+" : "";
        return sign + String.format(Locale.ROOT, "%.2f", value) + suffix;
    }

    private static String formatDistance(double distance) {
        if (!Double.isFinite(distance)) {
            return "none";
        }
        return String.format(Locale.ROOT, "%.0fm", distance);
    }

    private static String formatPosition(BlockPos pos) {
        if (pos == null) {
            return "none";
        }
        return "x=" + pos.getX() + ", z=" + pos.getZ();
    }

    private static String classifyPressureAnomaly(RegionAtmosphereState.PressureTargetDebug target,
                                                  AtmosphericUpdateScheduler.PressureDiagnostics pressure,
                                                  float windPressure,
                                                  float oceanPressure,
                                                  float cyclonePressure,
                                                  AtmosphericSupportEvaluator.Support support) {
        if (target.staleTargetDetected() && pressure.supportResistance() < 0.65f) {
            return "stale unsupported target";
        }
        if (cyclonePressure < -0.05f) {
            return "cyclone seed";
        }
        if (support != null && (support.rainSupport() >= 0.45f || support.thunderstormSupport() >= 0.35f || support.supercellSupport() >= 0.25f)) {
            return "rain/storm system";
        }
        if (windPressure < -0.05f) {
            return "wind-imported gradient";
        }
        if (oceanPressure < -0.05f) {
            return "ocean-influenced low";
        }
        if (target.effectiveTargetPressure() < 1008.0f) {
            return "active forecast anomaly";
        }
        return "current forecast";
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String formatTick(long tick) {
        return tick < 0L ? "never" : Long.toString(tick);
    }

    private static String formatReasonCounts(Map<String, Integer> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return "none";
        }
        return reasons.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(6)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
    }

    private static String summarizeWeakLow(WeakLowManager.WeakLowCandidate candidate) {
        if (candidate == null) {
            return "none";
        }
        return candidate.regionKey()
                + " support=" + fmt(candidate.supportScore())
                + " eligible=" + yesNo(candidate.eligible())
                + " bridge=" + yesNo(candidate.incipientLowBridge())
                + " blocked=" + candidate.blockedReason();
    }

    private static String summarizeWeatherCell(WeatherCellManager.WeatherCellCandidateDebug candidate) {
        if (candidate == null) {
            return "none";
        }
        return candidate.regionKey()
                + " score=" + fmt(candidate.score())
                + " eligible=" + yesNo(candidate.eligible())
                + " blocked=" + candidate.blockedReason();
    }

    private static String summarizeCyclone(CycloneManager.CycloneCandidateDebug candidate) {
        if (candidate == null) {
            return "none";
        }
        return candidate.regionKey()
                + " seed=" + fmt(candidate.seedSupport())
                + " canSpawn=" + yesNo(candidate.canSpawn())
                + " blocked=" + candidate.blockedReason();
    }

    private static String stormWeekSummary(ForecastRegion region, long gameTime) {
        if (region == null || region.curves() == null || region.curves().stormWeek() == null || region.curves().stormWeek().length == 0) {
            return "empty";
        }
        float[] stormWeek = region.curves().stormWeek();
        int day = (int) Math.floorMod(gameTime / 24000L, stormWeek.length);
        return "length=" + stormWeek.length + ", day=" + day + ", value=" + fmt(stormWeek[day]);
    }

    private static void sendPage(CommandSourceStack source, String pageText, int page, int totalPages) {
        String message = PaCommandMessages.PREFIX
                + "\nAction: Verification Report [" + page + "/" + totalPages + "]"
                + "\n"
                + pageText;
        source.sendSuccess(() -> Component.literal(message), false);
    }

    private static List<String> paginate(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }

        String[] lines = output.split("\\R", -1);
        List<String> pages = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            String candidate = current.length() == 0 ? line : current + "\n" + line;
            if (current.length() > 0 && candidate.length() > VERIFICATION_PAGE_CHAR_LIMIT) {
                pages.add(current.toString());
                current.setLength(0);
                current.append(line);
            } else {
                if (current.length() > 0) {
                    current.append('\n');
                }
                current.append(line);
            }
        }

        if (current.length() > 0) {
            pages.add(current.toString());
        }

        return pages;
    }

    private static Path writeReportFile(ServerLevel level, String output, boolean snapshot, BlockPos pos) throws IOException {
        String filename = String.format(
                java.util.Locale.ROOT,
                "verification_%s_%d_%d_%d.md",
                snapshot ? "snapshot" : "full",
                level.getGameTime(),
                pos.getX(),
                pos.getZ()
        );
        Path target = StorageUtils.getPerWorldSavePath(level, "debug/verification/" + filename);
        Files.createDirectories(target.getParent());
        String markdown = "# Project Atmosphere Verification\n\n"
                + "Generated at game time " + level.getGameTime() + "\n\n"
                + "```text\n"
                + output + "\n"
                + "```\n";
        Files.writeString(target, markdown, StandardCharsets.UTF_8);
        return target;
    }
}
