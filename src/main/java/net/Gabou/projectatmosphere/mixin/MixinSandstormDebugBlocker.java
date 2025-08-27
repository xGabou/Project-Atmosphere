package net.Gabou.projectatmosphere.mixin;

import com.BreadRes.desertstormwarming.client.SandstormDebugBlocker;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SandstormDebugBlocker.class)
public class MixinSandstormDebugBlocker {

    /**
     * Disable the PRE overlay handler entirely.
     * @author Gabou
     * @reason Because I said so
     */
    @Overwrite(remap = false)
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        // do nothing
    }

    /**
     * Disable the POST overlay handler entirely.
     * @author Gabou
     * @reason Because I said so
     */
    @Overwrite(remap = false)
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        // do nothing
    }
}
