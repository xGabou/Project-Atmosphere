package net.Gabou.projectatmosphere.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import net.Gabou.projectatmosphere.tools.debug.ShaderSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class CloudDumpCommand {

    @SubscribeEvent
    public static void onRegisterClientCommand(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("dumpcloud")
                        .executes(ctx -> {
                            Minecraft.getInstance().execute(CloudDumpCommand::dumpCloudTexture);

                            return 1;
                        })
        );
    }


    private static void dumpCloudTexture() {
        var renderer = SimpleCloudsRenderer.getInstance();
        var target = renderer.getCloudTarget();

        int texId = target.getColorTextureId();
        int width = target.width;
        int height = target.height;

        ShaderSnapshot.captureTexture(texId, width, height, "cumulonimbus_snapshot");
    }
}
