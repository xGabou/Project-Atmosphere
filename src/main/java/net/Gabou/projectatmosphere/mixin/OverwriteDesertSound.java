package net.Gabou.projectatmosphere.mixin;

import net.Gabou.projectatmosphere.event.BiomeChangeManager;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(targets = "com.BreadRes.desertstormwarming.client.SandstormSoundScheduler", remap = false)
public class OverwriteDesertSound {

    @Inject(method = "onClientTick", at = @At("HEAD"), cancellable = true)
    private static void onClientTick(ClientTickEvent.Pre event, CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        Player player = Minecraft.getInstance().player;
        if(level == null || player == null) {
            return;
        }
        if(!BiomeChangeManager.isDesert(AtmosphereUtils.getBiomeLocation(player.blockPosition(), level))) {
            ci.cancel();
        }
    }
}
