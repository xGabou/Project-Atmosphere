package net.Gabou.projectatmosphere.items;

import net.Gabou.projectatmosphere.util.InstrumentUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class Anemometer extends InstrumentBlockItem {
    /**
     * Creates a new anemometer item.
     *
     * @param block      the block associated with this item
     * @param properties the item properties
     */
    public Anemometer(Block block, Properties properties) {
        super(block, properties);
    }

    /**
     * Displays the current wind speed to the player.
     *
     * @param level  the world in which the player resides
     * @param player the player using the item
     */
    @OnlyIn(Dist.CLIENT)
    @Override
    public void display(Level level, Player player) {
        InstrumentUtils.displayWind(level, player);
    }
}
