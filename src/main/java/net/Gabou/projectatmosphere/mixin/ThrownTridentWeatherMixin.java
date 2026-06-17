package net.Gabou.projectatmosphere.mixin;

import net.Gabou.projectatmosphere.clouds.AtmosphereCloudPolicy;
import net.Gabou.projectatmosphere.clouds.WeatherCloudQueries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ThrownTrident.class)
public abstract class ThrownTridentWeatherMixin {
    @Redirect(method = "onHitEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isThundering()Z"))
    private boolean projectatmosphere$useLocalizedThunderForChanneling(Level level) {
        if (!AtmosphereCloudPolicy.shouldOwnWeather(level)) {
            return level.isThundering();
        }

        ThrownTrident trident = (ThrownTrident) (Object) this;
        BlockPos pos = BlockPos.containing(trident.getX(), trident.getY(), trident.getZ());
        return WeatherCloudQueries.isThunderingAt(level, pos);
    }
}
