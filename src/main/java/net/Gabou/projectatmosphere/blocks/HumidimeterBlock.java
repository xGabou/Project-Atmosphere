package net.Gabou.projectatmosphere.blocks;

import net.Gabou.projectatmosphere.util.InstrumentUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public class HumidimeterBlock extends InstrumentBlock {
    public HumidimeterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void display(Level level, Player player) {
        InstrumentUtils.displayHumidity(level, player);
    }
}
