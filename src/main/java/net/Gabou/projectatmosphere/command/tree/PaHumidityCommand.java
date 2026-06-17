package net.Gabou.projectatmosphere.command.tree;

import net.Gabou.projectatmosphere.command.tree.service.CommandHumidityService;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class PaHumidityCommand {
    private PaHumidityCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("humidity")
                .executes(ctx -> CommandHumidityService.sendCurrentHumidity(ctx.getSource()))
                .then(Commands.literal("current")
                        .executes(ctx -> CommandHumidityService.sendCurrentHumidity(ctx.getSource())))
                .then(Commands.literal("week")
                        .executes(ctx -> CommandHumidityService.sendWeeklyHumidity(ctx.getSource())))
                .then(Commands.literal("raw")
                        .executes(ctx -> CommandHumidityService.sendRawHumidity(ctx.getSource())))
                .then(Commands.literal("help")
                        .executes(ctx -> {
                            net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages.success(
                                    ctx.getSource(),
                                    false,
                                    "Humidity commands",
                                    "/pa humidity current",
                                    "/pa humidity week",
                                    "/pa humidity raw"
                            );
                            return 1;
                        }));
    }
}
