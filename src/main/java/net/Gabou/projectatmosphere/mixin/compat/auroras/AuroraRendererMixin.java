package net.Gabou.projectatmosphere.mixin.compat.auroras;

import auroras.util.AuroraRenderer;
import net.Gabou.projectatmosphere.compat.auroras.AuroraSeasonHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Safely scales aurora brightness using Project Atmosphere seasonal boosts.
 */
@Mixin(value = AuroraRenderer.class, remap = false)
public abstract class AuroraRendererMixin {

    /**
     * The first float local variable stored in render() is nlBrightness.
     * We intercept it right after it’s calculated.
     */
    @ModifyVariable(
            method = "render(Lauroras/util/AuroraData;Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/level/Level;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;F)V",
            at = @At(value = "STORE"),
            ordinal = 0,
            require = 0 // don’t crash if the target changes
    )
    private float projectatmosphere$scaleAuroraBrightness(float nlBrightness) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null)
            return nlBrightness;

        Level level = mc.level;
        BlockPos pos = mc.player.blockPosition();
        float boost = AuroraSeasonHelper.combinedBoost(level, pos);
        return Math.min(1.0F, nlBrightness * boost);
    }
}
