package net.Gabou.projectatmosphere.command.tree.service;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandSupport;
import net.Gabou.projectatmosphere.telemetry.verification.VerificationCollector;
import net.Gabou.projectatmosphere.telemetry.verification.VerificationFormatter;
import net.Gabou.projectatmosphere.telemetry.verification.VerificationReport;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

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

    public static int runVerification(CommandSourceStack source, boolean snapshot) {
        ServerLevel level = source.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            PaCommandMessages.failure(source, "Verification", "Verification is only available in the Overworld.");
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        VerificationReport report = VerificationCollector.collect(level, pos);
        String output = snapshot
                ? VerificationFormatter.formatSnapshot(report)
                : VerificationFormatter.formatFull(report);
        source.sendSuccess(() -> Component.literal(output), false);
        return 1;
    }
}
