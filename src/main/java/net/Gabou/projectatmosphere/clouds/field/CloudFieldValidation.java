package net.Gabou.projectatmosphere.clouds.field;

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

        CloudField movedField = field.withCenter(new Vec3(25.0D, 128.0D, -10.0D), field.ageTicks() + 40L);
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
        return new CloudField(
                UUID.fromString("00000000-0000-0000-0000-000000000143"),
                987654321L,
                "minecraft:overworld",
                center,
                180.0F,
                120.0F,
                220.0F,
                0.75F,
                0.80F,
                1.0F,
                0.0F,
                0.65F,
                new Vec3(0.15D, 0.0D, -0.04D),
                0.70F,
                0.20F,
                "cumulus_humilis",
                net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily.PUFF,
                0.0F,
                0.0F,
                32,
                2400L,
                0L
        );
    }

    private static boolean sameLayout(CloudletLayout.Cloudlet first, CloudletLayout.Cloudlet second) {
        return first.id().equals(second.id())
                && sameVec(first.localOffset(), second.localOffset())
                && close(first.horizontalRadius(), second.horizontalRadius())
                && close(first.verticalScale(), second.verticalScale())
                && close(first.densityScale(), second.densityScale())
                && close(first.coverageWeight(), second.coverageWeight())
                && first.coherentAgeTicks() == second.coherentAgeTicks();
    }

    private static boolean sameVec(Vec3 first, Vec3 second) {
        return first.distanceTo(second) <= EPSILON;
    }

    private static boolean close(float first, float second) {
        return Math.abs(first - second) <= EPSILON;
    }
}
