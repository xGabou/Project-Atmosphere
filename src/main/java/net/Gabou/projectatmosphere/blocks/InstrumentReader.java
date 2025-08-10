package net.Gabou.projectatmosphere.blocks;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Represents a block capable of displaying information to a player.
 */
public interface InstrumentReader {

    /**
     * Displays the instrument's data to the specified player.
     *
     * @param level  world level
     * @param player player to receive the data
     */
    void display(Level level, Player player);
}
