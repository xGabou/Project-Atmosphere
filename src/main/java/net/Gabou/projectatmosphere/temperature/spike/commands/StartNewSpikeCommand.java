package net.Gabou.projectatmosphere.temperature.spike.commands;

import net.Gabou.projectatmosphere.temperature.spike.SpikeData;

import java.util.Random;

public class StartNewSpikeCommand {

    private final SpikeData data;
    private static final Random random = new Random();

    public StartNewSpikeCommand(SpikeData data) {
        this.data = data;
    }

    public void execute() {
        boolean isHeat = random.nextBoolean();
        int startDay = random.nextInt(4);
        int duration = 3 + random.nextInt(2);
        float magnitude = generateWeightedSpikeMagnitude();

        if (!isHeat) magnitude = -magnitude;

        data.state.spikeMagnitude = magnitude;
        data.state.remainingSpikeDays = duration;
        data.state.currentSpikeDay = startDay;
        data.state.daysSinceLastSpike = 0;
    }

    private float generateWeightedSpikeMagnitude() {
        float max = 6.0f;
        return (float)(max - Math.pow(random.nextDouble(), 2) * max);
    }
}
