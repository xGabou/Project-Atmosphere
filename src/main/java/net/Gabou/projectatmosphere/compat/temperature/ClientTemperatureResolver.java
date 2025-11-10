package net.Gabou.projectatmosphere.compat.temperature;

import net.Gabou.projectatmosphere.client.BiomeClientTemperatureCache;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.compat.ColdSweatCompat;
import net.Gabou.projectatmosphere.compat.LegendarySurvivalCompat;
import net.Gabou.projectatmosphere.compat.ToughAsNailsCompat;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientTemperatureResolver {

    private ClientTemperatureResolver() {
    }

    public static float getCelsius(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return 10.0f;
        }
        return switch (CompatHandler.getActiveTemperatureMod()) {
            case LEGENDARY_SURVIVAL -> LegendarySurvivalCompat.getLiveTemperature(level, pos);
            case TOUGH_AS_NAILS -> ToughAsNailsCompat.getLiveTemperatureTAN(level, pos);
            case COLD_SWEAT -> ColdSweatCompat.getLiveTemperatureColdSweat(level, pos);
            default -> {
                ResourceLocation biome = AtmosphereUtils.getBiomeLocation(pos, level);
                yield BiomeClientTemperatureCache.getTemperature(biome, level);
            }
        };
    }
}
