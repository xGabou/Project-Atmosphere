package net.Gabou.projectatmosphere.mixin.compat.auroras;

import auroras.util.AuroraRenderer;
import net.Gabou.projectatmosphere.compat.auroras.AuroraSeasonHelper;
import net.Gabou.projectatmosphere.client.render.SkyEffectState;
import net.Gabou.projectatmosphere.compat.temperature.ClientTemperatureResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
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
        if (mc == null || mc.player == null || mc.level == null) {
            SkyEffectState.setAurora(false, null);
            return 0.0f;
        }

        Level level = mc.level;
        BlockPos pos = mc.player.blockPosition();
        double time = level.getTimeOfDay(0.0f);
        boolean night = time < 0.25 || time > 0.75;
        Biome biome = level.getBiome(pos).value();
        float tempC = ClientTemperatureResolver.getCelsius(level, pos);
        boolean cold = biome.coldEnoughToSnow(pos) || tempC <= 4.0f;
        if (!night || !cold) {
            SkyEffectState.setAurora(false, null);
            return 0.0f;
        }
        float boost = AuroraSeasonHelper.combinedBoost(level, pos);
        float scaled = Math.min(1.0F, nlBrightness * boost);
        SkyEffectState.setAurora(scaled > 0.01f, mc.player.position());
        return scaled;
    }
}
