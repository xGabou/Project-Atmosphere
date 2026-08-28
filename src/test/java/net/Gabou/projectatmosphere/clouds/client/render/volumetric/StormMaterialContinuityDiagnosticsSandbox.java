package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** T128 deterministic centre-line material trace for the live 3c039aa7 composition. */
public final class StormMaterialContinuityDiagnosticsSandbox {
    private StormMaterialContinuityDiagnosticsSandbox() {
    }

    public static void main(String[] args) {
        StormFieldSampler sampler = StormFieldSampler.of(
                StormFieldSampler.Composition.CORRECTED_PHASE_4S,
                CloudNoiseFieldModel.bakeBase(),
                CloudNoiseFieldModel.bakeDetail()
        );
        StormMaterialContinuityDiagnostics.Trace trace = StormMaterialContinuityDiagnostics.trace(
                sampler, fixture(), 0.0D, 0.0D, 208.0D, 528.0D, 16.0D, null
        );
        require(trace.samples().size() == 21, "unexpected trace sample count");
        require(trace.interval() <= StormMaterialContinuityDiagnostics.MAX_INTERVAL_BLOCKS,
                "trace interval exceeds 16 blocks");

        System.out.println("T128_TRACE_HEADER|Y|roles|coverage|strength|baseNoise|detailFbm|"
                + "body|erosion|density|coreFill|height01|extinction|opticalDepth|direct|ambient|"
                + "phaseShadow|final|branch");
        for (StormMaterialContinuityDiagnostics.Sample sample : trace.samples()) {
            System.out.println(String.format(Locale.ROOT,
                    "T128_TRACE|%.0f|%s|%.5f|%.5f|%.5f|%.5f|%.5f|%.5f|%.5f|%.5f|%.5f|%s",
                    sample.y(), sample.activeRoles(), sample.coverage(), sample.envelopeStrength(),
                    sample.baseNoise(), sample.detailErosionInput(), sample.bodyBeforeErosion(),
                    sample.detailErosion(), sample.finalDensity(), sample.coreFill(), sample.height01(),
                    sample.branchFlags()));
        }
        StormMaterialContinuityDiagnostics.Sample largest = trace.largestDensityStep();
        require(largest != null, "trace has no adjacent density step");
        System.out.println(String.format(Locale.ROOT,
                "T128_LARGEST_DENSITY_STEP|Y=%.0f|density=%.5f|roles=%s",
                largest.y(), largest.finalDensity(), largest.activeRoles()));
    }

    static List<StormLobeDescriptor> fixture() {
        UUID group = UUID.fromString("3c039aa7-0000-0000-0000-000000000000");
        return List.of(
                descriptor(group, 0, StormLobeDescriptor.Role.BASE, -28, -6, 224, 300, 172, 138, .7780F),
                descriptor(group, 1, StormLobeDescriptor.Role.BASE, 26, 10, 226, 296, 158, 126, .8746F),
                descriptor(group, 2, StormLobeDescriptor.Role.CORE, -12, 2, 250, 368, 98, 82, .9504F),
                descriptor(group, 3, StormLobeDescriptor.Role.CORE, 14, -8, 256, 374, 92, 76, .9691F),
                descriptor(group, 4, StormLobeDescriptor.Role.TOWER, -6, 0, 300, 448, 58, 48, .9138F),
                descriptor(group, 5, StormLobeDescriptor.Role.TOWER, 16, -6, 308, 456, 54, 44, .8904F),
                descriptor(group, 6, StormLobeDescriptor.Role.ANVIL, 10, 0, 396, 504, 206, 82, .8222F),
                descriptor(group, 7, StormLobeDescriptor.Role.ANVIL, 44, -8, 404, 508, 184, 74, .7661F),
                descriptor(group, 8, StormLobeDescriptor.Role.ANVIL, -30, 6, 400, 500, 176, 70, .8137F),
                descriptor(group, 9, StormLobeDescriptor.Role.ANVIL, -58, -12, 390, 498, 168, 68, .7950F)
        );
    }

    private static StormLobeDescriptor descriptor(
            UUID group, int index, StormLobeDescriptor.Role role,
            double x, double z, float baseY, float topY, float major, float minor, float density
    ) {
        return new StormLobeDescriptor(
                UUID.nameUUIDFromBytes(("t128-" + index).getBytes()), group, index, 10, 0, role,
                x, z, baseY, topY, major, minor, .3420F, .9397F,
                role == StormLobeDescriptor.Role.TOWER ? 26.0F : 8.0F,
                role == StormLobeDescriptor.Role.TOWER ? 6.0F : 2.0F,
                density, .14F, .37F, .62F, .78F, 1.0F
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}

