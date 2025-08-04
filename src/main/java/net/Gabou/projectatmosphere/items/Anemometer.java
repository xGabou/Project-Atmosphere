package net.Gabou.projectatmosphere.items;

import net.Gabou.projectatmosphere.util.InstrumentUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class Anemometer extends InstrumentBlockItem {
    public Anemometer(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void display(Level level, Player player) {
        InstrumentUtils.displayWind(level, player);
    }
}
