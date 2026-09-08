package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * T123's short, on-demand workload readback. Diagnostic frames encode
 * integer per-pixel counter channels; their target-wide sum is the actual
 * executed work for that rendered frame. FINAL rendering never enters this
 * class or pays a readback.
 */
final class StormWorkloadRuntimeCapture {
    private static final int STAGES = 32;
    /** Token value that never identifies an accepted capture. */
    static final long NO_TOKEN = 0L;
    /**
     * T132 freshness token. Every accepted request takes the next value, and a
     * completed {@link WorkloadResult} carries the token of the request that
     * produced it. The consumer matches on that token, so a result left over
     * from an earlier capture of the same view can never be read as the current
     * one. Topology generation is deliberately not used: generation drift is
     * allowed while the structural fingerprint is unchanged, so it identifies
     * neither a capture nor a fixture.
     */
    private static final AtomicLong CAPTURE_SEQUENCE = new AtomicLong();
    private static volatile Request active;
    private static volatile String latest = "not_captured";
    private static volatile WorkloadResult latestResult;

    private StormWorkloadRuntimeCapture() {
    }

    static synchronized String request(String view) {
        return requestCapture(view).status();
    }

    /**
     * Requests a capture and returns the token the caller must later match.
     * Any previously completed result is dropped here too, so a capture that
     * fails without producing a new result cannot expose the old one as
     * current.
     */
    static synchronized CaptureRequest requestCapture(String view) {
        if (active != null) {
            return new CaptureRequest("busy:stage=" + active.stage + "/" + STAGES, NO_TOKEN);
        }
        if (view == null || view.isBlank()) {
            return new CaptureRequest("invalid_view", NO_TOKEN);
        }
        latestResult = null;
        long token = CAPTURE_SEQUENCE.incrementAndGet();
        active = new Request(view.trim().toLowerCase(Locale.ROOT), token);
        latest = "acquiring view=" + active.view + " captureToken=" + token
                + " stage=0/" + STAGES;
        VolumetricCloudRenderer.invalidateHistory();
        return new CaptureRequest(latest, token);
    }

    static boolean active() {
        return active != null;
    }

    /**
     * Abandons an in-flight capture and drops any partial values. A capture the
     * caller has given up on must not stay active: the next request would be
     * refused as busy and the stale request would then finish its remaining
     * stages from frames rendered under a different arm, producing a result
     * stitched from several configurations.
     */
    static synchronized void abort(String reason) {
        Request request = active;
        if (request == null) {
            return;
        }
        active = null;
        latestResult = null;
        latest = "capture_abandoned:view=" + request.view
                + " captureToken=" + request.token
                + " stage=" + request.stage + "/" + STAGES
                + " reason=" + reason;
        VolumetricCloudRenderer.invalidateHistory();
    }

    static VolumetricCloudRaymarchDebugView view() {
        Request request = active;
        int stage = request == null ? 0 : request.stage;
        return switch (stage) {
            case 1 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_SECONDARY;
            case 2 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_TERTIARY;
            case 3 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_QUATERNARY;
            case 4 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_QUINARY;
            case 5 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_ORACLE_DISTANCE;
            case 6 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_ORACLE_STATUS;
            case 7 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_ORACLE_ALPHA_STEPS;
            case 8 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_ORACLE_ALPHA_DENSITY;
            case 9 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_ORACLE_ALPHA_DESCRIPTOR;
            case 10 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_ORACLE_ALPHA_LIGHT;
            case 11 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_ORACLE_ALPHA_DETAIL;
            case 12 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_LIGHT_ATTRIBUTION;
            case 13 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_DETAIL_ATTRIBUTION;
            case 14 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_PRIMARY_DENSITY_A;
            case 15 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_PRIMARY_DENSITY_B;
            case 16 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_REUSE_A;
            case 17 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_REUSE_B;
            case 18 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_REUSE_C;
            case 19 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_LOBE_A;
            case 20 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_LOBE_B;
            case 21 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_DOMINANCE_A;
            case 22 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_DOMINANCE_B;
            case 23 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_CONSUMER_A;
            case 24 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_CONSUMER_B;
            case 25 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_CONSUMER_C;
            case 26 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_SCAN_A;
            case 27 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_SCAN_B;
            case 28 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_SCAN_C;
            case 29 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_SHAPE_A;
            case 30 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_SHAPE_B;
            case 31 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_SHAPE_C;
            default -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_PRIMARY;
        };
    }

    static String latest() {
        return latest;
    }

    /** Most recent completed counter readback, retained for the diagnostic suite. */
    static WorkloadResult latestResult() {
        return latestResult;
    }

    static synchronized void capture(RenderTarget target) {
        Request request = active;
        if (request == null || target == null || !RenderSystem.isOnRenderThread()
                || target.getColorTextureId() <= 0 || target.width <= 0 || target.height <= 0) {
            return;
        }
        FloatBuffer pixels = BufferUtils.createFloatBuffer(target.width * target.height * 4);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.getColorTextureId());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
            double first = 0.0D;
            double second = 0.0D;
            double third = 0.0D;
            double fourth = 0.0D;
            int count = target.width * target.height;
            for (int pixel = 0; pixel < count; pixel++) {
                int offset = pixel * 4;
                first += Math.max(0.0F, pixels.get(offset));
                second += Math.max(0.0F, pixels.get(offset + 1));
                third += Math.max(0.0F, pixels.get(offset + 2));
                fourth += Math.max(0.0F, pixels.get(offset + 3));
            }
            request.values[request.stage][0] = first;
            request.values[request.stage][1] = second;
            request.values[request.stage][2] = third;
            request.values[request.stage][3] = fourth;
            request.width = target.width;
            request.height = target.height;
            request.stage++;
            if (request.stage >= STAGES) {
                latestResult = request.result();
                latest = latestResult.format();
                active = null;
                VolumetricCloudRenderer.invalidateHistory();
            } else {
                latest = "acquiring view=" + request.view
                        + " captureToken=" + request.token
                        + " stage=" + request.stage + "/" + STAGES;
            }
        } catch (RuntimeException exception) {
            // A failed capture must leave no result behind. Previously the last
            // successful WorkloadResult stayed readable while active was
            // cleared, so a consumer matching only on the view name accepted it
            // as the current capture.
            active = null;
            latestResult = null;
            latest = "capture_failed:view=" + request.view
                    + " captureToken=" + request.token
                    + " cause=" + exception.getClass().getSimpleName();
            VolumetricCloudRenderer.invalidateHistory();
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        }
    }

    private static final class Request {
        private final String view;
        private final long token;
        private final double[][] values = new double[STAGES][4];
        private int stage;
        private int width;
        private int height;

        private Request(String view, long token) {
            this.view = view;
            this.token = token;
        }

        private WorkloadResult result() {
            return new WorkloadResult(token, view, width, height,
                    values[1][3], values[0][3],
                    values[0][0], values[0][1], values[0][2],
                    values[1][0], values[1][1], values[1][2],
                    values[2][0], values[2][1], values[2][2], values[2][3],
                    values[3][0], values[3][1], values[3][2], values[3][3],
                    values[4][0], values[4][1], values[4][2], values[4][3],
                    values[5][0], values[5][1], values[5][2], values[5][3],
                    values[6][0], values[6][1], values[6][2], values[6][3],
                    ThresholdWork.of(values[7]),
                    ThresholdWork.of(values[8]),
                    ThresholdWork.of(values[9]),
                    ThresholdWork.of(values[10]),
                    ThresholdWork.of(values[11]),
                    values[12][0], values[12][1], values[12][2], values[12][3],
                    values[13][0], values[13][1], values[13][2], values[13][3],
                    values[14][0], values[14][1], values[14][2], values[14][3],
                    values[15][0], values[15][1], values[15][2], values[15][3],
                    values[16][0], values[16][1], values[16][2], values[16][3],
                    values[17][0], values[17][1], values[17][2], values[17][3],
                    values[18][0], values[18][1],
                    values[19][0], values[19][1], values[19][2], values[19][3],
                    values[20][0], values[20][1],
                    values[21][0], values[21][1], values[21][2], values[21][3],
                    values[22][0], values[22][1], values[22][2],
                    values[23][0], values[23][1], values[23][2], values[23][3],
                    values[24][0], values[24][1], values[24][2], values[24][3],
                    values[25][0],
                    values[26][0], values[26][1], values[26][2], values[26][3],
                    values[27][0], values[27][1], values[27][2], values[27][3],
                    values[28][0],
                    values[29][0], values[29][1], values[29][2], values[29][3],
                    values[30][0], values[30][1], values[30][2], values[30][3],
                    values[31][0], values[31][1]);
        }
    }

    /** Outcome of {@link #requestCapture(String)}: status text plus the token to match. */
    record CaptureRequest(String status, long token) {
        boolean accepted() {
            return token != NO_TOKEN && status.startsWith("acquiring");
        }
    }

    record WorkloadResult(
            long captureToken, String view, int width, int height,
            double conservativeDescriptorRejects, double avoidedDescriptorTextureFetches,
            double primaryRaySteps, double descriptorEvaluations, double descriptorTextureFetches,
            double lightMarchDensityEvaluations, double emptySpaceRejects, double earlyTerminations,
            double directStormShapeCalls, double groupFieldCalls, double lobesVisited,
            double cloudDensityCalls,
            double densityZeroCalls, double segmentTestCalls, double segmentTestPositive,
            double boxBoundRejects, double detailOctaveEvaluations,
            double descriptorCandidateRanks, double descriptorGroupsEntered,
            double descriptorUnionContributors,
            double oracleSkippedDistance, double oraclePreCloudDistance,
            double oracleHoleDistance, double oraclePostCloudDistance,
            double oracleSkipEvents, double oracleIntervalsSeen,
            double oracleOverflowPixels, double oracleOpticalExits,
            ThresholdWork stepsAfterAlpha, ThresholdWork densityAfterAlpha,
            ThresholdWork descriptorAfterAlpha, ThresholdWork lightAfterAlpha,
            ThresholdWork detailAfterAlpha,
            double lightConeMarches, double lightConeTaps,
            double lightConeEarlyOuts, double lightCheapProbes,
            double detailFetchPrimary, double detailFetchLight,
            double detailFetchSecondOctave, double lightMarchBelowFloor,
            double primaryDensityCalls, double primaryDensityZero,
            double primaryDensityNegligible, double primaryDensityLow,
            double primaryDensityMedium, double primaryDensityHigh,
            double primaryMaterialRuns, double primaryZeroRuns,
            double reuseTapsClassified, double reuseTapsEmpty,
            double reuseSufficient, double reusePartial,
            double reuseWrong, double reuseGroupsEnteredInTaps,
            double reuseSuffOrd1, double reuseSuffOrd2,
            double reuseSuffOrd3, double reuseSuffOrd4,
            double lobeExactSdf, double lobeExactSdfNoChange,
            double lobeVisitsLight, double lobeExactSdfLight,
            double lobeCheapRejectLight, double lobeDominanceRejects,
            double domChangeZero, double domChangeBelowEpsilon,
            double domChangeTiny, double domChangeMeaningful,
            double domZeroLight, double domZeroPrimary,
            double domWouldRejectWithExactBlend,
            double probeCalls, double probeGroupWalks, double probeExactSdf,
            double bracketCalls, double bracketGroupWalks, double bracketExactSdf,
            double otherCalls, double otherGroupWalks, double otherExactSdf,
            double refineEvents, double scanEvents, double scanFoundMaterial,
            double scanCapReached,
            double scanProbes1To2, double scanProbes3To4,
            double scanProbes5To8, double scanProbes9To16,
            double scanWastedProbes,
            double shapePrimary, double shapeLight, double shapeProbe,
            double shapeBracket,
            double shapeRefine, double shapeRainSegment, double shapeRainShaft,
            double shapeCamera,
            double shapeLightForward, double shapeUntagged
    ) {
        /** Keeps the pre-T153 deterministic freshness sandbox source-compatible. */
        WorkloadResult(
                long captureToken, String view, int width, int height,
                double conservativeDescriptorRejects, double avoidedDescriptorTextureFetches,
                double primaryRaySteps, double descriptorEvaluations,
                double descriptorTextureFetches, double lightMarchDensityEvaluations,
                double emptySpaceRejects, double earlyTerminations,
                double directStormShapeCalls, double groupFieldCalls, double lobesVisited,
                double cloudDensityCalls, double densityZeroCalls, double segmentTestCalls,
                double segmentTestPositive, double boxBoundRejects,
                double detailOctaveEvaluations) {
            this(captureToken, view, width, height,
                    conservativeDescriptorRejects, avoidedDescriptorTextureFetches,
                    primaryRaySteps, descriptorEvaluations, descriptorTextureFetches,
                    lightMarchDensityEvaluations, emptySpaceRejects, earlyTerminations,
                    directStormShapeCalls, groupFieldCalls, lobesVisited, cloudDensityCalls,
                    densityZeroCalls, segmentTestCalls, segmentTestPositive,
                    boxBoundRejects, detailOctaveEvaluations,
                    0.0D, 0.0D, 0.0D,
                    0.0D, 0.0D, 0.0D, 0.0D,
                    0.0D, 0.0D, 0.0D, 0.0D,
                    ThresholdWork.ZERO, ThresholdWork.ZERO, ThresholdWork.ZERO,
                    ThresholdWork.ZERO, ThresholdWork.ZERO,
                    0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                    // T175 primary density histogram, absent from the legacy shape.
                    0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                    // T177 reuse validity, likewise absent.
                    0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                    // T178 lobe attribution, likewise absent.
                    0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                    // T179 dominance histogram, likewise absent.
                    0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                    // T180 consumer attribution, likewise absent.
                    0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                    // T181 scan distribution, likewise absent.
                    0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                    // T182 shape attribution, likewise absent.
                    0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        private static String ratio(double numerator, double denominator) {
            return denominator <= 0.0D ? "n/a" : String.format("%.4f", numerator / denominator);
        }

        String format() {
            return "T123 workload view=" + view
                    + " captureToken=" + captureToken
                    + " target=" + width + "x" + height
                    + " conservativeDescriptorRejects=" + fmt(conservativeDescriptorRejects)
                    + " avoidedDescriptorTextureFetches=" + fmt(avoidedDescriptorTextureFetches)
                    + " primaryRaySteps=" + fmt(primaryRaySteps)
                    + " descriptorEvaluations=" + fmt(descriptorEvaluations)
                    + " descriptorTextureFetches=" + fmt(descriptorTextureFetches)
                    + " lightMarchDensityEvaluations=" + fmt(lightMarchDensityEvaluations)
                    + " emptySpaceRejects=" + fmt(emptySpaceRejects)
                    + " earlyTerminations=" + fmt(earlyTerminations)
                    + " directStormShapeCalls=" + fmt(directStormShapeCalls)
                    + " groupFieldCalls=" + fmt(groupFieldCalls)
                    + " lobesVisited=" + fmt(lobesVisited)
                    + " cloudDensityCalls=" + fmt(cloudDensityCalls)
                    + " densityZeroCalls=" + fmt(densityZeroCalls)
                    + " segmentTestCalls=" + fmt(segmentTestCalls)
                    + " segmentTestPositive=" + fmt(segmentTestPositive)
                    + " boxBoundRejects=" + fmt(boxBoundRejects)
                    + " detailOctaveEvaluations=" + fmt(detailOctaveEvaluations)
                    + " lightEvaluationsPerPixel=" + perPixel(lightMarchDensityEvaluations)
                    + " detailOctaveEvaluationsPerPixel=" + perPixel(detailOctaveEvaluations)
                    // T168. The traversal question is how many descriptors enter
                    // the loop versus how many change the answer, so both are
                    // reported per density call rather than per pixel.
                    + " descriptorCandidateRanks=" + fmt(descriptorCandidateRanks)
                    + " descriptorGroupsEntered=" + fmt(descriptorGroupsEntered)
                    + " descriptorUnionContributors=" + fmt(descriptorUnionContributors)
                    + " oracleSkippedDistance=" + fmt(oracleSkippedDistance)
                    + " oraclePreCloudDistance=" + fmt(oraclePreCloudDistance)
                    + " oracleHoleDistance=" + fmt(oracleHoleDistance)
                    + " oraclePostCloudDistance=" + fmt(oraclePostCloudDistance)
                    + " oraclePostOpacityDistance=" + fmt(oraclePostOpacityDistance())
                    + " oracleSkipEvents=" + fmt(oracleSkipEvents)
                    + " oracleIntervalsSeen=" + fmt(oracleIntervalsSeen)
                    + " oracleOverflowPixels=" + fmt(oracleOverflowPixels)
                    + " oracleOpticalExits=" + fmt(oracleOpticalExits)
                    + " stepsAfterAlpha=" + stepsAfterAlpha.format()
                    + " densityAfterAlpha=" + densityAfterAlpha.format()
                    + " descriptorAfterAlpha=" + descriptorAfterAlpha.format()
                    + " lightAfterAlpha=" + lightAfterAlpha.format()
                    + " detailAfterAlpha=" + detailAfterAlpha.format()
                    // T169. Taps alone cannot separate "more material sampled"
                    // from "more lighting per sample", so marches and taps are
                    // reported apart, and detail is split by the path that
                    // asked for it.
                    + " lightConeMarches=" + fmt(lightConeMarches)
                    + " lightConeTaps=" + fmt(lightConeTaps)
                    + " lightConeEarlyOuts=" + fmt(lightConeEarlyOuts)
                    + " lightCheapProbes=" + fmt(lightCheapProbes)
                    + " lightMarchBelowFloor=" + fmt(lightMarchBelowFloor)
                    + " tapsPerConeMarch=" + ratio(lightConeTaps, lightConeMarches)
                    + " coneMarchesPerDensityCall=" + ratio(lightConeMarches, cloudDensityCalls)
                    + " detailFetchPrimary=" + fmt(detailFetchPrimary)
                    + " detailFetchLight=" + fmt(detailFetchLight)
                    + " primaryDensityCalls=" + fmt(primaryDensityCalls)
                    + " primaryDensityZero=" + fmt(primaryDensityZero)
                    + " primaryDensityNegligible=" + fmt(primaryDensityNegligible)
                    + " primaryDensityLow=" + fmt(primaryDensityLow)
                    + " primaryDensityMedium=" + fmt(primaryDensityMedium)
                    + " primaryDensityHigh=" + fmt(primaryDensityHigh)
                    + " primaryMaterialRuns=" + fmt(primaryMaterialRuns)
                    + " primaryZeroRuns=" + fmt(primaryZeroRuns)
                    // Derived here so the report cannot restate them wrongly:
                    // T174 conflated primary and lighting density calls and
                    // reported 25.37 per pixel when the primary march makes
                    // 11.28. These ratios are primary-only by construction.
                    + " primaryDensityPerPixel="
                    + ratio(primaryDensityCalls, (double) width * (double) height)
                    + " materialFraction="
                    + ratio(primaryDensityLow + primaryDensityMedium + primaryDensityHigh,
                            primaryDensityCalls)
                    + " emptyFraction="
                    + ratio(primaryDensityZero + primaryDensityNegligible, primaryDensityCalls)
                    + " meanMaterialRun="
                    + ratio(primaryDensityLow + primaryDensityMedium + primaryDensityHigh,
                            primaryMaterialRuns)
                    + " meanEmptyRun="
                    + ratio(primaryDensityZero + primaryDensityNegligible, primaryZeroRuns)
                    + " detailFetchSecondOctave=" + fmt(detailFetchSecondOctave)
                    + " detailFetchLightShare=" + ratio(detailFetchLight,
                            detailFetchPrimary + detailFetchLight)
                    // T177. Reuse validity, derived here for the same reason
                    // the primary ratios are: so a report cannot restate them
                    // wrongly. "Classified" counts only taps that reached the
                    // candidate walk and resolved at least one contributing
                    // group; taps that resolved none are counted separately
                    // rather than being scored as trivially reusable.
                    + " reuseTapsClassified=" + fmt(reuseTapsClassified)
                    + " reuseTapsEmpty=" + fmt(reuseTapsEmpty)
                    + " reuseSufficient=" + fmt(reuseSufficient)
                    + " reusePartial=" + fmt(reusePartial)
                    + " reuseWrong=" + fmt(reuseWrong)
                    + " reuseGroupsEnteredInTaps=" + fmt(reuseGroupsEnteredInTaps)
                    + " reuseSuffOrd1=" + fmt(reuseSuffOrd1)
                    + " reuseSuffOrd2=" + fmt(reuseSuffOrd2)
                    + " reuseSuffOrd3=" + fmt(reuseSuffOrd3)
                    + " reuseSuffOrd4=" + fmt(reuseSuffOrd4)
                    + " reuseValidFraction="
                    + ratio(reuseSufficient, reuseTapsClassified)
                    + " reusePartialFraction="
                    + ratio(reusePartial, reuseTapsClassified)
                    + " reuseWrongFraction="
                    + ratio(reuseWrong, reuseTapsClassified)
                    + " groupsPerLightTap="
                    + ratio(reuseGroupsEnteredInTaps, lightConeTaps)
                    // T178. A lobe VISIT is not an expensive lobe EVALUATION.
                    // Production already rejects some visits cheaply through
                    // the T121 bound; only the lobes that pay a full exact SDF
                    // and then fail to move the union are opportunity.
                    + " lobeExactSdf=" + fmt(lobeExactSdf)
                    + " lobeExactSdfNoChange=" + fmt(lobeExactSdfNoChange)
                    + " lobeVisitsLight=" + fmt(lobeVisitsLight)
                    + " lobeExactSdfLight=" + fmt(lobeExactSdfLight)
                    + " lobeCheapRejectLight=" + fmt(lobeCheapRejectLight)
                    + " lobeDominanceRejects=" + fmt(lobeDominanceRejects)
                    // T179. What each exact SDF did to the union. "Zero" is the
                    // strict ceiling: those lobes could have been skipped with
                    // no image consequence at all.
                    + " domChangeZero=" + fmt(domChangeZero)
                    + " domChangeBelowEpsilon=" + fmt(domChangeBelowEpsilon)
                    + " domChangeTiny=" + fmt(domChangeTiny)
                    + " domChangeMeaningful=" + fmt(domChangeMeaningful)
                    + " domZeroLight=" + fmt(domZeroLight)
                    + " domZeroPrimary=" + fmt(domZeroPrimary)
                    + " domWouldRejectWithExactBlend="
                    + fmt(domWouldRejectWithExactBlend)
                    + " domZeroFraction=" + ratio(domChangeZero, lobeExactSdf)
                    + " domZeroPerGroupWalk=" + ratio(domChangeZero, groupFieldCalls)
                    + " domExactBlendGainPerGroupWalk="
                    + ratio(domWouldRejectWithExactBlend, groupFieldCalls)
                    // T180. Direct consumer attribution. T175 derived the
                    // "48% segment/probe/quadrature" class by subtraction;
                    // these are tagged at the real call sites instead, so the
                    // buckets are disjoint and sum back to the total.
                    + " probeCalls=" + fmt(probeCalls)
                    + " probeGroupWalks=" + fmt(probeGroupWalks)
                    + " probeExactSdf=" + fmt(probeExactSdf)
                    + " bracketCalls=" + fmt(bracketCalls)
                    + " bracketGroupWalks=" + fmt(bracketGroupWalks)
                    + " bracketExactSdf=" + fmt(bracketExactSdf)
                    + " otherCalls=" + fmt(otherCalls)
                    + " otherGroupWalks=" + fmt(otherGroupWalks)
                    + " otherExactSdf=" + fmt(otherExactSdf)
                    + " probeWalkShare=" + ratio(probeGroupWalks, groupFieldCalls)
                    + " bracketWalkShare=" + ratio(bracketGroupWalks, groupFieldCalls)
                    + " otherWalkShare=" + ratio(otherGroupWalks, groupFieldCalls)
                    + " probeSdfShare=" + ratio(probeExactSdf, lobeExactSdf)
                    + " bracketSdfShare=" + ratio(bracketExactSdf, lobeExactSdf)
                    // T181. The probe cap is 16 but the loop exits early, so
                    // these say whether 16 ever binds - and how much of the
                    // scan is duplicated by the fine march that follows it.
                    + " refineEvents=" + fmt(refineEvents)
                    + " scanEvents=" + fmt(scanEvents)
                    + " scanFoundMaterial=" + fmt(scanFoundMaterial)
                    + " scanCapReached=" + fmt(scanCapReached)
                    + " scanProbes1To2=" + fmt(scanProbes1To2)
                    + " scanProbes3To4=" + fmt(scanProbes3To4)
                    + " scanProbes5To8=" + fmt(scanProbes5To8)
                    + " scanProbes9To16=" + fmt(scanProbes9To16)
                    + " scanWastedProbes=" + fmt(scanWastedProbes)
                    + " probesPerScan=" + ratio(probeCalls, scanEvents)
                    + " scanCapBindFraction=" + ratio(scanCapReached, scanEvents)
                    + " scanMaterialFraction=" + ratio(scanFoundMaterial, scanEvents)
                    + " wastedProbeFraction=" + ratio(scanWastedProbes, probeCalls)
                    // T182. directStormShape by consumer. shapeUntagged must be
                    // zero and shapeTagged must equal directStormShapeCalls, or
                    // the attribution is incomplete and no share derived from it
                    // can be trusted - which is exactly how T180 went wrong.
                    + " shapePrimary=" + fmt(shapePrimary)
                    + " shapeLight=" + fmt(shapeLight)
                    + " shapeProbe=" + fmt(shapeProbe)
                    + " shapeBracket=" + fmt(shapeBracket)
                    + " shapeRefine=" + fmt(shapeRefine)
                    + " shapeRainSegment=" + fmt(shapeRainSegment)
                    + " shapeRainShaft=" + fmt(shapeRainShaft)
                    + " shapeCamera=" + fmt(shapeCamera)
                    + " shapeLightForward=" + fmt(shapeLightForward)
                    + " shapeUntagged=" + fmt(shapeUntagged)
                    + " shapeTagged=" + fmt(shapePrimary + shapeLight + shapeProbe
                            + shapeBracket + shapeRefine + shapeRainSegment
                            + shapeRainShaft + shapeCamera + shapeLightForward)
                    // T183. The tolerance was half a call, which is stricter than
                    // the capture can be: each debug view is a separately rendered
                    // frame, so the totals are sampled from different frames and
                    // disagree by a fraction of a percent. 0.5% is two orders of
                    // magnitude above that variance and two orders below the
                    // smallest consumer ever found (rain shaft, 0.77% at SIDE), so
                    // it still fails if a real consumer goes missing.
                    // shapeUntagged stays a strict zero - that is the structural
                    // check, and this is only the arithmetic one.
                    + " shapeAccountingResidual="
                    + fmt(shapePrimary + shapeLight + shapeProbe + shapeBracket
                            + shapeRefine + shapeRainSegment + shapeRainShaft
                            + shapeCamera + shapeLightForward + shapeUntagged
                            - directStormShapeCalls)
                    + " shapeAccountingResidualFraction="
                    + ratio(Math.abs(shapePrimary + shapeLight + shapeProbe
                            + shapeBracket + shapeRefine + shapeRainSegment
                            + shapeRainShaft + shapeCamera + shapeLightForward
                            + shapeUntagged - directStormShapeCalls),
                            directStormShapeCalls)
                    + " shapeAccountingClosed="
                    + (shapeUntagged == 0.0D
                        && Math.abs(shapePrimary + shapeLight + shapeProbe
                            + shapeBracket + shapeRefine + shapeRainSegment
                            + shapeRainShaft + shapeCamera + shapeLightForward
                            + shapeUntagged - directStormShapeCalls)
                                <= 0.005D * Math.max(1.0D, directStormShapeCalls))
                    + " shapeRainSegmentShare="
                    + ratio(shapeRainSegment, directStormShapeCalls)
                    + " lobeVisitsPerGroupWalk=" + ratio(lobesVisited, groupFieldCalls)
                    + " exactSdfPerGroupWalk=" + ratio(lobeExactSdf, groupFieldCalls)
                    + " cheapRejectPerGroupWalk="
                    + ratio(conservativeDescriptorRejects, groupFieldCalls)
                    + " contributorsPerGroupWalk="
                    + ratio(descriptorUnionContributors, groupFieldCalls)
                    + " wastedSdfPerGroupWalk="
                    + ratio(lobeExactSdfNoChange, groupFieldCalls)
                    + " wastedSdfFraction="
                    + ratio(lobeExactSdfNoChange, lobeExactSdf);
        }

        double oraclePostOpacityDistance() {
            return Math.max(0.0D, oracleSkippedDistance
                    - oraclePreCloudDistance - oracleHoleDistance - oraclePostCloudDistance);
        }

        private String perPixel(double value) {
            long pixels = (long) width * (long) height;
            return pixels <= 0L
                    ? "n/a"
                    : String.format(Locale.ROOT, "%.6f", value / (double) pixels);
        }
    }

    record ThresholdWork(double alpha50, double alpha90, double alpha95, double alpha98) {
        private static final ThresholdWork ZERO = new ThresholdWork(0.0D, 0.0D, 0.0D, 0.0D);
        static ThresholdWork of(double[] values) {
            return new ThresholdWork(values[0], values[1], values[2], values[3]);
        }

        String format() {
            return "[50=" + fmt(alpha50)
                    + ",90=" + fmt(alpha90)
                    + ",95=" + fmt(alpha95)
                    + ",98=" + fmt(alpha98) + "]";
        }
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    /**
     * Deterministic no-GL guard for the T132 freshness contract: tokens are
     * unique and monotonic, a new request drops any completed result, and a
     * failed capture leaves no result behind.
     */
    static synchronized void selfCheckFreshnessContract() {
        Request previousActive = active;
        WorkloadResult previousResult = latestResult;
        String previousLatest = latest;
        try {
            active = null;
            latestResult = null;
            CaptureRequest first = requestCapture("above");
            if (!first.accepted()) {
                throw new IllegalStateException("workload capture refused a valid request");
            }
            if (!requestCapture("above").status().startsWith("busy")) {
                throw new IllegalStateException("workload capture accepted a concurrent request");
            }
            WorkloadResult completed = active.result();
            latestResult = completed;
            active = null;
            if (completed.captureToken() != first.token()) {
                throw new IllegalStateException("workload result lost its capture token");
            }
            if (!completed.format().contains("captureToken=" + first.token())) {
                throw new IllegalStateException("workload report omits its capture token");
            }

            CaptureRequest second = requestCapture("above");
            if (second.token() <= first.token()) {
                throw new IllegalStateException("workload capture token is not monotonic");
            }
            if (latestResult != null) {
                throw new IllegalStateException("a new request kept the previous completed result");
            }
            // Reinstate the earlier result, then fail the in-flight capture the
            // way the catch branch does. The stale result must not survive.
            latestResult = completed;
            Request inFlight = active;
            active = null;
            latestResult = null;
            latest = "capture_failed:view=" + inFlight.view + " captureToken=" + inFlight.token;
            if (latestResult != null) {
                throw new IllegalStateException("a failed capture exposed the previous result");
            }
        } finally {
            active = previousActive;
            latestResult = previousResult;
            latest = previousLatest;
        }
    }
}
