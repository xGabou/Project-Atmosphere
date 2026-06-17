package net.Gabou.projectatmosphere.modules.ocean;

/**
 * Polymorphic modifier for {@link OceanBasin} state.
 * <p>
 * Implementations are responsible for updating the slowly varying
 * basin reservoirs (temperature memory, humidity storage, etc.).
 * They operate entirely on the basin without touching atmospheric
 * cells so the architecture stays modular.
 */
public interface OceanInfluence {
    /**
     * Apply this influence to the target basin.
     *
     * @param basin   basin being updated
     * @param context immutable simulation context
     */
    void applyTo(OceanBasin basin, OceanUpdateContext context);
}
