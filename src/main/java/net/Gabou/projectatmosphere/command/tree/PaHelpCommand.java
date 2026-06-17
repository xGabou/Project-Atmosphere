package net.Gabou.projectatmosphere.command.tree;

import net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class PaHelpCommand {
    private PaHelpCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("help")
                .executes(ctx -> {
                    PaCommandMessages.success(
                            ctx.getSource(),
                            false,
                            "Project Atmosphere Commands",
                            "/pa forecast",
                            "/pa temperature",
                            "/pa humidity",
                            "/pa pressure",
                            "/pa wind",
                            "/pa fog",
                            "/pa cloud",
                            "/pa tornado",
                            "/pa hurricane",
                            "/pa system"
                    );
                    return 1;
                });
    }
}
