package net.Gabou.projectatmosphere.command.tree.service;

import net.Gabou.projectatmosphere.client.fog.FogBiomeClassifier;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandSupport;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.fog.FogHeuristics;
import net.Gabou.projectatmosphere.network.FogDebugOverridePacket;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.Gabou.projectatmosphere.util.UnitFormatter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CommandFogService {
    private static final float DEFAULT_STRENGTH = 0.85F;
    private static final int DEFAULT_DURATION_SECONDS = 30;

    private CommandFogService() {
    }

    public static int sendFogInfo(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(Component.literal("Fog debug is only available in the Overworld."));
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        long tick = level.getGameTime();
        float humidity = ForecastOrchestrator.getCurrentHumidity(level, pos, tick);
        float rainIntensity = net.Gabou.projectatmosphere.api.AtmoApi.getInstance().getWeatherSnapshot(level, pos, tick).rainIntensity();
        float wetBiomeFactor = FogBiomeClassifier.computeWetBiomeFactor(level, pos);
        FogHeuristics.FogProfile fog = FogHeuristics.sample(humidity, wetBiomeFactor, rainIntensity);
        ResourceLocation biome = level.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getKey(level.getBiome(pos).value());

        PaCommandMessages.success(
                source,
                false,
                "Fog info",
                "Position: " + pos.getX() + " " + pos.getY() + " " + pos.getZ(),
                "Enabled: " + AtmoCommonConfig.FOG_ENABLED.get(),
                "Biome: " + biome,
                "Humidity: " + UnitFormatter.formatHumidity(humidity),
                "Rain intensity: " + String.format(java.util.Locale.ROOT, "%.2f", rainIntensity),
                "Wet biome factor: " + String.format(java.util.Locale.ROOT, "%.2f", wetBiomeFactor),
                "Fog strength: " + String.format(java.util.Locale.ROOT, "%.2f", fog.strength())
        );
        return 1;
    }

    public static int spawnFog(CommandSourceStack source, float strength, int seconds) {
        ServerPlayer player = PaCommandSupport.requirePlayer(source, "Fog override is only available to players.");
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(Component.literal("Fog debug override is only available in the Overworld."));
            return 0;
        }

        int durationTicks = seconds * 20;
        PacketDistributor.sendToPlayer(player, new FogDebugOverridePacket(strength, durationTicks));
        PaCommandMessages.success(
                source,
                true,
                "Fog override applied",
                "Strength: " + String.format(java.util.Locale.ROOT, "%.2f", strength),
                "Duration: " + seconds + " s"
        );
        return 1;
    }

    public static int clearFog(CommandSourceStack source) {
        ServerPlayer player = PaCommandSupport.requirePlayer(source, "Fog override is only available to players.");
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(Component.literal("Fog debug override is only available in the Overworld."));
            return 0;
        }

        PacketDistributor.sendToPlayer(player, new FogDebugOverridePacket(0.0F, 0));
        PaCommandMessages.success(source, true, "Fog override cleared");
        return 1;
    }
}
