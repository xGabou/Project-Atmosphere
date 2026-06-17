package net.Gabou.projectatmosphere.mixin.client;

import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import it.unimi.dsi.fastutil.ints.IntList;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SimpleCloudsRenderer.class, remap = false)
public abstract class SimpleCloudsRendererLightningBufferMixin {
    @Unique
    private static final Logger PROJECTATMOSPHERE$LOGGER = LogManager.getLogger(ProjectAtmosphere.MODID + "/SimpleCloudsCompat");

    @Unique
    private int projectatmosphere$lightningBufferCountOverride;
    @Unique
    private boolean projectatmosphere$loggedLightningBufferCap;

    @Inject(method = {"onResourceManagerReload", "m_6213_"}, at = @At("HEAD"), require = 0)
    private void projectatmosphere$resetLightningBufferCount(CallbackInfo ci) {
        this.projectatmosphere$lightningBufferCountOverride = 0;
    }

    @ModifyConstant(method = {"onResourceManagerReload", "m_6213_"}, constant = @Constant(intValue = 3), require = 0)
    private int projectatmosphere$capLightningBufferCount(int original) {
        if (this.projectatmosphere$lightningBufferCountOverride > 0) {
            return this.projectatmosphere$lightningBufferCountOverride;
        }

        IntList usedBindings = BindingManagerAccessor.projectatmosphere$getShaderStorageBindings();
        int maxBindings = GL11.glGetInteger(GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS);
        int usablePositiveBindings = Math.max(0, maxBindings - 1);
        int availableBindings = usablePositiveBindings - usedBindings.size();
        int capped = Math.max(1, Math.min(original, availableBindings));
        this.projectatmosphere$lightningBufferCountOverride = capped;

        if (capped < original && !this.projectatmosphere$loggedLightningBufferCap) {
            this.projectatmosphere$loggedLightningBufferCap = true;
            PROJECTATMOSPHERE$LOGGER.warn(
                    "Capping Simple Clouds lightning SSBO buffers from {} to {} because this client has {} shader-storage bindings and {} are already reserved before lightning setup.",
                    original,
                    capped,
                    maxBindings,
                    usedBindings.size()
            );
        }

        return capped;
    }
}
