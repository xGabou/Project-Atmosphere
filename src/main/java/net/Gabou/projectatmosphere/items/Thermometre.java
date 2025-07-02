package net.Gabou.projectatmosphere.items;

import net.Gabou.projectatmosphere.client.HUDOverlayRenderer;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class Thermometre extends Item {
    public Thermometre(Properties p_41383_) {
        super(p_41383_);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level serverWorld, Player player, InteractionHand hand){
        ItemStack itemStack = player.getItemInHand(hand);
        if (serverWorld.isClientSide) {
            double temp = ForecastGenerator.getTemperatureValue(
                    AtmosphereUtils.findNearestBiomeInstanceKeyWithNoMap(
                            AtmosphereUtils.getBiomeLocation(player.blockPosition(), serverWorld),
                            player.blockPosition()
                    ),
                    serverWorld.getDayTime()
            );

            String msg = "Current temperature: " + String.format("%.1f°C", temp);
            HUDOverlayRenderer.showTemperatureOverlay(msg);
        }

        return InteractionResultHolder.success(itemStack);
    }
}
