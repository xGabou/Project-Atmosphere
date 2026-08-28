package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * On-demand production-shader centre-line trace.  It deliberately renders a
 * short diagnostic pass instead of reproducing shader lighting on the CPU.
 * Four RGBA passes encode the fields required to attribute a material change;
 * ordinary frames remain on the FINAL debug view and do no readback.
 */
final class StormMaterialRuntimeTrace {
    private static final int STAGES = 4;
    private static final double MIN_CENTROID_TOLERANCE_BLOCKS = 32.0D;
    private static volatile Request active;
    private static volatile String latest = "not_captured";

    private StormMaterialRuntimeTrace() {
    }

    static synchronized String request(double x, double z, float yStart, float yEnd) {
        if (active != null) {
            return "busy:stage=" + active.stage + "/" + STAGES;
        }
        if (!Double.isFinite(x) || !Double.isFinite(z)
                || !Float.isFinite(yStart) || !Float.isFinite(yEnd) || yEnd < yStart) {
            return "invalid_trace_range";
        }
        Resolution resolution = resolve(x, z);
        if (!resolution.valid()) {
            latest = "no_complete_published_storm_group";
            return latest;
        }
        if (resolution.centroidDistance() > resolution.centroidTolerance()) {
            latest = "rejected_centroid_mismatch requested=" + fmt((float) x) + ',' + fmt((float) z)
                    + " group=" + shortId(resolution.groupId())
                    + " resolved=" + fmt((float) resolution.centerX()) + ',' + fmt((float) resolution.centerZ())
                    + " material=" + fmt((float) resolution.materialCentroidX()) + ','
                    + fmt((float) resolution.materialCentroidZ())
                    + " distance=" + fmt((float) resolution.centroidDistance())
                    + " tolerance=" + fmt((float) resolution.centroidTolerance());
            return latest;
        }
        int samples = Math.max(2, Math.min(96, (int) Math.ceil((yEnd - yStart) / 16.0F) + 1));
        float interval = samples <= 1 ? 0.0F : (yEnd - yStart) / (samples - 1);
        active = new Request((float) x, (float) z, resolution, yStart, interval, samples);
        latest = "acquiring stage=0/4 samples=" + samples
                + " group=" + shortId(resolution.groupId())
                + " resolved=" + fmt((float) resolution.centerX()) + ',' + fmt((float) resolution.centerZ());
        return latest;
    }

    static boolean active() {
        return active != null;
    }

    static int stage() {
        Request request = active;
        return request == null ? 0 : request.stage;
    }

    static float x() { return active == null ? 0.0F : (float) active.resolution.centerX(); }
    static float z() { return active == null ? 0.0F : (float) active.resolution.centerZ(); }
    static float yStart() { return active == null ? 0.0F : active.yStart; }
    static float interval() { return active == null ? 1.0F : active.interval; }
    static int samples() { return active == null ? 2 : active.samples; }

    static String latest() {
        return latest;
    }

    /** Called after the trace pass; synchronous only while the user explicitly requested it. */
    static synchronized void capture(RenderTarget target) {
        Request request = active;
        if (request == null || !RenderSystem.isOnRenderThread() || target == null
                || target.getColorTextureId() <= 0 || target.width <= 0 || target.height <= 0) {
            return;
        }
        FloatBuffer pixels = BufferUtils.createFloatBuffer(target.width * target.height * 4);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.getColorTextureId());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
            for (int sample = 0; sample < request.samples; sample++) {
                int x = Math.min(target.width - 1, Math.max(0,
                        (int) (((sample + 0.5D) / request.samples) * target.width)));
                int offset = x * 4; // Shader writes the same trace stripe on every target row.
                for (int channel = 0; channel < 4; channel++) {
                    request.values[request.stage][sample][channel] = pixels.get(offset + channel);
                }
            }
            request.stage++;
            if (request.stage >= STAGES) {
                latest = request.format();
                active = null;
                VolumetricCloudRenderer.invalidateHistory();
            } else {
                latest = "acquiring stage=" + request.stage + "/4 samples=" + request.samples;
            }
        } catch (RuntimeException exception) {
            latest = "capture_failed:" + exception.getClass().getSimpleName();
            active = null;
            VolumetricCloudRenderer.invalidateHistory();
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        }
    }

    private static final class Request {
        private final float requestedX;
        private final float requestedZ;
        private final Resolution resolution;
        private final float yStart;
        private final float interval;
        private final int samples;
        private final float[][][] values;
        private int stage;

        private Request(float requestedX, float requestedZ, Resolution resolution,
                        float yStart, float interval, int samples) {
            this.requestedX = requestedX;
            this.requestedZ = requestedZ;
            this.resolution = resolution;
            this.yStart = yStart;
            this.interval = interval;
            this.samples = samples;
            this.values = new float[STAGES][samples][4];
        }

        private String format() {
            StringBuilder out = new StringBuilder("T128 production shader material trace")
                    .append("\nrequestedPlayer=").append(fmt(requestedX)).append(',').append(fmt(requestedZ))
                    .append(" resolvedGroup=").append(resolution.groupId())
                    .append(" resolvedCentre=").append(fmt((float) resolution.centerX()))
                    .append(',').append(fmt((float) resolution.centerZ()))
                    .append(" productionMaterialCentroid=")
                    .append(fmt((float) resolution.materialCentroidX()))
                    .append(',').append(fmt((float) resolution.materialCentroidZ()))
                    .append(" centreDistance=").append(fmt((float) resolution.centroidDistance()))
                    .append(" tolerance=").append(fmt((float) resolution.centroidTolerance()))
                    .append(" groupRoleMask=").append(resolution.groupRoleMask())
                    .append(" yStart=").append(fmt(yStart))
                    .append(" interval=").append(fmt(interval))
                    .append("\nY|groupRoleMask|activeRoleMask|coverage|strength|carrierRaw|baseField|bodyBefore|bodyAfter|erosion|density|h01|extinction|lightOD|direct|ambient|phaseShadow|radiance|flags");
            for (int sample = 0; sample < samples; sample++) {
                float[] a = values[0][sample];
                float[] b = values[1][sample];
                float[] c = values[2][sample];
                float[] d = values[3][sample];
                int packedFlags = Math.round(d[3] * 31.0F);
                out.append('\n').append(fmt(yStart + interval * sample))
                        .append('|').append(resolution.groupRoleMask())
                        .append('|').append(packedFlags & 15)
                        .append('|').append(fmt(a[0])).append('|').append(fmt(a[1]))
                        .append('|').append(fmt(a[2])).append('|').append(fmt(a[3]))
                        .append('|').append(fmt(b[0])).append('|').append(fmt(b[1]))
                        .append('|').append(fmt(b[2])).append('|').append(fmt(b[3]))
                        .append('|').append(fmt(c[0])).append('|').append(fmt(c[1]))
                        .append('|').append(fmt(c[2])).append('|').append(fmt(c[3]))
                        .append('|').append(fmt(d[0])).append('|').append(fmt(d[1]))
                        .append('|').append(fmt(d[2]))
                        .append('|').append(packedFlags >>> 4);
            }
            return out.toString();
        }
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.5f", value);
    }

    /** Resolves only descriptors published by a successfully composited production frame. */
    /** Shared read-only resolver for diagnostics bound to the production-published topology. */
    static Resolution resolve(double requestedX, double requestedZ) {
        StormLobeDescriptor[] descriptors = StormGeometryBuildCoordinator.snapshot().descriptorsUnsafe();
        List<Group> groups = new ArrayList<>();
        for (StormLobeDescriptor descriptor : descriptors) {
            if (descriptor == null) {
                continue;
            }
            Group group = groups.stream()
                    .filter(candidate -> candidate.id.equals(descriptor.groupId()))
                    .findFirst()
                    .orElseGet(() -> {
                        Group created = new Group(descriptor.groupId());
                        groups.add(created);
                        return created;
                    });
            group.members.add(descriptor);
        }
        return groups.stream()
                .filter(Group::complete)
                .min(Comparator.comparingDouble(group -> group.distanceTo(requestedX, requestedZ)))
                .map(Group::finish)
                .orElse(Resolution.INVALID);
    }

    private static final class Group {
        private final UUID id;
        private final List<StormLobeDescriptor> members = new ArrayList<>();

        private Group(UUID id) { this.id = id; }

        private boolean complete() {
            if (members.isEmpty()) {
                return false;
            }
            int expected = members.get(0).memberCount();
            return members.size() == expected;
        }

        private double distanceTo(double x, double z) {
            Resolution resolution = finish();
            return Math.hypot(resolution.centerX() - x, resolution.centerZ() - z);
        }

        private Resolution finish() {
            double centerX = 0.0D;
            double centerZ = 0.0D;
            double materialX = 0.0D;
            double materialZ = 0.0D;
            double materialWeight = 0.0D;
            double meanRadius = 0.0D;
            int roleMask = 0;
            for (StormLobeDescriptor member : members) {
                centerX += member.centerX();
                centerZ += member.centerZ();
                double weight = Math.max(0.0001D,
                        member.density() * member.detailWeight()
                                * member.majorRadius() * member.minorRadius());
                materialX += member.centerX() * weight;
                materialZ += member.centerZ() * weight;
                materialWeight += weight;
                meanRadius += Math.max(member.majorRadius(), member.minorRadius());
                roleMask |= 1 << member.role().gpuId();
            }
            centerX /= members.size();
            centerZ /= members.size();
            materialX /= materialWeight;
            materialZ /= materialWeight;
            double distance = Math.hypot(centerX - materialX, centerZ - materialZ);
            double tolerance = Math.max(MIN_CENTROID_TOLERANCE_BLOCKS,
                    meanRadius / members.size() * 0.35D);
            return new Resolution(true, id, centerX, centerZ, materialX, materialZ,
                    distance, tolerance, roleMask);
        }
    }

    record Resolution(
            boolean valid,
            UUID groupId,
            double centerX,
            double centerZ,
            double materialCentroidX,
            double materialCentroidZ,
            double centroidDistance,
            double centroidTolerance,
            int groupRoleMask
    ) {
        private static final Resolution INVALID = new Resolution(false, null,
                0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0);
    }

    private static String shortId(UUID id) {
        return id == null ? "none" : id.toString().substring(0, 8);
    }
}
