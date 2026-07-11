package net.Gabou.projectatmosphere.compat.simpleclouds;

import net.Gabou.projectatmosphere.client.fog.SimpleCloudsWhiteoutFogHandler;
import net.Gabou.projectatmosphere.client.hurricane.cache.ClientHurricaneStateCache;
import net.Gabou.projectatmosphere.clouds.client.render.ClientCloudRenderOwnership;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import net.Gabou.projectatmosphere.clouds.service.OptionalCloudQueries;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.Gabou.projectatmosphere.network.SevereWeatherClientPacketHandlers;
import net.minecraftforge.common.MinecraftForge;

/** Loaded reflectively only after the Simple Clouds mod-presence check. */
public final class SimpleCloudsClientIntegration {
    private SimpleCloudsClientIntegration() {
    }

    public static void register() {
        ClientCloudRenderOwnership.setSimpleCloudsDimensionProbe(SimpleCloudsRenderer::canRenderInDimension);
        OptionalCloudQueries.install(SimpleCloudsCompat::isCloudAtPos, SimpleCloudsCompat::isRainningAt);
        SevereWeatherClientPacketHandlers.install(
                spawn -> TornadoManager.spawnClient(
                        spawn.id(),
                        spawn.position(),
                        spawn.radius(),
                        spawn.wind(),
                        spawn.bottomY(),
                        spawn.height()
                ),
                TornadoManager::removeClientTornado,
                TornadoManager::applyClientSnapshots,
                ClientHurricaneStateCache::applySnapshots
        );
        MinecraftForge.EVENT_BUS.register(SimpleCloudsWhiteoutFogHandler.class);
    }
}
