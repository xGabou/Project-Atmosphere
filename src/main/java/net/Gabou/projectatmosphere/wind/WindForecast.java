package net.Gabou.projectatmosphere.wind;

import java.util.EnumMap;

public final class WindForecast {
    private final EnumMap<WindForecastPart, FloatRange> baseRanges;
    private final EnumMap<WindForecastPart, FloatRange> gustRanges;
    private final EnumMap<WindForecastPart, Float> gustProb;
    private final EnumMap<WindForecastPart, FloatRange> dirRangesDeg;

    public WindForecast(EnumMap<WindForecastPart, FloatRange> baseRanges,
                        EnumMap<WindForecastPart, FloatRange> gustRanges,
                        EnumMap<WindForecastPart, Float> gustProb,
                        EnumMap<WindForecastPart, FloatRange> dirRangesDeg) {
        this.baseRanges = baseRanges;
        this.gustRanges = gustRanges;
        this.gustProb = gustProb;
        this.dirRangesDeg = dirRangesDeg;
    }

    public EnumMap<WindForecastPart, FloatRange> getBaseRanges() {
        return baseRanges;
    }

    public EnumMap<WindForecastPart, FloatRange> getGustRanges() {
        return gustRanges;
    }

    public EnumMap<WindForecastPart, Float> getGustProb() {
        return gustProb;
    }

    public EnumMap<WindForecastPart, FloatRange> getDirRangesDeg() {
        return dirRangesDeg;
    }
}

