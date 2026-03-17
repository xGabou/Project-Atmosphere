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

public class CloudProbeItem extends Item {
    private static final double MAX_DISTANCE = 50000.0D;
    private static final double STEP = 4.0D;
    private static final double VERTICAL_TOLERANCE = 96.0D;

    public CloudProbeItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

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
            player.sendSystemMessage(Component.literal("No cloud detected in sight.").withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.success(stack);
        }

        ResourceLocation cloudType = hit.getCloudTypeId();
        String cloudName = cloudType == null ? "unknown" : cloudType.toString();
        player.sendSystemMessage(
                Component.literal("Cloud: ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(cloudName).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" | radius: " + Math.round(hit.getWorldRadius())).withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(" | center: " + Math.round(hit.getWorldX()) + ", " + Math.round(hit.getWorldZ()))
                                .withStyle(ChatFormatting.DARK_GRAY))
        );
        return InteractionResultHolder.success(stack);
    }

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
    private static CloudRegion findBestMatchingCloud(
            Vec3 sample,
            List<CloudRegion> candidates,
            List<CloudRegion> excluded,
            int cloudHeight
    ) {
        CloudRegion bestCloud = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (CloudRegion region : candidates) {
            if (excluded.contains(region) || !isSampleInsideRegion(sample, region, cloudHeight)) {
                continue;
            }

            double sampleDistanceSq = sample.distanceToSqr(region.getWorldX(), sample.y, region.getWorldZ());
            if (sampleDistanceSq < bestDistanceSq) {
                bestDistanceSq = sampleDistanceSq;
                bestCloud = region;
            }
        }

        return bestCloud;
    }

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
