package net.Gabou.projectatmosphere.blocks;

import net.Gabou.projectatmosphere.util.InstrumentUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Block representing a barometer that shows atmospheric pressure.
 */
public class BarometreBlock extends InstrumentBlock {

    /**
     * Creates a new barometer block.
     *
     * @param properties block properties
     */
    public BarometreBlock(Properties properties) {
        super(properties);
    }

    /**
     * Displays the current atmospheric pressure to the player.
     *
     * @param level  world level
     * @param player player receiving the data
     */
    @Override
    public void display(Level level, Player player) {
        InstrumentUtils.displayPressure(level, player);
    }
}
