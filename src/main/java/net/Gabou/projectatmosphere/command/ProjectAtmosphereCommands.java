package net.Gabou.projectatmosphere.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

/**
 * Legacy root registration shim.
 *
 * @deprecated use {@link net.Gabou.projectatmosphere.command.tree.ProjectAtmosphereCommands}
 */
@Deprecated
public final class ProjectAtmosphereCommands {
    private ProjectAtmosphereCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        net.Gabou.projectatmosphere.command.tree.ProjectAtmosphereCommands.register(dispatcher);
    }
}
