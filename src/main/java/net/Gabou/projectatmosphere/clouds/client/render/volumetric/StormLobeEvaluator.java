package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.List;

/**
 * Pure CPU authority for the analytic storm field independently mirrored by GLSL.
 *
 * <p>Phase 4S: every lobe exposes a real world-space geometric distance field
 * (blocks, negative inside), lobes and groups are smoothly unioned in that
 * geometric domain with blend radii in blocks, and the union result is a
 * bounded <em>coverage envelope</em> - not a visible density. The visible storm
 * body is formed from that envelope by {@link StormDensityModel}. See
 * {@code contracts/storm-density-composition.md}.
 */
final class StormLobeEvaluator {
    private static final double NO_FIELD = Double.POSITIVE_INFINITY;

    /**
     * Lobe-to-lobe smooth-union blend distance as a fraction of the smaller
     * participating lobe's world-space radius. Proportional blending hides
     * primitive intersections without widening a narrow tower toward base or
     * anvil scale.
     */
    private static final double LOBE_BLEND_FRACTION = 0.25D;

    /** Group-to-group blending is deliberately gentler than lobe-to-lobe. */
    private static final double GROUP_BLEND_FRACTION = 0.18D;

    /** Blend distances are clamped to a sane world-space range, in blocks. */
    private static final double MIN_BLEND_BLOCKS = 4.0D;
    private static final double MAX_BLEND_BLOCKS = 48.0D;

    /**
     * Minimum envelope boundary half-width, in blocks.
     *
     * <p>Derived, not chosen: the coverage envelope exists so the noise stages
     * can form the visible body inside it. An envelope boundary sharper than
     * the coarsest noise that is meant to sculpt it would show through as the
     * descriptor's own geometric edge - a descriptor seam, which FR-024
     * rejects. The bound is therefore half of the lowest detail octave's
     * wavelength, so the boundary always spans at least one full cycle of the
     * coarsest sculpting frequency.
     */
    private static final double MIN_EDGE_BLOCKS =
            0.5D * StormMorphologyThresholds.LOWEST_DETAIL_WAVELENGTH_BLOCKS;

    /** Envelope boundary width as a multiple of the descriptor's edge softness. */
    private static final double EDGE_SOFTNESS_BLOCKS = 1.0D;

    /** Cap fillet as a fraction of the lobe's smaller extent. */
    private static final double CAP_ROUNDING_FRACTION = 0.35D;

    /**
     * Ceiling on the envelope boundary half-width as a fraction of a lobe's own
     * half-height. See {@link #edgeWidthBlocks}: the boundary is isotropic, so
     * it must stay inside the extent it is applied across.
     */
    private static final double VERTICAL_EDGE_BOUND_FRACTION = 0.75D;

    private StormLobeEvaluator() {
    }

    // -----------------------------------------------------------------
    // Stage 1: per-lobe geometric distance field
    // -----------------------------------------------------------------

    /**
     * Signed geometric distance from {@code (worldX, worldY, worldZ)} to this
     * lobe's surface, in world-space blocks. Negative inside, zero on the
     * surface, positive outside.
     *
     * <p>The lobe is the intersection of its oriented, sheared, height-varying
     * ellipse with its own vertical span, where the role's vertical shape
     * function tapers the available radius so the lobe closes into rounded
     * caps rather than a cylinder with faded ends.
     *
     * <p>Horizontally the normalized ellipse coordinate is converted to blocks
     * by dividing by the magnitude of its gradient, which is exact for a
     * circular section and a well-behaved first-order distance for the
     * eccentricities the role profiles produce. Vertically the distance to the
     * lobe's own span is already in blocks. The two are combined by
     * intersection, which is the standard conservative construction: it never
     * overstates how far inside a point is, stays monotonic as the probe moves
     * away, and remains finite and correct everywhere outside the surface -
     * including directly above or below the lobe, where a density-derived
     * pseudo-distance carries no information at all.
     */
    static double signedDistanceAt(
            StormLobeDescriptor lobe,
            double worldX,
            double worldY,
            double worldZ
    ) {
        double roleBaseY = roleBaseY(lobe);
        double roleTopY = roleTopY(lobe);
        double height = Math.max(roleTopY - roleBaseY, 1.0D);
        double centerY = (roleBaseY + roleTopY) * 0.5D;
        double halfHeight = height * 0.5D;
        double vertical01 = clamp01((worldY - roleBaseY) / height);

        double profileRadius = profileRadius(lobe.role(), vertical01);
        double shearProgress = shearProgress(lobe.role(), vertical01);

        double localX = worldX - lobe.centerX() - lobe.shearX() * shearProgress;
        double localZ = worldZ - lobe.centerZ() - lobe.shearZ() * shearProgress;
        double orientedX = localX * lobe.cosOrientation() + localZ * lobe.sinOrientation();
        double orientedZ = -localX * lobe.sinOrientation() + localZ * lobe.cosOrientation();
        double major = Math.max(1.0D, lobe.majorRadius() * profileRadius);
        double minor = Math.max(1.0D, lobe.minorRadius() * profileRadius
                * (lobe.role() == StormLobeDescriptor.Role.ANVIL ? 1.56D : 1.0D));

        double radial = Math.sqrt(orientedX * orientedX / (major * major)
                + orientedZ * orientedZ / (minor * minor));
        // The coherent warp displaces the surface, so it shifts the iso-level
        // rather than scaling the distance.
        radial += coherentMorphologyWarp(lobe, worldX, worldY, worldZ) * 0.08D;

        // Normalized ellipse coordinate to blocks, by dividing out the
        // magnitude of its gradient. Exact for a circular section and a
        // well-behaved first-order distance at the eccentricities the role
        // profiles produce.
        double gradient = Math.sqrt(
                orientedX * orientedX / (major * major * major * major)
                        + orientedZ * orientedZ / (minor * minor * minor * minor));
        double effectiveRadius = gradient > 1.0E-9D
                ? radial / gradient
                : Math.min(major, minor);

        // Signed distance to each face of the unrounded column, in blocks.
        double wallDistance = (radial - 1.0D) * effectiveRadius;
        double capDistance = Math.abs(worldY - centerY) - halfHeight;

        // Rounded-box distance in the (radial, height) half-plane. This is the
        // standard construction and, unlike folding the vertical taper into the
        // radius, it has a bounded gradient everywhere: a vertical shape that
        // ramps over a few percent of the lobe height would otherwise change
        // the surface position by tens of blocks per block of travel, which is
        // both a false distance and a flat-slab cap.
        double rounding = capRounding(effectiveRadius, halfHeight);
        double roundedWall = wallDistance + rounding;
        double roundedCap = capDistance + rounding;
        double outsideWall = Math.max(roundedWall, 0.0D);
        double outsideCap = Math.max(roundedCap, 0.0D);
        return Math.sqrt(outsideWall * outsideWall + outsideCap * outsideCap)
                + Math.min(Math.max(roundedWall, roundedCap), 0.0D)
                - rounding;
    }

    /**
     * Cap fillet radius in blocks.
     *
     * <p>Derived rather than chosen. A cap rounded over less than the coarsest
     * sculpting wavelength reads as a flat slab, because the noise stages have
     * no room to break its silhouette, so the fillet reaches half that
     * wavelength wherever the lobe is large enough to carry it. It is bounded
     * above by a fraction of the lobe's own smaller extent, so a small lobe
     * rounds partially instead of collapsing into a recognizable sphere - both
     * flat slabs and sphere primitives are rejected forms under FR-024.
     */
    private static double capRounding(double effectiveRadius, double halfHeight) {
        double extentBound = Math.min(effectiveRadius, halfHeight) * CAP_ROUNDING_FRACTION;
        return Math.min(extentBound, MIN_EDGE_BLOCKS);
    }

    /**
     * Legacy per-lobe analytic mass. Retained because descriptor packing,
     * profile and role regressions pin it, and because the audited composition
     * is reproduced from it for the fail-first record. It is <em>not</em> part
     * of the corrected density path.
     */
    static double densityAt(StormLobeDescriptor lobe, double worldX, double worldY, double worldZ) {
        double height = lobe.topY() - lobe.baseY();
        double vertical01 = (worldY - lobe.baseY()) / height;
        if (vertical01 <= 0.0D || vertical01 >= 1.0D) {
            return 0.0D;
        }

        double profileRadius = profileRadius(lobe.role(), vertical01);
        double verticalShape = verticalShape(lobe.role(), vertical01);
        double shearProgress = shearProgress(lobe.role(), vertical01);

        double localX = worldX - lobe.centerX() - lobe.shearX() * shearProgress;
        double localZ = worldZ - lobe.centerZ() - lobe.shearZ() * shearProgress;
        double orientedX = localX * lobe.cosOrientation() + localZ * lobe.sinOrientation();
        double orientedZ = -localX * lobe.sinOrientation() + localZ * lobe.cosOrientation();
        double major = Math.max(1.0D, lobe.majorRadius() * profileRadius);
        double minor = Math.max(1.0D, lobe.minorRadius() * profileRadius
                * (lobe.role() == StormLobeDescriptor.Role.ANVIL ? 1.56D : 1.0D));
        double radial = Math.sqrt(orientedX * orientedX / (major * major)
                + orientedZ * orientedZ / (minor * minor));
        radial += coherentMorphologyWarp(lobe, worldX, worldY, worldZ) * 0.08D;
        double edgeWidth = lobe.role() == StormLobeDescriptor.Role.ANVIL
                ? Math.max(0.12D, lobe.edgeSoftness() * 1.25D)
                : Math.max(0.06D, lobe.edgeSoftness() * 0.62D);
        double horizontalShape = 1.0D - smoothstep(1.0D - edgeWidth, 1.0D, radial);
        return clamp01(horizontalShape * verticalShape * lobe.density() * lobe.detailWeight());
    }

    // -----------------------------------------------------------------
    // Stages 2-4: smooth unions and the bounded coverage envelope
    // -----------------------------------------------------------------

    /**
     * Bounded coverage envelope of the whole descriptor set at this point.
     *
     * <p>Every lobe contributes its geometric distance field, including lobes
     * the point lies outside of: a lobe is never dropped because its local
     * density evaluates to zero, since that is exactly the region where a
     * smooth union needs its distance.
     */
    static double coverageEnvelopeAt(
            List<StormLobeDescriptor> lobes,
            double worldX,
            double worldY,
            double worldZ
    ) {
        double stormDistance = NO_FIELD;
        double stormStrength = 0.0D;
        double stormSoftness = 0.0D;
        double previousGroupRadius = 0.0D;
        for (int first = 0; first < lobes.size(); first++) {
            StormLobeDescriptor identity = lobes.get(first);
            if (hasEarlierGroup(lobes, first, identity)) {
                continue;
            }
            double groupDistance = NO_FIELD;
            double groupStrength = 0.0D;
            double groupSoftness = 0.0D;
            double previousLobeRadius = 0.0D;
            StormLobeDescriptor previousLobe = null;
            double minimumGroupRadius = Double.POSITIVE_INFINITY;
            for (int index = 0; index < lobes.size(); index++) {
                StormLobeDescriptor lobe = lobes.get(index);
                if (!lobe.groupId().equals(identity.groupId())) {
                    continue;
                }
                // Every lobe of the group contributes, including lobes this
                // point lies outside of: that is precisely where a smooth
                // union needs a valid distance.
                double lobeDistance = signedDistanceAt(lobe, worldX, worldY, worldZ);
                double lobeRadius = smallerRadius(lobe);
                double lobeStrength = envelopeStrength(lobe);
                double lobeSoftness = edgeWidthBlocks(lobe);
                if (groupDistance == NO_FIELD) {
                    groupDistance = lobeDistance;
                    groupStrength = lobeStrength;
                    groupSoftness = lobeSoftness;
                } else {
                    double blend = lobeBlendRadius(previousLobe, lobe);
                    double mix = blendFactor(groupDistance, lobeDistance, blend);
                    groupDistance = smoothMinimum(groupDistance, lobeDistance, blend);
                    groupStrength = lerp(mix, lobeStrength, groupStrength);
                    groupSoftness = lerp(mix, lobeSoftness, groupSoftness);
                }
                previousLobeRadius = lobeRadius;
                previousLobe = lobe;
                minimumGroupRadius = Math.min(minimumGroupRadius, lobeRadius);
            }
            if (groupDistance == NO_FIELD) {
                continue;
            }
            if (stormDistance == NO_FIELD) {
                stormDistance = groupDistance;
                stormStrength = groupStrength;
                stormSoftness = groupSoftness;
            } else {
                double blend = groupBlendRadius(previousGroupRadius, minimumGroupRadius);
                double mix = blendFactor(stormDistance, groupDistance, blend);
                stormDistance = smoothMinimum(stormDistance, groupDistance, blend);
                stormStrength = lerp(mix, groupStrength, stormStrength);
                stormSoftness = lerp(mix, groupSoftness, stormSoftness);
            }
            previousGroupRadius = minimumGroupRadius;
        }
        return envelopeFromDistance(stormDistance, stormSoftness, stormStrength);
    }

    /** Allocation-free coverage envelope used by the published render snapshot. */
    static double coverageEnvelopeAt(
            StormLobeDescriptor[] lobes,
            double worldX,
            double worldY,
            double worldZ
    ) {
        double stormDistance = NO_FIELD;
        double stormStrength = 0.0D;
        double stormSoftness = 0.0D;
        double previousGroupRadius = 0.0D;
        for (int first = 0; first < lobes.length; first++) {
            StormLobeDescriptor identity = lobes[first];
            if (hasEarlierGroup(lobes, first, identity)) {
                continue;
            }
            double groupDistance = NO_FIELD;
            double groupStrength = 0.0D;
            double groupSoftness = 0.0D;
            double previousLobeRadius = 0.0D;
            StormLobeDescriptor previousLobe = null;
            double minimumGroupRadius = Double.POSITIVE_INFINITY;
            for (StormLobeDescriptor lobe : lobes) {
                if (!lobe.groupId().equals(identity.groupId())) {
                    continue;
                }
                double lobeDistance = signedDistanceAt(lobe, worldX, worldY, worldZ);
                double lobeRadius = smallerRadius(lobe);
                double lobeStrength = envelopeStrength(lobe);
                double lobeSoftness = edgeWidthBlocks(lobe);
                if (groupDistance == NO_FIELD) {
                    groupDistance = lobeDistance;
                    groupStrength = lobeStrength;
                    groupSoftness = lobeSoftness;
                } else {
                    double blend = lobeBlendRadius(previousLobe, lobe);
                    double mix = blendFactor(groupDistance, lobeDistance, blend);
                    groupDistance = smoothMinimum(groupDistance, lobeDistance, blend);
                    groupStrength = lerp(mix, lobeStrength, groupStrength);
                    groupSoftness = lerp(mix, lobeSoftness, groupSoftness);
                }
                previousLobeRadius = lobeRadius;
                previousLobe = lobe;
                minimumGroupRadius = Math.min(minimumGroupRadius, lobeRadius);
            }
            if (groupDistance == NO_FIELD) {
                continue;
            }
            if (stormDistance == NO_FIELD) {
                stormDistance = groupDistance;
                stormStrength = groupStrength;
                stormSoftness = groupSoftness;
            } else {
                double blend = groupBlendRadius(previousGroupRadius, minimumGroupRadius);
                double mix = blendFactor(stormDistance, groupDistance, blend);
                stormDistance = smoothMinimum(stormDistance, groupDistance, blend);
                stormStrength = lerp(mix, groupStrength, stormStrength);
                stormSoftness = lerp(mix, groupSoftness, stormSoftness);
            }
            previousGroupRadius = minimumGroupRadius;
        }
        return envelopeFromDistance(stormDistance, stormSoftness, stormStrength);
    }

    /**
     * Continuous descriptor strength selected by the same two smooth unions
     * that select the coverage envelope. This remains an envelope input; it
     * never replaces final noise-formed density.
     */
    static double envelopeStrengthAt(
            List<StormLobeDescriptor> lobes,
            double worldX,
            double worldY,
            double worldZ
    ) {
        double stormDistance = NO_FIELD;
        double stormStrength = 0.0D;
        double previousGroupRadius = 0.0D;
        for (int first = 0; first < lobes.size(); first++) {
            StormLobeDescriptor identity = lobes.get(first);
            if (hasEarlierGroup(lobes, first, identity)) {
                continue;
            }
            double groupDistance = NO_FIELD;
            double groupStrength = 0.0D;
            double previousLobeRadius = 0.0D;
            StormLobeDescriptor previousLobe = null;
            double minimumGroupRadius = Double.POSITIVE_INFINITY;
            for (StormLobeDescriptor lobe : lobes) {
                if (!lobe.groupId().equals(identity.groupId())) {
                    continue;
                }
                double lobeDistance = signedDistanceAt(lobe, worldX, worldY, worldZ);
                double lobeRadius = smallerRadius(lobe);
                double lobeStrength = envelopeStrength(lobe);
                if (groupDistance == NO_FIELD) {
                    groupDistance = lobeDistance;
                    groupStrength = lobeStrength;
                } else {
                    double blend = lobeBlendRadius(previousLobe, lobe);
                    double mix = blendFactor(groupDistance, lobeDistance, blend);
                    groupDistance = smoothMinimum(groupDistance, lobeDistance, blend);
                    groupStrength = lerp(mix, lobeStrength, groupStrength);
                }
                previousLobeRadius = lobeRadius;
                previousLobe = lobe;
                minimumGroupRadius = Math.min(minimumGroupRadius, lobeRadius);
            }
            if (groupDistance == NO_FIELD) {
                continue;
            }
            if (stormDistance == NO_FIELD) {
                stormDistance = groupDistance;
                stormStrength = groupStrength;
            } else {
                double blend = groupBlendRadius(previousGroupRadius, minimumGroupRadius);
                double mix = blendFactor(stormDistance, groupDistance, blend);
                stormDistance = smoothMinimum(stormDistance, groupDistance, blend);
                stormStrength = lerp(mix, groupStrength, stormStrength);
            }
            previousGroupRadius = minimumGroupRadius;
        }
        return clamp01(stormStrength);
    }

    /**
     * Per-sample active-role mask. It is a composition fact, not a role
     * material selector: it identifies the deep BASE+CORE+TOWER overlap where
     * a zero carrier must not split one connected convective column.
     */
    static int activeRoleMaskAt(
            List<StormLobeDescriptor> lobes,
            double worldX,
            double worldY,
            double worldZ
    ) {
        int mask = 0;
        for (StormLobeDescriptor lobe : lobes) {
            if (signedDistanceAt(lobe, worldX, worldY, worldZ) <= edgeWidthBlocks(lobe)) {
                mask |= 1 << lobe.role().gpuId();
            }
        }
        return mask;
    }

    static boolean hasEmbeddedConvectiveOverlap(
            List<StormLobeDescriptor> lobes,
            double worldX,
            double worldY,
            double worldZ
    ) {
        int required = (1 << StormLobeDescriptor.Role.BASE.gpuId())
                | (1 << StormLobeDescriptor.Role.CORE.gpuId())
                | (1 << StormLobeDescriptor.Role.TOWER.gpuId());
        return (activeRoleMaskAt(lobes, worldX, worldY, worldZ) & required) == required;
    }

    /** Allocation-free array variant used by published render snapshots. */
    static double envelopeStrengthAt(
            StormLobeDescriptor[] lobes,
            double worldX,
            double worldY,
            double worldZ
    ) {
        double stormDistance = NO_FIELD;
        double stormStrength = 0.0D;
        double previousGroupRadius = 0.0D;
        for (int first = 0; first < lobes.length; first++) {
            StormLobeDescriptor identity = lobes[first];
            if (hasEarlierGroup(lobes, first, identity)) {
                continue;
            }
            double groupDistance = NO_FIELD;
            double groupStrength = 0.0D;
            double previousLobeRadius = 0.0D;
            StormLobeDescriptor previousLobe = null;
            double minimumGroupRadius = Double.POSITIVE_INFINITY;
            for (StormLobeDescriptor lobe : lobes) {
                if (!lobe.groupId().equals(identity.groupId())) {
                    continue;
                }
                double lobeDistance = signedDistanceAt(lobe, worldX, worldY, worldZ);
                double lobeRadius = smallerRadius(lobe);
                double lobeStrength = envelopeStrength(lobe);
                if (groupDistance == NO_FIELD) {
                    groupDistance = lobeDistance;
                    groupStrength = lobeStrength;
                } else {
                    double blend = lobeBlendRadius(previousLobe, lobe);
                    double mix = blendFactor(groupDistance, lobeDistance, blend);
                    groupDistance = smoothMinimum(groupDistance, lobeDistance, blend);
                    groupStrength = lerp(mix, lobeStrength, groupStrength);
                }
                previousLobeRadius = lobeRadius;
                previousLobe = lobe;
                minimumGroupRadius = Math.min(minimumGroupRadius, lobeRadius);
            }
            if (groupDistance == NO_FIELD) {
                continue;
            }
            if (stormDistance == NO_FIELD) {
                stormDistance = groupDistance;
                stormStrength = groupStrength;
            } else {
                double blend = groupBlendRadius(previousGroupRadius, minimumGroupRadius);
                double mix = blendFactor(stormDistance, groupDistance, blend);
                stormDistance = smoothMinimum(stormDistance, groupDistance, blend);
                stormStrength = lerp(mix, groupStrength, stormStrength);
            }
            previousGroupRadius = minimumGroupRadius;
        }
        return clamp01(stormStrength);
    }

    /**
     * The unioned world-space distance itself, before the envelope mapping.
     * Exposed so regressions can assert on the geometric domain directly
     * rather than through a mapping that saturates to zero coverage well
     * before the distance field stops being meaningful.
     */
    static double unionDistanceAt(
            List<StormLobeDescriptor> lobes,
            double worldX,
            double worldY,
            double worldZ
    ) {
        double stormDistance = NO_FIELD;
        double previousGroupRadius = 0.0D;
        for (int first = 0; first < lobes.size(); first++) {
            StormLobeDescriptor identity = lobes.get(first);
            if (hasEarlierGroup(lobes, first, identity)) {
                continue;
            }
            double groupDistance = NO_FIELD;
            double previousLobeRadius = 0.0D;
            StormLobeDescriptor previousLobe = null;
            double minimumGroupRadius = Double.POSITIVE_INFINITY;
            for (StormLobeDescriptor lobe : lobes) {
                if (!lobe.groupId().equals(identity.groupId())) {
                    continue;
                }
                double lobeDistance = signedDistanceAt(lobe, worldX, worldY, worldZ);
                double lobeRadius = smallerRadius(lobe);
                groupDistance = groupDistance == NO_FIELD
                        ? lobeDistance
                        : smoothMinimum(groupDistance, lobeDistance,
                                lobeBlendRadius(previousLobe, lobe));
                previousLobeRadius = lobeRadius;
                previousLobe = lobe;
                minimumGroupRadius = Math.min(minimumGroupRadius, lobeRadius);
            }
            if (groupDistance == NO_FIELD) {
                continue;
            }
            stormDistance = stormDistance == NO_FIELD
                    ? groupDistance
                    : smoothMinimum(stormDistance, groupDistance,
                            groupBlendRadius(previousGroupRadius, minimumGroupRadius));
            previousGroupRadius = minimumGroupRadius;
        }
        return stormDistance;
    }

    /**
     * Maps the unioned world-space distance to a bounded coverage envelope.
     * The boundary width is in blocks, so the envelope's softness is a real
     * spatial quantity rather than a density-space fudge.
     */
    static double envelopeFromDistance(double distance, double softnessBlocks, double strength) {
        if (!Double.isFinite(distance)) {
            return 0.0D;
        }
        double softness = Math.max(softnessBlocks, MIN_EDGE_BLOCKS);
        double coverage = 1.0D - smoothstep(-softness, softness, distance);
        return clamp01(coverage * clamp01(strength));
    }

    /**
     * The audited Phase 4R composition, retained solely so the fail-first
     * evidence stays reproducible. Never used by the render path.
     */
    static double auditedUnionDensityAt(
            List<StormLobeDescriptor> lobes,
            double worldX,
            double worldY,
            double worldZ
    ) {
        double stormDistance = NO_FIELD;
        double previousGroupRadius = 0.0D;
        for (int first = 0; first < lobes.size(); first++) {
            StormLobeDescriptor identity = lobes.get(first);
            if (hasEarlierGroup(lobes, first, identity)) {
                continue;
            }
            double groupDistance = NO_FIELD;
            double previousLobeRadius = 0.0D;
            double minimumGroupRadius = Double.POSITIVE_INFINITY;
            for (int index = 0; index < lobes.size(); index++) {
                StormLobeDescriptor lobe = lobes.get(index);
                if (!lobe.groupId().equals(identity.groupId())) {
                    continue;
                }
                double lobeDensity = densityAt(lobe, worldX, worldY, worldZ);
                if (lobeDensity <= 0.0D) {
                    continue;
                }
                double lobeRadius = smallerRadius(lobe);
                double lobeDistance = 1.0D - lobeDensity;
                groupDistance = groupDistance == NO_FIELD
                        ? lobeDistance
                        : auditedSmoothMinimum(
                                groupDistance,
                                lobeDistance,
                                clamp(Math.min(previousLobeRadius, lobeRadius) / 420.0D, 0.045D, 0.24D)
                        );
                previousLobeRadius = lobeRadius;
                minimumGroupRadius = Math.min(minimumGroupRadius, lobeRadius);
            }
            if (groupDistance == NO_FIELD) {
                continue;
            }
            stormDistance = stormDistance == NO_FIELD
                    ? groupDistance
                    : auditedSmoothMinimum(
                            stormDistance,
                            groupDistance,
                            clamp(Math.min(previousGroupRadius, minimumGroupRadius) / 520.0D, 0.035D, 0.18D)
                    );
            previousGroupRadius = minimumGroupRadius;
        }
        if (!Double.isFinite(stormDistance)) {
            return 0.0D;
        }
        double density = clamp01(1.0D - stormDistance);
        if (density > 0.0D && density < 0.20D) {
            density = clamp01(density + 0.0006D * smoothstep(0.0D, 0.20D, density));
        }
        return density;
    }

    private static double auditedSmoothMinimum(double first, double second, double blendRadius) {
        double h = clamp01(0.5D + 0.5D * (second - first) / Math.max(blendRadius, 1.0E-4D));
        double smallerDensity = Math.min(1.0D - first, 1.0D - second);
        double supportFade = smoothstep(0.0D, blendRadius, smallerDensity);
        return lerp(h, second, first) - blendRadius * h * (1.0D - h) * supportFade;
    }

    /**
     * Local rain/body attachment plane derived only from BASE descriptors
     * whose support reaches the queried column. Distance-based, so a column
     * just outside a BASE lobe still resolves rather than falling through to
     * the global fallback.
     */
    static double localBaseUndersideAt(
            StormLobeDescriptor[] lobes,
            double worldX,
            double worldZ,
            double fallbackY
    ) {
        double weightedBase = 0.0D;
        double totalWeight = 0.0D;
        for (StormLobeDescriptor lobe : lobes) {
            if (lobe.role() != StormLobeDescriptor.Role.BASE) {
                continue;
            }
            double supportY = lerp(0.22D, lobe.baseY(), lobe.topY());
            double support = baseSupportWeight(lobe, worldX, supportY, worldZ);
            if (support <= 0.0D) {
                continue;
            }
            weightedBase += lobe.baseY() * support;
            totalWeight += support;
        }
        return totalWeight > 1.0E-8D ? weightedBase / totalWeight : fallbackY;
    }

    /** Interior BASE height used to sample the identical union for rain support. */
    static double localBaseSupportHeightAt(
            StormLobeDescriptor[] lobes,
            double worldX,
            double worldZ
    ) {
        double weightedSupportY = 0.0D;
        double totalWeight = 0.0D;
        for (StormLobeDescriptor lobe : lobes) {
            if (lobe.role() != StormLobeDescriptor.Role.BASE) {
                continue;
            }
            double supportY = lerp(0.22D, lobe.baseY(), lobe.topY());
            double support = baseSupportWeight(lobe, worldX, supportY, worldZ);
            if (support <= 0.0D) {
                continue;
            }
            weightedSupportY += supportY * support;
            totalWeight += support;
        }
        return totalWeight > 1.0E-8D ? weightedSupportY / totalWeight : Double.NaN;
    }

    private static double baseSupportWeight(
            StormLobeDescriptor lobe,
            double worldX,
            double worldY,
            double worldZ
    ) {
        double distance = signedDistanceAt(lobe, worldX, worldY, worldZ);
        double softness = edgeWidthBlocks(lobe);
        return clamp01(1.0D - smoothstep(-softness, softness, distance)) * envelopeStrength(lobe);
    }

    // -----------------------------------------------------------------
    // World-space blend and boundary widths
    // -----------------------------------------------------------------

    static double smallerRadius(StormLobeDescriptor lobe) {
        return Math.min(lobe.majorRadius(), lobe.minorRadius());
    }

    /** Envelope boundary half-width in blocks for this descriptor. */
    /**
     * T143. Horizontal distance from a lobe's stored centre beyond which no
     * column can receive any contribution from it, in world blocks.
     *
     * <p>This is the single source for the reach bound the shader hoists out of
     * the march loop, and every term is an upper bound on something the exact
     * evaluation can do:
     *
     * <ul>
     *   <li>the widest radius the role's height profile can reach anywhere in
     *       [0,1], including the anvil's 1.56 short-axis widening, and floored
     *       at 1.0 the way the exact evaluation floors its radii;</li>
     *   <li>x1.08 for the most the coherent morphology warp can subtract from
     *       the normalized radius (its three components each lie in [-1,1] and
     *       are weighted by 0.45, 0.20 and 0.35, then scaled by 0.08);</li>
     *   <li>the full shear magnitude, which covers every shear progress the
     *       role profiles produce in [0,1];</li>
     *   <li>the cap-rounding fillet the signed distance subtracts at the
     *       end;</li>
     *   <li>the lobe's own edge softness, which is how far past its surface the
     *       coverage envelope still fades;</li>
     *   <li>the maximum blend, which bounds how far below the individual lobe
     *       distances the ordered smooth union - the webbing - can sit.</li>
     * </ul>
     *
     * <p>A column further away than this cannot be inside the lobe, inside its
     * envelope fade, or inside webbing the union forms with it.
     */
    public static double horizontalReachBlocks(StormLobeDescriptor lobe) {
        double profileMax = maximumProfileRadius(lobe.role());
        double profileMin = minimumProfileRadius(lobe.role());
        double anvilWiden = lobe.role() == StormLobeDescriptor.Role.ANVIL ? 1.56D : 1.0D;
        double widest = Math.max(
                Math.max(1.0D, lobe.majorRadius() * profileMax),
                Math.max(1.0D, lobe.minorRadius() * anvilWiden * profileMax));
        double narrowest = Math.min(
                Math.max(1.0D, lobe.majorRadius() * profileMin),
                Math.max(1.0D, lobe.minorRadius() * profileMin));
        // The signed distance converts its normalized ellipse coordinate to
        // blocks by dividing out the gradient magnitude, and that conversion is
        // exact only for a circular section. Writing u = oriented / radii, the
        // wall term is (|u| - 1) * |u| / |u / radii|, and |u| / |u / radii| is
        // bounded below by the smaller radius - so an eccentric lobe reports a
        // distance that grows at only minor/major of the geometric rate, and a
        // bound of the form "widest radius plus a guard band" is not sound. A
        // 5:1 BASE lobe falsified exactly that form by 25.9 blocks.
        //
        // Solving (|o| / widest - 1.08) * narrowest > guard for |o| gives the
        // multiplicative form below. The 0.9 on the narrow radius covers the
        // most the domain warp can shrink the effective radius by.
        double guard = edgeWidthBlocks(lobe) + MAX_BLEND_BLOCKS + MIN_EDGE_BLOCKS;
        return widest * (1.08D + guard / (0.9D * narrowest))
                + Math.hypot(lobe.shearX(), lobe.shearZ());
    }

    /** Smallest value {@link #profileRadius} can take over a role's height. */
    static double minimumProfileRadius(StormLobeDescriptor.Role role) {
        return switch (role) {
            case BASE -> 0.58D;
            case CORE -> 0.56D;
            case TOWER -> 0.60D;
            case ANVIL -> 0.70D;
        };
    }

    /**
     * Largest value {@link #profileRadius} can take over the whole height of a
     * role. Each is the analytic maximum of that role's expression, rounded up.
     */
    static double maximumProfileRadius(StormLobeDescriptor.Role role) {
        return switch (role) {
            case BASE -> 1.06D;
            case CORE -> 1.02D;
            case TOWER -> 1.43D;
            case ANVIL -> 2.18D;
        };
    }

    static double edgeWidthBlocks(StormLobeDescriptor lobe) {
        double normalized = switch (lobe.role()) {
            // The live anvil strengths are deliberately below the core.
            // Give that wide canopy a real spatial boundary rather than
            // letting the strength-weighted iso contour discard its rim.
            case ANVIL -> Math.max(0.12D, lobe.edgeSoftness() * 1.65D);
            // A slightly wider physical BASE boundary prevents a hard lateral
            // top-cap handoff outside the smaller core envelope.
            case BASE -> Math.max(0.06D, lobe.edgeSoftness() * 0.66D);
            default -> Math.max(0.06D, lobe.edgeSoftness() * 0.62D);
        };
        double horizontal = normalized * smallerRadius(lobe) * EDGE_SOFTNESS_BLOCKS;
        // T098: envelopeFromDistance fades over +/- this width, so a lobe whose
        // softness exceeds its own half-height has a boundary wider than it is
        // tall - the fade consumes the body and spills past the far cap. The
        // width above is derived from smallerRadius, a horizontal extent, but
        // applied isotropically, so a role much wider than it is tall is
        // exposed. Measured on the severe fixture, softness/halfHeight is 0.73
        // BASE, 0.57 CORE, 0.27 TOWER and 2.47-2.58 for all four ANVIL members:
        // the anvil's ~272-block boundary against a 105-block half-height hung
        // ~150 blocks of very-low-coverage haze below its own baseY, which the
        // carrier shredded into the 50-84 disconnected fragments that made the
        // storm read as a detached anvil over a broken column. Bounding by the
        // lobe's vertical extent is dimensionally correct for every role and,
        // at this fraction, binds only the degenerate case.
        double halfHeight = Math.max(roleTopY(lobe) - roleBaseY(lobe), 1.0D) * 0.5D;
        return Math.max(MIN_EDGE_BLOCKS,
                Math.min(horizontal, halfHeight * VERTICAL_EDGE_BOUND_FRACTION));
    }

    /** Descriptor authority over how much coverage this lobe contributes. */
    static double envelopeStrength(StormLobeDescriptor lobe) {
        return clamp01(lobe.density() * lobe.detailWeight());
    }

    static double lobeBlendRadius(double firstRadius, double secondRadius) {
        double smaller = firstRadius <= 0.0D
                ? secondRadius
                : Math.min(firstRadius, secondRadius);
        return clamp(smaller * LOBE_BLEND_FRACTION, MIN_BLEND_BLOCKS, MAX_BLEND_BLOCKS);
    }

    /**
     * CORE and TOWER are a single developing convective column, and the
     * ANVIL descriptors are one coherent canopy rather than decorative
     * lobes. Their wider, role-specific unions soften those handoffs; BASE
     * pairings retain the audited blend.
     */
    static double lobeBlendRadius(StormLobeDescriptor first, StormLobeDescriptor second) {
        if (first == null) {
            return lobeBlendRadius(0.0D, smallerRadius(second));
        }
        double fraction = isConvectiveColumnPair(first, second) ? 0.60D : LOBE_BLEND_FRACTION;
        return clamp(
                Math.min(smallerRadius(first), smallerRadius(second)) * fraction,
                MIN_BLEND_BLOCKS,
                MAX_BLEND_BLOCKS
        );
    }

    private static boolean isConvectiveColumnPair(StormLobeDescriptor first, StormLobeDescriptor second) {
        boolean firstColumn = first.role() == StormLobeDescriptor.Role.CORE
                || first.role() == StormLobeDescriptor.Role.TOWER;
        boolean secondColumn = second.role() == StormLobeDescriptor.Role.CORE
                || second.role() == StormLobeDescriptor.Role.TOWER;
        return (firstColumn && secondColumn)
                || (first.role() == StormLobeDescriptor.Role.ANVIL
                    && second.role() == StormLobeDescriptor.Role.ANVIL);
    }

    static double groupBlendRadius(double firstRadius, double secondRadius) {
        double smaller = firstRadius <= 0.0D
                ? secondRadius
                : Math.min(firstRadius, secondRadius);
        return clamp(smaller * GROUP_BLEND_FRACTION, MIN_BLEND_BLOCKS, MAX_BLEND_BLOCKS);
    }

    /**
     * Polynomial smooth minimum on world-space distances. Symmetric,
     * order-independent within numerical tolerance, and continuous in its
     * first derivative.
     */
    /**
     * The interpolation factor the smooth minimum uses. Material properties -
     * envelope strength and boundary softness - are blended by the same factor
     * so they stay continuous wherever the distance field is continuous. A
     * hard "nearest lobe wins" selection here would reintroduce exactly the
     * winner-switch seams the descriptor union exists to remove.
     */
    static double blendFactor(double first, double second, double blendRadius) {
        double radius = Math.max(blendRadius, 1.0E-4D);
        return clamp01(0.5D + 0.5D * (second - first) / radius);
    }

    static double smoothMinimum(double first, double second, double blendRadius) {
        double radius = Math.max(blendRadius, 1.0E-4D);
        double h = clamp01(0.5D + 0.5D * (second - first) / radius);
        return lerp(h, second, first) - radius * h * (1.0D - h);
    }

    private static boolean hasEarlierGroup(
            List<StormLobeDescriptor> lobes,
            int index,
            StormLobeDescriptor lobe
    ) {
        for (int previous = 0; previous < index; previous++) {
            if (lobes.get(previous).groupId().equals(lobe.groupId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEarlierGroup(
            StormLobeDescriptor[] lobes,
            int index,
            StormLobeDescriptor lobe
    ) {
        for (int previous = 0; previous < index; previous++) {
            if (lobes[previous].groupId().equals(lobe.groupId())) {
                return true;
            }
        }
        return false;
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double normalized = clamp01((value - edge0) / Math.max(1.0E-8D, edge1 - edge0));
        return normalized * normalized * (3.0D - 2.0D * normalized);
    }

    static double profileRadius(StormLobeDescriptor.Role role, double vertical01) {
        return switch (role) {
            // Narrow the immediate underside slightly while retaining a broad
            // lower-middle shelf. This gives the primary base field room to
            // form billows instead of exposing one smooth rounded balloon.
            case BASE -> lerp(vertical01, 0.84D, 0.58D)
                    + 0.22D * Math.pow(Math.sin(Math.PI * vertical01), 0.70D);
            case CORE -> lerp(vertical01, 0.84D, 0.56D)
                    + 0.18D * Math.pow(Math.sin(Math.PI * vertical01), 0.65D);
            // A materially broad root remains inside the core through its
            // upper half, then tapers continuously rather than pinching into
            // a separate upper mass.
            case TOWER -> lerp(vertical01, 1.25D, 0.60D)
                    + 0.18D * Math.pow(Math.sin(Math.PI * vertical01), 0.65D);
            // A mature anvil begins while the tower is still present and its
            // wide profile retains the actual descriptor-strength differences.
            case ANVIL -> lerp(smoothstep(0.0D, 0.62D, vertical01), 0.70D, 2.10D)
                    + 0.08D * Math.pow(Math.sin(Math.PI * vertical01), 0.55D)
                    - 0.10D * smoothstep(0.88D, 1.0D, vertical01);
        };
    }

    /**
     * Role-envelope handoff bounds in world blocks. The descriptor endpoints
     * remain authoritative anchors, while these bounded extensions make the
     * semantically continuous CORE→TOWER→ANVIL transitions occupy an actual
     * vertical interval rather than touching only at caps.
     */
    static double roleBaseY(StormLobeDescriptor lobe) {
        return switch (lobe.role()) {
            case TOWER -> lobe.baseY() - 28.0D;
            case ANVIL -> lobe.baseY() - 12.0D;
            default -> lobe.baseY();
        };
    }

    static double roleTopY(StormLobeDescriptor lobe) {
        return switch (lobe.role()) {
            case CORE -> lobe.topY() + 32.0D;
            // Keep the lateral canopy substantial through the measured upper
            // role transition instead of collapsing at a thin cap.
            case ANVIL -> lobe.topY() + 16.0D;
            default -> lobe.topY();
        };
    }

    static double verticalShape(StormLobeDescriptor.Role role, double vertical01) {
        return switch (role) {
            case BASE -> smoothstep(0.0D, 0.08D, vertical01)
                    * (1.0D - smoothstep(0.48D, 1.0D, vertical01));
            case CORE -> smoothstep(0.0D, 0.24D, vertical01)
                    * (1.0D - smoothstep(0.62D, 1.0D, vertical01));
            case TOWER -> smoothstep(0.0D, 0.28D, vertical01)
                    * (1.0D - smoothstep(0.72D, 1.0D, vertical01));
            case ANVIL -> smoothstep(0.0D, 0.35D, vertical01)
                    * (1.0D - smoothstep(0.76D, 1.0D, vertical01));
        };
    }

    static double shearProgress(StormLobeDescriptor.Role role, double vertical01) {
        return switch (role) {
            case BASE -> vertical01 * 0.12D;
            case CORE -> smoothstep(0.0D, 1.0D, vertical01) * 0.35D;
            case TOWER -> Math.pow(vertical01, 1.6D);
            case ANVIL -> smoothstep(0.0D, 0.65D, vertical01);
        };
    }

    private static double lerp(double amount, double start, double end) {
        return start + (end - start) * clamp01(amount);
    }

    static double coherentMorphologyWarp(
            StormLobeDescriptor lobe,
            double worldX,
            double worldY,
            double worldZ
    ) {
        double groupOffset = Math.max(0, lobe.groupSlot()) * 997.0D;
        double x = worldX * 2.3D + groupOffset;
        double y = worldY * 2.3D + groupOffset * 0.61D;
        double z = worldZ * 2.3D - groupOffset * 0.73D;
        double warpX = Math.sin(x * 0.00173D + y * 0.00091D - z * 0.00127D + 1.7D);
        double warpY = Math.sin(-x * 0.00111D + y * 0.00149D + z * 0.00083D - 2.3D);
        double warpZ = Math.sin(x * 0.00079D - y * 0.00131D + z * 0.00191D + 4.1D);
        return warpX * 0.45D + warpY * 0.20D + warpZ * 0.35D;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0D, 1.0D);
    }
}
