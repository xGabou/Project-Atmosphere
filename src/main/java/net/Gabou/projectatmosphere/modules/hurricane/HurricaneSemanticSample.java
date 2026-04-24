package net.Gabou.projectatmosphere.modules.hurricane;

import net.minecraft.resources.ResourceLocation;

public record HurricaneSemanticSample(
        ResourceLocation cloudTypeId,
        float anchorY,
        float coverage,
        float rainStrength,
        boolean inEye,
        float coreCoverage,
        float outerCoverage
) {
    public static HurricaneSemanticSample none() {
        return new HurricaneSemanticSample(HurricaneInstance.HURRICANE_CLOUD_TYPE_ID, 0.0F, 0.0F, 0.0F, false, 0.0F, 0.0F);
    }

    public boolean isPresent() {
        return this.coverage > 0.001F;
    }
}
