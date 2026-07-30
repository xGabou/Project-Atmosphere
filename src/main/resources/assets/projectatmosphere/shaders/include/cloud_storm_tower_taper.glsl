float paStormTowerTaper(
        float rawTowerSupport,
        float coverageMul,
        float remappedTowerSupport,
        float towerTaperHeight) {
    return smoothstep(
        mix(0.05, 0.42, towerTaperHeight),
        mix(0.38, 0.78, towerTaperHeight),
        remappedTowerSupport
    );
}
