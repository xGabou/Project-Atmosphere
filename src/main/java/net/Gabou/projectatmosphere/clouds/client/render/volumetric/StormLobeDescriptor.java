package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/** Immutable, render-only projection of one authoritative severe-storm member. */
public record StormLobeDescriptor(
        UUID fieldId,
        UUID groupId,
        int memberIndex,
        int memberCount,
        int groupSlot,
        Role role,
        double centerX,
        double centerZ,
        float baseY,
        float topY,
        float majorRadius,
        float minorRadius,
        float sinOrientation,
        float cosOrientation,
        float shearX,
        float shearZ,
        float density,
        float edgeSoftness,
        float seed01,
        float lifecycleStage,
        float verticalDevelopment,
        float detailWeight
) {
    public static final int TEXELS_PER_DESCRIPTOR = 4;
    public static final int FLOATS_PER_DESCRIPTOR = TEXELS_PER_DESCRIPTOR * 4;
    public static final Comparator<StormLobeDescriptor> STABLE_IDENTITY_ORDER =
            Comparator.comparing(StormLobeDescriptor::groupId)
                    .thenComparingInt(StormLobeDescriptor::memberIndex)
                    .thenComparing(StormLobeDescriptor::fieldId);

    public StormLobeDescriptor {
        fieldId = Objects.requireNonNull(fieldId, "fieldId");
        groupId = Objects.requireNonNull(groupId, "groupId");
        role = Objects.requireNonNull(role, "role");
        memberCount = Math.max(1, memberCount);
        memberIndex = Math.max(0, Math.min(memberIndex, memberCount - 1));
        groupSlot = Math.max(-1, Math.min(groupSlot, StormLobeSpatialIndex.MAX_GROUPS - 1));
        centerX = finite(centerX, 0.0D);
        centerZ = finite(centerZ, 0.0D);
        baseY = finite(baseY, 0.0F);
        topY = Math.max(baseY + 1.0F, finite(topY, baseY + 1.0F));
        majorRadius = Math.max(1.0F, finite(majorRadius, 1.0F));
        minorRadius = Math.max(1.0F, finite(minorRadius, 1.0F));
        float orientationLength = (float) Math.sqrt(
                sinOrientation * sinOrientation + cosOrientation * cosOrientation
        );
        if (!Float.isFinite(orientationLength) || orientationLength < 1.0E-5F) {
            sinOrientation = 0.0F;
            cosOrientation = 1.0F;
        } else {
            sinOrientation /= orientationLength;
            cosOrientation /= orientationLength;
        }
        shearX = finite(shearX, 0.0F);
        shearZ = finite(shearZ, 0.0F);
        density = clamp01(density);
        edgeSoftness = Math.max(0.001F, clamp01(edgeSoftness));
        seed01 = clamp01(seed01);
        lifecycleStage = clamp01(lifecycleStage);
        verticalDevelopment = clamp01(verticalDevelopment);
        detailWeight = clamp01(detailWeight);
    }

    public static StormLobeDescriptor fromCell(VolumetricRenderCell cell, int groupSlot) {
        Objects.requireNonNull(cell, "cell");
        Role role = Role.fromEnvelope(cell.envelopeRole());
        float orientation = cell.orientationRadians();
        float cos = (float) Math.cos(orientation);
        float sin = (float) Math.sin(orientation);
        float shearScale = shearScale(role);
        float shear = cell.radiusMajor()
                * shearScale
                * (0.35F + 0.65F * cell.verticalDevelopment());
        return new StormLobeDescriptor(
                cell.fieldId(),
                cell.morphologyGroupId(),
                cell.morphologyMemberIndex(),
                cell.morphologyMemberCount(),
                groupSlot,
                role,
                cell.x(),
                cell.z(),
                cell.baseY(),
                cell.topY(),
                cell.radiusMajor(),
                cell.radiusMinor(),
                sin,
                cos,
                cos * shear,
                sin * shear,
                cell.density(),
                cell.edgeSoftness(),
                cell.seed01(),
                cell.lifecycleStage(),
                cell.verticalDevelopment(),
                1.0F
        );
    }

    public StormLobeDescriptor withGroupSlot(int slot) {
        return new StormLobeDescriptor(
                fieldId, groupId, memberIndex, memberCount, slot, role,
                centerX, centerZ, baseY, topY, majorRadius, minorRadius,
                sinOrientation, cosOrientation, shearX, shearZ, density,
                edgeSoftness, seed01, lifecycleStage, verticalDevelopment, detailWeight
        );
    }

    /**
     * Returns this descriptor with a different analytic LOD weight.
     *
     * <p>The weight scales the descriptor's contribution to the <em>coverage
     * envelope</em>, not to the final density: fading a distant group has to
     * dissolve it into the broad map by admitting less of the noise field,
     * rather than uniformly dimming a body that is still fully shaped.
     */
    public StormLobeDescriptor withDetailWeight(float weight) {
        return new StormLobeDescriptor(
                fieldId, groupId, memberIndex, memberCount, groupSlot, role,
                centerX, centerZ, baseY, topY, majorRadius, minorRadius,
                sinOrientation, cosOrientation, shearX, shearZ, density,
                edgeSoftness, seed01, lifecycleStage, verticalDevelopment, weight
        );
    }

    /** Reconstructs the exact immutable descriptor represented by uploaded GPU texels. */
    static StormLobeDescriptor fromTexels(
            StormLobeDescriptor identity,
            float[] texels,
            int offset
    ) {
        int packedGroupRole = Math.round(texels[offset + 15]);
        int groupSlot = Math.max(0, packedGroupRole / 8);
        Role role = Role.fromGpuId(Math.floorMod(packedGroupRole, 8));
        return new StormLobeDescriptor(
                identity.fieldId(), identity.groupId(), identity.memberIndex(), identity.memberCount(),
                groupSlot, role,
                texels[offset], texels[offset + 1], texels[offset + 2], texels[offset + 3],
                texels[offset + 4], texels[offset + 5], texels[offset + 6], texels[offset + 7],
                texels[offset + 8], texels[offset + 9], texels[offset + 10], texels[offset + 11],
                texels[offset + 12], texels[offset + 13], texels[offset + 14], 1.0F
        );
    }

    public boolean sameIdentity(StormLobeDescriptor other) {
        return other != null
                && fieldId.equals(other.fieldId)
                && groupId.equals(other.groupId)
                && memberIndex == other.memberIndex;
    }

    public float packedGroupRole() {
        return groupSlot * 8.0F + role.gpuId();
    }

    public void writeTexels(float[] destination, int offset) {
        if (destination == null || offset < 0 || offset + FLOATS_PER_DESCRIPTOR > destination.length) {
            throw new IllegalArgumentException("descriptor destination is too small");
        }
        destination[offset] = (float) centerX;
        destination[offset + 1] = (float) centerZ;
        destination[offset + 2] = baseY;
        destination[offset + 3] = topY;
        destination[offset + 4] = majorRadius;
        destination[offset + 5] = minorRadius;
        destination[offset + 6] = sinOrientation;
        destination[offset + 7] = cosOrientation;
        destination[offset + 8] = shearX;
        destination[offset + 9] = shearZ;
        destination[offset + 10] = density * detailWeight;
        destination[offset + 11] = edgeSoftness;
        destination[offset + 12] = seed01;
        destination[offset + 13] = lifecycleStage;
        destination[offset + 14] = verticalDevelopment;
        destination[offset + 15] = packedGroupRole();
    }

    /** Writes a live cell directly into reusable upload storage without allocating a descriptor. */
    public static void writeCellTexels(
            VolumetricRenderCell cell,
            int groupSlot,
            float[] destination,
            int offset
    ) {
        if (cell == null || destination == null || offset < 0
                || offset + FLOATS_PER_DESCRIPTOR > destination.length) {
            throw new IllegalArgumentException("invalid live storm descriptor destination");
        }
        Role role = Role.fromEnvelope(cell.envelopeRole());
        float orientation = cell.orientationRadians();
        float cos = (float) Math.cos(orientation);
        float sin = (float) Math.sin(orientation);
        float shearScale = shearScale(role);
        float shear = cell.radiusMajor()
                * shearScale
                * (0.35F + 0.65F * cell.verticalDevelopment());
        destination[offset] = (float) cell.x();
        destination[offset + 1] = (float) cell.z();
        destination[offset + 2] = cell.baseY();
        destination[offset + 3] = Math.max(cell.baseY() + 1.0F, cell.topY());
        destination[offset + 4] = Math.max(1.0F, cell.radiusMajor());
        destination[offset + 5] = Math.max(1.0F, cell.radiusMinor());
        destination[offset + 6] = sin;
        destination[offset + 7] = cos;
        destination[offset + 8] = cos * shear;
        destination[offset + 9] = sin * shear;
        destination[offset + 10] = clamp01(cell.density());
        destination[offset + 11] = Math.max(0.001F, clamp01(cell.edgeSoftness()));
        destination[offset + 12] = clamp01(cell.seed01());
        destination[offset + 13] = clamp01(cell.lifecycleStage());
        destination[offset + 14] = clamp01(cell.verticalDevelopment());
        destination[offset + 15] = groupSlot * 8.0F + role.gpuId();
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, finite(value, 0.0F)));
    }

    static float shearScale(Role role) {
        return switch (role) {
            case BASE -> 0.02F;
            case CORE -> 0.08F;
            case TOWER -> 0.18F;
            case ANVIL -> 0.24F;
        };
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    public enum Role {
        BASE(0), CORE(1), TOWER(2), ANVIL(3);

        private final int gpuId;

        Role(int gpuId) {
            this.gpuId = gpuId;
        }

        public int gpuId() {
            return gpuId;
        }

        public static Role fromGpuId(int id) {
            return switch (id) {
                case 0 -> BASE;
                case 1 -> CORE;
                case 2 -> TOWER;
                case 3 -> ANVIL;
                default -> throw new IllegalArgumentException("invalid storm role id " + id);
            };
        }

        /** True only for cells whose body can be owned by a storm descriptor. */
        static boolean supports(VolumetricRenderCell.EnvelopeRole role) {
            return role == VolumetricRenderCell.EnvelopeRole.BASE
                    || role == VolumetricRenderCell.EnvelopeRole.CORE
                    || role == VolumetricRenderCell.EnvelopeRole.TOWER
                    || role == VolumetricRenderCell.EnvelopeRole.ANVIL;
        }

        static Role fromEnvelope(VolumetricRenderCell.EnvelopeRole role) {
            return switch (role) {
                case BASE -> BASE;
                case CORE -> CORE;
                case TOWER -> TOWER;
                case ANVIL -> ANVIL;
                default -> throw new IllegalArgumentException("not a severe-storm role: " + role);
            };
        }
    }
}
