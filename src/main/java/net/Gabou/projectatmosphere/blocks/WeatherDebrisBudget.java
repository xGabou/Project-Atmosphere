package net.Gabou.projectatmosphere.blocks;

final class WeatherDebrisBudget {
    private static final int GLOBAL_WEATHER_ITEM_BUDGET_PER_TICK = 64;
    private static int globalWeatherItemTokens = GLOBAL_WEATHER_ITEM_BUDGET_PER_TICK;

    private WeatherDebrisBudget() {
    }

    static void reset() {
        globalWeatherItemTokens = GLOBAL_WEATHER_ITEM_BUDGET_PER_TICK;
    }

    static boolean tryConsume(int count) {
        if (globalWeatherItemTokens >= count) {
            globalWeatherItemTokens -= count;
            return true;
        }
        return false;
    }

    static int clampSpawnCount(int requested) {
        return Math.min(requested, globalWeatherItemTokens);
    }
}
