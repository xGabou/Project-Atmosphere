package net.Gabou.projectatmosphere.client.loading;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.Util;

import java.util.concurrent.atomic.AtomicReference;

public final class ForecastLoadingState {
    private static final AtomicReference<Snapshot> STATE = new AtomicReference<>(Snapshot.idle());

    private ForecastLoadingState() {
    }

    public static Snapshot snapshot() {
        return STATE.get();
    }

    public static void start(ForecastLoadingStage stage, String message, String subtext, Float progress, String source) {
        setSnapshot(new Snapshot(
                true,
                stage,
                normalizeMessage(stage, message),
                clamp(progress),
                normalizeText(subtext),
                Util.getMillis(),
                normalizeSource(source)
        ));
    }

    public static void update(ForecastLoadingStage stage, String message, String subtext, Float progress, String source) {
        setSnapshot(new Snapshot(
                true,
                stage,
                normalizeMessage(stage, message),
                clamp(progress),
                normalizeText(subtext),
                Util.getMillis(),
                normalizeSource(source)
        ));
    }

    public static void markReady(String source) {
        setSnapshot(new Snapshot(
                false,
                ForecastLoadingStage.READY,
                ForecastLoadingStage.READY.defaultMessage(),
                1.0F,
                null,
                Util.getMillis(),
                normalizeSource(source)
        ));
    }

    public static void reset(String source) {
        setSnapshot(new Snapshot(
                false,
                ForecastLoadingStage.WAITING_FOR_SERVER,
                ForecastLoadingStage.WAITING_FOR_SERVER.defaultMessage(),
                null,
                null,
                Util.getMillis(),
                normalizeSource(source)
        ));
    }

    private static void setSnapshot(Snapshot next) {
        Snapshot previous = STATE.getAndSet(next);
        if (ProjectAtmosphere.DEBUG_MODE && !previous.equals(next)) {
            ProjectAtmosphere.LOGGER.info(
                    "[Atmosphere] Loading state -> active={}, stage={}, progress={}, source={}",
                    next.active(),
                    next.stage(),
                    next.progress(),
                    next.lastUpdateSource()
            );
        }
    }

    private static Float clamp(Float progress) {
        if (progress == null) {
            return null;
        }
        return Math.max(0.0F, Math.min(1.0F, progress));
    }

    private static String normalizeMessage(ForecastLoadingStage stage, String message) {
        if (message == null || message.isBlank()) {
            return stage.defaultMessage();
        }
        return message;
    }

    private static String normalizeText(String text) {
        return text == null || text.isBlank() ? null : text;
    }

    private static String normalizeSource(String source) {
        return source == null || source.isBlank() ? "unknown" : source;
    }

    public record Snapshot(
            boolean active,
            ForecastLoadingStage stage,
            String message,
            Float progress,
            String subtext,
            long lastUpdateTimeMs,
            String lastUpdateSource
    ) {
        private static Snapshot idle() {
            return new Snapshot(
                    false,
                    ForecastLoadingStage.WAITING_FOR_SERVER,
                    ForecastLoadingStage.WAITING_FOR_SERVER.defaultMessage(),
                    null,
                    null,
                    0L,
                    "init"
            );
        }

        public boolean hasDeterminateProgress() {
            return progress != null;
        }

        public float visualProgress() {
            return progress != null ? progress : stageBaseline(stage);
        }
    }

    private static float stageBaseline(ForecastLoadingStage stage) {
        return switch (stage) {
            case WAITING_FOR_SERVER -> 0.08F;
            case DESIGNING_FORECAST_REGIONS -> 0.22F;
            case RECEIVING_FORECAST_DATA -> 0.32F;
            case BUILDING_LOCAL_FORECAST_CACHE -> 0.68F;
            case PREPARING_WEATHER_SYSTEMS -> 0.9F;
            case READY -> 1.0F;
        };
    }
}
