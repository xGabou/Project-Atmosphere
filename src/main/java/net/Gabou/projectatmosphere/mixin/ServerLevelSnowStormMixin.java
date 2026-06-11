package net.Gabou.projectatmosphere.mixin;

import net.Gabou.gaboulibs.util.ISnowStormLevel;
import net.Gabou.projectatmosphere.modules.snowstorm.SnowstormManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerLevel.class)
public abstract class ServerLevelSnowStormMixin implements ISnowStormLevel {


    @Override
    public boolean sereneseasonsplus$isSnowStormAt(ChunkPos pos) {
        return SnowstormManager.isSnowStormAt(pos);
    }

    @Override
    public int sereneseasonsplus$getSnowStormIntensity(ChunkPos pos) {
        return SnowstormManager.getSnowStormIntensity(pos);
    }
}