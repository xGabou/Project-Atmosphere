package net.Gabou.projectatmosphere.clouds.field;

import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;

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
        int memberCount
) {
    private static final UUID UNGROUPED_ID = new UUID(0L, 0L);

    public CloudMorphologyMembership {
        groupId = groupId == null ? UNGROUPED_ID : groupId;
        memberCount = Math.max(1, memberCount);
        memberIndex = Math.max(0, Math.min(memberIndex, memberCount - 1));
    }

    public static CloudMorphologyMembership ungrouped() {
        return new CloudMorphologyMembership(UNGROUPED_ID, 0, 1);
    }

    public static CloudMorphologyMembership single(UUID identity) {
        return new CloudMorphologyMembership(identity, 0, 1);
    }

    public CloudMorphologyMembership withFallbackGroup(UUID fallbackGroupId) {
        if (!UNGROUPED_ID.equals(groupId)) {
            return this;
        }
        return single(fallbackGroupId);
    }

    public boolean isGrouped() {
        return memberCount > 1;
    }

    /**
     * Resolves the four independently rendered tiers of the deterministic
     * native TOWER topology. Other families retain their existing macro path.
     */
    public Stage stageFor(CloudMorphologyFamily family) {
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

    public enum Stage {
        MACRO,
        BASE,
        CORE,
        TOWER,
        CROWN
    }
}
