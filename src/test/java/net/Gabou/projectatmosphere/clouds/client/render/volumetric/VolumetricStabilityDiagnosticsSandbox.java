package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.clouds.client.render.CloudTextureUnitContract;

/** Standalone deterministic checks for the CPU stability/composite analyzer. */
public final class VolumetricStabilityDiagnosticsSandbox {
    private VolumetricStabilityDiagnosticsSandbox() {
    }

    public static void main(String[] args) {
        CloudTextureUnitContract.selfCheck();
        VolumetricCloudDebugConfig.selfCheck();
        VolumetricStabilityDiagnostics.selfCheck();
        PuffLobeSpatialIndex.selfCheck();
        System.out.println("Volumetric stability diagnostics self-check passed.");
    }
}
