package net.Gabou.projectatmosphere.telemetry;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Optional-backend bridge for archive data that is not part of native CloudCells. */
public final class SevereWeatherArchiveBridge {
    private static final Supplier<List<ServerStateArchiveWriter.TornadoExport>> NO_TORNADOES = List::of;
    private static final Supplier<List<ServerStateArchiveWriter.HurricaneExport>> NO_HURRICANES = List::of;

    private static volatile Supplier<List<ServerStateArchiveWriter.TornadoExport>> tornadoes = NO_TORNADOES;
    private static volatile Supplier<List<ServerStateArchiveWriter.HurricaneExport>> hurricanes = NO_HURRICANES;

    private SevereWeatherArchiveBridge() {
    }

    public static void install(
            Supplier<List<ServerStateArchiveWriter.TornadoExport>> tornadoSupplier,
            Supplier<List<ServerStateArchiveWriter.HurricaneExport>> hurricaneSupplier
    ) {
        tornadoes = Objects.requireNonNull(tornadoSupplier, "tornadoSupplier");
        hurricanes = Objects.requireNonNull(hurricaneSupplier, "hurricaneSupplier");
    }

    public static List<ServerStateArchiveWriter.TornadoExport> captureTornadoes() {
        return List.copyOf(tornadoes.get());
    }

    public static List<ServerStateArchiveWriter.HurricaneExport> captureHurricanes() {
        return List.copyOf(hurricanes.get());
    }

    public static void reset() {
        tornadoes = NO_TORNADOES;
        hurricanes = NO_HURRICANES;
    }
}
