package net.Gabou.projectatmosphere.clouds.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL11;

/**
 * Gère les futures cibles de rendu des nuages Project Atmosphere.
 * Cette classe ne fait pas de rendu et ne lit jamais le backend.
 */
public final class CloudRenderTargetManager {

    private static RenderTarget cloudColorTarget;
    private static RenderTarget cloudShadowTarget;
    private static boolean ownsCloudColorTarget;

    private CloudRenderTargetManager() {

    }

    /**
     * Prépare les render targets pour la taille actuelle de la fenêtre.
     */
    public static void prepareTargets() {
        prepareTargets(CloudRenderProfile.createDefault());
    }

    public static void prepareTargets(CloudRenderProfile profile) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.getMainRenderTarget() == null) {
            return;
        }

        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        float resolutionScale = profile != null ? profile.getResolutionScale() : 1.0F;

        ensureTargets(mainTarget, resolutionScale);
    }

    /**
     * Supprime les render targets quand elles ne sont plus nécessaires.
     */
    public static void clearTargets() {
        RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();

        if (cloudColorTarget != null && ownsCloudColorTarget) {
            cloudColorTarget.destroyBuffers();
        }

        cloudColorTarget = null;
        cloudShadowTarget = null;
        ownsCloudColorTarget = false;
    }

    /**
     * Retourne la cible couleur des nuages.
     *
     * @return render target couleur, ou null si elle n'existe pas
     */
    public static RenderTarget getCloudColorTarget() {
        return cloudColorTarget;
    }

    /**
     * Retourne la cible d'ombre des nuages.
     *
     * @return render target shadow, ou null si elle n'existe pas
     */
    public static RenderTarget getCloudShadowTarget() {
        return cloudShadowTarget;
    }

    /**
     * Vérifie ou recrée les render targets selon la taille demandée.
     *
     * @param width largeur cible
     * @param height hauteur cible
     */
    private static void ensureTargets(RenderTarget mainTarget, float resolutionScale) {
        if (mainTarget == null || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return;
        }

        float scale = Mth.clamp(resolutionScale, 0.10F, 1.0F);
        if (scale >= 0.999F) {
            destroyCloudColorTargetIfOwned(mainTarget);
            cloudColorTarget = mainTarget;
            ownsCloudColorTarget = false;
        } else {
            int width = Math.max(1, Mth.ceil(mainTarget.width * scale));
            int height = Math.max(1, Mth.ceil(mainTarget.height * scale));
            if (cloudColorTarget == null
                    || cloudColorTarget == mainTarget
                    || cloudColorTarget.width != width
                    || cloudColorTarget.height != height) {
                destroyCloudColorTargetIfOwned(mainTarget);
                cloudColorTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
                cloudColorTarget.setFilterMode(GL11.GL_LINEAR);
                cloudColorTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                ownsCloudColorTarget = true;
            }
        }

        if (cloudShadowTarget == null || cloudShadowTarget != mainTarget) {
            cloudShadowTarget = mainTarget;
        }
    }

    private static void destroyCloudColorTargetIfOwned(RenderTarget mainTarget) {
        if (cloudColorTarget != null && ownsCloudColorTarget) {
            cloudColorTarget.destroyBuffers();
            ownsCloudColorTarget = false;
        }
    }
}
