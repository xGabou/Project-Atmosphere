package net.Gabou.projectatmosphere.compat;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.entity.data.Preference;
import com.momosoftworks.coldsweat.util.world.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class ColdSweatCompat {


    public static boolean isLoaded() {
        return CompatHandler.isColdSweatLoaded();
    }

    public static float getLiveTemperatureColdSweat(ServerLevel level, BlockPos pos) {
        return (float) Temperature.convert(WorldHelper.getTemperatureAt(level, pos), Temperature.Units.MC, Temperature.Units.C, true);
    }
}
