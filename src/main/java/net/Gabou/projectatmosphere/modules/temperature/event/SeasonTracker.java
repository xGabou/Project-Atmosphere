package net.Gabou.projectatmosphere.modules.temperature.event;

import net.Gabou.projectatmosphere.modules.temperature.manager.TemperatureManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

@Mod.EventBusSubscriber
public class SeasonTracker {

    private static Season lastSeason = null;

    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent event) {
        try {
            if (event.phase != TickEvent.Phase.END || event.level.isClientSide) return;
            ServerLevel world = event.level.getServer().overworld();
            if (!world.dimension().equals(Level.OVERWORLD)) return;
            Season current = SeasonHelper.getSeasonState(world).getSubSeason().getSeason();


            if (lastSeason != null && current != lastSeason) {
                // Season changed

                    TemperatureManager.onSeasonChange(world);

            }
            lastSeason = current;
        } catch (Exception e) {
            // Handle the case where the server is not initialized yet
            // This can happen if the event is triggered before the server is fully set up
            System.err.println("Server not initialized yet: " + e.getMessage());
        }

    }
}
