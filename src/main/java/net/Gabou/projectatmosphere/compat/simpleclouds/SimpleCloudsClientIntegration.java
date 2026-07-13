package net.Gabou.projectatmosphere.compat.simpleclouds;

import net.Gabou.projectatmosphere.client.fog.SimpleCloudsWhiteoutFogHandler;
import net.Gabou.projectatmosphere.client.hurricane.cache.ClientHurricaneStateCache;
import net.Gabou.projectatmosphere.clouds.client.render.ClientCloudRenderOwnership;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import net.Gabou.projectatmosphere.clouds.service.OptionalCloudQueries;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.Gabou.projectatmosphere.modules.tornado.TornadoSnapshot;
import net.Gabou.projectatmosphere.network.SevereWeatherClientPacketHandlers;
import net.Gabou.projectatmosphere.network.SevereWeatherClientPacketHandlers.TornadoSpawn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.common.MinecraftForge;

import java.util.List;

/** Loaded reflectively only after the Simple Clouds mod-presence check. */
public final class SimpleCloudsClientIntegration {
    private SimpleCloudsClientIntegration() {
    }

    public static void register() {
        ClientCloudRenderOwnership.setSimpleCloudsDimensionProbe(SimpleCloudsRenderer::canRenderInDimension);
        OptionalCloudQueries.install(SimpleCloudsCompat::isCloudAtPos, SimpleCloudsCompat::isRainningAt);
        SevereWeatherClientPacketHandlers.install(
                SimpleCloudsClientIntegration::spawnTornado,
                TornadoManager::removeClientTornado,
                SimpleCloudsClientIntegration::syncTornadoes,
                ClientHurricaneStateCache::applySnapshots
        );
        MinecraftForge.EVENT_BUS.register(SimpleCloudsWhiteoutFogHandler.class);
    }

    private static void spawnTornado(TornadoSpawn spawn) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        TornadoManager.spawnClient(
                level,
                spawn.id(),
                spawn.position(),
                spawn.radius(),
                spawn.wind(),
                spawn.bottomY(),
                spawn.height()
        );
    }

    private static void syncTornadoes(List<TornadoSnapshot> snapshots) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            TornadoManager.clearClientTornadoes();
            return;
        }
        TornadoManager.applyClientSnapshots(level, snapshots);
    }
}
