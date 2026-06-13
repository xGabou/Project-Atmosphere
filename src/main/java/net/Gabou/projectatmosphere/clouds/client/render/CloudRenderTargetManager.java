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
    private static final int SHADOW_TARGET_SIZE = 64;

    private static RenderTarget cloudColorTarget;
    private static RenderTarget cloudShadowTarget;
    private static final RenderTarget[] cloudHistoryTargets = new RenderTarget[2];
    private static int cloudHistoryReadIndex;
    private static boolean ownsCloudColorTarget;
    private static boolean cloudColorTargetHasDepth;
    private static boolean cloudHistoryValid;

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
     * Explicit resize entrypoint called by the client renderer lifecycle.
     */
    public static void onResize(int width, int height) {
        if (cloudColorTarget == null) {
            return;
        }
        prepareTargets();
    }

    /**
     * Clears GPU-owned targets when the client world changes.
     */
    public static void onLevelChanged() {
        clearTargets();
    }

    /**
     * Releases GPU-owned targets during renderer shutdown.
     */
    public static void shutdown() {
        clearTargets();
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

        destroyHistoryTargets();
        if (cloudShadowTarget != null) {
            cloudShadowTarget.destroyBuffers();
        }
        cloudColorTarget = null;
        cloudShadowTarget = null;
        ownsCloudColorTarget = false;
        cloudColorTargetHasDepth = false;
        cloudHistoryReadIndex = 0;
        cloudHistoryValid = false;
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

    public static RenderTarget getCloudHistoryReadTarget() {
        return cloudHistoryTargets[cloudHistoryReadIndex];
    }

    public static RenderTarget getCloudHistoryWriteTarget() {
        return cloudHistoryTargets[1 - cloudHistoryReadIndex];
    }

    public static boolean isCloudHistoryValid() {
        return cloudHistoryValid
                && cloudHistoryTargets[0] != null
                && cloudHistoryTargets[1] != null;
    }

    public static void invalidateCloudHistory() {
        cloudHistoryValid = false;
    }

    public static void swapCloudHistoryTargets() {
        cloudHistoryReadIndex = 1 - cloudHistoryReadIndex;
        cloudHistoryValid = true;
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

        ensureHistoryTargets(width, height);

        if (cloudShadowTarget == null || cloudShadowTarget.width != SHADOW_TARGET_SIZE || cloudShadowTarget.height != SHADOW_TARGET_SIZE) {
            if (cloudShadowTarget != null) {
                cloudShadowTarget.destroyBuffers();
            }
            cloudShadowTarget = new TextureTarget(SHADOW_TARGET_SIZE, SHADOW_TARGET_SIZE, false, Minecraft.ON_OSX);
            cloudShadowTarget.setFilterMode(GL11.GL_LINEAR);
            cloudShadowTarget.resize(SHADOW_TARGET_SIZE, SHADOW_TARGET_SIZE, Minecraft.ON_OSX);
            cloudShadowTarget.setClearColor(1.0F, 1.0F, 1.0F, 1.0F);
            cloudShadowTarget.clear(Minecraft.ON_OSX);
        }
    }

    private static void ensureHistoryTargets(int width, int height) {
        boolean recreate = false;
        for (RenderTarget target : cloudHistoryTargets) {
            if (target == null || target.width != width || target.height != height) {
                recreate = true;
                break;
            }
        }

        if (!recreate) {
            return;
        }

        destroyHistoryTargets();
        for (int i = 0; i < cloudHistoryTargets.length; i++) {
            RenderTarget target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
            target.resize(width, height, Minecraft.ON_OSX);
            target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            target.clear(Minecraft.ON_OSX);
            cloudHistoryTargets[i] = target;
        }
        cloudHistoryReadIndex = 0;
        cloudHistoryValid = false;
        ProjectAtmosphere.LOGGER.info(
                "[CloudState] cloudHistory.create size={}x{} readColor={} writeColor={}",
                width,
                height,
                cloudHistoryTargets[0].getColorTextureId(),
                cloudHistoryTargets[1].getColorTextureId()
        );
        logGlError("cloud-history-create");
    }

    private static void destroyCloudColorTargetIfOwned() {
        if (cloudColorTarget != null && ownsCloudColorTarget) {
            cloudColorTarget.destroyBuffers();
            ownsCloudColorTarget = false;
        }
    }

    private static void destroyHistoryTargets() {
        for (int i = 0; i < cloudHistoryTargets.length; i++) {
            RenderTarget target = cloudHistoryTargets[i];
            if (target != null) {
                target.destroyBuffers();
                cloudHistoryTargets[i] = null;
            }
        }
        cloudHistoryValid = false;
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
