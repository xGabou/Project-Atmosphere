package net.Gabou.projectatmosphere.blocks;

import com.mojang.serialization.MapCodec;
import net.Gabou.projectatmosphere.util.InstrumentUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/**
 * Block representing a thermometer that reports temperature.
 */
public class ThermometerBlock extends InstrumentBlock {

    public static final MapCodec<ThermometerBlock> CODEC = simpleCodec(ThermometerBlock::new);


    /**
     * Creates a new thermometer block.
     *
     * @param properties block properties
     */
    public ThermometerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends InstrumentBlock> codec() {
        return CODEC;
    }

    /**
     * Displays temperature information to the player.
     *
     * @param level  world level
     * @param player player receiving the data
     */
    @Override
    public void display(Level level, Player player) {
        InstrumentUtils.displayTemperature(level, player);
    }
}
