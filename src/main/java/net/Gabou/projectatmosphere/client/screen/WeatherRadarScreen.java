package net.Gabou.projectatmosphere.client.screen;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneManager;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;

import java.util.List;

public class WeatherRadarScreen extends Screen {
    private static final int MAP_SIZE = 256; // higher resolution map for more detail
    private static final int RANGE = 2048;   // world blocks covered at zoom 1.0
    private static final int FORECAST_TICKS = 20 * 30;
    private static final int MAX_RENDER_RADIUS_PX = 24; // cap to avoid huge fill cost
    private static final int MIN_RENDER_RADIUS_PX = 1;
    private static final int MAX_BLOBS_PER_REGION = 3; // breakup circles into a few blobs

    private final Player player;
    private final CloudManager<?> cloudManager;
    private float zoom = 1.0f; // zoom > 1 shows more detail (less blocks per pixel)

    public WeatherRadarScreen(Player player) {
        super(Component.translatable("item.projectatmosphere.weather_radar"));
        this.player = player;
        this.cloudManager = CloudManager.get(player.level());
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
        float scale = ((float) RANGE / MAP_SIZE) / Math.max(0.25f, zoom); // blocks per pixel
        guiGraphics.fill(left, top, left + MAP_SIZE, top + MAP_SIZE, 0xFF000000);

        if (cloudManager != null) {
            List<CloudRegion> clouds = cloudManager.getClouds();
            for (CloudRegion region : clouds) {
                float dx = (region.getWorldX() - (float) player.getX()) / scale;
                float dz = (region.getWorldZ() - (float) player.getZ()) / scale;
                int r = Math.max(MIN_RENDER_RADIUS_PX, Math.round(region.getWorldRadius() / scale));
                r = Math.min(MAX_RENDER_RADIUS_PX, r);
                int x = left + MAP_SIZE / 2 + Math.round(dx);
                int y = top + MAP_SIZE / 2 + Math.round(dz);

                int severity = CloudLibrary.getSeverityFromRessourceLocation(region.getCloudTypeId());
                String cloudId = region.getCloudTypeId().getPath();
                boolean thunder = CloudLibrary.isThunderCloud(cloudId);

                int colorRgb = classifyColor(cloudId, severity, thunder);
                int alpha = Math.max(0x50, Math.min(0xE0, 0x20 + Math.round((severity / 7.0f) * 0xCF)));

                long seed = (long) (region.getWorldX() * 31) ^ (long) (region.getWorldZ() * 131) ^ cloudId.hashCode();
                java.util.Random rand = new java.util.Random(seed);

                int blobs = 1 + rand.nextInt(MAX_BLOBS_PER_REGION);
                for (int i = 0; i < blobs; i++) {
                    float angle = rand.nextFloat() * (float) Math.PI * 2f;
                    float dist = rand.nextFloat() * (r * 0.4f);
                    int bx = x + Math.round((float) Math.cos(angle) * dist);
                    int by = y + Math.round((float) Math.sin(angle) * dist);

                    float scaleBlob = 0.6f + rand.nextFloat() * 0.6f; // 60%..120%
                    int rx = Math.max(MIN_RENDER_RADIUS_PX, Math.round(r * scaleBlob));
                    int ry = Math.max(MIN_RENDER_RADIUS_PX, Math.round(r * (0.7f + rand.nextFloat() * 0.6f))); // ellipse aspect

                    int jittered = jitterColor(rand, colorRgb, thunder ? 28 : 18);
                    int localAlpha = clamp(0x40, 0xE0, alpha + rand.nextInt(33) - 16);
                    int color = (localAlpha << 24) | jittered;
                    fillEllipse(guiGraphics, bx, by, rx, ry, color);
                }

                Vec2 dir = region.getMovementDirection();
                float speed = region.getMaxSpeed();
                float futureX = region.getWorldX() + dir.x * speed * FORECAST_TICKS;
                float futureZ = region.getWorldZ() + dir.y * speed * FORECAST_TICKS;
                float fdx = (futureX - (float) player.getX()) / scale;
                float fdz = (futureZ - (float) player.getZ()) / scale;
                int fx = left + MAP_SIZE / 2 + Math.round(fdx);
                int fy = top + MAP_SIZE / 2 + Math.round(fdz);
                int forecastColor = ((alpha / 2) << 24) | colorRgb;
                fillEllipse(guiGraphics, fx, fy, Math.max(1, r / 2), Math.max(1, r / 3), forecastColor);
            }
        }

        // Overlay: Tornadoes (purple) and Hurricanes (black)
        for (TornadoInstance t : TornadoManager.getClientTornadoes()) {
            float dx = (float) ((t.position.x - player.getX()) / scale);
            float dz = (float) ((t.position.z - player.getZ()) / scale);
            int r = Math.max(2, Math.round(t.radius / scale));
            int x = left + MAP_SIZE / 2 + Math.round(dx);
            int y = top + MAP_SIZE / 2 + Math.round(dz);
            int color = (0xC0 << 24) | 0x800080; // semi-opaque purple
            fillEllipse(guiGraphics, x, y, Math.max(2, r), Math.max(2, (int) (r * 0.7f)), color);
        }

        for (var h : HurricaneManager.getClientHurricanes()) {
            float dx = (float) ((h.position.x - player.getX()) / scale);
            float dz = (float) ((h.position.z - player.getZ()) / scale);
            int r = Math.max(3, Math.round(h.radius / scale));
            int x = left + MAP_SIZE / 2 + Math.round(dx);
            int y = top + MAP_SIZE / 2 + Math.round(dz);
            int color = (0xC0 << 24) | 0x000000; // semi-opaque black
            fillEllipse(guiGraphics, x, y, Math.max(3, r), Math.max(3, (int) (r * 0.8f)), color);
        }

        // Legend (top-left of the map)
        int legendX = left + 4;
        int legendY = top + 4;
        drawLegendEntry(guiGraphics, legendX, legendY, 0xFFFFFF, "Clouds"); legendY += 10;
        drawLegendEntry(guiGraphics, legendX, legendY, 0x00FF00, "Rain"); legendY += 10;
        drawLegendEntry(guiGraphics, legendX, legendY, 0x006400, "Intense Rain"); legendY += 10;
        drawLegendEntry(guiGraphics, legendX, legendY, 0xFFFF00, "Thunderstorm"); legendY += 10;
        drawLegendEntry(guiGraphics, legendX, legendY, 0xFF0000, "Severe TS"); legendY += 10;
        drawLegendEntry(guiGraphics, legendX, legendY, 0x800080, "Tornado"); legendY += 10;
        drawLegendEntry(guiGraphics, legendX, legendY, 0x000000, "Hurricane");

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private static int classifyColor(String cloudId, int severity, boolean thunder) {
        // White: generic clouds (<=4)
        // Green: rain (5)
        // Dark green: intense rain (6 and not thunder)
        // Yellow: small thunderstorm (low thunder clouds)
        // Red: intense thunderstorm (severe thunder ids)
        if (thunder) {
            if (isSevereThunderId(cloudId)) return 0xFF0000; // red
            return 0xFFFF00; // yellow
        }
        if (severity >= 6) return 0x006400; // dark green
        if (severity >= 5) return 0x00FF00; // green
        return 0xFFFFFF; // white
    }

    private static boolean isSevereThunderId(String id) {
        return id.equals("severe_cumulonimbus") || id.equals("dense_tsegrus") || id.equals("dark_wall");
    }

    private void drawLegendEntry(GuiGraphics g, int x, int y, int rgb, String label) {
        int color = (0xFF << 24) | rgb;
        g.fill(x, y, x + 6, y + 6, color);
        g.drawString(this.font, label, x + 8, y - 1, 0xFFFFFF, false);
    }

    private void fillCircle(GuiGraphics g, int cx, int cy, int r, int argb) {
        if (r <= 0) return;
        int r2 = r * r;
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.floor(Math.sqrt(Math.max(0, r2 - dy * dy)));
            g.fill(cx - dx, cy + dy, cx + dx, cy + dy + 1, argb);
        }
    }

    private void fillEllipse(GuiGraphics g, int cx, int cy, int rx, int ry, int argb) {
        rx = Math.max(1, rx);
        ry = Math.max(1, ry);
        int rx2 = rx * rx;
        int ry2 = ry * ry;
        for (int dy = -ry; dy <= ry; dy++) {
            int y2 = dy * dy;
            double term = 1.0 - (double) y2 / (double) ry2;
            if (term < 0) continue;
            int dx = (int) Math.floor(Math.sqrt(term * rx2));
            g.fill(cx - dx, cy + dy, cx + dx, cy + dy + 1, argb);
        }
    }

    private static int jitterColor(java.util.Random rand, int rgb, int range) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = clamp(0, 255, r + rand.nextInt(range * 2 + 1) - range);
        g = clamp(0, 255, g + rand.nextInt(range * 2 + 1) - range);
        b = clamp(0, 255, b + rand.nextInt(range * 2 + 1) - range);
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(int min, int max, int v) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Zoom in/out with +/- keys
        if (keyCode == 334 || keyCode == 81 /*KP+ or '='*/ ) { // increase zoom
            zoom = Math.min(8.0f, zoom * 1.25f);
            return true;
        } else if (keyCode == 333 || keyCode == 82 /*KP- or '-'*/ ) { // decrease zoom
            zoom = Math.max(0.25f, zoom / 1.25f);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
