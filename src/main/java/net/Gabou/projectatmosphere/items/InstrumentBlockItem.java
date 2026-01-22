package net.Gabou.projectatmosphere.items;

import net.Gabou.projectatmosphere.blocks.InstrumentReader;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public abstract class InstrumentBlockItem extends BlockItem implements InstrumentReader {
    /**
     * Creates a new instrument item.
     *
     * @param block      the block associated with this item
     * @param properties the item properties
     */
    public InstrumentBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    /**
     * Uses the item on a block, displaying the instrument reading when the player is not sneaking.
     *
     * @param context the use-on context
     * @return the interaction result
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && !player.isShiftKeyDown()) {
            if (!context.getLevel().isClientSide) {
                display(context.getLevel(), player);
            }
            return InteractionResult.SUCCESS;
        }
        
        return super.useOn(context);
    }


    /**
     * Handles right-click interactions, displaying the instrument reading when the player is not sneaking.
     *
     * @param level  the world in which the item is used
     * @param player the player using the item
     * @param hand   the hand holding the item
     * @return the interaction result holder
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {

        if (!player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                display(level, player);
            }
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }
}
