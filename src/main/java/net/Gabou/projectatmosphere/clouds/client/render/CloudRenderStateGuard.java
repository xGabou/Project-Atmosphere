package net.Gabou.projectatmosphere.clouds.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

/**
 * Restores vanilla-friendly GL state after PA fullscreen cloud passes.
 */
public final class CloudRenderStateGuard {
    private static final int MAX_TEXTURE_UNIT_TO_CLEAR = 11;

    private CloudRenderStateGuard() {
    }

    public static void restoreAfterCloudPass() {
        restoreAfterCloudPass(Minecraft.getInstance().getMainRenderTarget());
    }

    public static void restoreAfterCloudPass(RenderTarget targetToBind) {
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableScissor();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        GL11.glCullFace(GL11.GL_BACK);

        clearTextureUnits();

        if (targetToBind != null) {
            targetToBind.bindWrite(true);
        }
    }

    private static void clearTextureUnits() {
        for (int unit = MAX_TEXTURE_UNIT_TO_CLEAR; unit >= 0; unit--) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
            GlStateManager._bindTexture(0);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }
}
