package net.Gabou.projectatmosphere.clouds.field;

import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lightweight validation and self-check helpers for the isolated CloudField
 * base layer. These are safe to call from future debug commands or tests.
 */
public final class CloudFieldValidation {
    private static final double EPSILON = 0.000001D;

    private CloudFieldValidation() {
    }

    public static List<String> validate(CloudField field) {
        List<String> issues = new ArrayList<>();
        if (field == null) {
            issues.add("field is null");
            return issues;
        }
        if (field.radius() <= 0.0F) {
            issues.add("field radius must be positive");
        }
        if (field.topY() <= field.baseY()) {
            issues.add("field topY must be above baseY");
        }
        if (field.cloudletCount() < 0) {
            issues.add("cloudlet count must be non-negative");
        }
        return List.copyOf(issues);
    }

    public static List<String> validate(CloudFieldSnapshot snapshot) {
        List<String> issues = new ArrayList<>();
        if (snapshot == null) {
            issues.add("snapshot is null");
            return issues;
        }
        if (snapshot.topY() <= snapshot.baseY()) {
            issues.add("snapshot topY must be above baseY");
        }
        if (snapshot.activeCloudletCount() > snapshot.targetCloudletCount()) {
            issues.add("active cloudlet count exceeds target count");
        }
        if (snapshot.dynamicCloudletCount() > 0 && snapshot.hydrationProgress() <= 0.0F) {
            issues.add("dynamic cloudlets require positive hydration");
        }
        return List.copyOf(issues);
    }

    public static List<String> validate(CloudFieldRuntimeState runtimeState) {
        List<String> issues = new ArrayList<>();
        if (runtimeState == null) {
            issues.add("runtime state is null");
            return issues;
        }
        if (runtimeState.currentCloudletCount() > 0
                && runtimeState.hydrationState() == CloudFieldHydrationState.NOT_HYDRATED) {
            issues.add("cloudlets cannot be active while not hydrated");
        }
        return List.copyOf(issues);
    }

    public static List<String> runSelfCheck() {
        List<String> issues = new ArrayList<>();
        CloudField field = sampleField(Vec3.ZERO);
        CloudFieldSnapshotFactory snapshotFactory = new CloudFieldSnapshotFactory();
        CloudFieldHydrationController hydrationController = CloudFieldHydrationController.defaultController();
        CloudFieldRuntimeState runtime = new CloudFieldRuntimeState(
                field.fieldId(),
                CloudLodBand.DYNAMIC,
                CloudLodBand.TRANSITION,
                CloudFieldHydrationState.HYDRATED,
                1.0F,
                100L,
                field.cloudletCount(),
                field.center()
        );
        CloudFieldTickContext context = CloudFieldTickContext.of(new Vec3(0.0D, 128.0D, 0.0D), 100L, 1.0F);
        CloudFieldSnapshot snapshot = snapshotFactory.create(field, runtime, context);

        CloudletLayout.Cloudlet id3A = CloudletLayout.generate(snapshot, CloudletId.of(3));
        CloudletLayout.Cloudlet id3B = CloudletLayout.generate(snapshot, CloudletId.of(3));
        CloudletLayout.Cloudlet id4 = CloudletLayout.generate(snapshot, CloudletId.of(4));
        if (!sameLayout(id3A, id3B)) {
            issues.add("same seed and cloudlet id did not produce stable layout");
        }
        if (sameLayout(id3A, id4)) {
            issues.add("different cloudlet ids produced identical layout");
        }
        validateMorphologyLayouts(issues, snapshotFactory, context);

        CloudField movedField = field.withCenter(
                new Vec3(25.0D, field.center().y(), -10.0D),
                field.ageTicks() + 40L
        );
        CloudFieldSnapshot movedSnapshot = snapshotFactory.create(movedField, runtime, context);
        CloudletLayout.Cloudlet movedId3 = CloudletLayout.generate(movedSnapshot, CloudletId.of(3));
        if (!sameVec(id3A.localOffset(), movedId3.localOffset())) {
            issues.add("moving the field changed stable cloudlet identity layout");
        }

        CloudFieldRuntimeState farState = new CloudFieldRuntimeState(
                field.fieldId(),
                CloudLodBand.FAR_PROCEDURAL,
                CloudLodBand.FAR_PROCEDURAL,
                CloudFieldHydrationState.NOT_HYDRATED,
                0.0F,
                100L,
                0,
                field.center()
        );
        CloudFieldRuntimeState hydrating = hydrationController.update(
                field,
                farState,
                CloudLodBand.TRANSITION,
                101L,
                10.0F,
                field.center()
        );
        if (hydrating.hydrationProgress() <= farState.hydrationProgress()) {
            issues.add("far to transition did not increase hydration");
        }
        CloudFieldRuntimeState dynamic = hydrationController.update(
                field,
                hydrating,
                CloudLodBand.DYNAMIC,
                102L,
                10.0F,
                field.center()
        );
        if (dynamic.hydrationProgress() <= hydrating.hydrationProgress()) {
            issues.add("transition to dynamic did not increase hydration");
        }
        CloudFieldRuntimeState dehydrating = hydrationController.update(
                field,
                dynamic,
                CloudLodBand.FAR_PROCEDURAL,
                103L,
                10.0F,
                field.center()
        );
        if (dehydrating.hydrationProgress() >= dynamic.hydrationProgress()) {
            issues.add("dynamic to far did not decrease hydration");
        }

        Vec3 centerBeforeSnapshot = field.center();
        snapshotFactory.create(field, runtime, context);
        if (!sameVec(centerBeforeSnapshot, field.center())) {
            issues.add("snapshot creation mutated field center");
        }

        issues.addAll(validate(field));
        issues.addAll(validate(snapshot));
        issues.addAll(validate(runtime));
        return List.copyOf(issues);
    }

    private static CloudField sampleField(Vec3 center) {
        return sampleField(center, "cumulus_humilis", CloudMorphologyFamily.PUFF);
    }

    private static CloudField sampleField(
            Vec3 center,
            String cloudTypeId,
            CloudMorphologyFamily morphology
    ) {
        boolean tower = morphology == CloudMorphologyFamily.TOWER;
        boolean storm = morphology == CloudMorphologyFamily.STORM_ANVIL
                || morphology == CloudMorphologyFamily.SPIRAL_STORM;
        boolean sheet = morphology == CloudMorphologyFamily.SHEET
                || morphology == CloudMorphologyFamily.CELLULAR_SHEET;
        boolean filament = morphology == CloudMorphologyFamily.FILAMENT;
        float baseY = filament ? 280.0F : (sheet ? 150.0F : 120.0F);
        float topY = filament ? 310.0F : (sheet ? 188.0F : (storm ? 360.0F : (tower ? 270.0F : 220.0F)));
        return new CloudField(
                new UUID(0L, 0x143L + morphology.ordinal()),
                987654321L,
                "minecraft:overworld",
                center,
                180.0F,
                baseY,
                topY,
                0.75F,
                0.80F,
                1.0F,
                0.0F,
                0.65F,
                new Vec3(0.15D, 0.0D, -0.04D),
                storm ? 1.0F : (tower ? 0.82F : (sheet || filament ? 0.18F : 0.70F)),
                storm ? 0.95F : 0.20F,
                cloudTypeId,
                morphology,
                storm ? 0.88F : 0.0F,
                storm || "nimbostratus".equals(cloudTypeId) ? 0.82F : 0.0F,
                32,
                2400L,
                0L
        );
    }

    private static void validateMorphologyLayouts(
            List<String> issues,
            CloudFieldSnapshotFactory snapshotFactory,
            CloudFieldTickContext context
    ) {
        List<LayoutCase> cases = List.of(
                new LayoutCase("cumulus_humilis", CloudMorphologyFamily.PUFF),
                new LayoutCase("cumulus_congestus", CloudMorphologyFamily.TOWER),
                new LayoutCase("cumulonimbus_capillatus", CloudMorphologyFamily.STORM_ANVIL),
                new LayoutCase("stratus_nebulosus", CloudMorphologyFamily.SHEET),
                new LayoutCase("nimbostratus", CloudMorphologyFamily.SHEET),
                new LayoutCase("stratocumulus", CloudMorphologyFamily.CELLULAR_SHEET),
                new LayoutCase("cirrus", CloudMorphologyFamily.FILAMENT),
                new LayoutCase("supercell", CloudMorphologyFamily.SPIRAL_STORM)
        );
        for (LayoutCase layoutCase : cases) {
            CloudField field = sampleField(Vec3.ZERO, layoutCase.cloudTypeId(), layoutCase.morphology());
            CloudFieldRuntimeState runtime = new CloudFieldRuntimeState(
                    field.fieldId(),
                    CloudLodBand.DYNAMIC,
                    CloudLodBand.DYNAMIC,
                    CloudFieldHydrationState.HYDRATED,
                    1.0F,
                    100L,
                    field.cloudletCount(),
                    field.center()
            );
            CloudFieldSnapshot snapshot = snapshotFactory.create(field, runtime, context);
            for (int id = 0; id < 8; id++) {
                CloudletLayout.Cloudlet cloudlet = CloudletLayout.generate(snapshot, CloudletId.of(id));
                validateCloudlet(issues, layoutCase.cloudTypeId(), snapshot, cloudlet);
            }
            if (layoutCase.morphology() == CloudMorphologyFamily.TOWER) {
                if (CloudletLayout.generate(snapshot, CloudletId.of(0)).role()
                        != CloudletLayout.CloudletRole.TOWER
                        || CloudletLayout.generate(snapshot, CloudletId.of(1)).role()
                        != CloudletLayout.CloudletRole.BASE) {
                    issues.add(layoutCase.cloudTypeId() + " lost stable tower/base role ordering");
                }
            }
            if (layoutCase.morphology() == CloudMorphologyFamily.STORM_ANVIL
                    || layoutCase.morphology() == CloudMorphologyFamily.SPIRAL_STORM) {
                if (CloudletLayout.generate(snapshot, CloudletId.of(0)).role()
                        != CloudletLayout.CloudletRole.CORE
                        || CloudletLayout.generate(snapshot, CloudletId.of(1)).role()
                        != CloudletLayout.CloudletRole.BASE
                        || CloudletLayout.generate(snapshot, CloudletId.of(2)).role()
                        != CloudletLayout.CloudletRole.ANVIL) {
                    issues.add(layoutCase.cloudTypeId() + " lost stable core/base/anvil role ordering");
                }
                if (CloudletLayout.generate(snapshot, CloudletId.of(3)).role()
                        != CloudletLayout.CloudletRole.TOWER
                        || CloudletLayout.generate(snapshot, CloudletId.of(4)).role()
                        != CloudletLayout.CloudletRole.TOWER) {
                    issues.add(layoutCase.cloudTypeId() + " lost stable stacked tower anchors");
                }
            }
        }
    }

    private static void validateCloudlet(
            List<String> issues,
            String cloudTypeId,
            CloudFieldSnapshot snapshot,
            CloudletLayout.Cloudlet cloudlet
    ) {
        Vec3 offset = cloudlet.localOffset();
        Vec3 worldCenter = cloudlet.worldCenter(snapshot);
        boolean finite = Double.isFinite(offset.x())
                && Double.isFinite(offset.y())
                && Double.isFinite(offset.z())
                && Float.isFinite(cloudlet.horizontalRadius())
                && Float.isFinite(cloudlet.verticalScale())
                && Float.isFinite(cloudlet.densityScale())
                && Float.isFinite(cloudlet.coverageWeight())
                && Float.isFinite(cloudlet.horizontalAspect())
                && Float.isFinite(cloudlet.orientationRadians());
        boolean bounded = cloudlet.horizontalRadius() > 0.0F
                && cloudlet.verticalScale() > 0.0F
                && cloudlet.verticalScale() <= 1.0F
                && cloudlet.densityScale() > 0.0F
                && cloudlet.coverageWeight() >= 0.0F
                && cloudlet.horizontalAspect() > 0.0F
                && worldCenter.y() >= snapshot.baseY() - EPSILON
                && worldCenter.y() <= snapshot.topY() + EPSILON
                && cloudlet.role() != null;
        if (!finite || !bounded) {
            issues.add(cloudTypeId + " produced invalid cloudlet " + cloudlet.id().value());
        }
    }

    private record LayoutCase(String cloudTypeId, CloudMorphologyFamily morphology) {
    }

    private static boolean sameLayout(CloudletLayout.Cloudlet first, CloudletLayout.Cloudlet second) {
        return first.id().equals(second.id())
                && sameVec(first.localOffset(), second.localOffset())
                && close(first.horizontalRadius(), second.horizontalRadius())
                && close(first.verticalScale(), second.verticalScale())
                && close(first.densityScale(), second.densityScale())
                && close(first.coverageWeight(), second.coverageWeight())
                && close(first.horizontalAspect(), second.horizontalAspect())
                && close(first.orientationRadians(), second.orientationRadians())
                && first.role() == second.role()
                && first.coherentAgeTicks() == second.coherentAgeTicks();
    }

    private static boolean sameVec(Vec3 first, Vec3 second) {
        return first.distanceTo(second) <= EPSILON;
    }

    private static boolean close(float first, float second) {
        return Math.abs(first - second) <= EPSILON;
    }
}
