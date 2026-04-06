package net.Gabou.projectatmosphere.util;

public record HurricaneUpload(
        float typeIndex,
        float centerX,
        float centerZ,
        float outerRadius,
        float eyeRadius,
        float edgeFade,
        float bandCount,
        float bandWidth,
        float spiralTightness,
        float rotationPhase,
        float rotationSpeed,
        float ageTicks
) {
}
