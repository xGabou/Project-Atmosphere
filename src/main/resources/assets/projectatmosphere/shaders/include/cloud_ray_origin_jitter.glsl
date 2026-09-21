float paRayOriginJitterDistance(
        float fineStep,
        float coarseStep,
        int originJitterMode,
        bool cameraStartsInsideRayVolume,
        bool cameraInsideCloud) {
    // Precipitation extends the ray volume below the real cloud slab. A camera
    // standing under a rainy cloud therefore starts inside the ray volume but
    // is not inside cloud material. Keep the short, stable origin only for a
    // canonical in-cloud view; exterior rays need a full-step blue-noise phase
    // to avoid coherent sampling shells.
    // T211 defect 2. The exterior phase must cover the stride the sample
    // actually lands on, not the finest stride the march can choose. A
    // low-density body - the anvil above all - never promotes to the fine
    // stride, so it is integrated on coarseStep (>= 3 x fineStep). Jittering
    // the origin by only fineStep left those samples clustered into shells one
    // coarseStep apart, which is the horizontal terracing measured on the
    // anvil flanks (period 6-11 px against a 7.5-11.6 block coarse stride,
    // ripple up to 12.7/255). Phasing by the coarse stride decorrelates them;
    // the fine march is unaffected because fineStep <= coarseStep / 3 and the
    // promotion tests are untouched.
    // Mode 2 is the middle: wide enough to break the shells, narrow enough
    // that a deep downward column does not pay the full coarse-stride
    // variance as visible grain.
    float exterior = originJitterMode == 1 ? coarseStep
        : originJitterMode == 2 ? min(coarseStep, fineStep * 2.0)
        : fineStep;
    return cameraInsideCloud
        ? min(fineStep, 0.75)
        : exterior;
}
