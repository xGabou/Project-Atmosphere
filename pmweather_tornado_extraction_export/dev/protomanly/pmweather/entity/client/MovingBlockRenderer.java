package dev.protomanly.pmweather.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.protomanly.pmweather.entity.MovingBlock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;

public class MovingBlockRenderer extends EntityRenderer<MovingBlock> {
   private final BlockRenderDispatcher dispatcher;

   public MovingBlockRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 0.5F;
      this.dispatcher = context.getBlockRenderDispatcher();
   }

   public void render(MovingBlock entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
      BlockState blockstate = entity.getBlockState();
      float age = ((float)entity.tickCount + partialTicks) * 5.0F;
      if (blockstate.getRenderShape() == RenderShape.MODEL) {
         Level level = entity.level();
         if (blockstate != level.getBlockState(entity.blockPosition()) && blockstate.getRenderShape() != RenderShape.INVISIBLE) {
            poseStack.pushPose();
            BlockPos pos = BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
            poseStack.mulPose(Axis.XP.rotationDegrees(age * 2.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(age * 2.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(age * 2.0F));
            poseStack.translate((double)-0.5F, (double)0.0F, (double)-0.5F);
            BakedModel model = this.dispatcher.getBlockModel(blockstate);

            for(RenderType renderType : model.getRenderTypes(blockstate, RandomSource.create(blockstate.getSeed(entity.getStartPos())), ModelData.EMPTY)) {
               this.dispatcher.getModelRenderer().tesselateBlock(level, model, blockstate, pos, poseStack, bufferSource.getBuffer(RenderTypeHelper.getMovingBlockRenderType(renderType)), false, RandomSource.create(), blockstate.getSeed(entity.getStartPos()), OverlayTexture.NO_OVERLAY, ModelData.EMPTY, renderType);
            }

            poseStack.popPose();
            super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
         }
      }

   }

   public @NotNull ResourceLocation getTextureLocation(@NotNull MovingBlock movingBlock) {
      return TextureAtlas.LOCATION_BLOCKS;
   }
}
