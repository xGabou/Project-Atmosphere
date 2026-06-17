package net.Gabou.projectatmosphere.event;

import com.teamtea.eclipticseasons.api.event.SolarTermChangeEvent;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.minecraft.server.level.ServerLevel;


public class EclipticTracker {

    public static void onSolarTermChange(SolarTermChangeEvent event) {

        System.out.println("Solar term changed: "
                + event.getOldSolarTerm() + " -> " + event.getNewSolarTerm());

        if (event.getLevel() instanceof ServerLevel serverLevel) {
            if(event.getNewSolarTerm().getSeason() != event.getOldSolarTerm().getSeason())
                AtmosphereManager.onSeasonChange(serverLevel);
        }
    }

}
