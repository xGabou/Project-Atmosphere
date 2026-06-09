package net.Gabou.projectatmosphere.clouds.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL11;

import java.util.Locale;

/**
 * Gère les futures cibles de rendu des nuages Project Atmosphere.
 * Cette classe ne fait pas de rendu et ne lit jamais le backend.
 */
public final class CloudRenderTargetManager {

    private static RenderTarget cloudColorTarget;
    private static RenderTarget cloudShadowTarget;
    private static boolean ownsCloudColorTarget;
    private static boolean cloudColorTargetHasDepth;

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
        if (cloudColorTarget != null) {
            ProjectAtmosphere.LOGGER.info(
                    "[CloudState] cloudTarget.clear color={} depth={} size={}x{} owned={} depthAttachment={}",
                    cloudColorTarget.getColorTextureId(),
                    cloudColorTarget.getDepthTextureId(),
                    cloudColorTarget.width,
                    cloudColorTarget.height,
                    ownsCloudColorTarget,
                    cloudColorTargetHasDepth
            );
            if (ownsCloudColorTarget) {
                cloudColorTarget.destroyBuffers();
                logGlError("cloud-target-clear");
            }
        }

        cloudColorTarget = null;
        cloudShadowTarget = null;
        ownsCloudColorTarget = false;
        cloudColorTargetHasDepth = false;
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
        int width = Math.max(1, Mth.ceil(mainTarget.width * scale));
        int height = Math.max(1, Mth.ceil(mainTarget.height * scale));
        String recreateReason = null;
        if (cloudColorTarget == null) {
            recreateReason = "missing";
        } else if (cloudColorTarget == mainTarget) {
            recreateReason = "sharedMainTarget";
        } else if (cloudColorTarget.width != width || cloudColorTarget.height != height) {
            recreateReason = "resize";
        } else if (!cloudColorTargetHasDepth) {
            recreateReason = "missingDepth";
        }

        if (recreateReason != null) {
            int previousColorId = cloudColorTarget != null ? cloudColorTarget.getColorTextureId() : -1;
            int previousDepthId = cloudColorTarget != null ? cloudColorTarget.getDepthTextureId() : -1;
            int previousWidth = cloudColorTarget != null ? cloudColorTarget.width : -1;
            int previousHeight = cloudColorTarget != null ? cloudColorTarget.height : -1;
            destroyCloudColorTargetIfOwned();
            cloudColorTarget = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            cloudColorTarget.setFilterMode(GL11.GL_LINEAR);
            cloudColorTarget.resize(width, height, Minecraft.ON_OSX);
            cloudColorTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            ownsCloudColorTarget = true;
            cloudColorTargetHasDepth = cloudColorTarget.getDepthTextureId() >= 0;
            ProjectAtmosphere.LOGGER.info(
                    "[CloudState] cloudTarget.create reason={} scale={} main={}x{} prev={}x{} prevColor={} prevDepth={} new={}x{} color={} depth={} owned={} depthAttachment={}",
                    recreateReason,
                    formatFloat(scale),
                    mainTarget.width,
                    mainTarget.height,
                    previousWidth,
                    previousHeight,
                    previousColorId,
                    previousDepthId,
                    cloudColorTarget.width,
                    cloudColorTarget.height,
                    cloudColorTarget.getColorTextureId(),
                    cloudColorTarget.getDepthTextureId(),
                    ownsCloudColorTarget,
                    cloudColorTargetHasDepth
            );
            logGlError("cloud-target-create");
        }

        if (cloudShadowTarget == null || cloudShadowTarget != mainTarget) {
            cloudShadowTarget = mainTarget;
        }
    }

    private static void destroyCloudColorTargetIfOwned() {
        if (cloudColorTarget != null && ownsCloudColorTarget) {
            cloudColorTarget.destroyBuffers();
            ownsCloudColorTarget = false;
        }
    }

    private static void logGlError(String context) {
        int error = GL11.glGetError();
        if (error == GL11.GL_NO_ERROR) {
            return;
        }

        ProjectAtmosphere.LOGGER.warn(
                "[CloudState] glError context={} code=0x{}",
                context,
                String.format(Locale.ROOT, "%04X", error)
        );
    }

    private static String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
