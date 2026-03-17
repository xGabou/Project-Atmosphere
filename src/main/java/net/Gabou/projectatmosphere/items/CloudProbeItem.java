package net.Gabou.projectatmosphere.items;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Debug tool used to identify the cloud region the player is looking at.
 * This does not rely on hitboxes. Instead, it ray marches forward from the
 * player's eyes and checks whether sampled positions fall inside a cloud region.
 */
public class CloudProbeItem extends Item {

    private static final double MAX_DISTANCE = 50000.0D;
    private static final double STEP = 4.0D;
    private static final double VERTICAL_TOLERANCE = 96.0D;

    /**
     * Creates a new cloud probe item.
     *
     * @param properties the item properties
     */
    public CloudProbeItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    /**
     * Right click to probe the cloud the player is currently aiming at.
     *
     * @param level  the level
     * @param player the player
     * @param hand   the used hand
     * @return interaction result
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.pass(stack);
        }

        CloudRegion hit = findLookedAtCloud(serverLevel, player, MAX_DISTANCE, STEP);

        if (hit == null) {
            player.sendSystemMessage(Component.literal("No cloud detected in sight.")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.success(stack);
        }

        ResourceLocation cloudType = hit.getCloudTypeId();
        String cloudName = cloudType == null ? "unknown" : cloudType.toString();

        player.sendSystemMessage(
                Component.literal("Cloud: ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(cloudName).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" | radius: " + Math.round(hit.getWorldRadius()))
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(" | center: "
                                        + Math.round(hit.getWorldX()) + ", "
                                        + Math.round(hit.getWorldZ()))
                                .withStyle(ChatFormatting.DARK_GRAY))
        );

        return InteractionResultHolder.success(stack);
    }

    /**
     * Performs a custom ray march from the player's eye position to find the first
     * cloud region intersected by the look direction.
     *
     * @param level       the server level
     * @param player      the player
     * @param maxDistance maximum ray distance
     * @param step        step size in blocks
     * @return the first matching cloud region, or null
     */
    @Nullable
    private static CloudRegion findLookedAtCloud(ServerLevel level, Player player, double maxDistance, double step) {
        CloudManager<?> cloudManager = CloudManager.get(level);
        if (cloudManager == null) {
            return null;
        }

        List<CloudRegion> clouds = cloudManager.getClouds();
        if (clouds == null || clouds.isEmpty()) {
            return null;
        }

        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        List<CloudRegion> containingClouds = new ArrayList<>();

        for (CloudRegion region : clouds) {
            if (isSampleInsideRegion(eyePos, region, cloudManager.getCloudHeight())) {
                containingClouds.add(region);
            }
        }

        for (double distance = step; distance <= maxDistance; distance += step) {
            Vec3 sample = eyePos.add(look.scale(distance));
            CloudRegion hit = findBestMatchingCloud(sample, clouds, containingClouds, cloudManager.getCloudHeight());
            if (hit != null) {
                return hit;
            }
        }

        if (containingClouds.isEmpty()) {
            return null;
        }

        for (double distance = 0.0D; distance <= maxDistance; distance += step) {
            Vec3 sample = eyePos.add(look.scale(distance));
            CloudRegion hit = findBestMatchingCloud(sample, containingClouds, List.of(), cloudManager.getCloudHeight());
            if (hit != null) {
                return hit;
            }
        }

        return null;
    }

    @Nullable
    private static CloudRegion findBestMatchingCloud(Vec3 sample,
                                                     List<CloudRegion> candidates,
                                                     List<CloudRegion> excluded,
                                                     int cloudHeight) {
        CloudRegion bestCloud = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (CloudRegion region : candidates) {
            if (excluded.contains(region) || !isSampleInsideRegion(sample, region, cloudHeight)) {
                continue;
            }

            double centerX = region.getWorldX();
            double centerZ = region.getWorldZ();
            double sampleDistanceSq = sample.distanceToSqr(centerX, sample.y, centerZ);

            if (sampleDistanceSq < bestDistanceSq) {
                bestDistanceSq = sampleDistanceSq;
                bestCloud = region;
            }
        }

        return bestCloud;
    }

    /**
     * Tests whether a sampled world position should count as being inside a cloud region.
     * Since the cloud does not expose a vanilla hitbox, we approximate using horizontal radius
     * and a configurable vertical tolerance around the cloud layer.
     *
     * @param sample the sampled point along the ray
     * @param region the cloud region
     * @param level  the level
     * @return true if the sample intersects the region approximation
     */
    private static boolean isSampleInsideRegion(Vec3 sample, CloudRegion region, int cloudHeight) {
        double dx = sample.x - region.getWorldX();
        double dz = sample.z - region.getWorldZ();
        double horizontalDistanceSq = dx * dx + dz * dz;
        double radius = region.getWorldRadius();

        if (horizontalDistanceSq > radius * radius) {
            return false;
        }

        return Math.abs(sample.y - cloudHeight) <= VERTICAL_TOLERANCE;
    }
}
