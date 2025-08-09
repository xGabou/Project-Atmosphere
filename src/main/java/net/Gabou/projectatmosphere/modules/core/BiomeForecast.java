package net.Gabou.projectatmosphere.modules.core;

public class BiomeForecast {
    private float[][] temperature; 
    private float[][] pressure;
    private float[][] humidity;
    private WindVector[] wind;

    private float[][] stormChance;

    private float[] temperatureDay;
    private float[] temperatureTomorrow;
    private float[] pressureDay;

    private float[] stormChanceDay;

    private WindVector windDay;
    private float[] pressureTomorrow;
    private float[] humidityDay;
    private float[] humidityTomorrow;

    private float[] stormChanceTomorrow;

    private WindVector windTomorrow;

    private boolean sandstormExpected;

    private boolean legendaryFlag = false;
    private boolean toughAsNailsFlag = false;
    public BiomeForecast() {
    }

    public BiomeForecast(float[][] temperature, float[][] pressure, float[][] humidity, WindVector[] wind,float[][] stormChance) {
        this.temperature = temperature;
        this.pressure = pressure;
        this.humidity = humidity;
        this.wind = wind;
        this.stormChance = stormChance;
    }

    
    public float[][] getTemperature() {
        return temperature;
    }

    public float[][] getPressure() {
        return pressure;
    }

    public float[][] getHumidity() {
        return humidity;
    }

    public WindVector[] getWind() {
        return wind;
    }
    public float[][] getStormChance() {
        return stormChance;
    }

    
    public float[] getTemperatureDay() {
        return temperatureDay;
    }

    public float[] getTemperatureTomorrow() {
        return temperatureTomorrow;
    }

    public float[] getPressureDay() {
        return pressureDay;
    }

    public float[] getPressureTomorrow() {
        return pressureTomorrow;
    }

    public float[] getHumidityDay() {
        return humidityDay;
    }

    public float[] getStormChanceDay() {
        return stormChanceDay;
    }

    public float[] getHumidityTomorrow() {
        return humidityTomorrow;
    }

    public float[] getStormChanceTomorrow() {
        return stormChanceTomorrow;
    }

    
    public void setTemperature(float[][] temperature) {
        this.temperature = temperature;
    }

    public void setPressure(float[][] pressure) {
        this.pressure = pressure;
    }

    public void setHumidity(float[][] humidity) {
        this.humidity = humidity;
    }

    public void setWind(WindVector[] wind) {
        this.wind = wind;
    }

    public void setStormChance(float[][] stormChance) {
        this.stormChance = stormChance;
    }

    
    public void setTemperatureDay(float[] temperatureDay) {
        this.temperatureDay = temperatureDay;
    }

    public void setStormChanceDay(float[] stormChanceDay) {
        this.stormChanceDay = stormChanceDay;
    }

    public void setTemperatureTomorrow(float[] temperatureTomorrow) {
        this.temperatureTomorrow = temperatureTomorrow;
    }

    public void setPressureDay(float[] pressureDay) {
        this.pressureDay = pressureDay;
    }

    public void setPressureTomorrow(float[] pressureTomorrow) {
        this.pressureTomorrow = pressureTomorrow;
    }
    public void setStormChanceTomorrow(float[] stormChanceTomorrow) {
        this.stormChanceTomorrow = stormChanceTomorrow;
    }

    public void setHumidityDay(float[] humidityDay) {
        this.humidityDay = humidityDay;
    }

    public void setHumidityTomorrow(float[] humidityTomorrow) {
        this.humidityTomorrow = humidityTomorrow;
    }

    public void setWindDay(WindVector windDay) {
        this.windDay = windDay;
    }
    public WindVector getWindDay() {
        return windDay;
    }
    public void setWindTomorrow(WindVector windTomorrow) {
        this.windTomorrow = windTomorrow;
    }
    public WindVector getWindTomorrow() {
        return windTomorrow;
    }

    public boolean hasData(ForecastType type) {
        return switch (type) {
            case HUMIDITY -> humidityDay != null;
            case TEMPERATURE -> temperatureDay != null;
            case PRESSURE -> pressureDay != null;
            case WIND -> windDay != null && wind.length > 0;
            case STORM -> stormChanceDay != null;
            default -> false;
        };
    }

    public boolean isLegendaryFlag() {
        return legendaryFlag;
    }

    public void setLegendaryFlag(boolean legendaryFlag) {
        this.legendaryFlag = legendaryFlag;
    }

    public boolean isToughAsNailsFlag() {
        return toughAsNailsFlag;
    }

    public void setToughAsNailsFlag(boolean toughAsNailsFlag) {
        this.toughAsNailsFlag = toughAsNailsFlag;
    }



    public void setSandstormExpected(boolean value) {
        this.sandstormExpected = value;
    }

    public boolean isSandstormExpected() {
        return sandstormExpected;
    }



}
