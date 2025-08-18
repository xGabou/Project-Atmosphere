package net.Gabou.projectatmosphere.client.screen;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;

import java.util.List;

public class WeatherRadarScreen extends Screen {
    private static final int MAP_SIZE = 128;
    private static final int RANGE = 2048;
    private static final int FORECAST_TICKS = 20 * 30;

    private final Player player;
    private final CloudManager<?> cloudManager;
    private final float scale;

    public WeatherRadarScreen(Player player) {
        super(Component.translatable("item.projectatmosphere.weather_radar"));
        this.player = player;
        this.cloudManager = CloudManager.get(player.level());
        this.scale = (float) RANGE / MAP_SIZE;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        int left = (this.width - MAP_SIZE) / 2;
        int top = (this.height - MAP_SIZE) / 2;
        guiGraphics.fill(left, top, left + MAP_SIZE, top + MAP_SIZE, 0xFF000000);

        if (cloudManager != null) {
            List<CloudRegion> clouds = cloudManager.getClouds();
            for (CloudRegion region : clouds) {
                float dx = (region.getWorldX() - (float) player.getX()) / scale;
                float dz = (region.getWorldZ() - (float) player.getZ()) / scale;
                int r = Math.max(1, Math.round(region.getWorldRadius() / scale));
                int x = left + MAP_SIZE / 2 + Math.round(dx);
                int y = top + MAP_SIZE / 2 + Math.round(dz);
                guiGraphics.fill(x - r, y - r, x + r, y + r, 0x80FFFFFF);

                Vec2 dir = region.getMovementDirection();
                float speed = region.getMaxSpeed();
                float futureX = region.getWorldX() + dir.x * speed * FORECAST_TICKS;
                float futureZ = region.getWorldZ() + dir.y * speed * FORECAST_TICKS;
                float fdx = (futureX - (float) player.getX()) / scale;
                float fdz = (futureZ - (float) player.getZ()) / scale;
                int fx = left + MAP_SIZE / 2 + Math.round(fdx);
                int fy = top + MAP_SIZE / 2 + Math.round(fdz);
                guiGraphics.fill(fx - r, fy - r, fx + r, fy + r, 0x40FF0000);
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
