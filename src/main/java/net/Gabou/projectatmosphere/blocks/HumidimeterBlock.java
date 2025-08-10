package net.Gabou.projectatmosphere.blocks;

import net.Gabou.projectatmosphere.util.InstrumentUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * Block representing a humidimeter that reports relative humidity.
 */
public class HumidimeterBlock extends InstrumentBlock {

    /**
     * Creates a new humidimeter block.
     *
     * @param properties block properties
     */
    public HumidimeterBlock(Properties properties) {
        super(properties);
    }

    /**
     * Displays humidity information to the player.
     *
     * @param level  world level
     * @param player player receiving the data
     */
    @Override
    public void display(Level level, Player player) {
        InstrumentUtils.displayHumidity(level, player);
    }
}
