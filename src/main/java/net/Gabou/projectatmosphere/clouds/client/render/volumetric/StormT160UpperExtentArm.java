package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

/**
 * T160 diagnostic-only relaxation of the upper-cloud extent limits.
 *
 * <p>T160 asks one question: is the upper TOWER/ANVIL morphology terminated
 * before the existing profile reaches its natural maximum width and height, or
 * does the existing profile inherently produce the rounded cap the captures
 * show? Answering it needs an arm that gives the <em>existing</em> profile room
 * to continue, without inventing a new one.
 *
 * <p>This holds the four upper extent limits and their relaxed counterparts.
 * It is <strong>off by default and never enabled by production code</strong> -
 * only {@code StormT160UpperEnvelopeSandbox} switches it on, around a single
 * measurement, and switches it back. With the flag off every accessor returns
 * the shipped constant, so {@link StormLobeEvaluator} evaluates exactly the
 * expressions it evaluated before this class existed.
 *
 * <p>Deliberately <em>not</em> relaxed, because T160 forbids confounding the
 * measurement with them: render distance, renderer safety bounds, march budget,
 * internal resolution, lighting, detail noise, erosion, the anvil's 1.56 minor
 * widening, and every non-upper role parameter.
 *
 * <p>Nothing here is a proposed shipping value. The relaxed numbers are chosen
 * large enough to make the profile's natural behaviour unambiguous, which is
 * the opposite of a tuning candidate. Final morphology values belong to T098b.
 */
final class StormT160UpperExtentArm {

    /**
     * Height fraction at which the anvil's radius lerp saturates. Above this
     * the shipped profile has constant radius, which is the shape the
     * measurement is interrogating.
     */
    static final double ANVIL_RADIUS_KNEE = 0.62D;
    static final double ANVIL_RADIUS_KNEE_RELAXED = 0.98D;

    /** Upper endpoint of the anvil's radius lerp, in profile-radius units. */
    static final double ANVIL_RADIUS_MAX = 2.10D;
    static final double ANVIL_RADIUS_MAX_RELAXED = 4.20D;

    /** Height fraction at which the anvil's vertical shape begins to fade. */
    static final double ANVIL_FADE_START = 0.76D;
    static final double ANVIL_FADE_START_RELAXED = 0.94D;

    /** Height fraction at which the tower's vertical shape begins to fade. */
    static final double TOWER_FADE_START = 0.72D;
    static final double TOWER_FADE_START_RELAXED = 0.90D;

    /** Blocks the anvil's role envelope extends above its descriptor top. */
    static final double ANVIL_TOP_EXTENSION = 16.0D;
    static final double ANVIL_TOP_EXTENSION_RELAXED = 120.0D;

    /**
     * Analytic maximum of the anvil's radius profile. Relaxed alongside the
     * profile itself: this feeds {@code horizontalReachBlocks}, and leaving it
     * at the shipped value under a widened profile would make the spatial
     * index's conservative bound unsound and silently drop material - which
     * would be indistinguishable from the truncation being measured.
     */
    static final double ANVIL_MAX_PROFILE_RADIUS = 2.18D;
    static final double ANVIL_MAX_PROFILE_RADIUS_RELAXED = 4.30D;

    private static boolean relaxed;

    private StormT160UpperExtentArm() {
    }

    static boolean relaxed() {
        return relaxed;
    }

    /** Diagnostic use only; production never calls this. */
    static void setRelaxed(boolean value) {
        relaxed = value;
    }

    static double anvilRadiusKnee() {
        return relaxed ? ANVIL_RADIUS_KNEE_RELAXED : ANVIL_RADIUS_KNEE;
    }

    static double anvilRadiusMax() {
        return relaxed ? ANVIL_RADIUS_MAX_RELAXED : ANVIL_RADIUS_MAX;
    }

    static double anvilFadeStart() {
        return relaxed ? ANVIL_FADE_START_RELAXED : ANVIL_FADE_START;
    }

    static double towerFadeStart() {
        return relaxed ? TOWER_FADE_START_RELAXED : TOWER_FADE_START;
    }

    static double anvilTopExtension() {
        return relaxed ? ANVIL_TOP_EXTENSION_RELAXED : ANVIL_TOP_EXTENSION;
    }

    static double anvilMaxProfileRadius() {
        return relaxed ? ANVIL_MAX_PROFILE_RADIUS_RELAXED : ANVIL_MAX_PROFILE_RADIUS;
    }
}
