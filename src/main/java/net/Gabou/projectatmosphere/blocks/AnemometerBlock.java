package net.Gabou.projectatmosphere.blocks;

import net.Gabou.projectatmosphere.util.InstrumentUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * Block representing an anemometer that reports wind speed.
 */
public class AnemometerBlock extends InstrumentBlock {

    /**
     * Creates a new anemometer block.
     *
     * @param properties block properties
     */
    public AnemometerBlock(Properties properties) {
        super(properties);
    }

    /**
     * Displays the current wind information to the player.
     *
     * @param level  world level
     * @param player player receiving the data
     */
    @Override
    public void display(Level level, Player player) {
        InstrumentUtils.displayWind(level, player);
    }
}
