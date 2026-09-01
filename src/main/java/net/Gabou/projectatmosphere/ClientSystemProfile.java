package net.Gabou.projectatmosphere;

import java.util.Locale;
import java.util.regex.Pattern;
import org.lwjgl.opengl.GL11;

public class ClientSystemProfile extends ProjectAtmosphere.SystemProfile {

    // The profile only sizes background work.  It is deliberately conservative for
    // integrated GPUs, but recognizes current discrete AMD and Intel families too.
    private static final Pattern NVIDIA_RTX_20_SERIES =
            Pattern.compile("\\bRTX\\s+20(?:7[0-9]|8[0-9]|9[0-9])0?\\b");
    private static final Pattern NVIDIA_RTX_30_SERIES =
            Pattern.compile("\\bRTX\\s+30(?:[5-9][0-9]|[0-9]{3,})\\b");
    private static final Pattern NVIDIA_RTX_40_OR_NEWER =
            Pattern.compile("\\bRTX\\s+(?:[4-9]\\d{2,}|[1-9]\\d{4,})\\b");

    private static final Pattern AMD_RX_5000_OR_NEWER =
            Pattern.compile("\\b(?:AMD\\s+)?RADEON(?:\\(TM\\))?\\s+RX\\s+[5-9]\\d{3,4}\\b");
    private static final Pattern AMD_HIGH_END_VEGA =
            Pattern.compile("\\b(?:AMD\\s+)?RADEON(?:\\(TM\\))?\\s+(?:VII|VEGA\\s+(?:56|64))\\b");

    private static final Pattern INTEL_ARC_CAPABLE =
            Pattern.compile("\\bINTEL(?:\\(R\\))?\\s+ARC(?:\\(TM\\))?(?:\\s+GRAPHICS)?\\s+(?:A(?:580|[67]\\d{2})|B[5-9]\\d{2,3})\\b");

    @Override
    public String getGPUName() {
        try {
            String renderer = GL11.glGetString(GL11.GL_RENDERER);
            return renderer == null || renderer.isBlank() ? null : renderer.trim();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isGoodEnoughGPU() {
        return isGoodEnoughRenderer(getGPUName());
    }

    /**
     * Kept independent from OpenGL so the policy remains deterministic and can be
     * validated from the renderer string reported by the driver.
     */
    static boolean isGoodEnoughRenderer(String renderer) {
        if (renderer == null || renderer.isBlank()) {
            return false;
        }

        String gpuUpper = renderer.toUpperCase(Locale.ROOT);

        return NVIDIA_RTX_20_SERIES.matcher(gpuUpper).find()
                || NVIDIA_RTX_30_SERIES.matcher(gpuUpper).find()
                || NVIDIA_RTX_40_OR_NEWER.matcher(gpuUpper).find()
                || AMD_RX_5000_OR_NEWER.matcher(gpuUpper).find()
                || AMD_HIGH_END_VEGA.matcher(gpuUpper).find()
                || INTEL_ARC_CAPABLE.matcher(gpuUpper).find();
    }
}
