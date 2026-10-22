package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype;
import net.Gabou.projectatmosphere.clouds.morphology.StormGeometryProgram;
import net.Gabou.projectatmosphere.clouds.client.render.CloudGpuTimer;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Isolated T255 fixture authority; never weather/save/network authority. Default absent. */
final class ExperimentalStormMorphologyState {
    private record Fixture(StormGeometryProgram program, String dimensionId, float x, float y, float z) {}
    private static volatile Fixture fixture;
    private static int texture;
    private static long uploadedSignature;
    private static boolean uploaded;
    private static boolean parity;
    private static boolean intervals;
    private static boolean legacy; // T260.4 attribution arm: pre-fix fixture march (bit 1 of PaMorphologyIntervals)
    private static int lighting = 1; // T260.5: PaDiagnosticLightingMode (1 flat, 20-24 neutral volumetric + diagnostics)
    private static boolean poison; // T260.5 contamination probe: world maps read mirrored, world lighting uniforms scrambled
    private static final CloudGpuTimer PIPELINE_TIMER = new CloudGpuTimer();

    private ExperimentalStormMorphologyState() {}

    static boolean enabled() { return fixture != null; }
    static CoreCostDiagnosticProgram program() {
        boolean growth = fixture != null && fixture.program.growthDefined();
        if (fixture != null && fixture.program instanceof NeutralLitProgram) { // T260.5 baked neutral light
            if (parity) return CoreCostDiagnosticProgram.CONVECTIVE_SYSTEM_PARITY;
            return lighting == 20 ? CoreCostDiagnosticProgram.CONVECTIVE_SYSTEM_NEUTRAL_FINAL : CoreCostDiagnosticProgram.CONVECTIVE_SYSTEM_NEUTRAL;
        }
        if (fixture != null && fixture.program instanceof net.Gabou.projectatmosphere.clouds.morphology.ConvectiveVolumeT2606.Program) { // T260.6 arms (flat)
            if (!parity && lighting != 1) return CoreCostDiagnosticProgram.CONVECTIVE_SYSTEM_NEUTRAL;
            return parity ? CoreCostDiagnosticProgram.CONVECTIVE_SYSTEM_PARITY : CoreCostDiagnosticProgram.CONVECTIVE_SYSTEM_PROTOTYPE;
        }
        if (fixture != null && fixture.program instanceof net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Program) {
            if (!parity && lighting != 1) return CoreCostDiagnosticProgram.CONVECTIVE_SYSTEM_NEUTRAL; // T260.5
            return parity ? CoreCostDiagnosticProgram.CONVECTIVE_SYSTEM_PARITY : CoreCostDiagnosticProgram.CONVECTIVE_SYSTEM_PROTOTYPE;
        }
        if (fixture != null && fixture.program instanceof net.Gabou.projectatmosphere.clouds.morphology.ConvectiveGrowthFieldA.Program) {
            return parity ? CoreCostDiagnosticProgram.CONVECTIVE_A_MORPHOLOGY_PARITY : CoreCostDiagnosticProgram.CONVECTIVE_A_MORPHOLOGY_PROTOTYPE;
        }
        if (fixture != null && fixture.program instanceof net.Gabou.projectatmosphere.clouds.morphology.StormAnatomyCandidateC.Program) {
            return parity ? CoreCostDiagnosticProgram.CONVECTIVE_C_MORPHOLOGY_PARITY : CoreCostDiagnosticProgram.CONVECTIVE_C_MORPHOLOGY_PROTOTYPE;
        }
        if (fixture != null && fixture.program instanceof net.Gabou.projectatmosphere.clouds.morphology.ConvectiveGrowthFieldPrototype.Program) {
            return parity ? CoreCostDiagnosticProgram.CONVECTIVE_MORPHOLOGY_PARITY : CoreCostDiagnosticProgram.CONVECTIVE_MORPHOLOGY_PROTOTYPE;
        }
        if (growth && !parity && fixture.program instanceof net.Gabou.projectatmosphere.clouds.morphology.GrowthDefinedStormPrototype.Program p) {
            switch (p.inspectionMask()) {
                case 1: return CoreCostDiagnosticProgram.GROWTH_MORPHOLOGY_NO_CANOPY;
                case 2: return CoreCostDiagnosticProgram.GROWTH_MORPHOLOGY_NO_ICE;
                case 4: return CoreCostDiagnosticProgram.GROWTH_MORPHOLOGY_HARD_MERGE;
                case 8: return CoreCostDiagnosticProgram.GROWTH_MORPHOLOGY_COUNTS;
            }
        }
        return growth ? (parity ? CoreCostDiagnosticProgram.GROWTH_MORPHOLOGY_PARITY : CoreCostDiagnosticProgram.GROWTH_MORPHOLOGY_PROTOTYPE)
                : (parity ? CoreCostDiagnosticProgram.MORPHOLOGY_PARITY : CoreCostDiagnosticProgram.MORPHOLOGY_PROTOTYPE);
    }
    static boolean belongsTo(String dimensionId) {
        Fixture f = fixture;
        return f != null && f.dimensionId.equals(dimensionId);
    }
    static float density(double x, double y, double z) {
        Fixture f = fixture;
        return f == null ? 0 : f.program.density((float) (x - f.x), (float) (y - f.y),
                (float) (z - f.z), StormMorphologyPrototype.Scale.MESO);
    }
    static void parity(boolean value) { parity = value; }
    static boolean parity() { return parity; }
    static void intervals(boolean value) { intervals = value; }
    static boolean intervals() { return intervals; }
    static void legacy(boolean value) { legacy = value; }
    static void lighting(int mode) { lighting = mode; }
    static void poison(boolean value) { poison = value; }
    static void beginFrame() { if (enabled()) { PIPELINE_TIMER.poll(); PIPELINE_TIMER.begin(); } }
    static void endFrame() { if (enabled()) PIPELINE_TIMER.end(); }
    static long pipelineSerial() { return PIPELINE_TIMER.getLastResultSerial(); }
    static float pipelineMs() { return PIPELINE_TIMER.getLastMilliseconds(); }
    static int pipelineAge() { return PIPELINE_TIMER.getLastResultAgeFrames(); }

    static void install(StormMorphologyPrototype.Program program, float x, float y, float z) {
        install(StormGeometryProgram.legacy(program), x, y, z);
    }

    static void install(StormGeometryProgram program, float x, float y, float z) {
        if (!StormMorphologyCapture.enabled() && !ExperimentalCloudPreview.enabled()) { // preview: explicit PA_CLOUD_PREVIEW=1
            throw new IllegalStateException("Morphology fixture requires explicit PA_T255_CAPTURE=1, PA_T256_CAPTURE=1 or PA_T257_CAPTURE=1");
        }
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            throw new IllegalArgumentException("Non-finite fixture placement");
        }
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) throw new IllegalStateException("No dimension for experimental fixture");
        fixture = new Fixture(java.util.Objects.requireNonNull(program), level.dimension().location().toString(), x, y, z);
        ClientCloudVisualDensity.clear(); // Publish only after the new fixture is actually composited.
        if (ClientCloudVisualDensity.source() != ClientCloudVisualDensity.Source.NONE
                || ClientCloudVisualDensity.densityAt(fixture.dimensionId, new net.minecraft.world.phys.Vec3(x, y, z)) != 0) {
            throw new IllegalStateException("Prototype published camera density before presentation");
        }
        VolumetricCloudRenderer.invalidateHistory();
    }

    static int textureId() {
        RenderSystem.assertOnRenderThread();
        Fixture f = fixture;
        if (f == null) return 0;
        if (texture == 0) texture = GL11.glGenTextures();
        if (uploaded && f.program instanceof net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Program system
                && uploadedSignature != f.program.signature() && ConvectiveSystemCapture.subUpload(system, texture, uploadedSignature)) {
            uploadedSignature = f.program.signature(); // T260: dirty texel ranges only (incremental update evidence)
        }
        if (!uploaded || uploadedSignature != f.program.signature()) {
            float[] packed = f.program.textureData();
            var buffer = BufferUtils.createFloatBuffer(packed.length);
            buffer.put(packed).flip();
            int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            // T258: NativeImage uploads leave GL_UNPACK_ROW_LENGTH/SKIP_* set; a wide fixture texture must not inherit them.
            int rowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH), skipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
            int skipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS), alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, f.program.textureWidth(), f.program.textureHeight(), 0,
                    GL11.GL_RGBA, GL11.GL_FLOAT, buffer);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
            if (StormMorphologyCapture.anatomyCampaign()) StormMorphologyCapture.uploadState(rowLength, skipPixels, skipRows, alignment);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
            uploadedSignature = f.program.signature();
            uploaded = true;
        }
        return texture;
    }

    static void bind(ShaderInstance shader, float cameraX, float cameraY, float cameraZ) {
        Fixture f = fixture;
        shader.safeGetUniform("PaMorphologyPrototype").set(f == null ? 0 : parity ? 2 : 1);
        if (f == null) return;
        var b = f.program.bounds();
        shader.safeGetUniform("PaMorphologyOrigin").set(f.x, f.y, f.z);
        shader.safeGetUniform("PaMorphologyMin").set(b.minX(), b.minY(), b.minZ());
        shader.safeGetUniform("PaMorphologyMax").set(b.maxX(), b.maxY(), b.maxZ());
        shader.safeGetUniform("PaMorphologyScale").set(1); // MESO: micro disabled for anatomy evidence.
        shader.safeGetUniform("PaMorphologyIntervals").set(intervals ? 1 : 0);
        if (legacy) shader.safeGetUniform("PaMorphologyIntervals").set((intervals ? 1 : 0) | 2);
        shader.safeGetUniform("FrameIndex").set(0f); // Identical integration jitter for the exact/interval pair.
        shader.safeGetUniform("SlabBaseY").set(f.y + b.minY());
        shader.safeGetUniform("SlabTopY").set(f.y + b.maxY());
        shader.safeGetUniform("CameraCloudDensity").set(f.program.density(cameraX - f.x,
                cameraY - f.y, cameraZ - f.z, StormMorphologyPrototype.Scale.MESO));
        shader.safeGetUniform("PuffLobeCount").set(0);
        shader.safeGetUniform("StormLobeCount").set(0);
        shader.safeGetUniform("FunnelCount").set(0);
        shader.safeGetUniform("MaxPrecipitation").set(0f);
        shader.safeGetUniform("CoveragePretestEnabled").set(0);
        shader.safeGetUniform("PaDiagnosticOptimizationMode").set(0);
        shader.safeGetUniform("PaDiagnosticStepBudget").set(2048);
        shader.safeGetUniform("ExteriorFineStep").set(4f);
        shader.safeGetUniform("MaxRenderDistance").set(12000f);
        shader.safeGetUniform("HistoryValid").set(0);
        shader.safeGetUniform("HistoryBlend").set(0f);
        shader.safeGetUniform("PaDiagnosticLightingMode").set(1);
        if (lighting != 1) shader.safeGetUniform("PaDiagnosticLightingMode").set(lighting);
        if (poison) {
            // T260.5: any fixture term still reading world data would now differ from the control arm.
            shader.safeGetUniform("PaMorphologyIntervals").set((intervals ? 1 : 0) | (legacy ? 2 : 0) | 4);
            shader.safeGetUniform("LightDir").set(0.31f, -0.62f, 0.72f);
            shader.safeGetUniform("LightColor").set(3.1f, 0.2f, 0.9f);
            shader.safeGetUniform("AmbientTop").set(0.05f, 1.7f, 0.3f);
            shader.safeGetUniform("AmbientBottom").set(1.3f, 0.1f, 0.6f);
            shader.safeGetUniform("NightFactor").set(0.8f);
            shader.safeGetUniform("SunsetStrength").set(0.9f);
            shader.safeGetUniform("StormDarkening").set(0.7f);
            shader.safeGetUniform("WorldTime").set(123457f);
        }
        shader.safeGetUniform("ExtinctionScale").set(VolumetricCloudRenderer.Tuning.CELLS.extinctionScale());
    }

    static void clear() {
        RenderSystem.assertOnRenderThread();
        boolean hadFixture = fixture != null;
        int releasedTexture = texture;
        if (fixture != null) ClientCloudVisualDensity.clear();
        fixture = null;
        if (texture != 0) GL11.glDeleteTextures(texture);
        texture = 0;
        uploaded = false;
        parity = false;
        intervals = false;
        legacy = false;
        lighting = 1;
        poison = false;
        PIPELINE_TIMER.close();
        if (hadFixture) {
            StormMorphologyCapture.releaseReadbacks();
            boolean pass = !enabled() && ClientCloudVisualDensity.source() == ClientCloudVisualDensity.Source.NONE
                    && (releasedTexture == 0 || !GL11.glIsTexture(releasedTexture)) && !uploaded;
            net.Gabou.projectatmosphere.ProjectAtmosphere.LOGGER.info(
                    StormMorphologyCapture.campaign() + "_RESET fixture=false cameraSource={} textureReleased={} readbacksReleased=true pass={}",
                    ClientCloudVisualDensity.source(), releasedTexture == 0 || !GL11.glIsTexture(releasedTexture), pass);
            if (!pass) throw new IllegalStateException("Prototype did not release authority/resources");
        }
    }
}
