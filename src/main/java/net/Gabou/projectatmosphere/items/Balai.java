package net.Gabou.projectatmosphere.items;

import net.Gabou.projectatmosphere.blocks.DustLayerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class Balai extends DiggerItem {

    /**
     * Creates a new broom item capable of sweeping dust layers.
     *
     * @param p_204108_ attack damage modifier
     * @param p_204109_ attack speed modifier
     * @param p_204110_ tool tier
     * @param p_204111_ effective block tag
     * @param p_204112_ item properties
     */
    public Balai(float p_204108_, float p_204109_, Tier p_204110_, TagKey<Block> p_204111_, Properties p_204112_) {
        super(p_204108_, p_204109_, p_204110_, p_204111_, p_204112_);
    }

    /**
     * Removes a dust layer at the targeted position and plays a sweeping animation.
     *
     * @param context the use-on context
     * @return the interaction result
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        BlockState state = level.getBlockState(pos);

        if (!(state.getBlock() instanceof DustLayerBlock)) {
            return InteractionResult.PASS;
        }

        int layers = state.getValue(DustLayerBlock.LAYERS);

        if (!level.isClientSide) {
            if (layers > 1) {
                level.setBlock(pos, state.setValue(DustLayerBlock.LAYERS, layers - 1), 3);
            } else {
                level.removeBlock(pos, false);
            }

            
            level.playSound(null, pos, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
            ((ServerLevel) level).sendParticles(ParticleTypes.CLOUD,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    5, 0.2, 0.1, 0.2, 0.01);


            
            context.getItemInHand().hurtAndBreak(1, player, p -> p.broadcastBreakEvent(context.getHand()));
        }

        return InteractionResult.SUCCESS;
    }

}
