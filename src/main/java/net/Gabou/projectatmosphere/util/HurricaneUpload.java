package net.Gabou.projectatmosphere.util;

public record HurricaneUpload(
        float typeIndex,
        float centerX,
        float centerZ,
        float anchorY,
        float coreRadius,
        float stormExtentRadius,
        float eyeRadius,
        float edgeFade,
        float bandCount,
        float bandWidth,
        float spiralTightness,
        float rotationPhase,
        float rotationSpeed,
        float transitionStart,
        float transitionEnd
) {
}
