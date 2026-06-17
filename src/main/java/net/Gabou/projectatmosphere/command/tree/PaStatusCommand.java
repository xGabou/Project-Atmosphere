package net.Gabou.projectatmosphere.command.tree;

import net.Gabou.projectatmosphere.command.tree.service.CommandStatusService;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class PaStatusCommand {
    private PaStatusCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("status")
                .executes(ctx -> CommandStatusService.sendStatus(ctx.getSource()));
    }
}
