package net.Gabou.projectatmosphere.command.tree;

import net.Gabou.projectatmosphere.command.tree.service.CommandSystemService;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class PaSystemCommand {
    private PaSystemCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("system")
                .then(Commands.literal("cpu")
                        .executes(ctx -> CommandSystemService.sendCpuInfo(ctx.getSource())))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandSystemService.reloadSystem(ctx.getSource())))
                .then(Commands.literal("sync")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandSystemService.syncInternal(ctx.getSource())))
                .then(Commands.literal("help")
                        .executes(ctx -> {
                            net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages.success(
                                    ctx.getSource(),
                                    false,
                                    "System commands",
                                    "/pa system cpu",
                                    "/pa system reload",
                                    "/pa system sync"
                            );
                            return 1;
                        }));
    }
}
