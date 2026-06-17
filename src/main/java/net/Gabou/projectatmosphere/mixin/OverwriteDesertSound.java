package net.Gabou.projectatmosphere.mixin;

import com.BreadRes.desertstormwarming.client.SandstormSoundScheduler;
import com.BreadRes.desertstormwarming.logic.SandstormUtils;
import com.BreadRes.desertstormwarming.sounds.SandstormSounds;
import net.Gabou.projectatmosphere.event.BiomeChangeManager;
import net.Gabou.projectatmosphere.modules.sandStorm.SandStormAPI;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(value = SandstormSoundScheduler.class, remap = false)
public class OverwriteDesertSound {

    @Inject(method = "onClientTick", at = @At("HEAD"), cancellable = true)
    private static void onClientTick(TickEvent.ClientTickEvent event, CallbackInfo ci) {
        if( event.phase != TickEvent.Phase.START || event.side.isServer()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        Player player = Minecraft.getInstance().player;
        if(level == null || player == null) {
            return;
        }
        if(!BiomeChangeManager.isDesert(AtmosphereUtils.getBiomeLocation(player.blockPosition(), level)) || SandstormUtils.getDesertProximity(player) <= 0.0F) {
            SandstormSounds.getSoundsForPhase(SandStormAPI.getSandstormPhase()).forEach(soundEvent -> Minecraft.getInstance().getSoundManager().stop(soundEvent.getLocation(),null));
            ci.cancel();

        }
    }
}
