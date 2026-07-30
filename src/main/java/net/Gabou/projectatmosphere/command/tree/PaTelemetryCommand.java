package net.Gabou.projectatmosphere.command.tree;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.command.tree.service.CommandTelemetryService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class PaTelemetryCommand {
    private PaTelemetryCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("telemetry")
                .then(Commands.literal("export")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            CommandTelemetryService.exportTelemetry(ctx.getSource());
                            return 1;
                        }));
    }
}
