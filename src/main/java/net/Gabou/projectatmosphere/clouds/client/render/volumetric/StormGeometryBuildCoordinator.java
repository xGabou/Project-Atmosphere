package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Coalesces pure CPU grid work and adopts/uploads results on the render thread. */
public final class StormGeometryBuildCoordinator {
    private static final AtomicReference<StormGeometryBuild> COMPLETED = new AtomicReference<>();
    private static final LatestRequestMailbox REQUESTS = new LatestRequestMailbox();
    private static final FloatBuffer CANDIDATE_UPLOAD = BufferUtils.createFloatBuffer(
            StormLobeSpatialIndex.GRID_SIZE * StormLobeSpatialIndex.GRID_SIZE * 4
    );
    private static final FloatBuffer DESCRIPTOR_UPLOAD = BufferUtils.createFloatBuffer(
            StormLobeSpatialIndex.MAX_LOBES * StormLobeDescriptor.FLOATS_PER_DESCRIPTOR
    );
    private static final float[] LIVE_DESCRIPTOR_TEXELS = new float[
            StormLobeSpatialIndex.MAX_LOBES * StormLobeDescriptor.FLOATS_PER_DESCRIPTOR
    ];
    private static final VolumetricRenderCell[] LIVE_DESCRIPTOR_MATCHES =
            new VolumetricRenderCell[StormLobeSpatialIndex.MAX_LOBES];

    private static long sessionGeneration = 1L;
    private static long nextRequestGeneration = 1L;
    private static long requestedGridSignature = Long.MIN_VALUE;
    private static StormGeometryBuild adopted;
    private static StormRenderSnapshot renderSnapshot = StormRenderSnapshot.EMPTY;
    private static StormRenderSnapshot publishedSnapshot = StormRenderSnapshot.EMPTY;
    private static long uploadedGridGeneration = Long.MIN_VALUE;
    private static long uploadedDescriptorSignature = Long.MIN_VALUE;
    private static int uploadedCandidateTextureId = -1;
    private static int uploadedDescriptorTextureId = -1;

    private StormGeometryBuildCoordinator() {
    }

    public static void update(
            List<VolumetricRenderCell> cells,
            double cameraX,
            double cameraZ,
            double originX,
            double originZ,
            float extent,
            RenderTarget candidateTarget,
            RenderTarget descriptorTarget
    ) {
        RenderSystem.assertOnRenderThread();
        long currentGridSignature = StormLobeSpatialIndex.gridSignature(cells, originX, originZ, extent);
        adoptCompleted(currentGridSignature, originX, originZ, extent);

        if (requestedGridSignature != currentGridSignature) {
            requestedGridSignature = currentGridSignature;
            REQUESTS.replacePending(StormLobeSpatialIndex.captureInput(
                    sessionGeneration,
                    nextRequestGeneration++,
                    cells,
                    cameraX,
                    cameraZ,
                    originX,
                    originZ,
                    extent
            ));
        }
        submitPending();
        uploadAdopted(cells, candidateTarget, descriptorTarget);
    }

    public static int lobeCount() {
        return adopted == null ? 0 : adopted.descriptorCount();
    }

    /**
     * The widest coverage-envelope boundary over the adopted descriptors, in
     * blocks.
     *
     * <p>T098's raymarch promotion probe subtracts this from the union distance
     * so the resulting advance is a lower bound on the distance to any
     * material: {@code stormEnvelopeFromDistance} fades coverage over plus or
     * minus a descriptor's softness, so material can begin that far outside the
     * union surface. Taking the maximum over every uploaded descriptor keeps
     * the bound valid regardless of which group the ray approaches.
     */
    public static float widestEdgeBlocks() {
        StormRenderSnapshot snapshot = publishedSnapshot;
        if (snapshot == null) {
            return 0.0F;
        }
        float widest = 0.0F;
        for (StormLobeDescriptor descriptor : snapshot.descriptorsUnsafe()) {
            widest = Math.max(widest, (float) StormLobeEvaluator.edgeWidthBlocks(descriptor));
        }
        return widest;
    }

    public static StormRenderSnapshot snapshot() {
        return publishedSnapshot;
    }

    /**
     * T098 calibration scaffolding: one bounded, on-demand description of the
     * live descriptor densities feeding the coverage envelope. Delete together
     * with {@link StormDensityCalibrationReport} once T098 records its
     * calibration, or fold into the US4 storm diagnostic capture.
     */
    public static String describeDensityCalibration(
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        return StormDensityCalibrationReport.describe(cameraX, cameraY, cameraZ);
    }

    /** Current render-generation ownership used to suppress member raster geometry. */
    static boolean renderOwnsGroup(UUID groupId) {
        if (groupId == null) {
            return false;
        }
        for (StormLobeDescriptor descriptor : renderSnapshot.descriptorsUnsafe()) {
            if (groupId.equals(descriptor.groupId())) {
                return true;
            }
        }
        return false;
    }

    public static long topologyGeneration() {
        return publishedSnapshot.topologyGeneration();
    }

    static long renderTopologyGeneration() {
        return adopted == null ? 0L : adopted.requestGeneration();
    }

    static long lifecycleGeneration() {
        return sessionGeneration;
    }

    /** Publishes only geometry that has reached a successfully composited frame. */
    public static void publishSuccessfulFrame() {
        RenderSystem.assertOnRenderThread();
        publishedSnapshot = renderSnapshot;
    }

    public static void reset() {
        RenderSystem.assertOnRenderThread();
        sessionGeneration++;
        requestedGridSignature = Long.MIN_VALUE;
        REQUESTS.reset();
        adopted = null;
        renderSnapshot = StormRenderSnapshot.EMPTY;
        publishedSnapshot = StormRenderSnapshot.EMPTY;
        COMPLETED.set(null);
        uploadedGridGeneration = Long.MIN_VALUE;
        uploadedDescriptorSignature = Long.MIN_VALUE;
        uploadedCandidateTextureId = -1;
        uploadedDescriptorTextureId = -1;
    }

    private static void submitPending() {
        StormGeometryBuildInput input = REQUESTS.takeForSubmission();
        if (input == null) {
            return;
        }
        boolean accepted = AsyncAtmosphereService.tryRunClient(() -> {
            try {
                COMPLETED.set(StormLobeSpatialIndex.build(input));
            } finally {
                REQUESTS.completeSubmission();
            }
        });
        if (!accepted) {
            REQUESTS.restoreRejected(input);
        }
    }

    /** One active build and one replaceable latest request; contains no GL state. */
    static final class LatestRequestMailbox {
        private StormGeometryBuildInput pending;
        private boolean inFlight;

        synchronized void replacePending(StormGeometryBuildInput input) {
            pending = input;
        }

        synchronized StormGeometryBuildInput takeForSubmission() {
            if (inFlight || pending == null) {
                return null;
            }
            StormGeometryBuildInput selected = pending;
            pending = null;
            inFlight = true;
            return selected;
        }

        synchronized void completeSubmission() {
            inFlight = false;
        }

        synchronized void restoreRejected(StormGeometryBuildInput rejected) {
            inFlight = false;
            if (pending == null) {
                pending = rejected;
            }
        }

        synchronized void reset() {
            pending = null;
            // A stale worker may still be finishing. Its generation will be
            // rejected; keep the physical in-flight gate until its finally
            // block calls completeSubmission().
        }
    }

    private static void adoptCompleted(
            long currentGridSignature,
            double originX,
            double originZ,
            float extent
    ) {
        StormGeometryBuild result = COMPLETED.getAndSet(null);
        if (result == null) {
            return;
        }
        boolean valid = isAdoptableForTest(
                result,
                sessionGeneration,
                currentGridSignature,
                originX,
                originZ,
                extent
        );
        if (!valid) {
            // A completed stale request was the only request recorded for this
            // signature. Forget that acknowledgement so the current frame
            // recreates it while the mailbox still preserves one in-flight
            // build plus its latest replaceable pending request.
            requestedGridSignature = Long.MIN_VALUE;
            return;
        }
        adopted = result;
        renderSnapshot = new StormRenderSnapshot(
                result.sessionGeneration(),
                result.requestGeneration(),
                result.gridSignature(),
                result.originX(),
                result.originZ(),
                result.extent(),
                result.selectedDescriptorsUnsafe()
        );
        uploadedGridGeneration = Long.MIN_VALUE;
        uploadedDescriptorSignature = Long.MIN_VALUE;
    }

    static boolean isAdoptableForTest(
            StormGeometryBuild result,
            long expectedSessionGeneration,
            long expectedGridSignature,
            double expectedOriginX,
            double expectedOriginZ,
            float expectedExtent
    ) {
        return result != null
                && result.matchesLifecycleGeneration(
                        expectedSessionGeneration,
                        expectedSessionGeneration,
                        expectedSessionGeneration,
                        expectedSessionGeneration,
                        expectedSessionGeneration,
                        expectedGridSignature
                )
                && result.gridSignature() == expectedGridSignature
                && Double.doubleToLongBits(result.originX()) == Double.doubleToLongBits(expectedOriginX)
                && Double.doubleToLongBits(result.originZ()) == Double.doubleToLongBits(expectedOriginZ)
                && Float.floatToIntBits(result.extent()) == Float.floatToIntBits(expectedExtent);
    }

    private static void uploadAdopted(
            List<VolumetricRenderCell> cells,
            RenderTarget candidateTarget,
            RenderTarget descriptorTarget
    ) {
        if (adopted == null || candidateTarget == null || descriptorTarget == null
                || candidateTarget.getColorTextureId() <= 0
                || descriptorTarget.getColorTextureId() <= 0) {
            return;
        }
        int candidateTextureId = candidateTarget.getColorTextureId();
        int descriptorTextureId = descriptorTarget.getColorTextureId();
        if (uploadedGridGeneration != adopted.requestGeneration()
                || uploadedCandidateTextureId != candidateTextureId) {
            uploadTexture(
                    candidateTextureId,
                    StormLobeSpatialIndex.GRID_SIZE,
                    StormLobeSpatialIndex.GRID_SIZE,
                    adopted.candidateTexelsUnsafe(),
                    CANDIDATE_UPLOAD
            );
            uploadedGridGeneration = adopted.requestGeneration();
            uploadedCandidateTextureId = candidateTextureId;
        }

        float[] live = LIVE_DESCRIPTOR_TEXELS;
        long signature = refreshLiveDescriptors(cells, adopted.selectedDescriptorsUnsafe(), live);
        if (signature != uploadedDescriptorSignature
                || uploadedDescriptorTextureId != descriptorTextureId) {
            uploadTexture(
                    descriptorTextureId,
                    StormLobeSpatialIndex.DESCRIPTOR_WIDTH,
                    StormLobeSpatialIndex.DESCRIPTOR_HEIGHT,
                    live,
                    DESCRIPTOR_UPLOAD
            );
            uploadedDescriptorSignature = signature;
            uploadedDescriptorTextureId = descriptorTextureId;
            renderSnapshot = snapshotFromUploadedTexels(adopted, live);
        }
    }

    private static StormRenderSnapshot snapshotFromUploadedTexels(
            StormGeometryBuild build,
            float[] texels
    ) {
        StormLobeDescriptor[] selected = build.selectedDescriptorsUnsafe();
        int validCount = 0;
        for (int index = 0; index < selected.length; index++) {
            if (texels[index * StormLobeDescriptor.FLOATS_PER_DESCRIPTOR + 15] >= 0.0F) {
                validCount++;
            }
        }
        StormLobeDescriptor[] exact = new StormLobeDescriptor[validCount];
        int exactIndex = 0;
        for (int index = 0; index < selected.length; index++) {
            int offset = index * StormLobeDescriptor.FLOATS_PER_DESCRIPTOR;
            if (texels[offset + 15] >= 0.0F) {
                exact[exactIndex++] = StormLobeDescriptor.fromTexels(selected[index], texels, offset);
            }
        }
        return new StormRenderSnapshot(
                build.sessionGeneration(), build.requestGeneration(), build.gridSignature(),
                build.originX(), build.originZ(), build.extent(), exact
        );
    }

    private static long refreshLiveDescriptors(
            List<VolumetricRenderCell> cells,
            StormLobeDescriptor[] selected,
            float[] destination
    ) {
        long signature = 0xcbf29ce484222325L;
        for (int selectedIndex = 0; selectedIndex < selected.length; selectedIndex++) {
            StormLobeDescriptor identity = selected[selectedIndex];
            VolumetricRenderCell match = null;
            if (cells != null) {
                for (VolumetricRenderCell cell : cells) {
                    if (StormLobeSpatialIndex.isDirectStorm(cell)
                            && identity.fieldId().equals(cell.fieldId())
                            && identity.groupId().equals(cell.morphologyGroupId())
                            && identity.memberIndex() == cell.morphologyMemberIndex()) {
                        match = cell;
                        break;
                    }
                }
            }
            LIVE_DESCRIPTOR_MATCHES[selectedIndex] = match;
        }
        for (int selectedIndex = selected.length;
                selectedIndex < LIVE_DESCRIPTOR_MATCHES.length;
                selectedIndex++) {
            LIVE_DESCRIPTOR_MATCHES[selectedIndex] = null;
        }

        for (int selectedIndex = 0; selectedIndex < selected.length; selectedIndex++) {
            StormLobeDescriptor identity = selected[selectedIndex];
            boolean completeGroup = true;
            int selectedMembers = 0;
            for (int groupIndex = 0; groupIndex < selected.length; groupIndex++) {
                if (!selected[groupIndex].groupId().equals(identity.groupId())) {
                    continue;
                }
                selectedMembers++;
                if (LIVE_DESCRIPTOR_MATCHES[groupIndex] == null) {
                    completeGroup = false;
                }
            }
            completeGroup &= selectedMembers == identity.memberCount();
            int offset = selectedIndex * StormLobeDescriptor.FLOATS_PER_DESCRIPTOR;
            if (!completeGroup) {
                ArraysSupport.clear(destination, offset, StormLobeDescriptor.FLOATS_PER_DESCRIPTOR);
                // Explicit invalid descriptor. Candidate entries may still
                // reference this stable slot, so every shader path rejects it
                // before decoding group/role or accumulating geometry.
                destination[offset + 15] = -1.0F;
            } else {
                StormLobeDescriptor.writeCellTexels(
                        LIVE_DESCRIPTOR_MATCHES[selectedIndex],
                        identity.groupSlot(),
                        destination,
                        offset
                );
            }
            for (int component = 0; component < StormLobeDescriptor.FLOATS_PER_DESCRIPTOR; component++) {
                signature = (signature ^ Float.floatToIntBits(destination[offset + component]))
                        * 0x100000001b3L;
            }
        }
        ArraysSupport.clear(
                destination,
                selected.length * StormLobeDescriptor.FLOATS_PER_DESCRIPTOR,
                destination.length - selected.length * StormLobeDescriptor.FLOATS_PER_DESCRIPTOR
        );
        return signature;
    }

    private static void uploadTexture(
            int textureId,
            int width,
            int height,
            float[] values,
            FloatBuffer upload
    ) {
        upload.clear();
        upload.put(values, 0, Math.min(values.length, upload.capacity()));
        while (upload.position() < upload.capacity()) {
            upload.put(0.0F);
        }
        upload.flip();
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try (PixelUnpackState ignored = PixelUnpackState.beginTightCpuUpload()) {
            try {
                RenderSystem.bindTexture(textureId);
                GL11.glTexSubImage2D(
                        GL11.GL_TEXTURE_2D, 0, 0, 0, width, height,
                        GL11.GL_RGBA, GL11.GL_FLOAT, upload
                );
            } finally {
                RenderSystem.bindTexture(previousTexture);
            }
        }
    }

    private static final class ArraysSupport {
        private ArraysSupport() {
        }

        private static void clear(float[] values, int offset, int count) {
            int end = Math.min(values.length, Math.max(offset, offset + count));
            for (int index = Math.max(0, offset); index < end; index++) {
                values[index] = 0.0F;
            }
        }
    }

    private record PixelUnpackState(
            int buffer,
            int alignment,
            int swapBytes,
            int rowLength,
            int imageHeight,
            int skipPixels,
            int skipRows,
            int skipImages
    ) implements AutoCloseable {
        private static PixelUnpackState beginTightCpuUpload() {
            PixelUnpackState state = new PixelUnpackState(
                    GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING),
                    GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT),
                    GL11.glGetInteger(GL11.GL_UNPACK_SWAP_BYTES),
                    GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH),
                    GL11.glGetInteger(GL12.GL_UNPACK_IMAGE_HEIGHT),
                    GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS),
                    GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS),
                    GL11.glGetInteger(GL12.GL_UNPACK_SKIP_IMAGES)
            );
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, GL11.GL_FALSE);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0);
            return state;
        }

        @Override
        public void close() {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, swapBytes);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, imageHeight);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, skipImages);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, buffer);
        }
    }
}
