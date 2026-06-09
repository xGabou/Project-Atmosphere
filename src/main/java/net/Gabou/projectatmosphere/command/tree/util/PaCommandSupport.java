package net.Gabou.projectatmosphere.command.tree.util;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class PaCommandSupport {
    private PaCommandSupport() {
    }

    public static boolean requireOverworld(CommandSourceStack source, ServerLevel level, String message) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            source.sendFailure(Component.literal(message));
            return false;
        }
        return true;
    }

    public static ServerPlayer requirePlayer(CommandSourceStack source, String message) {
        try {
            return source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendFailure(Component.literal(message));
            return null;
        }
    }

    public static BlockPos sourceBlockPos(CommandSourceStack source) {
        return BlockPos.containing(source.getPosition());
    }

    public static ResourceLocation currentBiomeId(ServerLevel level, BlockPos pos) {
        return level.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getKey(level.getBiome(pos).value());
    }

    public static ResourceLocation parseBiomeToken(CommandSourceStack source, ServerLevel level, BlockPos pos, String token) {
        if (token == null || token.isBlank()) {
            return currentBiomeId(level, pos);
        }
        String normalized = token.trim();
        if (normalized.equalsIgnoreCase("current")
                || normalized.equalsIgnoreCase("current_biome")
                || normalized.equalsIgnoreCase("currentbiome")) {
            return currentBiomeId(level, pos);
        }
        ResourceLocation parsed = ResourceLocation.tryParse(normalized);
        if (parsed == null) {
            source.sendFailure(Component.literal("Invalid biome id: " + normalized));
        }
        return parsed;
    }

    public static String formatWind(WindVector wind) {
        if (wind == null) {
            return "0.0 m/s calm";
        }
        return net.Gabou.projectatmosphere.util.UnitFormatter.formatWindSpeed(wind.baseSpeed())
                + " "
                + compassDirection(wind.angleRadians());
    }

    private static String compassDirection(float radians) {
        String[] directions = {
                "N", "NNE", "NE", "ENE",
                "E", "ESE", "SE", "SSE",
                "S", "SSW", "SW", "WSW",
                "W", "WNW", "NW", "NNW"
        };
        double degrees = Math.toDegrees(radians);
        double normalized = (degrees % 360.0 + 360.0) % 360.0;
        int index = (int) Math.round(normalized / 22.5) % directions.length;
        return directions[index];
    }
}
