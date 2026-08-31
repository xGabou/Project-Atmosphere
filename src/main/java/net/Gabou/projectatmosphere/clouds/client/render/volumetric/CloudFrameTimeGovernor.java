package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

/**
 * Frame-time governor: when the cloud raymarch consistently exceeds its GPU
 * budget, the step budget is scaled down one notch; when it stays well under
 * budget it recovers. Prevents cloud cost spikes from tanking weak machines.
 */
public final class CloudFrameTimeGovernor {
    private static final float BUDGET_MS = 4.2F;
    private static final int OVER_BUDGET_TRIGGER_FRAMES = 40;
    private static final int UNDER_BUDGET_RECOVER_FRAMES = 400;
    private static final float MIN_SCALE = 0.5F;

    private float stepScale = 1.0F;
    private int overBudgetFrames;
    private int underBudgetFrames;

    /**
     * Test-only pin. T098's acceptance captures are static poses whose whole
     * purpose is to judge what the renderer produces, but the governor had
     * saturated at MIN_SCALE for every frame of the 2026-08-30 campaign, so
     * those captures measured the governor's worst quality rather than the
     * renderer. Pinning holds the scale for the duration of a capture set.
     * Only {@code StormT098CaptureDriver} sets this, and only behind its
     * marker file.
     */
    private Float pinnedStepScale;

    /**
     * Feeds one GPU timing sample and returns the current step scale.
     *
     * @param gpuMilliseconds last measured raymarch GPU time, or negative when unavailable
     * @return step budget multiplier in [0.5, 1.0]
     */
    public float update(float gpuMilliseconds) {
        if (pinnedStepScale != null) {
            return pinnedStepScale;
        }
        if (gpuMilliseconds < 0.0F) {
            return stepScale;
        }
        if (gpuMilliseconds > BUDGET_MS) {
            overBudgetFrames++;
            underBudgetFrames = 0;
            if (overBudgetFrames >= OVER_BUDGET_TRIGGER_FRAMES && stepScale > MIN_SCALE) {
                stepScale = Math.max(MIN_SCALE, stepScale - 0.125F);
                overBudgetFrames = 0;
            }
        } else if (gpuMilliseconds < BUDGET_MS * 0.5F) {
            underBudgetFrames++;
            overBudgetFrames = 0;
            if (underBudgetFrames >= UNDER_BUDGET_RECOVER_FRAMES && stepScale < 1.0F) {
                stepScale = Math.min(1.0F, stepScale + 0.125F);
                underBudgetFrames = 0;
            }
        } else {
            overBudgetFrames = Math.max(0, overBudgetFrames - 1);
            underBudgetFrames = Math.max(0, underBudgetFrames - 1);
        }
        return stepScale;
    }

    public float stepScale() {
        return pinnedStepScale != null ? pinnedStepScale : stepScale;
    }

    /** Test-only: holds the step scale at {@code scale} until {@link #unpin()}. */
    public void pin(float scale) {
        pinnedStepScale = Math.max(MIN_SCALE, Math.min(1.0F, scale));
    }

    /** Test-only: releases a {@link #pin(float)} and resumes normal governing. */
    public void unpin() {
        pinnedStepScale = null;
    }

    public void reset() {
        stepScale = 1.0F;
        overBudgetFrames = 0;
        underBudgetFrames = 0;
    }
}
