package net.Gabou.projectatmosphere.blocks;

import net.Gabou.projectatmosphere.items.Balai;
import net.Gabou.projectatmosphere.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class DustLayerBlock extends SnowLayerBlock {
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, 8);

    public DustLayerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LAYERS, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    @Override
    public boolean isCollisionShapeFullBlock(BlockState state, BlockGetter getter, BlockPos pos) {
        return false;
    }
    @Override
    public @NotNull List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        ItemStack tool = builder.getParameter(LootContextParams.TOOL);

        if (tool != null && tool.getItem() instanceof Balai) {
            int layers = state.getValue(LAYERS);
            return Collections.singletonList(new ItemStack(ModItems.DUST.get(), layers));
        }

        
        return Collections.emptyList();
    }


}
