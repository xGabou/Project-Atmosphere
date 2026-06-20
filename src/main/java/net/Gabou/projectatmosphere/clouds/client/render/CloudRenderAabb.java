package net.Gabou.projectatmosphere.clouds.client.render;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderFrameContext;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class CloudRenderAabb {
    private static final int LOG_INTERVAL_TICKS = 20;
    private static long lastLoggedWorldTime = Long.MIN_VALUE;

    private CloudRenderAabb() {
    }

    public static @Nullable Bounds compute(@Nullable CloudRenderSnapshot snapshot) {
        if (snapshot == null || !snapshot.isEnabled()) {
            return null;
        }

        Vec3 center = snapshot.getRegionCenter();
        float radius = snapshot.getRegionRadius();
        float baseY = snapshot.getCloudBaseY();
        float topY = snapshot.getCloudTopY();
        if (center == null || radius <= 0.0F || topY <= baseY) {
            return null;
        }

        CloudShapeProfile shape = snapshot.getShapeProfile() == null ? CloudShapeProfile.DEFAULT : snapshot.getShapeProfile();
        float heightRange = Math.max(topY - baseY, 0.001F);
        float volumePaddingY = smoothStep(1.20F, 3.20F, snapshot.getHeightSquash())
                * Math.min(28.0F, Math.max(heightRange * 0.45F, radius * 0.035F));
        float shapeRadiusScale = Mth.clamp(shape.getBaseRadius() / Math.max(radius, 1.0F), 1.0F, 1.45F);
        float lobeReach = Mth.lerp(Mth.clamp(shape.getLobeStrength(), 0.0F, 1.0F), 1.16F, 1.42F);
        float anvilReach = 1.0F + snapshot.getAnvilStrength() * shape.getAnvilSpread() * 0.82F;
        float volumeRadius = radius * Math.min(1.58F, Math.max(Math.max(shapeRadiusScale, lobeReach), anvilReach));

        Vec3 min = new Vec3(center.x() - volumeRadius, baseY - volumePaddingY, center.z() - volumeRadius);
        Vec3 max = new Vec3(center.x() + volumeRadius, topY + volumePaddingY, center.z() + volumeRadius);
        return new Bounds(center, radius, baseY, topY, volumeRadius, volumePaddingY, min, max);
    }

    public static void logOneCloud(@NotNull CloudRenderFrameContext frameContext, @Nullable CloudRenderSnapshot snapshot) {
        if (!CloudRenderDebugMode.current().isActive()) {
            return;
        }

        long worldTime = frameContext.getWorldTime();
        if (worldTime == lastLoggedWorldTime || worldTime % LOG_INTERVAL_TICKS != 0L) {
            return;
        }

        lastLoggedWorldTime = worldTime;
        Bounds bounds = compute(snapshot);
        if (bounds == null) {
            ProjectAtmosphere.LOGGER.info("[CloudAABB] worldTime={} cloud=none", worldTime);
            return;
        }

        ProjectAtmosphere.LOGGER.info(
                "[CloudAABB] worldTime={} mode={} center={} radius={} baseY={} topY={} min={} max={} volumeRadius={} paddingY={}",
                worldTime,
                CloudRenderDebugMode.current().serializedName(),
                format(bounds.center()),
                fmt(bounds.regionRadius()),
                fmt(bounds.baseY()),
                fmt(bounds.topY()),
                format(bounds.min()),
                format(bounds.max()),
                fmt(bounds.volumeRadius()),
                fmt(bounds.volumePaddingY())
        );
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        float t = Mth.clamp((value - edge0) / Math.max(edge1 - edge0, 0.0001F), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static String format(Vec3 value) {
        return fmt((float) value.x()) + "," + fmt((float) value.y()) + "," + fmt((float) value.z());
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public record Bounds(
            @NotNull Vec3 center,
            float regionRadius,
            float baseY,
            float topY,
            float volumeRadius,
            float volumePaddingY,
            @NotNull Vec3 min,
            @NotNull Vec3 max
    ) {
    }
}
