package net.Gabou.projectatmosphere.command.tree.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class PaCommandMessages {
    public static final String PREFIX = "[Project Atmosphere]";

    private PaCommandMessages() {
    }

    public static Component format(String action, String... lines) {
        StringBuilder builder = new StringBuilder(PREFIX);
        if (action != null && !action.isBlank()) {
            builder.append("\nAction: ").append(action);
        }
        if (lines != null) {
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                builder.append("\n").append(line);
            }
        }
        return Component.literal(builder.toString());
    }

    public static void success(CommandSourceStack source, boolean broadcast, String action, String... lines) {
        source.sendSuccess(() -> format(action, lines), broadcast);
    }

    public static void failure(CommandSourceStack source, String action, String... lines) {
        source.sendFailure(format(action, lines));
    }
}
