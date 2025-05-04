package net.Gabou.projectatmosphere.temperature.spike.commands;

import net.Gabou.projectatmosphere.temperature.spike.SpikeData;

import java.util.Random;

public class ApplyRandomJoltCommand {

    private final SpikeData data;
    private final float maxJolt;
    private static final Random random = new Random();

    public ApplyRandomJoltCommand(SpikeData data, float maxJolt) {
        this.data = data;
        this.maxJolt = maxJolt;
    }

    public void execute() {
        int day = random.nextInt(7);
        float jolt = generateWeightedJolt(maxJolt);
        data.week[day][0] += jolt;
        data.week[day][1] += jolt;
        smoothNeighbors(data.week, day, jolt);
    }

    private float generateWeightedJolt(float max) {
        return (float)(Math.pow(random.nextDouble(), 1.5) * max);
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
