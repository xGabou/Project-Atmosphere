package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * T132 whole-frame cloud content.
 *
 * <p>The suite's fixture freezes one storm group's structural identity. Every
 * other cloud in the frame is untracked: other published storm groups keep
 * evolving, and the PUFF/cumulus family advects and grows on its own tick while
 * rendering through the same shader into the same buffer. A reference image can
 * therefore differ with every tracked uniform, clock and descriptor matching,
 * simply because a different cloud was in shot.
 *
 * <p>This is a separate signature from the fixture's
 * {@code StructuralFingerprint}, which it does not replace: the fingerprint
 * answers "is this the same storm", this answers "is this the same frame of
 * clouds".
 *
 * <p>Observation only - it reads already-published renderer state and changes
 * no descriptor, candidate, upload or generation behaviour.
 */
record StormCloudContent(
        int puffLobeCount,
        long puffDescriptorSignature,
        long puffCandidateSignature,
        int stormLobeCount,
        int stormDescriptorCount,
        int stormGroupCount,
        int fixtureDescriptorCount,
        long stormContentSignature
) {
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /** Descriptors published this frame that do not belong to the frozen fixture. */
    int foreignStormDescriptorCount() {
        return stormDescriptorCount - fixtureDescriptorCount;
    }

    /**
     * Reads the authoritative published state. The storm side comes from the
     * coordinator's own snapshot and lobe count; the puff side from the puff
     * index's existing diagnostic descriptor signature and lobe count. Nothing
     * is reconstructed from world state.
     */
    static StormCloudContent capture(UUID fixtureGroupId) {
        StormRenderSnapshot snapshot = StormGeometryBuildCoordinator.snapshot();
        return of(fixtureGroupId, snapshot,
                StormGeometryBuildCoordinator.lobeCount(),
                PuffLobeSpatialIndex.lobeCount(),
                PuffLobeSpatialIndex.descriptorSignatureForDiagnostics(),
                PuffLobeSpatialIndex.candidateSignatureForDiagnostics());
    }

    /** Pure form, so the sandbox can build an exact content state headlessly. */
    static StormCloudContent of(
            UUID fixtureGroupId, StormRenderSnapshot snapshot, int stormLobeCount,
            int puffLobeCount, long puffDescriptorSignature, long puffCandidateSignature) {
        long hash = FNV_OFFSET;
        int descriptorCount = 0;
        int fixtureCount = 0;
        Set<UUID> groups = new HashSet<>();
        if (snapshot != null) {
            for (StormLobeDescriptor descriptor : snapshot.descriptorsUnsafe()) {
                descriptorCount++;
                groups.add(descriptor.groupId());
                if (fixtureGroupId != null && fixtureGroupId.equals(descriptor.groupId())) {
                    fixtureCount++;
                }
                // Published order participates, so a reordering is a difference
                // even when the same descriptors are present.
                hash = mix(hash, descriptorCount);
                hash = mix(hash, descriptor.fieldId().getMostSignificantBits());
                hash = mix(hash, descriptor.fieldId().getLeastSignificantBits());
                hash = mix(hash, descriptor.groupId().getMostSignificantBits());
                hash = mix(hash, descriptor.groupId().getLeastSignificantBits());
                hash = mix(hash, descriptor.memberIndex());
                hash = mix(hash, descriptor.memberCount());
                hash = mix(hash, descriptor.groupSlot());
                hash = mix(hash, descriptor.role().gpuId());
                hash = mix(hash, Double.doubleToLongBits(descriptor.centerX()));
                hash = mix(hash, Double.doubleToLongBits(descriptor.centerZ()));
                hash = mix(hash, Float.floatToIntBits(descriptor.baseY()));
                hash = mix(hash, Float.floatToIntBits(descriptor.topY()));
                hash = mix(hash, Float.floatToIntBits(descriptor.majorRadius()));
                hash = mix(hash, Float.floatToIntBits(descriptor.minorRadius()));
                hash = mix(hash, Float.floatToIntBits(descriptor.shearX()));
                hash = mix(hash, Float.floatToIntBits(descriptor.shearZ()));
                hash = mix(hash, Float.floatToIntBits(descriptor.sinOrientation()));
                hash = mix(hash, Float.floatToIntBits(descriptor.cosOrientation()));
                hash = mix(hash, Float.floatToIntBits(descriptor.edgeSoftness()));
                hash = mix(hash, Float.floatToIntBits(descriptor.density()));
                hash = mix(hash, Float.floatToIntBits(descriptor.detailWeight()));
                hash = mix(hash, Float.floatToIntBits(descriptor.lifecycleStage()));
                hash = mix(hash, Float.floatToIntBits(descriptor.verticalDevelopment()));
            }
        }
        hash = mix(hash, descriptorCount);
        hash = mix(hash, groups.size());
        return new StormCloudContent(puffLobeCount, puffDescriptorSignature, puffCandidateSignature,
                stormLobeCount, descriptorCount, groups.size(), fixtureCount, hash);
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * FNV_PRIME;
    }

    String format() {
        return "cloudContent={puffLobeCount=" + puffLobeCount
                + " puffDescriptorSignature=" + Long.toHexString(puffDescriptorSignature)
                + " puffCandidateSignature=" + Long.toHexString(puffCandidateSignature)
                + " stormLobeCount=" + stormLobeCount
                + " stormDescriptorCount=" + stormDescriptorCount
                + " stormGroupCount=" + stormGroupCount
                + " fixtureDescriptorCount=" + fixtureDescriptorCount
                + " foreignStormDescriptorCount=" + foreignStormDescriptorCount()
                + " stormContentSignature=" + Long.toHexString(stormContentSignature) + '}';
    }

    /** Names every category of whole-frame cloud content that moved. */
    static Comparison compare(StormCloudContent a, StormCloudContent b) {
        if (a == null || b == null) {
            return new Comparison(false, false, List.of(), null, null, "cloud_content_missing");
        }
        List<String> categories = new ArrayList<>();
        if (a.puffLobeCount() != b.puffLobeCount()) {
            categories.add("puff_count");
        }
        if (a.puffDescriptorSignature() != b.puffDescriptorSignature()) {
            categories.add("puff_content");
        }
        if (a.puffCandidateSignature() != b.puffCandidateSignature()) {
            categories.add("candidate_content");
        }
        if (a.stormLobeCount() != b.stormLobeCount()
                || a.stormDescriptorCount() != b.stormDescriptorCount()) {
            categories.add("storm_count");
        }
        if (a.stormGroupCount() != b.stormGroupCount()) {
            categories.add("storm_group_count");
        }
        if (a.stormContentSignature() != b.stormContentSignature()) {
            categories.add("storm_content");
        }
        if (a.foreignStormDescriptorCount() != b.foreignStormDescriptorCount()) {
            categories.add("foreign_storm_content");
        }
        return new Comparison(true, categories.isEmpty(), List.copyOf(categories), a, b, "");
    }

    record Comparison(
            boolean evaluated,
            boolean cloudContentMatch,
            List<String> differingCategories,
            StormCloudContent a,
            StormCloudContent b,
            String unavailableReason
    ) {
        String format() {
            if (!evaluated) {
                return "cloudContent evaluated=false reason=" + unavailableReason;
            }
            return String.format(Locale.ROOT,
                    "cloudContent evaluated=true cloudContentMatch=%s differingCategories=%s"
                            + " puffLobeCountA=%d puffLobeCountB=%d puffLobeCountMatch=%s"
                            + " puffContentSignatureA=%s puffContentSignatureB=%s puffContentMatch=%s"
                            + " puffCandidateSignatureA=%s puffCandidateSignatureB=%s"
                            + " stormLobeCountA=%d stormLobeCountB=%d stormLobeCountMatch=%s"
                            + " stormDescriptorCountA=%d stormDescriptorCountB=%d stormDescriptorCountMatch=%s"
                            + " stormGroupCountA=%d stormGroupCountB=%d stormGroupCountMatch=%s"
                            + " stormContentSignatureA=%s stormContentSignatureB=%s stormContentMatch=%s"
                            + " fixtureDescriptorCountA=%d fixtureDescriptorCountB=%d"
                            + " foreignStormDescriptorCountA=%d foreignStormDescriptorCountB=%d",
                    cloudContentMatch,
                    differingCategories.isEmpty() ? "none" : String.join(",", differingCategories),
                    a.puffLobeCount(), b.puffLobeCount(), a.puffLobeCount() == b.puffLobeCount(),
                    Long.toHexString(a.puffDescriptorSignature()),
                    Long.toHexString(b.puffDescriptorSignature()),
                    a.puffDescriptorSignature() == b.puffDescriptorSignature(),
                    Long.toHexString(a.puffCandidateSignature()),
                    Long.toHexString(b.puffCandidateSignature()),
                    a.stormLobeCount(), b.stormLobeCount(), a.stormLobeCount() == b.stormLobeCount(),
                    a.stormDescriptorCount(), b.stormDescriptorCount(),
                    a.stormDescriptorCount() == b.stormDescriptorCount(),
                    a.stormGroupCount(), b.stormGroupCount(), a.stormGroupCount() == b.stormGroupCount(),
                    Long.toHexString(a.stormContentSignature()),
                    Long.toHexString(b.stormContentSignature()),
                    a.stormContentSignature() == b.stormContentSignature(),
                    a.fixtureDescriptorCount(), b.fixtureDescriptorCount(),
                    a.foreignStormDescriptorCount(), b.foreignStormDescriptorCount());
        }
    }
}
