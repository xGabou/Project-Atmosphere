package net.Gabou.projectatmosphere.blocks;

import net.Gabou.projectatmosphere.util.InstrumentUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class BarometreBlock extends InstrumentBlock {
    public BarometreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void display(Level level, Player player) {
        InstrumentUtils.displayPressure(level, player);
    }
}
