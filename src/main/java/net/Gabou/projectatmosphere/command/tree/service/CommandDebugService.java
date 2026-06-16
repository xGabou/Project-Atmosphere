package net.Gabou.projectatmosphere.command.tree.service;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandSupport;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericSupportEvaluator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericUpdateScheduler;
import net.Gabou.projectatmosphere.modules.atmosphere.CycloneManager;
import net.Gabou.projectatmosphere.modules.atmosphere.CycloneSnapshot;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.atmosphere.SeasonalAtmosphericDrift;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.ocean.OceanBasinManager;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
        lines.add("  Remaining Lifetime: " + snapshot.lifetimeTicks() + " ticks");
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
