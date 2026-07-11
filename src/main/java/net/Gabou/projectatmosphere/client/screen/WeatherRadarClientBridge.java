package net.Gabou.projectatmosphere.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class WeatherRadarClientBridge {
    private WeatherRadarClientBridge() {
    }

    public static void open(Player player) {
        Minecraft.getInstance().setScreen(new WeatherRadarScreen(player));
    }
}
