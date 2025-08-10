package net.Gabou.projectatmosphere.modules.temperature.spike;

import net.Gabou.projectatmosphere.modules.temperature.spike.commands.ApplyOngoingSpikeCommand;
import net.Gabou.projectatmosphere.modules.temperature.spike.commands.ApplyRandomJoltCommand;
import net.Gabou.projectatmosphere.modules.temperature.spike.commands.StartNewSpikeCommand;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SpikeManager {

    private static final Random random = new Random();
    private static final float MAX_SPIKE_CHANGE = 6.0f;
    private static final float RANDOM_JOLT_CHANCE = 0.10f;
    private static final float RANDOM_JOLT_MAX = 4.0f;

    private static final Map<BiomeInstanceKey, SpikeState> biomeSpikeStates = new HashMap<>();

    public static float[][] applySpikeLogic(BiomeInstanceKey biome, float[][] week) {
        SpikeState state = biomeSpikeStates.computeIfAbsent(biome, k -> new SpikeState());

        
        if (state.remainingSpikeDays > 0) {
            SpikeData data = new SpikeData(biome, week, state);
            new ApplyOngoingSpikeCommand(data).execute();
            return week;
        }

        
        if (shouldStartNewSpike(state)) {
            SpikeData data = new SpikeData(biome, week, state);
            new StartNewSpikeCommand(data).execute();
            new ApplyOngoingSpikeCommand(data).execute();
            return week;
        }

        
        if (random.nextFloat() < RANDOM_JOLT_CHANCE) {
            SpikeData data = new SpikeData(biome, week, state);
            new ApplyRandomJoltCommand(data, RANDOM_JOLT_MAX).execute();
        }

        state.daysSinceLastSpike++;
        return week;
    }

    private static boolean shouldStartNewSpike(SpikeState state) {
        return random.nextFloat() < 0.075f || state.daysSinceLastSpike > 10;
    }

    
    public static Map<BiomeInstanceKey, SpikeState> getAllStates() {
        return biomeSpikeStates;
    }

    public static void setAllStates(Map<BiomeInstanceKey, SpikeState> map) {
        biomeSpikeStates.clear();
        biomeSpikeStates.putAll(map);
    }

    public static void clearSpikeCache(ServerLevel level) {
        SpikeStateStorage.clearAll(level);
    }
}
