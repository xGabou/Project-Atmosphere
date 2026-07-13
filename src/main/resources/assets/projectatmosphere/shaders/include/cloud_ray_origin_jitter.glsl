float paRayOriginJitterDistance(
        float fineStep,
        bool cameraStartsInsideRayVolume,
        bool cameraInsideCloud) {
    // Precipitation extends the ray volume below the real cloud slab. A camera
    // standing under a rainy cloud therefore starts inside the ray volume but
    // is not inside cloud material. Keep the short, stable origin only for a
    // canonical in-cloud view; exterior rays need a full-step blue-noise phase
    // to avoid coherent sampling shells.
    return cameraInsideCloud
        ? min(fineStep, 0.75)
        : fineStep;
}
