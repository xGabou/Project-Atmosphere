package net.Gabou.projectatmosphere.tfc;

import net.Gabou.projectatmosphere.api.AtmoApi;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.common.Mod;

@Mod(TFCCompatMod.MOD_ID)
public class TFCCompatMod {
    public static final String MOD_ID = "project_atmosphere_tfc";

    public TFCCompatMod() {
        // Initialization hook for the compatibility module.
    }

    public static BiomeForecast sampleForecast(ServerLevel level, BlockPos pos) {
        return AtmoApi.getInstance().getWeatherForecast(level, pos);
    }
}
