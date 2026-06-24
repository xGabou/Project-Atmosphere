package net.Gabou.projectatmosphere.clouds.field;

import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldSource;

import java.util.Objects;

/**
 * Integration point for one CloudField runtime tick. Formation target
 * resolution and state evolution are delegated to dedicated production classes;
 * this controller keeps LOD classification and runtime hydration wiring.
 */
public final class CloudFieldLifecycleController {
    private final CloudFieldDistanceClassifier distanceClassifier;
    private final CloudFieldHydrationController hydrationController;
    private final CloudFieldTargetResolver targetResolver;
    private final CloudFieldEvolutionController evolutionController;

    public CloudFieldLifecycleController(
            CloudFieldDistanceClassifier distanceClassifier,
            CloudFieldHydrationController hydrationController
    ) {
        this(
                distanceClassifier,
                hydrationController,
                CloudFieldTargetResolver.createDefault(),
                CloudFieldEvolutionController.createDefault()
        );
    }

    public CloudFieldLifecycleController(
            CloudFieldDistanceClassifier distanceClassifier,
            CloudFieldHydrationController hydrationController,
            CloudFieldTargetResolver targetResolver,
            CloudFieldEvolutionController evolutionController
    ) {
        this.distanceClassifier = distanceClassifier == null
                ? CloudFieldDistanceClassifier.defaultClassifier()
                : distanceClassifier;
        this.hydrationController = hydrationController == null
                ? CloudFieldHydrationController.defaultController()
                : hydrationController;
        this.targetResolver = targetResolver == null
                ? CloudFieldTargetResolver.createDefault()
                : targetResolver;
        this.evolutionController = evolutionController == null
                ? CloudFieldEvolutionController.createDefault()
                : evolutionController;
    }

    public static CloudFieldLifecycleController defaultController() {
        return new CloudFieldLifecycleController(
                CloudFieldDistanceClassifier.defaultClassifier(),
                CloudFieldHydrationController.defaultController()
        );
    }

    public TickResult tick(CloudField field, CloudFieldRuntimeState runtimeState, CloudFieldTickContext context) {
        return tick(field, runtimeState, context, null);
    }

    /**
     * Advances a field by resolving its backend/weather target, evolving the
     * persistent state, then refreshing LOD hydration runtime state.
     */
    public TickResult tick(
            CloudField field,
            CloudFieldRuntimeState runtimeState,
            CloudFieldTickContext context,
            CloudFieldSource source
    ) {
        return tick(field, runtimeState, context, source, 0);
    }

    /**
     * Advances a field with source staleness context. Missing source ticks add
     * decay pressure but do not remove the persistent field immediately.
     */
    public TickResult tick(
            CloudField field,
            CloudFieldRuntimeState runtimeState,
            CloudFieldTickContext context,
            CloudFieldSource source,
            int missingSourceTicks
    ) {
        Objects.requireNonNull(field, "field");
        CloudFieldTickContext tickContext = context == null
                ? CloudFieldTickContext.of(field.center(), 0L, 0.0F)
                : context;

        CloudFieldTarget target = targetResolver.resolve(field, source, missingSourceTicks);
        CloudField advancedField = evolutionController.evolve(field, target, tickContext);
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

    /**
     * Returns the resolver that converts backend source data into evolution
     * targets for this lifecycle controller.
     */
    public CloudFieldTargetResolver targetResolver() {
        return targetResolver;
    }

    /**
     * Returns the controller that evolves persistent CloudField state toward
     * resolved targets.
     */
    public CloudFieldEvolutionController evolutionController() {
        return evolutionController;
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
