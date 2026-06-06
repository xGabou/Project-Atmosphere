package net.Gabou.projectatmosphere.items;

import net.Gabou.projectatmosphere.clouds.backend.CloudRegionManager;
import net.Gabou.projectatmosphere.clouds.backend.CloudRegionRenderData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

/**
 * Outil de diagnostic pour identifier une région de nuage PA regardée par le joueur.
 * Le test utilise un raymarch CPU simple contre les données backend synchronisables.
 */
public class CloudProbeItem extends Item {

    private static final double MAX_DISTANCE = 50000.0D;
    private static final double STEP = 4.0D;

    /**
     * Crée un nouvel outil de diagnostic des nuages.
     *
     * @param properties propriétés de l'objet
     */
    public CloudProbeItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    /**
     * Sonde la région de nuage PA actuellement visée par le joueur.
     *
     * @param level monde courant
     * @param player joueur qui utilise l'objet
     * @param hand main utilisée
     * @return résultat de l'interaction
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

        CloudRegionRenderData hit = findLookedAtCloud(serverLevel, player, MAX_DISTANCE, STEP);

        if (hit == null) {
            player.sendSystemMessage(Component.literal("No cloud detected in sight.")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.success(stack);
        }

        player.sendSystemMessage(
                Component.literal("Cloud: ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(hit.getRegionId().toString()).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" | radius: " + Math.round(hit.getRadius()))
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(" | center: "
                                        + Math.round(hit.getCenter().x()) + ", "
                                        + Math.round(hit.getCenter().y()) + ", "
                                        + Math.round(hit.getCenter().z()))
                                .withStyle(ChatFormatting.DARK_GRAY))
        );

        return InteractionResultHolder.success(stack);
    }

    /**
     * Cherche la première région de nuage PA intersectée par la direction du regard.
     *
     * @param level monde serveur
     * @param player joueur source
     * @param maxDistance distance maximale du rayon
     * @param step pas d'échantillonnage en blocs
     * @return première région trouvée, ou null
     */
    @Nullable
    private static CloudRegionRenderData findLookedAtCloud(ServerLevel level, Player player, double maxDistance, double step) {
        Collection<CloudRegionRenderData> clouds = CloudRegionManager.getInstance().getActiveRenderData(level);
        if (clouds == null || clouds.isEmpty()) {
            return null;
        }

        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();

        for (double distance = 0.0D; distance <= maxDistance; distance += step) {
            Vec3 sample = eyePos.add(look.scale(distance));
            CloudRegionRenderData hit = findBestMatchingCloud(sample, clouds);
            if (hit != null) {
                return hit;
            }
        }

        return null;
    }

    @Nullable
    private static CloudRegionRenderData findBestMatchingCloud(Vec3 sample,
                                                               Collection<CloudRegionRenderData> candidates) {
        CloudRegionRenderData bestCloud = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (CloudRegionRenderData region : candidates) {
            if (!isSampleInsideRegion(sample, region)) {
                continue;
            }

            double centerX = region.getCenter().x();
            double centerZ = region.getCenter().z();
            double sampleDistanceSq = sample.distanceToSqr(centerX, sample.y, centerZ);

            if (sampleDistanceSq < bestDistanceSq) {
                bestDistanceSq = sampleDistanceSq;
                bestCloud = region;
            }
        }

        return bestCloud;
    }

    /**
     * Teste si un point échantillonné tombe dans l'approximation cylindrique de la région.
     *
     * @param sample point échantillonné
     * @param region région de nuage PA
     * @return true si le point intersecte la région
     */
    private static boolean isSampleInsideRegion(Vec3 sample, CloudRegionRenderData region) {
        if (sample.y < region.getBaseY() || sample.y > region.getTopY()) {
            return false;
        }

        double dx = sample.x - region.getCenter().x();
        double dz = sample.z - region.getCenter().z();
        double horizontalDistanceSq = dx * dx + dz * dz;
        double radius = region.getRadius();
        return horizontalDistanceSq <= radius * radius;
    }
}
