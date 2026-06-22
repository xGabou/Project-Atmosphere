package net.Gabou.projectatmosphere.clouds.field;

import java.util.Objects;

/**
 * Owns the neutral field update flow: age, wind movement, LOD classification,
 * and hydration state updates.
 */
public final class CloudFieldLifecycleController {
    private final CloudFieldDistanceClassifier distanceClassifier;
    private final CloudFieldHydrationController hydrationController;

    public CloudFieldLifecycleController(
            CloudFieldDistanceClassifier distanceClassifier,
            CloudFieldHydrationController hydrationController
    ) {
        this.distanceClassifier = distanceClassifier == null
                ? CloudFieldDistanceClassifier.defaultClassifier()
                : distanceClassifier;
        this.hydrationController = hydrationController == null
                ? CloudFieldHydrationController.defaultController()
                : hydrationController;
    }

    public static CloudFieldLifecycleController defaultController() {
        return new CloudFieldLifecycleController(
                CloudFieldDistanceClassifier.defaultClassifier(),
                CloudFieldHydrationController.defaultController()
        );
    }

    public TickResult tick(CloudField field, CloudFieldRuntimeState runtimeState, CloudFieldTickContext context) {
        Objects.requireNonNull(field, "field");
        CloudFieldTickContext tickContext = context == null
                ? CloudFieldTickContext.of(field.center(), 0L, 0.0F)
                : context;

        CloudField advancedField = field.movedByWind(tickContext.deltaTicks());
        CloudFieldDistanceClassifier classifier = tickContext.distanceClassifier() == null
                ? distanceClassifier
                : tickContext.distanceClassifier();
        CloudLodBand lodBand = classifier.classify(advancedField, tickContext.cameraPosition());
        CloudFieldRuntimeState prior = runtimeState == null
                ? CloudFieldRuntimeState.initial(field, lodBand, tickContext.worldTime())
                : runtimeState;
        CloudFieldRuntimeState updatedRuntime = hydrationController.update(
                advancedField,
                prior,
                lodBand,
                tickContext.worldTime(),
                tickContext.deltaTicks(),
                field.center()
        );

        return new TickResult(advancedField, updatedRuntime);
    }

    public CloudFieldDistanceClassifier distanceClassifier() {
        return distanceClassifier;
    }

    public CloudFieldHydrationController hydrationController() {
        return hydrationController;
    }

    public record TickResult(
            CloudField field,
            CloudFieldRuntimeState runtimeState
    ) {
        public TickResult {
            field = Objects.requireNonNull(field, "field");
            runtimeState = Objects.requireNonNull(runtimeState, "runtimeState");
        }
    }
}
