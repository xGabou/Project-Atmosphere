package net.Gabou.projectatmosphere.items;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.client.HUDOverlayRenderer;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class WindChecker extends Item {
    public WindChecker(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            BiomeInstanceKey key = new BiomeInstanceKey(
                    AtmosphereUtils.getBiomeLocation(player.blockPosition(), level),
                    player.blockPosition()
            );

            String msg;
            if (ForecastGenerator.hasForecast(key)) {
                WindVector wind = ForecastOrchestrator.getCurrentWind(key);
                msg = "Wind: " + String.format("%.1fm/s at %.0f°", wind.speed(), Math.toDegrees(wind.angleRadians()));
            } else {
                ProjectAtmosphere.LOGGER.warn("Missing wind data for biome {} at {}", key.biomeType(), key.samplePos());
                msg = "Wind: Loading...";
            }
            HUDOverlayRenderer.showTemperatureOverlay(msg);
        }
        return InteractionResultHolder.success(stack);
    }
}
