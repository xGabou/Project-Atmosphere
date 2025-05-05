// src/main/java/net/Gabou/projectatmosphere/util/AtmosphericPhysics.java
package net.Gabou.projectatmosphere.util;

public class AtmosphericPhysics {

    private static final double Rd = 287.05;   // J/(kg·K) for dry air
    private static final double Rv = 461.495;  // J/(kg·K) for water vapor

    /**
     * Computes weekly air density using temperature and humidity forecasts.
     * @param temperatureWeek float[7][2] (min, max) daily temperatures in °C
     * @param humidityWeek float[7][2] (min, max) RH in %
     * @return double[7] air density in kg/m^3
     */
    public static double[] computeAirDensity(float[][] temperatureWeek, float[][] humidityWeek) {
        double[] density = new double[7];

        for (int d = 0; d < 7; d++) {
            float Tmin = temperatureWeek[d][0];
            float Tmax = temperatureWeek[d][1];
            float TmeanC = (Tmin + Tmax) * 0.5f;
            double T = TmeanC + 273.15;

            float RH = (humidityWeek[d][0] + humidityWeek[d][1]) * 0.5f;

            // Magnus formula to approximate saturation vapor pressure (in hPa)
            double es = 6.112 * Math.exp((17.67 * TmeanC) / (TmeanC + 243.5));

            // Partial pressures (in hPa → Pa)
            double pv = RH * es / 100.0 * 100.0;         // water vapor pressure in Pa
            double pd = (1013.25 * 100.0) - pv;          // dry air pressure in Pa

            density[d] = (pd / (Rd * T)) + (pv / (Rv * T));
        }

        return density;
    }
}
