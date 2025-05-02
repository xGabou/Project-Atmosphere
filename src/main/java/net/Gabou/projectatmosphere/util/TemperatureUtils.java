package net.Gabou.projectatmosphere.util;

          // Serene Seasons API
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import sereneseasons.season.SeasonHooks;

public class TemperatureUtils {

    // Serene/vanilla input range
    private static final float IN_MIN = -0.5f;
    private static final float IN_MAX =  2.0f;
    private static final float DEN    = (IN_MAX - IN_MIN);        // 2.5f

    // Sea level in Java Edition
    private static final float SEA_LEVEL = 63f;                   // top of water blocks :contentReference[oaicite:0]{index=0}
    // –6.5°C per 1000m → –0.0065°C per block
    private static final float LAPSE_RATE = -0.0065f;

    /**
     * Fetches the Serene Seasons temperature at this position,
     * maps it through your per-biome range, and then applies altitude.
     *
     * @param world    The current world (Level).
     * @param pos      The BlockPos you’re sampling at.
     * @return         The “real-world” °C at this biome + altitude.
     */
    public static float getRealTemperature(Level world, BlockPos pos) {
        // 1) get the Serene Seasons temperature (-0.5→2.0)
        float baseTemp = SeasonHooks.getBiomeTemperature(world, world.getBiome(pos), pos);
        // 2) do your biome-specific sea-level map
        Biome biome = world.getBiome(pos).value();
        float seaLevelC = toCelsiusSeaLevel(biome, baseTemp);
        // 3) apply altitude
        return seaLevelC + (pos.getY() - SEA_LEVEL) * LAPSE_RATE;
    }

    /**
     * Maps a Serene/vanilla baseTemp (–0.5→2.0) into per-biome [minC…maxC].
     */
    private static float toCelsiusSeaLevel(Biome biome, float baseTemp) {
        // clamp
        if (baseTemp < IN_MIN) baseTemp = IN_MIN;
        else if (baseTemp > IN_MAX) baseTemp = IN_MAX;

        // per-biome min/max ranges (from BiomeTempConfig)
        BiomeTempConfig.Range range = BiomeTempConfig.getRange(biome);

        float norm = (baseTemp - IN_MIN) / DEN;                 // [0…1]
        return range.minC() + norm * (range.maxC() - range.minC());
    }
}
