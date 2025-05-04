package net.Gabou.projectatmosphere.temperature.spike.commands;

import net.Gabou.projectatmosphere.temperature.spike.SpikeData;

public class ApplyOngoingSpikeCommand {

    private final SpikeData data;

    public ApplyOngoingSpikeCommand(SpikeData data) {
        this.data = data;
    }

    public void execute() {
        float mag = data.state.spikeMagnitude;
        for (int i = 0; i < data.state.remainingSpikeDays; i++) {
            int day = data.state.currentSpikeDay + i;
            if (day >= 7) break;
            data.week[day][0] += mag;
            data.week[day][1] += mag;
            smoothNeighbors(data.week, day, mag / 2f);
        }
        data.state.remainingSpikeDays = 0;
    }

    private void smoothNeighbors(float[][] week, int centerDay, float offset) {
        if (centerDay > 0) {
            week[centerDay - 1][0] += offset / 2f;
            week[centerDay - 1][1] += offset / 2f;
        }
        if (centerDay < 6) {
            week[centerDay + 1][0] += offset / 2f;
            week[centerDay + 1][1] += offset / 2f;
        }
    }
}
