package net.Gabou.projectatmosphere.clouds.field;

import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyMemberTier;

import java.util.UUID;

/**
 * Stable membership of one canonical simulation lobe in its generated cloud
 * morphology.  Rendering may derive a compact stage from this metadata, but
 * the persistent index/count remain available for diagnostics and future
 * topology changes.
 */
public record CloudMorphologyMembership(
        UUID groupId,
        int memberIndex,
        int memberCount,
        int layoutVersion,
        CloudMorphologyMemberTier memberTier
) {
    private static final UUID UNGROUPED_ID = new UUID(0L, 0L);

    public CloudMorphologyMembership {
        groupId = groupId == null ? UNGROUPED_ID : groupId;
        memberCount = Math.max(1, memberCount);
        memberIndex = Math.max(0, Math.min(memberIndex, memberCount - 1));
        layoutVersion = Math.max(0, layoutVersion);
        memberTier = memberTier == null ? CloudMorphologyMemberTier.UNKNOWN : memberTier;
        if (layoutVersion == 0) {
            memberTier = CloudMorphologyMemberTier.UNKNOWN;
        }
    }

    /** Backward-compatible construction for legacy, unversioned layouts. */
    public CloudMorphologyMembership(UUID groupId, int memberIndex, int memberCount) {
        this(groupId, memberIndex, memberCount, 0, CloudMorphologyMemberTier.UNKNOWN);
    }

    public static CloudMorphologyMembership ungrouped() {
        return new CloudMorphologyMembership(UNGROUPED_ID, 0, 1, 0,
                CloudMorphologyMemberTier.UNKNOWN);
    }

    public static CloudMorphologyMembership single(UUID identity) {
        return new CloudMorphologyMembership(identity, 0, 1, 0,
                CloudMorphologyMemberTier.UNKNOWN);
    }

    public CloudMorphologyMembership withFallbackGroup(UUID fallbackGroupId) {
        if (!UNGROUPED_ID.equals(groupId)) {
            return this;
        }
        return new CloudMorphologyMembership(
                fallbackGroupId,
                memberIndex,
                memberCount,
                layoutVersion,
                memberTier
        );
    }

    public boolean isGrouped() {
        return memberCount > 1;
    }

    /**
     * Resolves the independently rendered tiers of canonical convective
     * topology. PUFF indices are lateral sibling identities, not vertical
     * stages: their generator places every secondary member radially with only
     * small independent Y jitter. Keep grouped PUFF lobes in one stage channel
     * so the structured map preserves each local interval without inventing a
     * BASE/CORE/CROWN stack from index order.
     */
    public Stage stageFor(CloudMorphologyFamily family) {
        if (family == CloudMorphologyFamily.PUFF && memberCount >= 3) {
            return Stage.BASE;
        }
        if (family == CloudMorphologyFamily.STORM_ANVIL && memberCount >= 4) {
            return stormAnvilStage();
        }
        if (family != CloudMorphologyFamily.TOWER || memberCount <= 1) {
            return Stage.MACRO;
        }

        int baseEnd = Math.min(3, memberCount - 1);
        if (memberIndex <= baseEnd) {
            return Stage.BASE;
        }

        int upperCount = memberCount - baseEnd - 1;
        int crownCount = upperCount >= 6 ? 2 : 1;
        int towerCount = Math.max(1, (upperCount - crownCount) / 2);
        int coreCount = Math.max(1, upperCount - crownCount - towerCount);
        int relativeIndex = memberIndex - baseEnd - 1;
        if (relativeIndex < coreCount) {
            return Stage.CORE;
        }
        if (relativeIndex < coreCount + towerCount) {
            return Stage.TOWER;
        }
        return Stage.CROWN;
    }

    /**
     * Resolves the renderer roles already implied by the authoritative storm
     * generator. The generator places indices above {@code count / 2} in the
     * raised, horizontally displaced anvil population. The remaining members
     * form a lower convective stack; split them deterministically while
     * guaranteeing BASE, CORE, and TOWER support for every supported group.
     */
    private Stage stormAnvilStage() {
        int anvilStart = memberCount / 2 + 1;
        if (memberIndex >= anvilStart) {
            return Stage.ANVIL;
        }

        int lowerCount = anvilStart;
        int baseCount = Math.max(1, lowerCount / 3);
        int coreCount = Math.max(1, (lowerCount - baseCount) / 2);
        if (memberIndex < baseCount) {
            return Stage.BASE;
        }
        if (memberIndex < baseCount + coreCount) {
            return Stage.CORE;
        }
        return Stage.TOWER;
    }

    public enum Stage {
        MACRO,
        BASE,
        CORE,
        TOWER,
        CROWN,
        ANVIL
    }
}
