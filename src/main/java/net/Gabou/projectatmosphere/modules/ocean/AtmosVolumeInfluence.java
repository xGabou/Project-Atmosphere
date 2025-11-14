package net.Gabou.projectatmosphere.modules.ocean;

/**
 * Influence capable of modifying the atmospheric volume above a basin.
 * Implementations operate on {@link AtmosphericVolume} wrappers which
 * expose a {@code RegionAtmosphereState} together with metadata such as
 * coupling weight or whether the cell is water or coastal land.
 */
public interface AtmosVolumeInfluence {
    /**
     * Apply the influence to the given atmospheric volume.
     *
     * @param volume  target atmosphere wrapper
     * @param context immutable simulation context
     */
    void applyTo(AtmosphericVolume volume, OceanUpdateContext context);
}
