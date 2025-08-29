package net.Gabou.projectatmosphere.items;

import net.Gabou.projectatmosphere.blocks.DustLayerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class Balai extends DiggerItem {

    public Balai(float attackDamage, float attackSpeed, Tier tier,
                 TagKey<Block> effectiveTag, Item.Properties properties) {
        super(tier, effectiveTag, properties
                .component(DataComponents.TOOL, tier.createToolProperties(effectiveTag))
                .attributes(createAttributes(tier, attackDamage, attackSpeed)));

    }

    public static ItemAttributeModifiers createAttributes(Tier tier, float attackDamage, float attackSpeed) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID,
                                attackDamage + tier.getAttackDamageBonus(),
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID,
                                attackSpeed,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        BlockState state = level.getBlockState(pos);

        // Only work on DustLayerBlock
        if (!(state.getBlock() instanceof DustLayerBlock)) {
            return InteractionResult.PASS;
        }

        int layers = state.getValue(DustLayerBlock.LAYERS);

        if (!level.isClientSide) {
            if (layers > 1) {
                // Decrease layer count
                level.setBlock(pos, state.setValue(DustLayerBlock.LAYERS, layers - 1), 3);
            } else {
                // Remove block if no more layers
                level.removeBlock(pos, false);
            }

            // Play sound
            level.playSound(null, pos, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);

            // Spawn particles
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(
                        ParticleTypes.CLOUD,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        5, 0.2, 0.1, 0.2, 0.01
                );
            }

            // Damage the item if a player is holding it
            if (player != null) {
                context.getItemInHand().hurtAndBreak(
                        1,
                        player,
                        context.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                                ? EquipmentSlot.MAINHAND
                                : EquipmentSlot.OFFHAND
                );
            }


        }

        return InteractionResult.SUCCESS;
    }
}
