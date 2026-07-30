package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.clouds.cell.CloudCell;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSourceKind;
import net.Gabou.projectatmosphere.clouds.field.CloudMorphologyMembership;
import net.Gabou.projectatmosphere.clouds.field.CloudletLayout;
import net.Gabou.projectatmosphere.clouds.type.CloudMaterialProfile;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyMemberTier;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeDefinition;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.clouds.type.CloudVisualProfile;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Minimal render-side view of one cloud cell for weather-map splatting and
 * funnel rendering. Producible from real synced CloudCells or, as a migration
 * fallback, from legacy CloudFieldSnapshots.
 */
public record VolumetricRenderCell(
        double x,
        double z,
        float baseY,
        float topY,
        float radiusMajor,
        float radiusMinor,
        float orientationRadians,
        float density,
        float edgeSoftness,
        float energy,
        int cloudProfile,
        int morphologyFamily,
        float verticalDevelopment,
        float humidity,
        float materialDarkness,
        float anvilStrength,
        float precipitationIntensity,
        float lifecycleStage,
        float seed01,
        float funnelStrength,
        float funnelGroundY,
        float rotation,
        boolean macroCarrier,
        CloudMorphologyMemberTier puffTier,
        EnvelopeRole envelopeRole
) {
    public static VolumetricRenderCell fromCell(CloudCell cell) {
        return new VolumetricRenderCell(
                cell.x(),
                cell.z(),
                cell.baseY(),
                cell.topY(),
                cell.radiusMajor(),
                cell.radiusMinor(),
                cell.orientationRadians(),
                cell.density(),
                cell.edgeSoftness(),
                cell.energy(),
                profileFor(cell.classification()),
                morphologyFor(cell.classification()).ordinal(),
                Mth.clamp(cell.verticalExtentRatio(), 0.0F, 1.0F),
                cell.density(),
                materialDarknessFor(cell.classification()),
                cell.classification() == net.Gabou.projectatmosphere.clouds.cell.CloudCellClassification.CUMULONIMBUS
                        ? cell.energy() : 0.0F,
                cell.classification() == net.Gabou.projectatmosphere.clouds.cell.CloudCellClassification.CUMULONIMBUS
                        ? cell.energy() : 0.0F,
                lifecycleFor(cell.phase()),
                (cell.seed() & 0xFFFFL) / 65535.0F,
                cell.funnelStrength(),
                cell.funnelGroundY(),
                cell.rotation(),
                true,
                CloudMorphologyMemberTier.UNKNOWN,
                EnvelopeRole.MACRO
        );
    }

    public static VolumetricRenderCell fromFieldSnapshot(CloudFieldSnapshot snapshot) {
        return fromFieldSnapshot(snapshot, 0);
    }

    /**
     * Builds the field-scale fallback envelope. Once at least three detail
     * cloudlets actually survived the current frame budget, the macro becomes
     * an invisible carrier instead of a second giant visible ellipse. Using
     * the accepted count (not the requested LOD count) keeps budget pressure
     * from making the whole cloud disappear.
     */
    public static VolumetricRenderCell fromFieldSnapshot(
            CloudFieldSnapshot snapshot,
            int acceptedDetailCount
    ) {
        if (snapshot.sourceKind() == CloudFieldSourceKind.PA_CLUSTER) {
            return fromCanonicalClusterSnapshot(snapshot);
        }
        CloudMorphologyFamily morphology = snapshot.morphologyFamily();
        float effectiveDensity = snapshot.effectiveDensity();
        float effectiveCoverage = snapshot.effectiveCoverage();
        float radiusScale = switch (morphology) {
            case PUFF -> 0.58F;
            case TOWER -> 0.60F;
            case STORM_ANVIL -> 0.96F;
            case SHEET -> 0.92F;
            case CELLULAR_SHEET -> 0.78F;
            case FILAMENT -> 0.88F;
            case SPIRAL_STORM -> 1.00F;
            case DEBUG -> 0.58F;
        };
        float aspect = switch (morphology) {
            case PUFF -> 0.84F;
            case TOWER -> 0.72F;
            case STORM_ANVIL -> 0.82F;
            case SHEET -> 0.90F;
            case CELLULAR_SHEET -> 0.84F;
            case FILAMENT -> 0.14F;
            case SPIRAL_STORM -> 0.76F;
            case DEBUG -> 0.82F;
        };
        boolean identifiableDetail = acceptedDetailCount >= 3
                && snapshot.hydrationProgress() > 0.02F;
        float macroDensityScale = switch (morphology) {
            case PUFF -> identifiableDetail ? 0.36F : 0.58F;
            case TOWER -> identifiableDetail ? 0.48F : 0.70F;
            case STORM_ANVIL -> identifiableDetail ? 0.24F : 0.80F;
            case SHEET -> "nimbostratus".equals(snapshot.cloudTypeId())
                    ? (identifiableDetail ? 0.40F : 0.72F)
                    : (identifiableDetail ? 0.24F : 0.52F);
            case CELLULAR_SHEET -> identifiableDetail ? 0.32F : 0.60F;
            case FILAMENT -> identifiableDetail ? 0.72F : 0.84F;
            case SPIRAL_STORM -> identifiableDetail ? 0.26F : 0.86F;
            case DEBUG -> identifiableDetail ? 0.38F : 0.56F;
        };
        float edgeSoftness = switch (morphology) {
            case PUFF -> 0.44F;
            case TOWER -> 0.34F;
            case STORM_ANVIL -> 0.30F;
            case SHEET -> 0.56F;
            case CELLULAR_SHEET -> 0.44F;
            case FILAMENT -> 0.68F;
            case SPIRAL_STORM -> 0.28F;
            case DEBUG -> 0.44F;
        };
        float orientation = orientationFor(snapshot, morphology, (snapshot.seed() % 628L) * 0.01F);
        float radiusMajor = Math.max(4.0F, snapshot.radius() * radiusScale);
        return new VolumetricRenderCell(
                snapshot.center().x(),
                snapshot.center().z(),
                snapshot.baseY(),
                snapshot.topY(),
                radiusMajor,
                Math.max(3.0F, radiusMajor * aspect),
                orientation,
                Math.min(1.0F, effectiveDensity * macroDensityScale
                        * (0.64F + 0.36F * effectiveCoverage)
                        * Math.max(0.4F, snapshot.hydrationProgress())),
                edgeSoftness,
                snapshot.stormPotential(),
                profileFor(snapshot),
                morphology.ordinal(),
                snapshot.verticalDevelopment(),
                snapshot.humidityInfluence(),
                materialDarknessFor(snapshot.cloudTypeId()),
                snapshot.anvilStrength(),
                snapshot.precipitationIntensity(),
                snapshot.visualLifecycleStage(),
                (snapshot.seed() & 0xFFFFL) / 65535.0F,
                0.0F,
                0.0F,
                0.0F,
                true,
                CloudMorphologyMemberTier.UNKNOWN,
                identifiableDetail ? EnvelopeRole.CARRIER_ONLY : EnvelopeRole.MACRO
        );
    }

    /**
     * Projects one authoritative PA simulation cluster directly into one GPU
     * footprint. No second cloudlet hierarchy or LOD-dependent topology is
     * introduced here: neighbouring clusters are already the stable lobes of
     * the meteorological cloud group.
     */
    private static VolumetricRenderCell fromCanonicalClusterSnapshot(CloudFieldSnapshot snapshot) {
        CloudMorphologyFamily morphology = snapshot.morphologyFamily();
        CloudMorphologyMembership membership = snapshot.morphologyMembership();
        CloudMorphologyMemberTier puffTier = morphology == CloudMorphologyFamily.PUFF
                && membership.layoutVersion() > 0
                ? membership.memberTier()
                : CloudMorphologyMemberTier.UNKNOWN;
        CloudMorphologyMembership.Stage stage =
                membership.stageFor(morphology);
        if (morphology == CloudMorphologyFamily.PUFF
                && !VolumetricCloudDebugConfig.structuredPuffEnabled()) {
            stage = CloudMorphologyMembership.Stage.MACRO;
        }
        EnvelopeRole canonicalRole = switch (stage) {
            case BASE -> EnvelopeRole.BASE;
            case CORE -> EnvelopeRole.CORE;
            case TOWER -> EnvelopeRole.TOWER;
            case CROWN -> EnvelopeRole.CROWN;
            case MACRO -> EnvelopeRole.MACRO;
        };
        float radiusScale = switch (morphology) {
            case PUFF -> 0.96F;
            case TOWER -> 0.92F;
            case STORM_ANVIL -> 0.98F;
            case SHEET -> 1.06F;
            case CELLULAR_SHEET -> 0.96F;
            case FILAMENT -> 1.04F;
            case SPIRAL_STORM -> 1.00F;
            case DEBUG -> 0.96F;
        };
        float aspect = switch (morphology) {
            case PUFF -> 0.90F;
            case TOWER -> 0.86F;
            case STORM_ANVIL -> 0.82F;
            case SHEET -> 0.70F;
            case CELLULAR_SHEET -> 0.82F;
            case FILAMENT -> 0.20F;
            case SPIRAL_STORM -> 0.80F;
            case DEBUG -> 0.88F;
        };
        float densityScale = switch (morphology) {
            case FILAMENT -> 0.74F;
            case SHEET -> 0.88F;
            case CELLULAR_SHEET -> 0.86F;
            case STORM_ANVIL, SPIRAL_STORM -> 0.98F;
            default -> 0.94F;
        };
        float edgeSoftness = switch (morphology) {
            case PUFF -> 0.38F;
            case TOWER -> 0.30F;
            case STORM_ANVIL -> 0.28F;
            case SHEET -> 0.54F;
            case CELLULAR_SHEET -> 0.42F;
            case FILAMENT -> 0.66F;
            case SPIRAL_STORM -> 0.26F;
            case DEBUG -> 0.40F;
        };
        float radiusMajor = Math.max(4.0F, snapshot.radius() * radiusScale);
        float effectiveDensity = Math.min(1.0F,
                snapshot.effectiveDensity()
                        * densityScale
                        * (0.78F + snapshot.effectiveCoverage() * 0.22F));
        return new VolumetricRenderCell(
                snapshot.center().x(),
                snapshot.center().z(),
                snapshot.baseY(),
                snapshot.topY(),
                radiusMajor,
                Math.max(3.0F, radiusMajor * aspect),
                orientationFor(snapshot, morphology, (snapshot.seed() % 628L) * 0.01F),
                effectiveDensity,
                edgeSoftness,
                snapshot.stormPotential(),
                profileFor(snapshot),
                morphology.ordinal(),
                snapshot.verticalDevelopment(),
                snapshot.humidityInfluence(),
                materialDarknessFor(snapshot.cloudTypeId()),
                snapshot.anvilStrength(),
                snapshot.precipitationIntensity(),
                snapshot.visualLifecycleStage(),
                (snapshot.seed() & 0xFFFFL) / 65535.0F,
                0.0F,
                0.0F,
                morphology == CloudMorphologyFamily.SPIRAL_STORM
                        ? snapshot.stormPotential() * 0.04F
                        : 0.0F,
                false,
                puffTier,
                canonicalRole
        );
    }

    public static VolumetricRenderCell fromFieldCloudlet(
            CloudFieldSnapshot snapshot,
            CloudletLayout.Cloudlet cloudlet
    ) {
        Vec3 center = cloudlet.worldCenter(snapshot);
        CloudMorphologyFamily morphology = snapshot.morphologyFamily();
        float radiusExpansion = switch (morphology) {
            case PUFF -> 1.10F;
            case TOWER -> 1.08F;
            case STORM_ANVIL -> 1.10F;
            case SHEET -> 1.12F;
            case CELLULAR_SHEET -> 1.16F;
            case FILAMENT -> 1.22F;
            case SPIRAL_STORM -> 1.12F;
            case DEBUG -> 1.08F;
        };
        float radius = Math.max(5.0F, cloudlet.horizontalRadius() * radiusExpansion);
        float fieldSpan = Math.max(2.0F, snapshot.topY() - snapshot.baseY());
        float cellSpan = Math.max(2.0F, fieldSpan * cloudlet.verticalScale());
        float baseY = (float) center.y() - cellSpan * 0.5F;
        float topY = baseY + cellSpan;
        if (baseY < snapshot.baseY()) {
            topY += snapshot.baseY() - baseY;
            baseY = snapshot.baseY();
        }
        if (topY > snapshot.topY()) {
            baseY -= topY - snapshot.topY();
            topY = snapshot.topY();
        }
        baseY = Math.max(snapshot.baseY(), baseY);
        topY = Math.max(baseY + 1.0F, Math.min(snapshot.topY(), topY));

        long seed = cloudlet.id().mixedSeed(snapshot.seed());
        float familyDensityScale = switch (morphology) {
            case PUFF -> 0.92F;
            case TOWER -> 1.02F;
            case STORM_ANVIL -> 1.08F;
            case SHEET -> "nimbostratus".equals(snapshot.cloudTypeId()) ? 0.94F : 0.68F;
            case CELLULAR_SHEET -> 0.82F;
            case FILAMENT -> 1.00F;
            case SPIRAL_STORM -> 1.12F;
            case DEBUG -> 0.90F;
        };
        float effectiveDensity = Math.min(1.0F,
                snapshot.effectiveDensity() * cloudlet.densityScale()
                        * (0.12F + 0.88F * cloudlet.coverageWeight())
                        * familyDensityScale);
        float edgeSoftness = switch (cloudlet.role()) {
            case CORE -> morphology == CloudMorphologyFamily.SPIRAL_STORM
                    || morphology == CloudMorphologyFamily.STORM_ANVIL ? 0.44F : 0.26F;
            case LOBE -> 0.38F;
            case BASE -> morphology == CloudMorphologyFamily.SPIRAL_STORM
                    || morphology == CloudMorphologyFamily.STORM_ANVIL ? 0.40F : 0.30F;
            case TOWER -> morphology == CloudMorphologyFamily.SPIRAL_STORM
                    || morphology == CloudMorphologyFamily.STORM_ANVIL ? 0.38F : 0.24F;
            case ANVIL -> 0.54F;
            case SHEET_TILE -> "nimbostratus".equals(snapshot.cloudTypeId()) ? 0.46F : 0.56F;
            case FILAMENT -> 0.68F;
        };
        return new VolumetricRenderCell(
                center.x(),
                center.z(),
                baseY,
                topY,
                radius,
                Math.max(2.0F, radius * cloudlet.horizontalAspect()),
                cloudlet.orientationRadians(),
                effectiveDensity,
                edgeSoftness,
                snapshot.stormPotential(),
                profileFor(snapshot),
                snapshot.morphologyFamily().ordinal(),
                snapshot.verticalDevelopment(),
                snapshot.humidityInfluence(),
                materialDarknessFor(snapshot.cloudTypeId()),
                snapshot.anvilStrength(),
                snapshot.precipitationIntensity(),
                snapshot.visualLifecycleStage(),
                (seed & 0xFFFFL) / 65535.0F,
                0.0F,
                0.0F,
                morphology == CloudMorphologyFamily.SPIRAL_STORM
                        ? snapshot.stormPotential() * 0.04F
                        : 0.0F,
                false,
                puffTierFromCloudletRole(cloudlet.role()),
                EnvelopeRole.fromCloudletRole(cloudlet.role())
        );
    }

    private static CloudMorphologyMemberTier puffTierFromCloudletRole(
            CloudletLayout.CloudletRole role
    ) {
        if (role == null) {
            return CloudMorphologyMemberTier.UNKNOWN;
        }
        return switch (role) {
            case BASE -> CloudMorphologyMemberTier.BASE;
            case CORE, LOBE, SHEET_TILE, FILAMENT -> CloudMorphologyMemberTier.MIDDLE;
            case TOWER, ANVIL -> CloudMorphologyMemberTier.CROWN;
        };
    }

    /**
     * Compact role retained until the cached weather-map pass.  Coverage can
     * still use a smooth union, while severe base/top construction knows which
     * stamps may connect the low base to the high anvil.
     */
    public enum EnvelopeRole {
        DETAIL(0),
        MACRO(1),
        BASE(2),
        CORE(3),
        TOWER(4),
        ANVIL(5),
        CARRIER_ONLY(6),
        CROWN(7);

        private final int gpuId;

        EnvelopeRole(int gpuId) {
            this.gpuId = gpuId;
        }

        public int gpuId() {
            return this.gpuId;
        }

        private static EnvelopeRole fromCloudletRole(CloudletLayout.CloudletRole role) {
            return switch (role) {
                case BASE -> BASE;
                case CORE -> CORE;
                case TOWER -> TOWER;
                case ANVIL -> ANVIL;
                case LOBE, SHEET_TILE, FILAMENT -> DETAIL;
            };
        }
    }

    private static float orientationFor(
            CloudFieldSnapshot snapshot,
            CloudMorphologyFamily morphology,
            float fallback
    ) {
        if (morphology != CloudMorphologyFamily.FILAMENT
                && morphology != CloudMorphologyFamily.STORM_ANVIL
                && morphology != CloudMorphologyFamily.SPIRAL_STORM
                && morphology != CloudMorphologyFamily.SHEET) {
            return fallback;
        }
        Vec3 wind = snapshot.windVector();
        if (wind.horizontalDistanceSqr() <= 1.0E-8D) {
            return fallback;
        }
        return (float) Math.atan2(wind.z(), wind.x());
    }

    private static int profileFor(String cloudTypeId, CloudMorphologyFamily morphology) {
        String id = cloudTypeId == null ? "" : cloudTypeId.toLowerCase(Locale.ROOT);
        if (id.contains("supercell") || id.contains("hurricane")
                || morphology == CloudMorphologyFamily.SPIRAL_STORM) {
            return 7;
        }
        if (id.contains("cumulonimbus") || morphology == CloudMorphologyFamily.STORM_ANVIL) {
            return 4;
        }
        if (id.contains("nimbostratus")) {
            return 5;
        }
        if (id.contains("stratocumulus") || morphology == CloudMorphologyFamily.CELLULAR_SHEET) {
            return 2;
        }
        if (id.contains("stratus") || morphology == CloudMorphologyFamily.SHEET) {
            return 1;
        }
        if (id.contains("cirrus") || morphology == CloudMorphologyFamily.FILAMENT) {
            return 6;
        }
        if (id.contains("cumulus") || morphology == CloudMorphologyFamily.PUFF
                || morphology == CloudMorphologyFamily.TOWER) {
            return 3;
        }
        return 0;
    }

    /** Returns the stable shader profile encoded for one canonical field snapshot. */
    public static int profileFor(CloudFieldSnapshot snapshot) {
        return profileFor(snapshot.cloudTypeId(), snapshot.morphologyFamily());
    }

    private static float materialDarknessFor(String cloudTypeId) {
        CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault(cloudTypeId);
        CloudMaterialProfile material = definition.getMaterialProfile();
        CloudVisualProfile visual = definition.getVisualProfile();
        return Mth.clamp(Math.max(
                visual.getBaseDarkness(),
                Math.max(
                        material.getDarkness(),
                        Math.max(material.getUndersideDarkness(), material.getStormCoreDarkening())
                )
        ), 0.0F, 1.0F);
    }

    private static float materialDarknessFor(
            net.Gabou.projectatmosphere.clouds.cell.CloudCellClassification classification
    ) {
        return switch (classification) {
            case STRATUS -> 0.38F;
            case STRATOCUMULUS -> 0.30F;
            case CUMULUS_HUMILIS, CUMULUS_MEDIOCRIS -> 0.20F;
            case CUMULUS_CONGESTUS -> 0.36F;
            case CUMULONIMBUS -> 0.78F;
            case CIRRIFORM -> 0.08F;
            case UNCLASSIFIED -> 0.12F;
        };
    }

    private static int profileFor(net.Gabou.projectatmosphere.clouds.cell.CloudCellClassification classification) {
        return switch (classification) {
            case STRATUS -> 1;
            case STRATOCUMULUS -> 2;
            case CUMULUS_HUMILIS, CUMULUS_MEDIOCRIS, CUMULUS_CONGESTUS -> 3;
            case CUMULONIMBUS -> 4;
            case CIRRIFORM -> 6;
            case UNCLASSIFIED -> 0;
        };
    }

    private static CloudMorphologyFamily morphologyFor(
            net.Gabou.projectatmosphere.clouds.cell.CloudCellClassification classification
    ) {
        return switch (classification) {
            case STRATUS -> CloudMorphologyFamily.SHEET;
            case STRATOCUMULUS -> CloudMorphologyFamily.CELLULAR_SHEET;
            case CUMULUS_CONGESTUS -> CloudMorphologyFamily.TOWER;
            case CUMULONIMBUS -> CloudMorphologyFamily.STORM_ANVIL;
            case CIRRIFORM -> CloudMorphologyFamily.FILAMENT;
            case CUMULUS_HUMILIS, CUMULUS_MEDIOCRIS, UNCLASSIFIED -> CloudMorphologyFamily.PUFF;
        };
    }

    private static float lifecycleFor(
            net.Gabou.projectatmosphere.clouds.cell.CloudCellLifecyclePhase phase
    ) {
        return switch (phase) {
            case FORMING -> 0.20F;
            case MATURE -> 0.50F;
            case DISSIPATING -> 0.85F;
        };
    }
}
