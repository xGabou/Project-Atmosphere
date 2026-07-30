package net.Gabou.projectatmosphere.mixin;

import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
@Mixin(targets = "com.BreadRes.desertstormwarming.client.SandstormDebugBlocker", remap = false)
public class MixinSandstormDebugBlocker {

    /**
     * Disable the PRE overlay handler entirely.
     * @author Gabou
     * @reason Because I said so
     */
    @Overwrite(remap = false)
    public static void onRenderOverlay(RenderGuiEvent.Pre event) {
        // do nothing
    }

    /**
     * Disable the POST overlay handler entirely.
     * @author Gabou
     * @reason Because I said so
     */
    @Overwrite(remap = false)
    public static void onRenderOverlay(RenderGuiEvent.Post event) {
        // do nothing
    }
}
