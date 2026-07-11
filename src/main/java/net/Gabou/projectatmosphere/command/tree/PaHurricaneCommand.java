package net.Gabou.projectatmosphere.command.tree;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class PaHurricaneCommand {
    private PaHurricaneCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(boolean simpleCloudsLoaded) {
        if (!simpleCloudsLoaded) {
            return Commands.literal("hurricane")
                    .executes(ctx -> {
                        ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                                "Hurricane commands require the Simple Clouds backend."
                        ));
                        return 0;
                    });
        }
        return OptionalSimpleCloudsCommands.hurricane();
    }
}
