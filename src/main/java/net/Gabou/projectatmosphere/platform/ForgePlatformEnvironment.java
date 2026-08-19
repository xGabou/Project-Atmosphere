package net.Gabou.projectatmosphere.platform;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Forge 1.20.1 runtime-environment adapter. */
public final class ForgePlatformEnvironment implements PlatformEnvironment {
    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isProduction() {
        return FMLEnvironment.production;
    }
}
