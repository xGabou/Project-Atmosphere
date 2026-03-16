package net.Gabou.projectatmosphere.modules.seasonaltrees;

import net.Gabou.projectatmosphere.modules.seasonaltrees.core.SeasonalTreesCore;
import net.minecraftforge.common.MinecraftForge;

public final class SeasonalTreesBootstrap {
    private SeasonalTreesBootstrap() {
    }

    public static void registerReadOnlyEnhancements() {
        SeasonalTreesCore.registerReadOnlyEnhancements();
    }

    public static void initHost() {
        MinecraftForge.EVENT_BUS.register(SeasonalTreesEventHandler.class);
    }
}
