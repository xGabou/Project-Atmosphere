package net.Gabou.projectatmosphere.items;

import net.Gabou.projectatmosphere.blocks.InstrumentReader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;

public abstract class InstrumentBlockItem extends BlockItem implements InstrumentReader {
    public InstrumentBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && !player.isShiftKeyDown() && context.getLevel().isClientSide) {
            display(context.getLevel(), player); // show HUD
            return InteractionResult.SUCCESS;
        }
        // Shift + right-click = place block as usual
        return super.useOn(context);
    }


    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // Optional: handle right-click in air
        if (!player.isShiftKeyDown() && level.isClientSide) {
            display(level, player);
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }
}
