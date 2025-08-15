package net.Gabou.projectatmosphere.modules.hurricane;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HurricaneManager {

    private static final List<HurricaneInstance> ACTIVE_HURRICANES = new ArrayList<>();

    public static void spawnServer(ServerLevel level, Vec3 pos, float radius, WindVector wind, HurricaneCategory category) {
        ACTIVE_HURRICANES.add(new HurricaneInstance(pos, radius, wind, category));
    }

    public static void tick(ServerLevel level) {
        ACTIVE_HURRICANES.removeIf(h -> h.getLifetimeSeconds() > 1200);
        for (HurricaneInstance hurricane : ACTIVE_HURRICANES) {
            float speed = hurricane.wind.baseSpeed() * 0.01f;
            hurricane.position = hurricane.position.add(
                    Math.cos(hurricane.wind.angleRadians()) * speed,
                    0,
                    Math.sin(hurricane.wind.angleRadians()) * speed);
            hurricane.tick(level);
        }
    }

    public static List<HurricaneInstance> getActiveHurricanes() {
        return Collections.unmodifiableList(ACTIVE_HURRICANES);
    }

    public static void clearHurricanes() {
        ACTIVE_HURRICANES.clear();
    }

    public static void removeHurricane(HurricaneInstance hurricane) {
        ACTIVE_HURRICANES.remove(hurricane);
    }
}
