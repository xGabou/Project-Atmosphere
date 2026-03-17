package net.Gabou.projectatmosphere.client.loading;

public enum ForecastLoadingStage {
    WAITING_FOR_SERVER("Waiting for Server", "Waiting for atmosphere data..."),
    DESIGNING_FORECAST_REGIONS("Designing Forecast Data", "Designing forecast data..."),
    RECEIVING_FORECAST_DATA("Receiving Forecast Data", "Receiving forecast data..."),
    BUILDING_LOCAL_FORECAST_CACHE("Building Forecast Cache", "Building local forecast cache..."),
    PREPARING_WEATHER_SYSTEMS("Preparing Weather Systems", "Preparing weather systems..."),
    READY("Forecast Ready", "Forecast ready");

    private final String displayName;
    private final String defaultMessage;

    ForecastLoadingStage(String displayName, String defaultMessage) {
        this.displayName = displayName;
        this.defaultMessage = defaultMessage;
    }

    public String displayName() {
        return displayName;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
