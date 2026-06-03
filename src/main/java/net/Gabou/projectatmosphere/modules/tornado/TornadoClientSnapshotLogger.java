package net.Gabou.projectatmosphere.modules.tornado;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

final class TornadoClientSnapshotLogger {
    private static long lastClientSnapshotLogGameTime = Long.MIN_VALUE;

    private TornadoClientSnapshotLogger() {
    }

    static void log(String source, List<TornadoSnapshot> snapshots, TornadoClientLookup lookup) {
        if (!AtmoCommonConfig.TORNADO_DEBUG_LOGGING.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        if (lastClientSnapshotLogGameTime != Long.MIN_VALUE && gameTime - lastClientSnapshotLogGameTime < 20L) {
            return;
        }
        lastClientSnapshotLogGameTime = gameTime;

        if (snapshots.isEmpty()) {
            ProjectAtmosphere.LOGGER.info("[TornadoSync] client {} received empty snapshot list at gameTime={}", source, gameTime);
            return;
        }

        TornadoSnapshot first = snapshots.get(0);
        TornadoInstance existing = lookup.find(first.id());
        Vec3 renderPos = existing == null ? null : existing.getRenderPosition(1.0F);
        ProjectAtmosphere.LOGGER.info(
                "[TornadoSync] client {} received count={} firstId={} snapshotPos={} renderPos={} phase={} gameTime={}",
                source,
                snapshots.size(),
                first.id(),
                first.position(),
                renderPos,
                first.phase(),
                gameTime
        );
    }

    interface TornadoClientLookup {
        TornadoInstance find(UUID id);
    }
}
