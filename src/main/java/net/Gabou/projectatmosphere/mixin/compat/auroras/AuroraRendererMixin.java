package net.Gabou.projectatmosphere.mixin.compat.auroras;

import auroras.util.AuroraData;
import auroras.util.AuroraRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.Gabou.projectatmosphere.compat.auroras.AuroraSeasonHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = AuroraRenderer.class, remap = false)
public abstract class AuroraRendererMixin {

    @ModifyVariable(
            method = "render(Lauroras/util/AuroraData;Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/level/Level;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;F)V",
            at = @At(value = "STORE"),
            ordinal = 0
    )
    private float projectatmosphere$scaleAuroraBrightness(float nlBrightness,
                                                          AuroraData data,
                                                          Minecraft minecraft,
                                                          Level level,
                                                          PoseStack poseStack,
                                                          Matrix4f matrix,
                                                          float partialTicks) {
        if (minecraft == null || minecraft.player == null || level == null) {
            return nlBrightness;
        }
        BlockPos pos = minecraft.player.blockPosition();
        float boost = AuroraSeasonHelper.combinedBoost(level, pos);
        return Math.min(1.0f, nlBrightness * boost);
    }
}
