vec2 paBlendStormTowerWinnerRanges(
        float bestSupport,
        vec2 bestRange,
        float secondSupport,
        vec2 secondRange) {
    if (secondSupport <= 0.002) {
        return bestRange;
    }

    float overlap = min(bestRange.y, secondRange.y)
        - max(bestRange.x, secondRange.x);
    float shorterSpan = max(
        min(bestRange.y - bestRange.x, secondRange.y - secondRange.x),
        0.001
    );
    float overlapWeight = smoothstep(
        0.0,
        0.15,
        clamp(overlap / shorterSpan, 0.0, 1.0)
    );
    float supportRatio = clamp(
        secondSupport / max(bestSupport, 0.001),
        0.0,
        1.0
    );
    float secondWeight = 0.5
        * smoothstep(0.72, 1.0, supportRatio)
        * overlapWeight;
    return mix(bestRange, secondRange, secondWeight);
}
