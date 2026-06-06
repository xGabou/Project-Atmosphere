package net.Gabou.projectatmosphere.clouds.frontend;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;

/**
 * Gère les futures cibles de rendu des nuages Project Atmosphere.
 * Cette classe ne fait pas de rendu et ne lit jamais le backend.
 */
public final class CloudRenderTargetManager {

    private static RenderTarget cloudColorTarget;
    private static RenderTarget cloudShadowTarget;

    private CloudRenderTargetManager() {

    }

    /**
     * Prépare les render targets pour la taille actuelle de la fenêtre.
     */
    public static void prepareTargets() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.getWindow() == null) {
            return;
        }

        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();

        ensureTargets(width, height);
    }

    /**
     * Supprime les render targets quand elles ne sont plus nécessaires.
     */
    public static void clearTargets() {
        RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();

        if (cloudColorTarget != null && cloudColorTarget != mainTarget) {
            cloudColorTarget.destroyBuffers();
        }

        if (cloudShadowTarget != null && cloudShadowTarget != mainTarget) {
            cloudShadowTarget.destroyBuffers();
        }

        cloudColorTarget = null;
        cloudShadowTarget = null;
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
    private static void ensureTargets(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }

        if (cloudColorTarget == null || cloudColorTarget.width != width || cloudColorTarget.height != height) {
            RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
            if (cloudColorTarget != null && cloudColorTarget != mainTarget) {
                cloudColorTarget.destroyBuffers();
            }

            cloudColorTarget = mainTarget;
        }

        if (cloudShadowTarget == null) {
            cloudShadowTarget = Minecraft.getInstance().getMainRenderTarget();
        }
    }
}
