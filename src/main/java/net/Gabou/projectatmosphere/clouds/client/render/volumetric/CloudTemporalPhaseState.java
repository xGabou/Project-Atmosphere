package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * T207. The four-phase temporal cycle's bookkeeping.
 *
 * <p>One subpixel phase is marched per frame into its own raw target, and the
 * camera it was seen from and the frame it was rendered on are recorded here.
 * That record is what makes the resolve non-recursive: each phase is
 * reprojected <em>once</em> from its own origin frame rather than inherited
 * through a chain of resamples. T165 measured the difference on the banked
 * captures: once-reprojected raw phases keep 62-84% of the stationary lattice
 * gain, a re-resampled accumulation buffer collapses it to 11%. Nothing writes
 * a resolved frame back through this class; only a raw march is recorded.
 *
 * <p>Anything that makes old samples meaningless comes through
 * {@link #invalidateAll()} - a resize, a level or dimension change, a resource
 * reload, a teleport or camera cut, the pass disabled. The cost of forgetting
 * is three frames at single-phase quality; the cost of not forgetting is a
 * ghost, so it is deliberately blunt.
 */
public final class CloudTemporalPhaseState {
    /** Phases in the 2x2 lattice: four quarter-pixel offsets of one phase texel. */
    public static final int PHASES = 4;
    /**
     * Frames a recorded phase stays usable. Phases cycle every rendered
     * frame, so a valid phase is at most three frames old; anything older
     * means frames were skipped and the phase is stale.
     */
    public static final int MAX_AGE_FRAMES = 3;

    /**
     * Subpixel offsets in phase pixels, ordered so that phase index
     * {@code (x&1) + 2*(y&1)} owns lattice pixel {@code (x,y)} of the
     * resolved target: a negative offset lands on the lower lattice pixel
     * of the pair. True screen-space offsets of the ray, applied to the
     * march's texture coordinate before the ray is built.
     */
    private static final float[][] PHASE_JITTER = {
            {-0.25F, -0.25F},
            {0.25F, -0.25F},
            {-0.25F, 0.25F},
            {0.25F, 0.25F}
    };

    private static final Matrix4f[] viewProj = new Matrix4f[PHASES];
    private static final Vector3f[] cameraPos = new Vector3f[PHASES];
    private static final long[] frame = new long[PHASES];
    private static final boolean[] valid = new boolean[PHASES];
    /** E39: samples averaged into each phase target since it was last replaced. */
    private static final int[] accumulated = new int[PHASES];
    private static final float[] materialX = new float[PHASES];
    private static final float[] materialZ = new float[PHASES];
    private static float pendingWeight = 1.0F;
    public static String lastReason = "";
    private static int currentPhase;
    private static long generation;
    private static long invalidations;

    static {
        for (int i = 0; i < PHASES; i++) {
            viewProj[i] = new Matrix4f();
            cameraPos[i] = new Vector3f();
        }
    }

    private CloudTemporalPhaseState() {
    }

    public static int currentPhase() {
        return currentPhase;
    }

    public static float jitterX(int phase) {
        return PHASE_JITTER[phase & 3][0];
    }

    public static float jitterY(int phase) {
        return PHASE_JITTER[phase & 3][1];
    }

    /** True when the phase holds a raw frame rendered within {@link #MAX_AGE_FRAMES} of {@code currentFrame}. */
    public static boolean valid(int phase, long currentFrame) {
        int index = phase & 3;
        return valid[index] && currentFrame - frame[index] <= MAX_AGE_FRAMES
                && currentFrame >= frame[index];
    }

    public static Matrix4f viewProj(int phase) {
        return viewProj[phase & 3];
    }

    public static Vector3f cameraPos(int phase) {
        return cameraPos[phase & 3];
    }

    /** Advances to the next phase. Called once per rendered cloud frame, before the march. */
    public static void advance() {
        currentPhase = (currentPhase + 1) & 3;
    }

    /**
     * Records the camera the phase just marched was seen from and the frame
     * it was rendered on. Called after a successful raw march only - a phase
     * holding a failed frame would be resolved as content.
     */
    public static void record(int phase, Matrix4f frameViewProj, Vector3f camera, long frameIndex) {
        int index = phase & 3;
        viewProj[index].set(frameViewProj);
        cameraPos[index].set(camera);
        frame[index] = frameIndex;
        valid[index] = true;
        accumulated[index] = pendingWeight >= 1.0F ? 1 : accumulated[index] + 1;
    }

    /**
     * E39. The blend weight of this frame's march into the phase target: 1 replaces it (and restarts the
     * average), below 1 averages it with the same phase's previous result. Averaging is only allowed when the
     * phase's rays are the SAME rays - same camera, same projection, cloud material moved less than
     * {@code maxMaterialDrift} blocks - so nothing is resampled (T165: a resampled accumulation buffer loses
     * the lattice gain) and nothing ghosts. The weight never drops below {@code minWeight}, which bounds how
     * long a slow change (wind, a growing cloud) takes to show.
     */
    public static float accumulationWeight(int phase, Matrix4f frameViewProj, Vector3f camera, long frameIndex,
            float materialOffsetX, float materialOffsetZ, float minWeight, float maxMaterialDrift) {
        int index = phase & 3;
        // The phase about to be re-marched was last rendered one full cycle (PHASES frames) ago; valid()'s
        // MAX_AGE_FRAMES is the resolve's bound after this frame's record, one frame too strict here.
        boolean recent = valid[index] && frameIndex >= frame[index] && frameIndex - frame[index] <= PHASES;
        boolean sameRays = recent
                && cameraPos[index].distanceSquared(camera) < 1.0e-4F
                && viewProj[index].equals(frameViewProj, 1.0e-5F)
                && Math.abs(materialOffsetX - materialX[index]) < maxMaterialDrift
                && Math.abs(materialOffsetZ - materialZ[index]) < maxMaterialDrift;
        // E39: why a phase replaced instead of averaging (for a debug HUD / capture log).
        lastReason = !recent ? "invalid"
                : cameraPos[index].distanceSquared(camera) >= 1.0e-4F ? "camera"
                : !viewProj[index].equals(frameViewProj, 1.0e-5F) ? "viewproj"
                : (Math.abs(materialOffsetX - materialX[index]) >= maxMaterialDrift
                        || Math.abs(materialOffsetZ - materialZ[index]) >= maxMaterialDrift) ? "drift"
                : accumulated[index] <= 0 ? "empty" : "same";
        float weight = sameRays && accumulated[index] > 0
                ? Math.max(1.0F / (accumulated[index] + 1.0F), minWeight)
                : 1.0F;
        if (weight >= 1.0F) {
            // A replaced phase starts its drift reference over.
            materialX[index] = materialOffsetX;
            materialZ[index] = materialOffsetZ;
        }
        pendingWeight = weight;
        return weight;
    }

    /** Drops every phase; the next three frames resolve at single-phase quality. */
    public static void invalidateAll() {
        boolean any = false;
        for (int i = 0; i < PHASES; i++) {
            any |= valid[i];
            valid[i] = false;
            accumulated[i] = 0;
        }
        generation++;
        if (any) {
            invalidations++;
        }
    }

    /**
     * E41. How far the per-phase averages have converged: 0 right after any phase was replaced (motion, a cut,
     * drift), 1 once every valid phase averages {@code fullSamples} renders. The resolve's motion history fades
     * out with it, so a camera that stops keeps the history until the phases have caught up, and a long-static
     * camera is back on the exact non-recursive path.
     */
    public static float convergence(long currentFrame, int fullSamples) {
        int least = Integer.MAX_VALUE;
        for (int i = 0; i < PHASES; i++) {
            least = Math.min(least, valid(i, currentFrame) ? accumulated[i] : 0);
        }
        return Math.max(0.0F, Math.min(1.0F, (least - 1.0F) / Math.max(1.0F, fullSamples - 1.0F)));
    }

    public static int accumulatedCount(int phase) {
        return accumulated[phase & 3];
    }

    public static long generation() {
        return generation;
    }

    /** How many times a populated phase set was dropped this session, for lifecycle evidence. */
    public static long invalidations() {
        return invalidations;
    }

    /** Number of phases holding usable content at {@code currentFrame}, for reporting. */
    public static int validCount(long currentFrame) {
        int count = 0;
        for (int i = 0; i < PHASES; i++) {
            if (valid(i, currentFrame)) {
                count++;
            }
        }
        return count;
    }
}
