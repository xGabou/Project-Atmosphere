package net.Gabou.projectatmosphere.items;

import net.Gabou.projectatmosphere.blocks.InstrumentReader;
import net.Gabou.projectatmosphere.client.screen.WeatherRadarScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class WeatherRadarItem extends Item implements InstrumentReader {
    public WeatherRadarItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) {
            if (level.isClientSide) {
                display(level, player);
            }
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }

    @Override
    public void display(Level level, Player player) {
        Minecraft.getInstance().setScreen(new WeatherRadarScreen(player));
    }
}
