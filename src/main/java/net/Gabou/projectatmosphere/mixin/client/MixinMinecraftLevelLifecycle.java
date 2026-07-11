package net.Gabou.projectatmosphere.mixin.client;

import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudClientLifecycle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(Minecraft.class)
public class MixinMinecraftLevelLifecycle {
    @Inject(method = "setLevel", at = @At("TAIL"))
    private void projectatmosphere$onClientLevelChanged(@Nullable ClientLevel level, CallbackInfo ci) {
        VolumetricCloudClientLifecycle.onClientLevelChanged();
    }
}
