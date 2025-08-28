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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class WeatherRadarItem extends Item implements InstrumentReader {
    /**
     * Creates a new weather radar item.
     *
     * @param properties the item properties
     */
    public WeatherRadarItem(Properties properties) {
        super(properties);
    }

    /**
     * Opens the weather radar interface when the player right-clicks without sneaking.
     *
     * @param level  the world in which the item is used
     * @param player the player using the item
     * @param hand   the hand holding the item
     * @return the interaction result holder
     */
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

    /**
     * Displays the weather radar screen to the player.
     *
     * @param level  the world in which the player resides
     * @param player the player using the item
     */
    @OnlyIn(Dist.CLIENT)
    @Override
    public void display(Level level, Player player) {
        Minecraft.getInstance().setScreen(new WeatherRadarScreen(player));
    }
}
