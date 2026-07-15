package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.clouds.cell.CloudCell;
import net.Gabou.projectatmosphere.clouds.cell.CloudCellClassification;
import net.Gabou.projectatmosphere.clouds.cell.CloudCellLifecyclePhase;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Standalone deterministic checks for native-cloud material advection. */
public final class MaterialAdvectionSandbox {
    private static final String DIMENSION = "minecraft:overworld";

    private MaterialAdvectionSandbox() {
    }

    public static void main(String[] args) throws IOException {
        rigidMotionIsOrderIndependent();
        frozenMotionStaysBitStable();
        membershipChurnDoesNotMoveTheDomain();
        divergentMotionReportsResidual();
        dimensionChangeResetsTheDomain();
        regionalMotionUsesDeltaTimeOnly();
        shaderHasNoAbsoluteTimeWindTranslation();
        System.out.println("Material advection self-check passed.");
    }

    private static void rigidMotionIsOrderIndependent() {
        VolumetricMaterialAdvectionTracker tracker = new VolumetricMaterialAdvectionTracker();
        List<CloudCell> initial = rigidCells(0.0D, 0.0D, 12);
        tracker.updateCells(DIMENSION, 100.0D, initial);
        List<CloudCell> shifted = rigidCells(1.25D, -2.5D, 12);
        Collections.reverse(shifted);
        VolumetricMaterialAdvectionTracker.Frame frame = tracker.updateCells(DIMENSION, 101.0D, shifted);
        assertNear("rigid delta x", 1.25F, frame.frameDeltaX(), 0.000001F);
        assertNear("rigid delta z", -2.5F, frame.frameDeltaZ(), 0.000001F);
        assertNear("rigid offset x", 1.25F, frame.offsetX(), 0.000001F);
        assertNear("rigid offset z", -2.5F, frame.offsetZ(), 0.000001F);
        assertEquals("rigid matched", 12, frame.matched());
        assertNear("rigid residual", 0.0F, frame.motionResidualRms(), 0.000001F);
    }

    private static void frozenMotionStaysBitStable() {
        VolumetricMaterialAdvectionTracker tracker = new VolumetricMaterialAdvectionTracker();
        List<CloudCell> cells = rigidCells(0.0D, 0.0D, 12);
        tracker.updateCells(DIMENSION, 1_600_000.0D, cells);
        int expectedXBits = Float.floatToIntBits(tracker.frame().offsetX());
        int expectedZBits = Float.floatToIntBits(tracker.frame().offsetZ());
        for (int frameIndex = 1; frameIndex <= 600; frameIndex++) {
            tracker.updateCells(DIMENSION, 1_600_000.0D + frameIndex * 0.25D, cells);
            assertEquals("frozen x bits", expectedXBits, Float.floatToIntBits(tracker.frame().offsetX()));
            assertEquals("frozen z bits", expectedZBits, Float.floatToIntBits(tracker.frame().offsetZ()));
            assertNear("frozen frame delta", 0.0F,
                    Math.abs(tracker.frame().frameDeltaX()) + Math.abs(tracker.frame().frameDeltaZ()),
                    0.0F);
        }
    }

    private static void membershipChurnDoesNotMoveTheDomain() {
        VolumetricMaterialAdvectionTracker tracker = new VolumetricMaterialAdvectionTracker();
        List<CloudCell> initial = rigidCells(0.0D, 0.0D, 4);
        tracker.updateCells(DIMENSION, 10.0D, initial);
        List<CloudCell> changed = new ArrayList<>(initial.subList(1, initial.size()));
        changed.add(cell(new UUID(7L, 99L), 900.0D, -700.0D));
        VolumetricMaterialAdvectionTracker.Frame frame = tracker.updateCells(DIMENSION, 11.0D, changed);
        assertNear("churn delta x", 0.0F, frame.frameDeltaX(), 0.0F);
        assertNear("churn delta z", 0.0F, frame.frameDeltaZ(), 0.0F);
        assertEquals("churn matched", 3, frame.matched());
        assertEquals("churn entered", 1, frame.entered());
        assertEquals("churn left", 1, frame.left());
    }

    private static void divergentMotionReportsResidual() {
        VolumetricMaterialAdvectionTracker tracker = new VolumetricMaterialAdvectionTracker();
        UUID firstId = new UUID(11L, 1L);
        UUID secondId = new UUID(11L, 2L);
        tracker.updateCells(DIMENSION, 20.0D, List.of(cell(firstId, 0.0D, 0.0D), cell(secondId, 10.0D, 0.0D)));
        VolumetricMaterialAdvectionTracker.Frame frame = tracker.updateCells(
                DIMENSION,
                21.0D,
                List.of(cell(secondId, 13.0D, 0.0D), cell(firstId, 1.0D, 0.0D))
        );
        assertNear("divergent mean", 2.0F, frame.frameDeltaX(), 0.000001F);
        assertNear("divergent rms", 1.0F, frame.motionResidualRms(), 0.000001F);
        assertNear("divergent max", 1.0F, frame.motionResidualMax(), 0.000001F);
    }

    private static void dimensionChangeResetsTheDomain() {
        VolumetricMaterialAdvectionTracker tracker = new VolumetricMaterialAdvectionTracker();
        List<CloudCell> initial = rigidCells(0.0D, 0.0D, 2);
        tracker.updateCells(DIMENSION, 30.0D, initial);
        tracker.updateCells(DIMENSION, 31.0D, rigidCells(4.0D, 3.0D, 2));
        VolumetricMaterialAdvectionTracker.Frame frame = tracker.updateCells(
                "minecraft:the_nether",
                32.0D,
                rigidCells(100.0D, 100.0D, 2)
        );
        assertNear("dimension reset x", 0.0F, frame.offsetX(), 0.0F);
        assertNear("dimension reset z", 0.0F, frame.offsetZ(), 0.0F);
        if (!frame.discontinuity()) {
            throw new IllegalStateException("dimension change did not invalidate continuity");
        }
    }

    private static void regionalMotionUsesDeltaTimeOnly() {
        VolumetricMaterialAdvectionTracker tracker = new VolumetricMaterialAdvectionTracker();
        Vector3f wind = new Vector3f(0.125F, 0.0F, -0.25F);
        tracker.updateRegional(DIMENSION, 1_728_000.0D, wind);
        VolumetricMaterialAdvectionTracker.Frame frame = tracker.updateRegional(DIMENSION, 1_728_001.0D, wind);
        assertNear("regional delta x", 0.125F, frame.frameDeltaX(), 0.000001F);
        assertNear("regional delta z", -0.25F, frame.frameDeltaZ(), 0.000001F);
        assertNear("regional offset x", 0.125F, frame.offsetX(), 0.000001F);
        assertNear("regional offset z", -0.25F, frame.offsetZ(), 0.000001F);
    }

    private static void shaderHasNoAbsoluteTimeWindTranslation() throws IOException {
        Path shaderPath = Path.of(
                "src", "main", "resources", "assets", "projectatmosphere", "shaders", "core",
                "cloud_atmosphere_volume.fsh"
        );
        String shader = Files.readString(shaderPath);
        if (shader.contains("WindVec.xz * WorldTime")) {
            throw new IllegalStateException("absolute-time wind translation remains in the volume shader");
        }
        int materialOffsetUses = shader.split("MaterialOffset", -1).length - 1;
        if (materialOffsetUses < 3) {
            throw new IllegalStateException("material offset is not declared and consumed by both density paths");
        }
    }

    private static List<CloudCell> rigidCells(double shiftX, double shiftZ, int count) {
        List<CloudCell> cells = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            cells.add(cell(
                    new UUID(3L, index + 1L),
                    index * 7.0D + shiftX,
                    index * -5.0D + shiftZ
            ));
        }
        return cells;
    }

    private static CloudCell cell(UUID id, double x, double z) {
        return new CloudCell(
                id,
                id.getLeastSignificantBits(),
                DIMENSION,
                x,
                z,
                128.0F,
                180.0F,
                20.0F,
                16.0F,
                0.0F,
                1.0F,
                0.2F,
                0.2F,
                0.0F,
                0.0F,
                64.0F,
                Vec3.ZERO,
                CloudCellLifecyclePhase.MATURE,
                CloudCellClassification.CUMULUS_HUMILIS,
                100L,
                100L
        );
    }

    private static void assertNear(String label, float expected, float actual, float tolerance) {
        if (!Float.isFinite(actual) || Math.abs(expected - actual) > tolerance) {
            throw new IllegalStateException(label + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new IllegalStateException(label + " expected=" + expected + " actual=" + actual);
        }
    }
}
