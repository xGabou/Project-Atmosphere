package net.Gabou.projectatmosphere.temperature.event;

import net.Gabou.projectatmosphere.temperature.TemperatureManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.season.SeasonTime;

@Mod.EventBusSubscriber
public class SeasonTracker {

    private static Season lastSeason = null;

    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide) return;
        Level world = event.level;
        Season current = SeasonHelper.getSeasonState(world).getSubSeason().getSeason();

        try{
            if (lastSeason != null && current != lastSeason) {
                // Season changed
                TemperatureManager.onSeasonChange(world,world.getServer().getPlayerList().getPlayers().get(0).blockPosition());
            }
            lastSeason = current;
        }
        catch (Exception e){
            // Handle the case where the server is not initialized yet
            // This can happen if the event is triggered before the server is fully set up
            System.err.println("Server not initialized yet: " + e.getMessage());
        }

    }
}
