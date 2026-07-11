package net.Gabou.projectatmosphere.mixin.client;

import com.mojang.blaze3d.shaders.BlendMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlendMode.class)
public interface BlendModeAccessor {
    @Accessor("lastApplied")
    @Mutable
    static void projectatmosphere$setLastApplied(BlendMode blendMode) {
        throw new AssertionError();
    }
}
