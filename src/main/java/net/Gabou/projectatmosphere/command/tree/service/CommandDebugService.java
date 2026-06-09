package net.Gabou.projectatmosphere.command.tree.service;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages;
import net.minecraft.commands.CommandSourceStack;

public final class CommandDebugService {
    private CommandDebugService() {
    }

    public static int setDebugMode(CommandSourceStack source, boolean enabled) {
        ProjectAtmosphere.DEBUG_MODE = enabled;
        PaCommandMessages.success(
                source,
                true,
                "Debug mode updated",
                "Result: " + (enabled ? "on" : "off")
        );
        return 1;
    }
}
