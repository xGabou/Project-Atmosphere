package net.Gabou.projectatmosphere.mixin.client;

import dev.nonamecrackers2.simpleclouds.client.shader.buffer.BindingManager;
import it.unimi.dsi.fastutil.ints.IntList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BindingManager.class, remap = false)
public interface BindingManagerAccessor {
    @Accessor("ALL_SHADER_STORAGE_BINDINGS")
    static IntList projectatmosphere$getShaderStorageBindings() {
        throw new AssertionError();
    }
}
