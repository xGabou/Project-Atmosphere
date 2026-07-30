package net.Gabou.projectatmosphere.modules.seasonaltrees;

import net.Gabou.projectatmosphere.modules.seasonaltrees.core.SeasonalTreesCore;
import net.neoforged.neoforge.common.NeoForge;

public final class SeasonalTreesBootstrap {
    private SeasonalTreesBootstrap() {
    }

    public static void registerReadOnlyEnhancements() {
        SeasonalTreesCore.registerReadOnlyEnhancements();
    }

    public static void initHost() {
        NeoForge.EVENT_BUS.register(SeasonalTreesEventHandler.class);
    }
}
