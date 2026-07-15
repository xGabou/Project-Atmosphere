package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

/** Standalone deterministic checks for the CPU stability/composite analyzer. */
public final class VolumetricStabilityDiagnosticsSandbox {
    private VolumetricStabilityDiagnosticsSandbox() {
    }

    public static void main(String[] args) {
        VolumetricStabilityDiagnostics.selfCheck();
        System.out.println("Volumetric stability diagnostics self-check passed.");
    }
}
