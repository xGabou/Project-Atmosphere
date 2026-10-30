package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.morphology.CloudPreviewModels;
import net.Gabou.projectatmosphere.clouds.morphology.CloudPreviewModels.Baked;
import net.Gabou.projectatmosphere.clouds.morphology.CloudPreviewModels.Candidate;
import net.Gabou.projectatmosphere.clouds.morphology.CloudPreviewModels.Quality;
import net.Gabou.projectatmosphere.clouds.morphology.CloudPreviewModels.Request;
import net.Gabou.projectatmosphere.clouds.morphology.CloudPreviewModels.Scale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * EXPERIMENTAL in-game cloud preview (development tool; disabled unless {@code PA_CLOUD_PREVIEW=1} or
 * {@code -Dprojectatmosphere.cloudPreview=true}). Bakes one experimental morphology candidate (seed, growth stage) at
 * Minecraft-compressed dimensions on a worker thread and shows it through the T260 sparse-brick fixture renderer with
 * the T260.5 baked neutral light. While a preview is shown the fixture REPLACES the world's volumetric clouds; clearing
 * it restores normal rendering. Never weather, save or network authority; production cloud generation is untouched.
 * <p>Client command {@code /cloudpreview} (see {@link #register}).
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE, modid = ProjectAtmosphere.MODID)
public final class ExperimentalCloudPreview {
    private ExperimentalCloudPreview() {}

    private static final boolean ENABLED = "1".equals(System.getenv("PA_CLOUD_PREVIEW")) || Boolean.getBoolean("projectatmosphere.cloudPreview");

    /** True only when the experimental preview was explicitly enabled for this session. */
    public static boolean enabled() { return ENABLED; }

    private static final ExecutorService BAKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PA-cloud-preview-bake");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static final AtomicInteger GENERATION = new AtomicInteger();

    private static Scale scale = Scale.uniform(CloudPreviewModels.DEFAULT_SCALE);
    private static Quality quality = Quality.NORMAL;
    private static Float baseOverride;
    private static boolean hud = true;
    private static volatile String status = "idle";
    private static Request lastRequest;
    private static Baked shown;
    /** T260.17: sun elevation (degrees, T260.5 azimuth) and light-lattice spacing override (NaN = lightSpacing). */
    private static volatile float sunElevation = 35f, lightSpacingOverride = Float.NaN;
    /** Incremented each time a re-lit program is installed (the demo waits on it). */
    private static volatile int relightGeneration;

    /** The neutral light for a baked system: current sun and spacing, with the T260.17 directional block. */
    /**
     * S5 (stabilisation, RC-07): the preview compresses geometry to {@code scale} blocks per model metre, so the
     * extinction per block is divided by the same factor: optical depth per MODEL metre then equals the production
     * renderer's (CELLS) at 1 block = 1 m. Legacy (pre-S5) kept the per-block value, which made the compressed cloud
     * ~12x too thin. {@code extinctionOverride} (NaN = none) is a diagnostic arm. Used by the light bake ({@link #lit})
     * and the march uniform (ExperimentalStormMorphologyState).
     * <p>Default OFF (log E5): at the scale-consistent value the mean free path is ~1.5 blocks, far below the baked
     * light lattice's 10-16-block spacing, so the lattice's saturating optical depth draws contour rings and node
     * stipple (not the march: a 5x finer step left them unchanged). Enable with {@code /cloudpreview extinction scaled}
     * when judging geometry, knowing that the light is then lattice-limited.
     */
    static volatile boolean scaledExtinction = false;
    static volatile float extinctionOverride = Float.NaN;
    static float previewExtinction() {
        if (Float.isFinite(extinctionOverride)) return extinctionOverride;
        float cells = VolumetricCloudRenderer.Tuning.CELLS.extinctionScale();
        return scaledExtinction ? cells / scale.min() : cells;
    }

    static NeutralLitProgram lit(Baked b) {
        float spacing = Float.isFinite(lightSpacingOverride) ? lightSpacingOverride : CloudPreviewModels.lightSpacing(b.volume());
        return new NeutralLitProgram(b.program(), b.volume(), previewExtinction(), spacing,
                net.Gabou.projectatmosphere.clouds.morphology.NeutralLightVolume.sun(sunElevation), true);
    }

    /** T260.17: re-bakes only the light of the shown system (worker thread) for a sun / spacing and installs it in place. */
    static void relight(float elevation, float spacing) {
        Baked b = shown;
        if (b == null) return;
        sunElevation = elevation;
        lightSpacingOverride = spacing;
        int gen = GENERATION.get();
        BAKER.submit(() -> {
            long t0 = System.nanoTime();
            NeutralLitProgram lit = lit(b);
            double s = (System.nanoTime() - t0) / 1e9;
            Minecraft.getInstance().execute(() -> {
                if (gen != GENERATION.get() || shown != b) return;
                shownLit = lit;
                lightSeconds = s;
                ExperimentalStormMorphologyState.install(lit, (float) ox, (float) oy, (float) oz);
                relightGeneration++;
                ProjectAtmosphere.LOGGER.info("CLOUD_PREVIEW_RELIGHT {} sunElev={} lightS={} textureMB={} {}", b.request().describe(), fmt(elevation), fmt(s),
                        fmt(lit.textureWidth() * (double) lit.textureHeight() * 16 / 1048576), lit.light.stats());
            });
        });
    }

    /** T260.16: the installed lit program (re-installed by {@link #relift}) and the system's current lift in blocks. */
    private static NeutralLitProgram shownLit;
    private static double lift;
    /** T260.18: the frozen view box already includes the (constant) lift; origin x / z pinned across candidates of a seed. */
    static boolean viewBoxLifted;
    static double[] pinXZ;
    /** World position of the preview's local origin (x/z: volume frame origin, y: cloud base). */
    private static double ox, oy, oz;
    private static double anchorX, anchorZ;
    private static boolean anchored;
    private static double bakeSeconds, lightSeconds;
    // Pending two-step ground teleport: {x, z, yaw, pitchTargetY} applied once the destination chunk is loaded.
    private static double[] pendingGround;
    private static int pendingTicks;

    // ================================================================ commands

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (ENABLED) register(event.getDispatcher());
    }

    private static final SuggestionProvider<CommandSourceStack> CANDIDATES = (c, b) -> SharedSuggestionProvider.suggest(List.of("t15", "t15f", "t14", "t14f", "t13a", "t13b", "t13c", "t12", "t12off", "t11", "t10", "t9"), b);
    private static final SuggestionProvider<CommandSourceStack> STAGES = (c, b) -> SharedSuggestionProvider.suggest(List.of("initial", "developing", "growing", "mature", "late"), b);
    private static final SuggestionProvider<CommandSourceStack> PRESETS = (c, b) -> SharedSuggestionProvider.suggest(List.of("isolated", "multicell", "broad"), b);
    private static final SuggestionProvider<CommandSourceStack> VIEWS = (c, b) -> SharedSuggestionProvider.suggest(List.of("ground300", "ground800", "ground1500", "ground", "side",
            "three_quarter", "below", "above", "far", "close", "eside", "under", "ounder", "lunder", "lounder", "cground", "cthree", "cabove", "uprofile", "fprofile"), b);
    private static final SuggestionProvider<CommandSourceStack> QUALITIES = (c, b) -> SharedSuggestionProvider.suggest(List.of("fast", "normal", "high"), b);

    /**
     * <pre>
     * /cloudpreview                                     status and help
     * /cloudpreview spawn &lt;candidate&gt; &lt;seed&gt; &lt;stage&gt;   bake and show (candidate t10|t9; stage initial|developing|growing|mature|minutes)
     * /cloudpreview preset &lt;isolated|multicell|broad&gt;
     * /cloudpreview regenerate                          re-bake the last request (after a scale / quality change)
     * /cloudpreview clear                               remove the preview; normal clouds return
     * /cloudpreview view &lt;ground|side|three_quarter|below|above|far&gt;   teleport to an inspection point (needs cheats)
     * /cloudpreview scale &lt;factor&gt; [vertical]          model metres -> blocks (default 0.085 both)
     * /cloudpreview quality &lt;fast|normal|high&gt;
     * /cloudpreview base &lt;y|auto&gt;                       cloud-base altitude (auto: sea level + compressed LCL)
     * /cloudpreview hud                                 toggle the dimension readout
     * /cloudpreview info                                print dimensions and cost to chat
     * </pre>
     */
    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(LiteralArgumentBuilder.<CommandSourceStack>literal("cloudpreview")
                .executes(c -> say(c, help()))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("candidate", StringArgumentType.word()).suggests(CANDIDATES)
                                .then(Commands.argument("seed", LongArgumentType.longArg()).suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("2609101", "2609102", "2609103", "2609104", "2609196"), b))
                                        .then(Commands.argument("stage", StringArgumentType.word()).suggests(STAGES)
                                                .executes(c -> {
                                                    Candidate cand = Candidate.parse(StringArgumentType.getString(c, "candidate"));
                                                    stageName = StringArgumentType.getString(c, "stage");
                                                    return spawn(c, cand, LongArgumentType.getLong(c, "seed"), CloudPreviewModels.stage(cand, stageName));
                                                })))))
                .then(Commands.literal("preset")
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(PRESETS)
                                .executes(c -> {
                                    var p = CloudPreviewModels.preset(StringArgumentType.getString(c, "name"));
                                    stageName = String.format(Locale.ROOT, "%.0f", p.stage());
                                    say(c, "Preset " + p.name() + ": " + p.note());
                                    return spawn(c, p.candidate(), p.seed(), p.stage());
                                })))
                .then(Commands.literal("regenerate").executes(c -> {
                    if (lastRequest == null) return fail(c, "Nothing to regenerate yet: use /cloudpreview spawn or preset");
                    return spawn(c, lastRequest.candidate(), lastRequest.seed(), lastRequest.stage());
                }))
                .then(Commands.literal("clear").executes(c -> { clear(); return say(c, "Cloud preview cleared; normal clouds are rendered again."); }))
                .then(Commands.literal("view")
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(VIEWS)
                                .executes(c -> view(c, StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("scale")
                        .then(Commands.argument("factor", FloatArgumentType.floatArg(.005f, 1f))
                                .executes(c -> setScale(c, FloatArgumentType.getFloat(c, "factor"), FloatArgumentType.getFloat(c, "factor")))
                                .then(Commands.argument("vertical", FloatArgumentType.floatArg(.005f, 1f))
                                        .executes(c -> setScale(c, FloatArgumentType.getFloat(c, "factor"), FloatArgumentType.getFloat(c, "vertical"))))))
                .then(Commands.literal("quality")
                        .then(Commands.argument("level", StringArgumentType.word()).suggests(QUALITIES)
                                .executes(c -> {
                                    quality = Quality.parse(StringArgumentType.getString(c, "level"));
                                    return say(c, "Quality " + quality.name().toLowerCase(Locale.ROOT) + " (voxel " + quality.voxel + " blocks); /cloudpreview regenerate to apply.");
                                })))
                .then(Commands.literal("base")
                        .then(Commands.literal("auto").executes(c -> { baseOverride = null; return say(c, "Cloud base: sea level + compressed LCL; /cloudpreview regenerate to apply."); }))
                        .then(Commands.argument("y", FloatArgumentType.floatArg(-64, 2000))
                                .executes(c -> { baseOverride = FloatArgumentType.getFloat(c, "y"); return say(c, "Cloud base y=" + baseOverride + "; /cloudpreview regenerate to apply."); })))
                .then(Commands.literal("light")
                        .then(Commands.argument("mode", com.mojang.brigadier.arguments.IntegerArgumentType.integer(20, 41))
                                .executes(c -> {
                                    int m = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "mode");
                                    ExperimentalStormMorphologyState.lighting(m);
                                    return say(c, "Lighting mode " + m + " (" + lightName(m) + ")");
                                })))
                .then(Commands.literal("sun")
                        .then(Commands.argument("elevation", FloatArgumentType.floatArg(1, 89))
                                .executes(c -> { relight(FloatArgumentType.getFloat(c, "elevation"), lightSpacingOverride); return say(c, "Re-baking the light for a sun at " + FloatArgumentType.getFloat(c, "elevation") + " deg"); })))
                .then(Commands.literal("lightspacing")
                        .then(Commands.literal("auto").executes(c -> { relight(sunElevation, Float.NaN); return say(c, "Light lattice spacing: auto"); }))
                        .then(Commands.argument("blocks", FloatArgumentType.floatArg(4, 64))
                                .executes(c -> { relight(sunElevation, FloatArgumentType.getFloat(c, "blocks")); return say(c, "Light lattice spacing " + FloatArgumentType.getFloat(c, "blocks")); })))
                .then(Commands.literal("extinction") // S5: scale-consistent (lattice-limited light, log E5) or the per-block default
                        .then(Commands.literal("scaled").executes(c -> { scaledExtinction = true; relight(sunElevation, lightSpacingOverride); return say(c, "Extinction scale-consistent: " + previewExtinction() + "/block"); }))
                        .then(Commands.literal("legacy").executes(c -> { scaledExtinction = false; relight(sunElevation, lightSpacingOverride); return say(c, "Extinction legacy (pre-S5): " + previewExtinction() + "/block"); })))
                .then(Commands.literal("res")
                        .then(Commands.literal("auto").executes(c -> { VolumetricCloudDebugConfig.setFixedResolutionScale(Float.NaN); return say(c, "Cloud resolution: renderer default"); }))
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(.1f, 1f))
                                .executes(c -> { VolumetricCloudDebugConfig.setFixedResolutionScale(FloatArgumentType.getFloat(c, "scale")); return say(c, "Cloud resolution scale " + FloatArgumentType.getFloat(c, "scale")); })))
                .then(Commands.literal("lift")
                        .then(Commands.argument("blocks", FloatArgumentType.floatArg(0, 1000))
                                .executes(c -> { relift(FloatArgumentType.getFloat(c, "blocks")); return say(c, "System lifted " + lift + " blocks"); })))
                .then(Commands.literal("hud").executes(c -> { hud = !hud; return say(c, "Preview HUD " + (hud ? "on" : "off")); }))
                .then(Commands.literal("groups").executes(c -> { groupFrames = !groupFrames; return say(c, "Group boundaries " + (groupFrames ? "on" : "off")); }))
                .then(Commands.literal("info").executes(c -> say(c, info()))));
    }

    /** T260.16 lighting modes (PaDiagnosticLightingMode, experimental fixture only). */
    static String lightName(int m) {
        return switch (m) {
            case 20 -> "T260.5 neutral"; case 21 -> "density"; case 22 -> "direct sun only"; case 23 -> "ambient only";
            case 24 -> "sun optical depth (baked, capped at 8)"; case 25 -> "neutral, per-sample reference marches";
            case 26 -> "enhanced ambient fill (x3)"; case 27 -> "raking light 12 deg below the horizon"; case 28 -> "density-gradient normal";
            case 29 -> "T260.16 physically motivated candidate"; case 30 -> "sun optical depth uncapped (red > 8)";
            case 31 -> "T260.17 A directional ambient"; case 32 -> "T260.17 B gradient-aware"; case 33 -> "T260.17 C baked hybrid";
            case 34 -> "factor: + diffuse transmission"; case 35 -> "factor: + directional ambient"; case 36 -> "factor: display mapping";
            case 37 -> "baked filtered normal"; case 38 -> "T260.17 D grazing ambient quadrature"; case 39 -> "D quadrature ambient only"; case 40 -> "T260.18 L-graz bounded grazing shadows"; case 41 -> "T260.18 L-graz-c per-surface grazing shadows";
            case 42 -> "T260.L-family production lighting family (baked)"; case 43 -> "T260.L-family + two-tap sun-ward skin cone";
            default -> "?";
        };
    }

    private static String help() {
        return "Experimental cloud preview (" + status + "). /cloudpreview preset <isolated|multicell|broad>, spawn <t15|t15f|t14|t14f|t13a|t13b|t13c|t12|t12off|t11|t10|t9> <seed> <stage>, "
                + "view <ground300|ground800|ground1500|three_quarter|above|close|side|below|far>, scale <f> [v], quality <fast|normal|high>, base <y|auto>, regenerate, clear, hud, groups, info. Scale " + scaleText() + ", quality " + quality.name().toLowerCase(Locale.ROOT) + ".";
    }

    private static String scaleText() {
        return scale.horizontal() == scale.vertical() ? String.format(Locale.ROOT, "%.3f", scale.horizontal())
                : String.format(Locale.ROOT, "%.3f horizontal / %.3f vertical", scale.horizontal(), scale.vertical());
    }

    private static int setScale(CommandContext<CommandSourceStack> c, float h, float v) {
        scale = new Scale(h, v);
        return say(c, "Scale " + scaleText() + " blocks per model metre; /cloudpreview regenerate to apply.");
    }

    private static int say(CommandContext<CommandSourceStack> c, String s) {
        c.getSource().sendSuccess(() -> Component.literal("[cloud preview] " + s).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String s) {
        c.getSource().sendFailure(Component.literal("[cloud preview] " + s));
        return 0;
    }

    private static void chat(String s, boolean error) {
        var mc = Minecraft.getInstance();
        if (mc.gui != null) mc.gui.getChat().addMessage(Component.literal("[cloud preview] " + s).withStyle(error ? ChatFormatting.RED : ChatFormatting.AQUA));
    }

    // ================================================================ spawn / bake / install

    private static int spawn(CommandContext<CommandSourceStack> c, Candidate cand, long seed, float stage) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return fail(c, "Join a world first");
        if (!VolumetricCloudRenderHook.isActive()) return fail(c, "The volumetric cloud renderer is not active (check the cloud renderer config)");
        Request r = new Request(cand, seed, stage, scale, quality);
        // Anchor: ahead of the player (placed once the size is known); kept for regenerate of the same request.
        if (!anchored || lastRequest == null || lastRequest.seed() != seed || lastRequest.candidate() != cand) {
            var look = mc.player.getLookAngle();
            double h = Math.hypot(look.x, look.z);
            double dx = h < 1e-3 ? 0 : look.x / h, dz = h < 1e-3 ? 1 : look.z / h;
            anchorX = mc.player.getX() + dx;
            anchorZ = mc.player.getZ() + dz;
            anchorDir = new double[]{dx, dz, mc.player.getX(), mc.player.getZ()};
            anchored = true;
        }
        submit(r);
        return say(c, "Baking " + r.describe() + " on a worker thread (the game keeps running)...");
    }

    private static double[] anchorDir = {0, 1, 0, 0};
    /** Demo placement: centre the system ON the anchor instead of ahead of it. */
    private static boolean centreOnAnchor;
    private static String stageName = "";
    private static boolean groupFrames = true;

    static void submit(Request r) {
        lastRequest = r;
        int gen = GENERATION.incrementAndGet();
        status = "baking " + r.describe();
        ProjectAtmosphere.LOGGER.info("CLOUD_PREVIEW_BAKE_START gen={} {}", gen, r.describe());
        BAKER.submit(() -> {
            try {
                long t0 = System.nanoTime();
                Baked b = CloudPreviewModels.bake(r);
                double bakeS = (System.nanoTime() - t0) / 1e9;
                long t1 = System.nanoTime();
                // T260.5 neutral light, baked once per volume and appended to the fixture texture.
                NeutralLitProgram lit = lit(b);
                double lightS = (System.nanoTime() - t1) / 1e9;
                Minecraft.getInstance().execute(() -> install(gen, b, lit, bakeS, lightS));
            } catch (Throwable e) {
                ProjectAtmosphere.LOGGER.warn("CLOUD_PREVIEW_BAKE_FAILED gen={} {}", gen, r.describe(), e);
                Minecraft.getInstance().execute(() -> {
                    if (gen != GENERATION.get()) return;
                    status = "failed: " + e.getMessage();
                    chat("Bake failed: " + e.getMessage(), true);
                });
            }
        });
    }

    private static void install(int gen, Baked b, NeutralLitProgram lit, double bakeS, double lightS) {
        if (gen != GENERATION.get()) return; // superseded or cleared while baking
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { status = "idle"; return; }
        var dims = b.blocks();
        // Centre the measured footprint on the anchor, pushed ahead so the player starts outside the cloud.
        double ahead = centreOnAnchor ? 0 : .5 * Math.max(dims.width(), dims.depth()) + Math.max(250, .5 * dims.height());
        double cx = anchorDir[2] + anchorDir[0] * ahead, cz = anchorDir[3] + anchorDir[1] * ahead;
        ox = cx - dims.centerX();
        oz = cz - dims.centerZ();
        if (pinXZ != null) { ox = pinXZ[0]; oz = pinXZ[1]; } // T260.18: the same model frame for every candidate of a seed
        oy = baseOverride != null ? baseOverride : mc.level.getSeaLevel() + b.baseAglMetres() * b.request().scale().vertical();
        ExperimentalStormMorphologyState.install(lit, (float) ox, (float) oy, (float) oz);
        ExperimentalStormMorphologyState.intervals(true);
        ExperimentalStormMorphologyState.legacy(false);
        ExperimentalStormMorphologyState.poison(false);
        ExperimentalStormMorphologyState.lighting(20); // T260.5 neutral volumetric (baked light)
        shown = b;
        shownLit = lit;
        lift = 0;
        bakeSeconds = bakeS;
        lightSeconds = lightS;
        status = "showing " + b.request().describe();
        ProjectAtmosphere.LOGGER.info("CLOUD_PREVIEW_SHOWN {} cells={} blocks W={} D={} H={} baseY={} topY={} modelKm W={} D={} H={} bakeS={} lightS={} bricks={} textureMB={} origin=({}, {}, {}) env=[{}]",
                b.request().describe(), b.cells(), fmt(dims.width()), fmt(dims.depth()), fmt(dims.height()), fmt(oy + dims.minY()), fmt(oy + dims.maxY()),
                fmt(b.model().width() / 1000), fmt(b.model().depth() / 1000), fmt(b.model().height() / 1000), fmt(bakeS), fmt(lightS), b.volume().liveBricks(),
                fmt(lit.textureWidth() * (double) lit.textureHeight() * 16 / 1048576), fmt(ox), fmt(oy), fmt(oz), b.environment());
        ProjectAtmosphere.LOGGER.info("CLOUD_PREVIEW_LIGHT {} {}", b.request().describe(), lit.light.stats());
        for (var gi : b.groups())
            ProjectAtmosphere.LOGGER.info("CLOUD_PREVIEW_GROUP G{} role={} cells={} localT={} W={} D={} baseY={} topY={} centre=({}, {}) cached={}", gi.index(), gi.role(), gi.cells(),
                    fmt(gi.localTime()), fmt(gi.blocks().width()), fmt(gi.blocks().depth()), fmt(oy + gi.blocks().minY()), fmt(oy + gi.blocks().maxY()),
                    fmt(ox + gi.blocks().centerX()), fmt(oz + gi.blocks().centerZ()), gi.cached());
        if (!CloudPreviewModels.lastSystemLog.isEmpty() && b.request().candidate() == Candidate.T11) ProjectAtmosphere.LOGGER.info("{}", CloudPreviewModels.lastSystemLog);
        chat(info(), false);
    }

    /**
     * T260.16: moves the shown system up by {@code blocks} (relative to its baked placement) by re-installing the same lit
     * program at the new origin - no re-bake, no re-upload (same texture signature). The preview base sits only ~20
     * blocks above the terrain, too low for an underside camera to see more than a few masses; the lighting is
     * fixture-local (T260.5: no world term), so a lifted system renders identically apart from its position.
     */
    static void relift(double blocks) {
        if (shown == null || shownLit == null || blocks == lift || !ExperimentalStormMorphologyState.enabled()) return;
        oy += blocks - lift;
        lift = blocks;
        ExperimentalStormMorphologyState.install(shownLit, (float) ox, (float) oy, (float) oz);
    }

    static void clear() {
        GENERATION.incrementAndGet();
        pendingGround = null;
        if (shown != null || ExperimentalStormMorphologyState.enabled()) {
            if (com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) ExperimentalStormMorphologyState.clear();
            else Minecraft.getInstance().execute(ExperimentalStormMorphologyState::clear);
        }
        shown = null;
        anchored = false;
        status = "idle";
    }

    static String info() {
        Baked b = shown;
        if (b == null || !ExperimentalStormMorphologyState.enabled()) return "No preview shown (" + status + ").";
        var d = b.blocks();
        // S5: the smallest feature the bake can represent (~2 voxels) in model metres, and the extinction in use.
        float smallest = 2 * b.request().quality().voxel / b.request().scale().min();
        return String.format(Locale.ROOT, "%s, %d live cells%s: %.0f wide x %.0f deep x %.0f tall blocks (base y %.0f, top y %.0f); model %.1f x %.1f x %.1f km; bake %.1f s + light %.1f s, %d bricks; smallest feature ~%.0f m; extinction %.3f/block.",
                b.request().describe(), b.cells(), b.groups().isEmpty() ? "" : " in " + b.groups().size() + " groups", d.width(), d.depth(), d.height(), oy + d.minY(), oy + d.maxY(),
                b.model().width() / 1000, b.model().depth() / 1000, b.model().height() / 1000, bakeSeconds, lightSeconds, b.volume().liveBricks(), smallest, previewExtinction());
    }

    private static String fmt(double v) { return String.format(Locale.ROOT, "%.2f", v); }

    // ================================================================ inspection points

    /** World-space box of the shown cloud: {minX, minY, minZ, maxX, maxY, maxZ}. */
    private static double[] box() {
        var d = shown.blocks();
        return new double[]{ox + d.centerX() - .5 * d.width(), oy + d.minY(), oz + d.centerZ() - .5 * d.depth(),
                ox + d.centerX() + .5 * d.width(), oy + d.maxY(), oz + d.centerZ() + .5 * d.depth()};
    }

    private static int view(CommandContext<CommandSourceStack> c, String name) {
        if (shown == null) return fail(c, "No preview shown");
        String err = teleport(name);
        return err == null ? say(c, "View '" + name + "' (spectator mode recommended: /gamemode spectator).") : fail(c, err);
    }

    /**
     * Teleports the player (server /tp, needs cheats) to an inspection point. Points sit on the sun side of the
     * neutral light (sun azimuth from -x/+z) so the lit faces are seen; 'ground' and 'below' stand on the terrain.
     */
    static String teleport(String name) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return "no player";
        double[] b = viewBox != null ? viewBox : box();
        double cx = .5 * (b[0] + b[3]), cz = .5 * (b[2] + b[5]), w = Math.max(b[3] - b[0], b[5] - b[2]), h = b[4] - b[1];
        double vf = Math.toRadians(mc.options.fov().get() * mc.player.getFieldOfViewModifier());
        double sx = -Math.sqrt(.5), sz = Math.sqrt(.5); // toward the sun (NeutralLightVolume.SUN azimuth)
        double ex, ey, ez, ty;
        if (name.equals("cground") || name.equals("cthree") || name.equals("cabove")) {
            // T260.13: views centred on the lower mass (frozen target), sun side of its pair axis.
            double[] c = closeTarget != null ? closeTarget : strongestConnection();
            if (c == null) return "No lower mass / connection to centre on";
            closeTarget = c;
            double px = -c[4], pz = c[3];
            if (px * sx + pz * sz < 0) { px = -px; pz = -pz; }
            // The side of the pair axis whose line of sight to the connection is clear of other cloud; chosen once
            // (first candidate of a seed) and frozen, so every candidate gets the identical cameras.
            if (viewSide == 0) viewSide = clearerSide(c, px, pz, 900, b[1] + 40) >= 0 ? 1 : -1;
            px *= viewSide; pz *= viewSide;
            double half = .5 * Math.max(b[3] - b[0], b[5] - b[2]);
            double qx, qz, qy, tyy, hd;
            if (name.equals("cground")) {
                double dist = 900;
                qx = c[0] + px * dist; qz = c[2] + pz * dist;
                hd = Math.hypot(c[0] - qx, c[2] - qz);
                float yaw = (float) Math.toDegrees(Math.atan2(-(c[0] - qx), c[2] - qz));
                pendingGround = new double[]{qx, qz, yaw, c[1] + 120, hd, c[0], c[2]};
                pendingTicks = 0;
                sendTp(qx, Math.max(mc.level.getSeaLevel() + 120, 200), qz, yaw, 0);
                return null;
            }
            if (name.equals("cthree")) {
                double dist = 1100;
                qx = c[0] + (px * .8 + c[3] * .6) * dist; qz = c[2] + (pz * .8 + c[4] * .6) * dist;
                qy = b[1] + .5 * h; tyy = c[1] + 100;
            } else {
                qx = c[0] + px * 300; qz = c[2] + pz * 300;
                qy = b[4] + 250; tyy = c[1];
            }
            hd = Math.hypot(c[0] - qx, c[2] - qz);
            float yaw = (float) Math.toDegrees(Math.atan2(-(c[0] - qx), c[2] - qz));
            sendTp(qx, qy, qz, yaw, (float) -Math.toDegrees(Math.atan2(tyy - qy, hd)));
            return null;
        }
        if (name.equals("lunder") || name.equals("lounder")) {
            // T260.16: underside views of the LIFTED system (relift): 'lunder' straight up from 150 blocks under the
            // system's lowest point at the frozen focus (the strongest link); 'lounder' obliquely, 110 blocks under it and
            // 420 blocks off across the pair axis on the sun side, aimed 30 blocks above that lowest point. Both are far
            // below every density sample and far above the terrain (the lift is chosen for that).
            double[] c = closeTarget != null ? closeTarget : strongestConnection();
            if (c == null) c = new double[]{cx, b[1] + .12 * h, cz, 1, 0};
            closeTarget = c;
            double low = b[1] + (viewBoxLifted ? 0 : lift);
            if (name.equals("lunder")) {
                float yaw = (float) Math.toDegrees(Math.atan2(-c[3], c[4]));
                sendTp(c[0], low - 150, c[2], yaw, -89.9f);
                return null;
            }
            double px = -c[4], pz = c[3];
            if (px * sx + pz * sz < 0) { px = -px; pz = -pz; }
            ex = c[0] + px * 420; ez = c[2] + pz * 420;
            double dx = c[0] - ex, dz = c[2] - ez, hd = Math.hypot(dx, dz);
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            double ey0 = low - 110;
            sendTp(ex, ey0, ez, yaw, (float) -Math.toDegrees(Math.atan2(low + 30 - ey0, hd)));
            return null;
        }
        if (name.equals("fprofile")) {
            // T260.18: the FOUNDATION underside edge-on at foundation scale - eye 20 blocks below the system's lowest point,
            // 700 blocks from the frozen focus (the strongest link) across the pair axis on the sun side, looking
            // horizontally at it (aimed 30 blocks above the eye): the base profile against the sky, filling the frame.
            double[] c = closeTarget != null ? closeTarget : strongestConnection();
            if (c == null) c = new double[]{cx, b[1] + .12 * h, cz, 1, 0};
            closeTarget = c;
            double px = -c[4], pz = c[3];
            if (px * sx + pz * sz < 0) { px = -px; pz = -pz; }
            ex = c[0] + px * 700; ez = c[2] + pz * 700;
            double dx = c[0] - ex, dz = c[2] - ez, hd = Math.hypot(dx, dz), ey0 = b[1] - 20;
            sendTp(ex, ey0, ez, (float) Math.toDegrees(Math.atan2(-dx, dz)), (float) -Math.toDegrees(Math.atan2(30, hd)));
            return null;
        }
        if (name.equals("uprofile")) {
            // T260.18: the underside edge-on - eye 25 blocks below the system's lowest point, outside the footprint on the
            // sun side across the long axis (as 'eside'), looking horizontally (aimed 40 blocks above the eye): the base
            // profile is seen against the sky, so depth variation shows whatever the lighting does.
            double ax = (b[3] - b[0]) >= (b[5] - b[2]) ? 0 : 1, az = 1 - ax;
            if (ax * sx + az * sz < 0) { ax = -ax; az = -az; }
            double span = Math.max(b[3] - b[0], b[5] - b[2]), depth = .5 * ((b[3] - b[0]) >= (b[5] - b[2]) ? b[5] - b[2] : b[3] - b[0]);
            double hf = 2 * Math.atan(Math.tan(.5 * vf) * 16.0 / 9.0);
            double fit = .5 * span / Math.tan(.5 * hf) + depth;
            ex = cx + ax * fit; ez = cz + az * fit;
            double dx = cx - ex, dz = cz - ez, hd = Math.hypot(dx, dz), ey0 = b[1] - 25;
            sendTp(ex, ey0, ez, (float) Math.toDegrees(Math.atan2(-dx, dz)), (float) -Math.toDegrees(Math.atan2(40, hd)));
            return null;
        }
        if (name.equals("under") || name.equals("ounder")) {
            // T260.15: under the foundation (the strongest link, frozen like 'close'), looking up at its underside:
            // 'under' almost straight up (60 blocks off the target), 'ounder' obliquely across it (380 blocks off, across
            // the pair axis on the sun side, aimed just above the base level).
            double[] c = closeTarget != null ? closeTarget : strongestConnection();
            if (c == null) c = new double[]{cx, b[1] + .12 * h, cz, 1, 0};
            closeTarget = c;
            double px = -c[4], pz = c[3];
            if (px * sx + pz * sz < 0) { px = -px; pz = -pz; }
            boolean up = name.equals("under");
            double dist = up ? 60 : 380;
            ex = c[0] + px * dist; ez = c[2] + pz * dist;
            double dx = c[0] - ex, dz = c[2] - ez, hd = Math.hypot(dx, dz);
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            // In the air just under the lowest cloud (the preview base is often only ~20 blocks above the terrain, so a
            // ground camera stands inside hills / trees): spectator eye 10 blocks below the system's lowest point.
            double ey0 = b[1] - 10;
            sendTp(ex, ey0, ez, yaw, (float) -Math.toDegrees(Math.atan2((up ? b[1] + 400 : b[1] + 60) - ey0, hd)));
            return null;
        }
        if (name.equals("close")) {
            // T260.12: on the ground beside the strongest live connection, across its pair axis (sun side), looking at it.
            double[] c = closeTarget != null ? closeTarget : strongestConnection();
            // T260.14: a system without any organised pair is inspected at its centre (frozen like every target).
            if (c == null) c = new double[]{cx, b[1] + .12 * h, cz, 1, 0};
            closeTarget = c;
            double px = -c[4], pz = c[3];
            if (px * sx + pz * sz < 0) { px = -px; pz = -pz; }
            // Far enough out that no cloud hangs over the camera (the column above it is clear).
            double dist = closeDist > 0 ? closeDist : 320;
            double[] bb = box();
            while (closeDist <= 0 && dist < 2000) {
                boolean clear = true;
                for (double yy = bb[1]; yy <= bb[4] && clear; yy += 16) clear = density(c[0] + px * dist, yy, c[2] + pz * dist) == 0;
                if (clear) break;
                dist += 80;
            }
            closeDist = dist; // frozen with the target: every candidate of a seed gets the identical close camera
            ex = c[0] + px * dist; ez = c[2] + pz * dist;
            double dx = c[0] - ex, dz = c[2] - ez, hd = Math.hypot(dx, dz);
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            pendingGround = new double[]{ex, ez, yaw, c[1] + 40, hd, c[0], c[2]};
            pendingTicks = 0;
            sendTp(ex, Math.max(mc.level.getSeaLevel() + 120, 200), ez, yaw, 0);
            return null;
        }
        if (name.matches("ground\\d+")) {
            // Standing on the ground N blocks outside the system's footprint (from its edge, sun side).
            double n = Double.parseDouble(name.substring(6));
            double hw = .5 * (b[3] - b[0]), hdp = .5 * (b[5] - b[2]);
            double edge = Math.min(hw / Math.abs(sx), hdp / Math.abs(sz));
            double dist = edge + n;
            ex = cx + sx * dist; ez = cz + sz * dist;
            name = "ground_n";
            ty = b[1] + .45 * h;
            ey = Double.NaN;
        } else { ex = ez = ey = ty = 0; }
        switch (name) {
            case "ground_n" -> { }
            case "ground" -> { double dist = Math.max(400, .6 * w + 200); ex = cx + sx * dist; ez = cz + sz * dist; ey = Double.NaN; ty = b[1] + .5 * h; }
            case "far" -> { double dist = 2.2 * Math.max(w, h) + 600; ex = cx + sx * dist; ez = cz + sz * dist; ey = Double.NaN; ty = b[1] + .5 * h; }
            case "side", "three_quarter" -> {
                double fit = .5 * Math.max(h, w) / Math.tan(.5 * vf) + .5 * w;
                double ax = name.equals("side") ? -1 : sx, az = name.equals("side") ? 0 : sz;
                ex = cx + ax * fit; ez = cz + az * fit; ey = b[1] + .5 * h; ty = ey;
            }
            case "eside" -> {
                // T260.14: elevated side view across the system's long axis (sun side), eye at ~40 % of the height,
                // looking down at the lower foundation; far enough that the whole width fits the frame.
                double ax = (b[3] - b[0]) >= (b[5] - b[2]) ? 0 : 1, az = 1 - ax;
                if (ax * sx + az * sz < 0) { ax = -ax; az = -az; }
                double span = Math.max(b[3] - b[0], b[5] - b[2]), depth = .5 * ((b[3] - b[0]) >= (b[5] - b[2]) ? b[5] - b[2] : b[3] - b[0]);
                double hf = 2 * Math.atan(Math.tan(.5 * vf) * 16.0 / 9.0);
                double fit = .55 * span / Math.tan(.5 * hf) + depth;
                ex = cx + ax * fit; ez = cz + az * fit; ey = b[1] + .4 * h; ty = b[1] + .1 * h;
            }
            case "below" -> { ex = cx + sx * .3 * w; ez = cz + sz * .3 * w; ey = Double.NaN; ty = b[1] + .6 * h; }
            case "above" -> { double dist = .6 * w + 150; ex = cx + sx * dist; ez = cz + sz * dist; ey = b[4] + .45 * w + 150; ty = b[1] + .4 * h; }
            default -> { return "Unknown view '" + name + "' (ground, side, three_quarter, eside, below, above, far)"; }
        }
        double dx = cx - ex, dz = cz - ez, hd = Math.hypot(dx, dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        if (Double.isNaN(ey)) {
            // Stand on the terrain: go there high first, then drop once the chunk is loaded (two-step, see tick).
            pendingGround = new double[]{ex, ez, yaw, ty, hd, cx, cz};
            pendingTicks = 0;
            sendTp(ex, Math.max(mc.level.getSeaLevel() + 120, 200), ez, yaw, (float) -Math.toDegrees(Math.atan2(ty - 200, hd)));
            return null;
        }
        sendTp(ex, ey, ez, yaw, (float) -Math.toDegrees(Math.atan2(ty - ey, hd)));
        return null;
    }

    private static double[] lastTp;
    private static double lastLift;
    /** T260.12 demo: view points computed from one system box, reused for every candidate of a seed (identical cameras). */
    static double[] viewBox;
    /** T260.13: +1 / -1 side of the connection's pair axis used by the connection views (0 = not chosen yet). */
    static int viewSide;

    /** Positive when the +side line of sight (ground camera to the connection) crosses less cloud than the -side one. */
    private static double clearerSide(double[] c, double px, double pz, double dist, double eyeY) {
        double[] occ = new double[2];
        for (int k = 0; k < 2; k++) {
            double sgn = k == 0 ? 1 : -1, ex = c[0] + px * sgn * dist, ez = c[2] + pz * sgn * dist;
            for (int n = 0; n < 48; n++) {
                double f = n / 48.0 * .85;
                occ[k] += density(ex + (c[0] - ex) * f, eyeY + (c[1] - eyeY) * f, ez + (c[2] - ez) * f);
                occ[k] += density(ex + (c[0] - ex) * f, eyeY + (c[1] + 300 - eyeY) * f, ez + (c[2] - ez) * f);
            }
        }
        return occ[1] - occ[0] + 1e-3; // ties: the sun side
    }
    /** T260.12: {x, y, z, axisX, axisZ} of the connection the close view looks at (kept for the WITHOUT comparison). */
    static double[] closeTarget;
    /** T260.14: the close camera's distance, frozen with {@link #closeTarget} (0 = not chosen yet). */
    static double closeDist;

    private static double[] strongestConnection() {
        Baked b = shown;
        if (b == null) return null;
        CloudPreviewModels.ConnectionInfo best = null;
        // A bridge (between separate groups) shows a connection; a merger sits inside overlapping bases.
        for (var c : b.connections()) if (best == null || (c.merger() ? 0 : 10) + c.strength() * c.liveCells() > (best.merger() ? 0 : 10) + best.strength() * best.liveCells()) best = c;
        return best == null ? null : new double[]{ox + best.x(), oy + best.y(), oz + best.z(), best.axisX(), best.axisZ()};
    }

    private static double cxTarget() { double[] b = box(); return .5 * (b[0] + b[3]); }
    private static double czTarget() { double[] b = box(); return .5 * (b[2] + b[5]); }

    /** Preview density at a world point (0 outside). */
    private static float density(double x, double y, double z) {
        Baked b = shown;
        return b == null ? 0 : b.program().density((float) (x - ox), (float) (y - oy), (float) (z - oz), null);
    }

    private static void sendTp(double x, double y, double z, float yaw, float pitch) {
        var mc = Minecraft.getInstance();
        lastTp = new double[]{x, y, z};
        mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f %.2f %.2f", x, y, z, yaw, Math.max(-89.9f, Math.min(89.9f, pitch))));
        VolumetricCloudRenderer.invalidateHistory();
    }

    // ================================================================ tick, HUD, lifecycle

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (shown != null && !ExperimentalStormMorphologyState.enabled()) { shown = null; status = "idle (fixture released by the renderer)"; }
        if (pendingGround != null && mc.level != null && mc.player != null) {
            double[] p = pendingGround;
            int cx = (int) Math.floor(p[0]) >> 4, cz = (int) Math.floor(p[1]) >> 4;
            boolean there = lastTp != null && Math.abs(mc.player.getX() - p[0]) < 3 && Math.abs(mc.player.getZ() - p[1]) < 3;
            if (++pendingTicks > 20 * 60) pendingGround = null; // terrain never arrived: stay at the high point
            else if (pendingTicks > 10 && there && mc.level.getChunkSource().hasChunk(cx, cz)) {
                double y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(p[0]), (int) Math.floor(p[1])) + 1.7;
                // T260.18: above any canopy over and around the spot (the client heightmap can sit under the leaves of a
                // forest; a neighbouring tree hides the view as well as one overhead)
                var col = new net.minecraft.core.BlockPos.MutableBlockPos();
                int top = Integer.MIN_VALUE;
                for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++)
                    for (int yy = (int) Math.floor(y) - 2; yy < y + 48; yy++)
                        if (!mc.level.getBlockState(col.set((int) Math.floor(p[0]) + dx, yy, (int) Math.floor(p[1]) + dz)).isAir()) top = Math.max(top, yy);
                if (top != Integer.MIN_VALUE) y = Math.max(y, top + 1 + 1.7);
                // Terrain clearance: raise the eye until the line to the cloud base clears every loaded terrain column
                // on the way (unloaded terrain is not rendered, so it cannot occlude).
                double baseY = shown != null ? Math.min(box()[1], p[3]) : y, lift = 0, dirX = (p[5] - p[0]) / p[4], dirZ = (p[6] - p[1]) / p[4];
                for (double d = 8; d < p[4] - 8; d += 8) {
                    int tx = (int) Math.floor(p[0] + dirX * d), tz = (int) Math.floor(p[1] + dirZ * d);
                    if (!mc.level.getChunkSource().hasChunk(tx >> 4, tz >> 4)) break;
                    double ht = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, tx, tz) + 2, f = d / p[4];
                    double need = (ht - baseY * f) / (1 - f) - y; // eye height so the ray to the base passes above ht
                    lift = Math.max(lift, need);
                }
                y += Math.max(0, lift);
                // Never inside the cloud.
                while (shown != null && density(p[0], y, p[1]) > 0 && y > -60) y -= 8;
                pendingGround = null;
                lastLift = lift;
                sendTp(p[0], y - 1.62, p[1], (float) p[2], (float) -Math.toDegrees(Math.atan2(p[3] - y, p[4])));
            }
        }
        Demo.tick(mc);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (!ENABLED) return;
        GENERATION.incrementAndGet();
        shown = null;
        anchored = false;
        pendingGround = null;
        status = "idle";
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!ENABLED || !hud) return;
        var mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        List<String> lines = new ArrayList<>();
        Baked b = shown;
        if (b != null && ExperimentalStormMorphologyState.enabled()) {
            var d = b.blocks();
            double[] box = box();
            double cx = .5 * (box[0] + box[3]), cz = .5 * (box[2] + box[5]);
            lines.add("EXPERIMENTAL CLOUD PREVIEW");
            lines.add(b.request().describe() + (stageName.isEmpty() ? "" : "  stage " + stageName));
            lines.add(String.format(Locale.ROOT, "system W %.0f  D %.0f  H %.0f blocks  base y %.0f  top y %.0f", d.width(), d.depth(), d.height(), box[1], box[4]));
            lines.add(String.format(Locale.ROOT, "model %.1f x %.1f x %.1f km, %d cells%s", b.model().width() / 1000, b.model().depth() / 1000, b.model().height() / 1000, b.cells(),
                    b.groups().isEmpty() ? "" : ", " + b.groups().size() + " groups"));
            for (var gi : b.groups())
                lines.add(String.format(Locale.ROOT, " G%d %-10s %2d cells  %4.0f x %4.0f  top y %4.0f", gi.index(), gi.role(), gi.cells(), gi.blocks().width(), gi.blocks().depth(),
                        oy + gi.blocks().maxY()));
            for (var ci : b.connections())
                lines.add(b.components().isEmpty()
                        ? String.format(Locale.ROOT, " C G%d-G%d %s strength %.2f, %d/%d cells", ci.a(), ci.b(), ci.merger() ? "merger" : "bridge", ci.strength(), ci.liveCells(), ci.cells())
                        : CloudPreviewModels.foundationVersion(b.request().candidate()) > 0
                        ? String.format(Locale.ROOT, " foundation link G%d-G%d strength %.2f (%d foundation masses)", ci.a(), ci.b(), ci.strength(), ci.cells())
                        : String.format(Locale.ROOT, " lower mass G%d-G%d strength %.2f, %d components", ci.a(), ci.b(), ci.strength(), ci.cells()));
            lines.add(String.format(Locale.ROOT, "you: %.0f blocks from centre, y %.0f", Math.hypot(mc.player.getX() - cx, mc.player.getZ() - cz), mc.player.getY()));
            lines.add(String.format(Locale.ROOT, "bake %.1f s + light %.1f s (worker thread)", bakeSeconds, lightSeconds));
        }
        if (status.startsWith("baking") || status.startsWith("failed")) lines.add("cloud preview: " + status);
        if (lines.isEmpty()) return;
        var g = event.getGuiGraphics();
        if (b != null && groupFrames && ExperimentalStormMorphologyState.enabled()) frames(g, mc, b);
        int top = 26, y = top + 2, w = 0; // below the top-centre performance line
        for (String s : lines) w = Math.max(w, mc.font.width(s));
        g.fill(2, top, 8 + w, top + 4 + lines.size() * 10, 0x90000000);
        for (String s : lines) { g.drawString(mc.font, s, 5, y, 0xFFE0F0FF, false); y += 10; }
    }

    // ---------------------------------------------------------------- projected group boundaries

    private static final org.joml.Matrix4f VIEW = new org.joml.Matrix4f(), PROJ = new org.joml.Matrix4f();
    private static net.minecraft.world.phys.Vec3 camPos = net.minecraft.world.phys.Vec3.ZERO;
    private static boolean matrices;

    @SubscribeEvent
    public static void onRenderLevel(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        if (!ENABLED || event.getStage() != net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        VIEW.set(event.getPoseStack().last().pose());
        PROJ.set(event.getProjectionMatrix());
        camPos = event.getCamera().getPosition();
        matrices = true;
    }

    /**
     * Screen frames around every group (and the whole system) from the last world camera: a world-space box would be
     * clipped by the terrain far plane (~16 x render distance), the clouds are not.
     */
    private static void frames(net.minecraft.client.gui.GuiGraphics g, Minecraft mc, Baked b) {
        if (!matrices) return;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        List<double[]> boxes = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        for (var gi : b.groups()) {
            var d = gi.blocks();
            if (d.width() <= 0) continue; // faint remnant: nothing at density 0.5
            boxes.add(new double[]{ox + d.centerX() - .5 * d.width(), oy + d.minY(), oz + d.centerZ() - .5 * d.depth(), ox + d.centerX() + .5 * d.width(), oy + d.maxY(),
                    oz + d.centerZ() + .5 * d.depth()});
            labels.add(String.format(Locale.ROOT, "G%d %s", gi.index(), gi.role()));
            colours.add(switch (gi.role()) { case "developing" -> 0xFF66FF88; case "mature" -> 0xFFFFDD55; case "weakening" -> 0xFFFF8866; default -> 0xFF99AAFF; });
        }
        if (!b.groups().isEmpty()) { boxes.add(box()); labels.add("system"); colours.add(0xFFFFFFFF); }
        for (var cb : b.components()) {
            float[] x = cb.box();
            boxes.add(new double[]{ox + x[0], oy + x[1], oz + x[2], ox + x[3], oy + x[4], oz + x[5]});
            labels.add(cb.label());
            colours.add(cb.label().startsWith("lower-mass region") ? 0xFFFF55FF : 0xFF55E0FF);
        }
        for (var ci : b.connections()) {
            if (!b.components().isEmpty()) continue;
            double r = 60;
            boxes.add(new double[]{ox + ci.x() - r, oy + ci.y() - 30, oz + ci.z() - r, ox + ci.x() + r, oy + ci.y() + 30, oz + ci.z() + r});
            labels.add(String.format(Locale.ROOT, "C%d-%d %.2f", ci.a(), ci.b(), ci.strength()));
            colours.add(0xFF55E0FF);
        }
        for (int n = 0; n < boxes.size(); n++) {
            double[] bx = boxes.get(n);
            float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
            boolean behind = false;
            for (int c = 0; c < 8; c++) {
                var v = new org.joml.Vector4f((float) ((c & 1) == 0 ? bx[0] : bx[3]) - (float) camPos.x, (float) ((c & 2) == 0 ? bx[1] : bx[4]) - (float) camPos.y,
                        (float) ((c & 4) == 0 ? bx[2] : bx[5]) - (float) camPos.z, 1);
                VIEW.transform(v);
                PROJ.transform(v);
                if (v.w <= 1e-3f) { behind = true; break; }
                float sx = (v.x / v.w * .5f + .5f) * sw, sy = (1 - (v.y / v.w * .5f + .5f)) * sh;
                x0 = Math.min(x0, sx); y0 = Math.min(y0, sy); x1 = Math.max(x1, sx); y1 = Math.max(y1, sy);
            }
            if (behind || x1 < 0 || y1 < 0 || x0 > sw || y0 > sh) continue;
            int a = Math.round(Math.max(-2, x0)), bb = Math.round(Math.max(-2, y0)), c2 = Math.round(Math.min(sw + 2, x1)), d2 = Math.round(Math.min(sh + 2, y1));
            int col = colours.get(n);
            g.fill(a, bb, c2, bb + 1, col); g.fill(a, d2 - 1, c2, d2, col); g.fill(a, bb, a + 1, d2, col); g.fill(c2 - 1, bb, c2, d2, col);
            g.drawString(mc.font, labels.get(n), a + 2, Math.max(2, bb - 9), col, true);
        }
    }

    // ================================================================ demonstration driver (PA_CLOUD_PREVIEW_DEMO=1)

    /**
     * Unattended demonstration for validation: in the first world joined, shows each preset, takes screenshots from
     * the in-game inspection points into screenshots/cloudpreview/, clears the preview (normal clouds return), takes a
     * final screenshot and stops the client. Off unless PA_CLOUD_PREVIEW_DEMO=1.
     */
    static final class Demo {
        private static final boolean ON = ENABLED && "1".equals(System.getenv("PA_CLOUD_PREVIEW_DEMO"));
        /** T260.16 shot list (PA_CLOUD_PREVIEW_DEMO_SET=t16); otherwise the T260.15 comparison below, unchanged. */
        private static final boolean T16 = "t16".equals(System.getenv("PA_CLOUD_PREVIEW_DEMO_SET"));
        private static final List<String> VIEWS16 = List.of("ground300", "ground800", "eside", "close", "lunder", "lounder");
        private static final List<String> DIAG16 = List.of("eside", "lunder", "lounder");
        /** Lift of the system for the lifted underside views (blocks; identical for every candidate). */
        private static final double LIFT = 240;
        /** The seven T260.16 modes in brief order: neutral, fill, raking, density, gradient, optical depth, candidate. */
        private static final List<Integer> MODES = List.of(20, 26, 27, 21, 28, 30, 29);
        /** Underside views add the T260.5 attribution modes: direct only, ambient only, baked od, per-sample reference. */
        private static final List<Integer> MODES_UNDER = List.of(20, 26, 27, 21, 28, 30, 29, 22, 23, 24, 25);
        private static final List<String> TIMED16 = List.of("ground800", "eside", "lunder", "lounder");
        private static final List<String> VIEWS = List.of("ground300", "ground800", "three_quarter", "above", "close", "eside", "under", "ounder");
        /** Diagnostic (foundation geometry alone) views. */
        private static final List<String> DIAG_VIEWS = List.of("three_quarter", "eside", "under", "ounder");
        /** GPU timing views (the fixture pass timer, median / p95 over 120 presented frames). */
        private static final List<String> TIMED = List.of("ground800", "three_quarter", "eside", "ounder");
        /**
         * T260.12 comparison: per seed T260.12 (connections), T260.12 without connections and T260.11, at identical
         * cameras (view points from the T260.12 system box, close view on its strongest connection).
         */
        private record Shot(String name, Candidate candidate, long seed, String stage, boolean first) {}
        private static final List<Shot> SHOTS = List.of(
                // T260.15 comparison: per seed T260.15 first (its system box and focus freeze the cameras), then T260.14
                // and the T260.11 baseline at the identical cameras, then the two foundations alone (diagnostic views).
                new Shot("s2609101_t15", Candidate.T15, 2609101L, "mature", true), new Shot("s2609101_t14", Candidate.T14, 2609101L, "mature", false),
                new Shot("s2609101_t11", Candidate.T11, 2609101L, "mature", false),
                new Shot("s2609101_t15f", Candidate.T15F, 2609101L, "mature", false), new Shot("s2609101_t14f", Candidate.T14F, 2609101L, "mature", false),
                new Shot("s2609196_t15", Candidate.T15, 2609196L, "mature", true), new Shot("s2609196_t14", Candidate.T14, 2609196L, "mature", false),
                new Shot("s2609196_t11", Candidate.T11, 2609196L, "mature", false),
                new Shot("s2609196_t15f", Candidate.T15F, 2609196L, "mature", false), new Shot("s2609196_t14f", Candidate.T14F, 2609196L, "mature", false),
                new Shot("s2609102_t15", Candidate.T15, 2609102L, "developing", true), new Shot("s2609102_t14", Candidate.T14, 2609102L, "developing", false),
                new Shot("s2609102_t11", Candidate.T11, 2609102L, "developing", false));
        private static final List<Shot> SHOTS16 = List.of(
                // T260.16: per seed T260.15 first (its system box and focus freeze the cameras), then T260.14 at the
                // identical cameras, then both foundations alone; every view cycles the identical lighting modes.
                new Shot("s2609101_t15", Candidate.T15, 2609101L, "mature", true), new Shot("s2609101_t14", Candidate.T14, 2609101L, "mature", false),
                new Shot("s2609101_t15f", Candidate.T15F, 2609101L, "mature", false), new Shot("s2609101_t14f", Candidate.T14F, 2609101L, "mature", false),
                new Shot("s2609196_t15", Candidate.T15, 2609196L, "mature", true), new Shot("s2609196_t14", Candidate.T14, 2609196L, "mature", false),
                new Shot("s2609196_t15f", Candidate.T15F, 2609196L, "mature", false), new Shot("s2609196_t14f", Candidate.T14F, 2609196L, "mature", false),
                new Shot("s2609102_t15", Candidate.T15, 2609102L, "developing", true), new Shot("s2609102_t14", Candidate.T14, 2609102L, "developing", false));
        private static List<Shot> shots() { return T16 ? SHOTS16 : SHOTS; }
        private static int modeIndex, extra;
        private static final List<Float> GPU_PASS = new ArrayList<>();
        private static long passSerial;
        private static double anchorX0, anchorZ0;
        private static int step = -1, wait, preset, viewIndex, settled;
        private static long frameMark, timingSerial;
        private static final List<Float> GPU = new ArrayList<>();
        private static boolean diagnosticDone;

        static void tick(Minecraft mc) {
            if (!ON || mc.player == null || mc.level == null) return;
            if (Demo17.ON && step != -1) { Demo17.tick(mc); return; }
            mc.options.pauseOnLostFocus = false; // an unfocused window must not pause the integrated server mid-demo
            if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
            if (step == -1) {
                if (!VolumetricCloudRenderHook.isActive() || ++wait < 100) return;
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1600, 900);
                mc.player.connection.sendCommand("gamerule sendCommandFeedback false");
                mc.player.connection.sendCommand("gamemode spectator");
                mc.player.connection.sendCommand("time set 6000");
                mc.player.connection.sendCommand("gamerule doDaylightCycle false"); // identical light for every shot
                mc.player.connection.sendCommand("weather clear");
                mc.player.connection.sendCommand("tp @s ~ 120 ~ 0 0");
                anchorX0 = mc.player.getX();
                anchorZ0 = mc.player.getZ();
                step = 0; wait = 0; preset = 0;
                Demo17.anchorX0 = anchorX0; Demo17.anchorZ0 = anchorZ0;
                return;
            }
            wait++;
            long frames = VolumetricCloudRenderHook.presentedFrames();
            switch (step) {
                case 0 -> { // bake the next system
                    if (wait < 40) return;
                    if (preset >= shots().size()) { step = 5; wait = 0; hud = true; viewBox = null; closeTarget = null; closeDist = 0; clear(); return; }
                    var p = shots().get(preset);
                    if (p.first()) { viewBox = null; closeTarget = null; viewSide = 0; closeDist = 0; }
                    anchorDir = new double[]{0, 1, anchorX0, anchorZ0};
                    anchored = true;
                    centreOnAnchor = true;
                    stageName = p.stage();
                    submit(new Request(p.candidate(), p.seed(), CloudPreviewModels.stage(p.candidate(), p.stage()), scale, quality));
                    step = 1; wait = 0;
                }
                case 1 -> { // wait for the bake; freeze the cameras on the seed's first (T260.12) system
                    var p = shots().get(preset);
                    if (shown != null && shown.request().seed() == p.seed() && shown.request().candidate() == p.candidate() && ExperimentalStormMorphologyState.enabled()) {
                        if (p.first()) viewBox = box();
                        step = 2; wait = 0; viewIndex = 0;
                    } else if (status.startsWith("failed") || wait > 20 * 600) { ProjectAtmosphere.LOGGER.warn("CLOUD_PREVIEW_DEMO system failed: {}", status); preset++; step = 0; wait = 0; }
                }
                case 2 -> { // next view (beauty: no HUD, no frames)
                    var views = views(shots().get(preset));
                    if (viewIndex >= views.size()) {
                        var p = shots().get(preset);
                        if (p.candidate() == Candidate.T15 && !T16) { step = 7; wait = 0; return; }
                        preset++; step = 0; wait = 0; return;
                    }
                    hud = false; groupFrames = false;
                    if (T16) {
                        String vv = views.get(viewIndex);
                        relift(vv.equals("lunder") || vv.equals("lounder") ? LIFT : 0);
                        ExperimentalStormMorphologyState.lighting(20);
                    }
                    String err = teleport(views.get(viewIndex));
                    if (err != null) { ProjectAtmosphere.LOGGER.info("CLOUD_PREVIEW_DEMO skip view {}: {}", views.get(viewIndex), err); viewIndex++; return; }
                    step = 3; wait = 0;
                }
                case 3 -> { // arrival, terrain, then frames; GPU timing on the timed views
                    boolean arrived = pendingGround == null && lastTp != null
                            && Math.abs(mc.player.getX() - lastTp[0]) < 3 && Math.abs(mc.player.getY() - lastTp[1]) < 3 && Math.abs(mc.player.getZ() - lastTp[2]) < 3;
                    if (!arrived) { settled = 0; if (wait < 20 * 120) return; }
                    if (settled == 0) mc.gui.getChat().clearMessages(false);
                    if (++settled < 160) return;
                    if (T16) { settled = 0; frameMark = 0; modeIndex = 0; extra = 0; step = 9; wait = 0; return; }
                    if (frameMark == 0) { frameMark = frames; GPU.clear(); timingSerial = ExperimentalStormMorphologyState.pipelineSerial(); }
                    String v = views(shots().get(preset)).get(viewIndex);
                    if (TIMED.contains(v) && !CloudPreviewModels.isolated(shots().get(preset).candidate())) {
                        long serial = ExperimentalStormMorphologyState.pipelineSerial();
                        if (serial != timingSerial) {
                            timingSerial = serial;
                            float ms = ExperimentalStormMorphologyState.pipelineMs();
                            if (Float.isFinite(ms) && ms > 0) GPU.add(ms);
                        }
                        if (GPU.size() < 120 && frames - frameMark < 2000) return;
                    } else if (frames - frameMark < 30) return;
                    frameMark = 0;
                    settled = 0;
                    if (TIMED.contains(v) && !CloudPreviewModels.isolated(shots().get(preset).candidate())) logGpu(shots().get(preset).name() + "_" + v);
                    shoot(mc, shots().get(preset).name() + "_" + v);
                    viewIndex++; step = 2; wait = 0;
                }
                case 9 -> { // T260.16: every lighting mode at this camera; GPU p50 / p95 of both timers on the timed pairs
                    var p = shots().get(preset);
                    String v = views(p).get(viewIndex);
                    var ms = modes(v);
                    if (modeIndex < ms.size()) {
                        int m = ms.get(modeIndex);
                        if (frameMark == 0) {
                            ExperimentalStormMorphologyState.lighting(m);
                            VolumetricCloudRenderer.invalidateHistory();
                            frameMark = frames; GPU.clear(); GPU_PASS.clear();
                            timingSerial = ExperimentalStormMorphologyState.pipelineSerial();
                            passSerial = VolumetricCloudRenderer.lastGpuTimingSample();
                            return;
                        }
                        if (frames - frameMark < 20) return; // program switch, no history (HistoryValid 0)
                        if (timed16(p, v, m)) {
                            long serial = ExperimentalStormMorphologyState.pipelineSerial();
                            if (serial != timingSerial) {
                                timingSerial = serial;
                                float t = ExperimentalStormMorphologyState.pipelineMs();
                                if (Float.isFinite(t) && t > 0) GPU.add(t);
                            }
                            long ps = VolumetricCloudRenderer.lastGpuTimingSample();
                            if (ps != passSerial) {
                                passSerial = ps;
                                float t = VolumetricCloudRenderer.currentGpuQueryMilliseconds();
                                if (Float.isFinite(t) && t > 0) GPU_PASS.add(t);
                            }
                            if ((GPU.size() < 120 || GPU_PASS.size() < 120) && frames - frameMark < 1500) return;
                            logGpu16(p.name() + "_" + v + "_m" + m);
                        }
                        shoot(mc, p.name() + "_" + v + "_m" + m);
                        frameMark = 0;
                        modeIndex++;
                        return;
                    }
                    // checks on the full systems at the elevated side view: fast (interval skip) vs exact march image
                    // (offline diff of the two mode-20 frames), then the GPU kernel's density against the CPU's
                    if (v.equals("eside") && !CloudPreviewModels.isolated(p.candidate()) && extra < 2) {
                        if (extra == 0) {
                            if (frameMark == 0) { ExperimentalStormMorphologyState.lighting(20); ExperimentalStormMorphologyState.intervals(false); VolumetricCloudRenderer.invalidateHistory(); frameMark = frames; return; }
                            if (frames - frameMark < 20) return;
                            shoot(mc, p.name() + "_" + v + "_m20_exact");
                            ExperimentalStormMorphologyState.intervals(true);
                            frameMark = 0; extra = 1; return;
                        }
                        if (frameMark == 0) { ExperimentalStormMorphologyState.parity(true); frameMark = frames; return; }
                        if (frames - frameMark < 6) return;
                        try { densityParity(p.name()); } catch (RuntimeException e) { ProjectAtmosphere.LOGGER.warn("CLOUD_PREVIEW_PARITY name={} failed: {}", p.name(), e.toString()); }
                        ExperimentalStormMorphologyState.parity(false);
                        frameMark = 0; extra = 2; return;
                    }
                    ExperimentalStormMorphologyState.lighting(20);
                    frameMark = 0;
                    viewIndex++; step = 2; wait = 0;
                }
                case 7 -> { // one diagnostic capture: HUD and outlines on
                    if (wait == 1) { hud = true; groupFrames = true; teleport("eside"); }
                    boolean arrived = lastTp != null && Math.abs(mc.player.getX() - lastTp[0]) < 3 && Math.abs(mc.player.getZ() - lastTp[2]) < 3;
                    if (!arrived || wait < 160) return;
                    mc.gui.getChat().clearMessages(false);
                    if (wait < 200) return;
                    shoot(mc, shots().get(preset).name() + "_diagnostic_eside");
                    hud = false; groupFrames = false;
                    diagnosticDone = true;
                    preset++; step = 0; wait = 0;
                }
                case 5 -> { // preview cleared: normal clouds again, from the last viewpoint
                    if (wait < 120) return;
                    mc.gui.getChat().clearMessages(false);
                    shoot(mc, "zz_cleared_normal_clouds");
                    mc.player.connection.sendCommand("gamerule sendCommandFeedback true");
                    mc.player.connection.sendCommand("gamerule doDaylightCycle true");
                    step = 6; wait = 0;
                }
                case 6 -> {
                    if (wait < 40) return;
                    ProjectAtmosphere.LOGGER.info("CLOUD_PREVIEW_DEMO_DONE");
                    step = 8;
                    mc.stop();
                }
                default -> { }
            }
        }

        private static List<String> views(Shot p) {
            if (T16) return CloudPreviewModels.isolated(p.candidate()) ? DIAG16 : VIEWS16;
            return CloudPreviewModels.isolated(p.candidate()) ? DIAG_VIEWS : VIEWS;
        }

        private static List<Integer> modes(String view) { return view.equals("lunder") || view.equals("lounder") ? MODES_UNDER : MODES; }

        private static boolean timed16(Shot p, String view, int mode) {
            return !CloudPreviewModels.isolated(p.candidate()) && TIMED16.contains(view) && (mode == 20 || mode == 29);
        }

        /**
         * T260.16 CPU/GPU density parity on the shown system: the parity program writes the GPU kernel's density on a
         * lattice over the fixture bounds into the cloud target (row y % 64, slab y / 64); compared with the CPU density.
         */
        private static void densityParity(String name) {
            var target = VolumetricCloudRenderTargets.currentCloudTarget();
            var pixels = org.lwjgl.BufferUtils.createFloatBuffer(target.width * target.height * 4);
            com.mojang.blaze3d.systems.RenderSystem.bindTexture(target.getColorTextureId());
            org.lwjgl.opengl.GL11.glGetTexImage(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_FLOAT, pixels);
            var b = shown.program().bounds();
            int checked = 0, occupied = 0;
            float maxError = 0;
            for (int iy = 0; iy < target.height; iy += 3) for (int ix = 0; ix < target.width; ix += 5) {
                float x = b.minX() + (b.maxX() - b.minX()) * ((ix + .5f) / target.width);
                float y = b.minY() + (b.maxY() - b.minY()) * (((iy % 64) + .5f) / 64f);
                float z = b.minZ() + (b.maxZ() - b.minZ()) * (((iy / 64) + .5f) / (float) Math.ceil(target.height / 64.0));
                float cpu = shown.program().density(x, y, z, null), gpu = pixels.get((iy * target.width + ix) * 4);
                maxError = Math.max(maxError, Math.abs(cpu - gpu));
                if (cpu > .01f || gpu > .01f) occupied++;
                checked++;
            }
            ProjectAtmosphere.LOGGER.info("CLOUD_PREVIEW_PARITY name={} target={}x{} samples={} occupied={} maxAbsError={} tolerance=0.002 pass={}", name, target.width, target.height,
                    checked, occupied, maxError, maxError <= .002f && occupied > 0);
        }

        private static void logGpu16(String name) {
            var v = new ArrayList<>(GPU);
            var q = new ArrayList<>(GPU_PASS);
            v.sort(Float::compare);
            q.sort(Float::compare);
            ProjectAtmosphere.LOGGER.info("CLOUD_PREVIEW_GPU16 name={} cloudPass samples={} p50Ms={} p95Ms={} | completeRenderer samples={} p50Ms={} p95Ms={} maxMs={} window={}x{}", name,
                    q.size(), q.isEmpty() ? "-" : fmt(q.get(q.size() / 2)), q.isEmpty() ? "-" : fmt(q.get(Math.min(q.size() - 1, (int) Math.ceil(q.size() * .95) - 1))),
                    v.size(), v.isEmpty() ? "-" : fmt(v.get(v.size() / 2)), v.isEmpty() ? "-" : fmt(v.get(Math.min(v.size() - 1, (int) Math.ceil(v.size() * .95) - 1))),
                    v.isEmpty() ? "-" : fmt(v.get(v.size() - 1)), Minecraft.getInstance().getWindow().getWidth(), Minecraft.getInstance().getWindow().getHeight());
        }

        private static void logGpu(String name) {
            var v = new ArrayList<>(GPU);
            v.sort(Float::compare);
            if (v.isEmpty()) { ProjectAtmosphere.LOGGER.info("CLOUD_PREVIEW_GPU name={} samples=0", name); return; }
            ProjectAtmosphere.LOGGER.info("CLOUD_PREVIEW_GPU name={} samples={} medianMs={} p95Ms={} maxMs={} window={}x{}", name, v.size(), fmt(v.get(v.size() / 2)),
                    fmt(v.get(Math.min(v.size() - 1, (int) Math.ceil(v.size() * .95) - 1))), fmt(v.get(v.size() - 1)),
                    Minecraft.getInstance().getWindow().getWidth(), Minecraft.getInstance().getWindow().getHeight());
        }

        private static void shoot(Minecraft mc, String name) {
            new java.io.File(mc.gameDirectory, "screenshots/cloudpreview").mkdirs();
            Screenshot.grab(mc.gameDirectory, "cloudpreview/" + name + ".png", mc.getMainRenderTarget(), msg -> { });
            int tx = (int) Math.floor(mc.player.getX()), tz = (int) Math.floor(mc.player.getZ());
            String terrain = mc.level.getChunkSource().hasChunk(tx >> 4, tz >> 4) ? String.valueOf(mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, tx, tz)) : "unloaded";
            ProjectAtmosphere.LOGGER.info("CLOUD_PREVIEW_SHOT name={} player=({}, {}, {}) yaw={} pitch={} fov={} terrainLift={} eyeDensity={} systemLift={} terrainY={} lighting={} {}", name, fmt(mc.player.getX()),
                    fmt(mc.player.getY()), fmt(mc.player.getZ()), fmt(mc.player.getYRot()), fmt(mc.player.getXRot()), mc.options.fov().get(), fmt(lastLift),
                    fmt(density(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ())), fmt(lift), terrain, ExperimentalStormMorphologyState.lighting(), info());
        }
    }

    /**
     * T260.17 demonstration (PA_CLOUD_PREVIEW_DEMO_SET=t17): a flat list of actions - bake, re-light for a sun, go to a
     * view, capture a lighting mode (optionally timed), resolution override, fast/exact and parity checks.
     */
    static final class Demo17 {
        static final boolean D = "t17d".equals(System.getenv("PA_CLOUD_PREVIEW_DEMO_SET"));
        /** T260.18 geometry comparison (PA_CLOUD_PREVIEW_DEMO_SET=t18): lighting frozen at mode 20 (T260.5 neutral). */
        static final boolean T18C = "t18close".equals(System.getenv("PA_CLOUD_PREVIEW_DEMO_SET"));
        /** T260.18 second pass (PA_CLOUD_PREVIEW_DEMO_SET=t18n): T260.15 against the two geometry candidates D and E. */
        static final boolean T18N = "t18n".equals(System.getenv("PA_CLOUD_PREVIEW_DEMO_SET"));
        /** T260.18 third pass (t18p): T260.15 against D2 and F, with the foundation-scale edge-on profile. */
        static final boolean T18P = "t18p".equals(System.getenv("PA_CLOUD_PREVIEW_DEMO_SET"));
        /** T260.18 stage 2 (t18l): D2 geometry frozen; lighting 20 (current) against L-dir 35 and L-graz 40, suns 35 and 12. */
        static final boolean T18L = "t18l".equals(System.getenv("PA_CLOUD_PREVIEW_DEMO_SET"));
        /** T260.18 stage 2, second session (t18l2): mode 20 again (same-session control) and L-graz-c (41). */
        static final boolean T18L2 = "t18l2".equals(System.getenv("PA_CLOUD_PREVIEW_DEMO_SET"));
        /** T260.L-family (tlf): neutral mode 20 against the production lighting family (42) on frozen T260.10 / T260.15 / D2. */
        static final boolean LF = "tlf".equals(System.getenv("PA_CLOUD_PREVIEW_DEMO_SET"));
        /** T260.L-family supplement (tlf10): the T260.10 cluster at the generic three-quarter view ('cthree' needs a foundation). */
        static final boolean LF10 = "tlf10".equals(System.getenv("PA_CLOUD_PREVIEW_DEMO_SET"));
        /** Stabilisation S5 (s5): the preview conversion - legacy (0.25 / per-block extinction) vs S5, and S5 + S1. */
        static final boolean S5 = "s5".equals(System.getenv("PA_CLOUD_PREVIEW_DEMO_SET"));
        /** Stabilisation S7 (s7): T260.18 D2 against S7a (t19, base sags under the masses) at identical cameras. */
        static final boolean S7 = "s7".equals(System.getenv("PA_CLOUD_PREVIEW_DEMO_SET"));
        static final boolean T18 = "t18".equals(System.getenv("PA_CLOUD_PREVIEW_DEMO_SET")) || T18C || T18N || T18P || T18L || T18L2 || LF || LF10 || S5 || S7;
        static final boolean ON = Demo.ON && ("t17".equals(System.getenv("PA_CLOUD_PREVIEW_DEMO_SET")) || D || T18);
        record Act(String kind, Candidate cand, long seed, String stage, boolean first, String view, int mode, float a, float b, String name, boolean timed) {}
        static final List<Integer> CMP = D ? List.of(20, 33, 38) : List.of(20, 29, 31, 32, 33);
        static final List<Integer> FACTORS = D ? List.of(23, 39) : List.of(22, 23, 30, 34, 35, 28, 37, 36);
        static final List<String> FULL = List.of("ground300", "ground800", "eside", "close", "above", "lunder", "lounder");
        static final List<String> FOUND = List.of("eside", "lunder", "lounder");
        static final List<String> TIMED = List.of("ground800", "eside", "close", "lunder", "lounder");
        static final double LIFT = 240;
        /** T260.18: one lift for every view and candidate (deep hanging masses must clear the terrain in ground views too). */
        static final double LIFT18 = 320;
        static final List<Act> ACTS = S7 ? buildS7() : S5 ? buildS5() : LF10 ? buildLF10() : LF ? buildLF() : T18L2 ? build18l(new int[]{20, 41}) : T18L ? build18l(new int[]{20, 35, 40}) : T18P ? build18n(Candidate.T18F, Candidate.T18D2, Candidate.T18FF, Candidate.T18D2F) : T18N ? build18n(Candidate.T18E, Candidate.T18D, Candidate.T18EF, Candidate.T18DF) : T18C ? build18close() : T18 ? build18() : build();
        static double anchorX0, anchorZ0;
        static int idx, wait, settled, gen0;
        static long frameMark;
        static boolean started, done;
        static long pipeSerial, passSerial;
        static final List<Float> PIPE = new ArrayList<>(), PASS = new ArrayList<>();

        static Act bake(Candidate c, long seed, String stage, boolean first, float sun) { return new Act("bake", c, seed, stage, first, null, 0, sun, Float.NaN, null, false); }
        static Act sun(float elev, float spacing) { return new Act("sun", null, 0, null, false, null, 0, elev, spacing, null, false); }
        static Act view(String v) { return new Act("view", null, 0, null, false, v, 0, 0, 0, null, false); }
        static Act mode(int m, String name, boolean timed) { return new Act("mode", null, 0, null, false, null, m, 0, 0, name, timed); }
        static Act res(float scale) { return new Act("res", null, 0, null, false, null, 0, scale, 0, null, false); }
        static Act check(String kind, String name) { return new Act(kind, null, 0, null, false, null, 0, 0, 0, name, false); }

        /**
         * T260.18: per seed T260.18 A first (the deepest underside: the frozen cameras clear every candidate), then T260.14,
         * T260.15, T260.18 B, C, and the isolated foundations. Lighting mode 20 everywhere; the T260.16 raking mode 27 is
         * added at the underside views as an identical diagnostic for every candidate.
         */
        static List<Act> build18() {
            List<Act> l = new ArrayList<>();
            List<String> views = List.of("ground300", "ground800", "cthree", "eside", "above", "close", "lunder", "lounder", "uprofile");
            List<String> under = List.of("lunder", "lounder", "uprofile");
            List<String> timed = List.of("ground800", "cthree", "eside", "close", "lunder", "lounder");
            Object[][] seeds = {{2609101L, "mature"}, {2609196L, "mature"}, {2609102L, "developing"}};
            Candidate[][] sets = {{Candidate.T18A, Candidate.T14, Candidate.T15, Candidate.T18B, Candidate.T18C},
                    {Candidate.T18AF, Candidate.T14F, Candidate.T15F, Candidate.T18BF, Candidate.T18CF}};
            for (Object[] sd : seeds) {
                long seed = (Long) sd[0];
                String stage = (String) sd[1];
                boolean control = stage.equals("developing"); // no organised pair: every candidate is the T260.11 groups
                for (int k = 0; k < sets[0].length; k++) {
                    Candidate c = sets[0][k];
                    if (control && k > 1 && k < 4) continue; // t18a, t14 and t18c suffice to show the identical independent groups
                    l.add(bake(c, seed, stage, k == 0, 35));
                    for (String v : control ? List.of("ground800", "eside", "above") : views) {
                        l.add(view(v));
                        String base = String.format(Locale.ROOT, "s%d_%s_%s", seed, c.id, v);
                        l.add(mode(20, base + "_m20", !control && timed.contains(v)));
                        if (under.contains(v)) l.add(mode(27, base + "_m27", false));
                        if (v.equals("eside") && !control) { l.add(check("exact", base + "_m20_exact")); l.add(check("parity", "s" + seed + "_" + c.id)); }
                    }
                }
                if (control) continue;
                for (Candidate c : sets[1]) {
                    l.add(bake(c, seed, stage, false, 35));
                    for (String v : List.of("cthree", "eside", "lunder", "lounder", "uprofile")) {
                        l.add(view(v));
                        String base = String.format(Locale.ROOT, "s%d_%s_%s", seed, c.id, v);
                        l.add(mode(20, base + "_m20", false));
                        if (under.contains(v)) l.add(mode(27, base + "_m27", false));
                    }
                }
            }
            return l;
        }

        /**
         * T260.18 second pass: per seed E first (the lowest base: the frozen cameras clear every candidate), then T260.15
         * and D, then the isolated foundations. Lighting mode 20 everywhere; the raking mode 27 at the underside views is
         * an identical diagnostic for every candidate.
         */
        static List<Act> build18n(Candidate deep, Candidate other, Candidate deepF, Candidate otherF) {
            List<Act> l = new ArrayList<>();
            List<String> views = T18P ? List.of("lunder", "lounder", "fprofile", "ground800", "eside", "cthree", "uprofile") : List.of("lunder", "lounder", "ground800", "eside", "cthree", "uprofile");
            List<String> under = List.of("lunder", "lounder", "uprofile", "fprofile");
            List<String> timed = List.of("lunder", "lounder", "ground800", "eside", "cthree");
            Object[][] seeds = {{2609196L, "mature"}, {2609101L, "mature"}, {2609102L, "developing"}};
            Candidate[][] sets = {{deep, Candidate.T15, other}, {deepF, Candidate.T15F, otherF}};
            for (Object[] sd : seeds) {
                long seed = (Long) sd[0];
                String stage = (String) sd[1];
                boolean control = stage.equals("developing");
                for (int k = 0; k < sets[0].length; k++) {
                    Candidate c = sets[0][k];
                    l.add(bake(c, seed, stage, k == 0, 35));
                    for (String v : control ? List.of("ground800", "eside", "cthree", "lunder") : views) {
                        l.add(view(v));
                        String base = String.format(Locale.ROOT, "s%d_%s_%s", seed, c.id, v);
                        l.add(mode(20, base + "_m20", !control && timed.contains(v)));
                        if (under.contains(v) && !control) l.add(mode(27, base + "_m27", false));
                        if (v.equals("eside") && !control) { l.add(check("exact", base + "_m20_exact")); l.add(check("parity", "s" + seed + "_" + c.id)); }
                    }
                }
                if (control) continue;
                for (Candidate c : sets[1]) {
                    l.add(bake(c, seed, stage, false, 35));
                    for (String v : T18P ? List.of("lunder", "lounder", "fprofile", "eside") : List.of("lunder", "lounder", "eside", "uprofile")) {
                        l.add(view(v));
                        String base = String.format(Locale.ROOT, "s%d_%s_%s", seed, c.id, v);
                        l.add(mode(20, base + "_m20", false));
                        if (under.contains(v)) l.add(mode(27, base + "_m27", false));
                    }
                }
            }
            return l;
        }

        /**
         * T260.18 stage 2: the frozen D2 geometry under the current lighting (mode 20) and the two candidates, L-dir (35)
         * and L-graz (40), at identical cameras (frozen per seed at the D2 bake), suns 35 and 12 deg, every shot timed on
         * the mature seeds. Mode 27 (raking) at the underside views as the reference that the relief exists. Fast vs exact
         * march and a repeat (determinism) shot for every mode at the elevated side, CPU/GPU parity per seed.
         */
        static List<Act> build18l(int[] modes) {
            List<Act> l = new ArrayList<>();
            List<String> views = List.of("lunder", "lounder", "fprofile", "ground800", "eside");
            Object[][] seeds = {{2609196L, "mature"}, {2609101L, "mature"}, {2609102L, "developing"}};
            for (Object[] sd : seeds) {
                long seed = (Long) sd[0];
                String stage = (String) sd[1];
                boolean control = stage.equals("developing");
                l.add(bake(Candidate.T18D2, seed, stage, true, 35));
                for (int elev : new int[]{35, 12}) {
                    if (elev != 35) l.add(sun(elev, Float.NaN));
                    for (String v : control ? List.of("ground800", "eside", "lunder") : views) {
                        l.add(view(v));
                        String base = String.format(Locale.ROOT, "s%d_t18d2_sun%d_%s", seed, elev, v);
                        for (int m : modes) l.add(mode(m, base + "_m" + m, !control));
                        if (!control && elev == 35 && (v.equals("lunder") || v.equals("lounder"))) l.add(mode(27, base + "_m27", false));
                        if (v.equals("eside") && elev == 35) {
                            for (int m : modes) {
                                l.add(new Act("exact", null, 0, null, false, null, m, 0, 0, base + "_m" + m + "_exact", false));
                                l.add(mode(m, base + "_m" + m + "_repeat", false));
                            }
                            if (!control) l.add(check("parity", "s" + seed + "_t18d2"));
                        }
                    }
                }
            }
            return l;
        }

        /**
         * T260.L-family: per seed the D2 foundation first (its bake freezes the cameras that T260.15 then reuses), then
         * T260.15, then the T260.10 cluster on its own frozen box; at each of the five views the neutral lighting (20) and
         * the production family (42) back to back, both timed. The mature D2 also at sun 12. Fast vs exact and a repeat
         * shot per mode at the elevated side of D2 and T260.10, CPU/GPU parity per bake.
         */
        static List<Act> buildLF() {
            List<Act> l = new ArrayList<>();
            List<String> views = List.of("ground800", "cthree", "eside", "lunder", "close");
            int[] modes = {20, 42, 43};
            Object[][] seeds = {{2609101L, "mature"}, {2609196L, "mature"}, {2609102L, "developing"}};
            for (Object[] sd : seeds) {
                long seed = (Long) sd[0];
                String stage = (String) sd[1];
                Object[][] cands = {{Candidate.T18D2, true}, {Candidate.T15, false}, {Candidate.T10, true}};
                for (Object[] cf : cands) {
                    Candidate c = (Candidate) cf[0];
                    l.add(bake(c, seed, stage, (Boolean) cf[1], 35));
                    int[] suns = c == Candidate.T18D2 && stage.equals("mature") ? new int[]{35, 12} : new int[]{35};
                    for (int elev : suns) {
                        if (elev != 35) l.add(sun(elev, Float.NaN));
                        for (String v : views) {
                            l.add(view(v));
                            String base = String.format(Locale.ROOT, "s%d_%s_sun%d_%s", seed, c.id, elev, v);
                            for (int m : modes) l.add(mode(m, base + "_m" + m, elev == 35));
                            if (v.equals("eside") && elev == 35 && c != Candidate.T15) {
                                for (int m : modes) {
                                    l.add(new Act("exact", null, 0, null, false, null, m, 0, 0, base + "_m" + m + "_exact", false));
                                    l.add(mode(m, base + "_m" + m + "_repeat", false));
                                }
                            }
                            if (v.equals("eside") && elev == 35) l.add(check("parity", "s" + seed + "_" + c.id));
                        }
                    }
                }
            }
            return l;
        }

        /**
         * Stabilisation S5 (log E4): frozen T260.18 D2 seed 2609101 mature, sun 35, cameras frozen at the bake. Per
         * view: legacy conversion (res 0.25, extinction 0.055/block, legacy colour), S5 (production-equivalent
         * resolution, scale-consistent extinction), S5 + the S1 colour-managed composite; each in mode 20 and the
         * gradient normals (28).
         */
        static List<Act> buildS5() {
            List<Act> l = new ArrayList<>();
            String p = "s5_s2609101_t18d2_";
            l.add(bake(Candidate.T18D2, 2609101L, "mature", true, 35));
            for (String v : List.of("eside", "cthree", "lunder", "close", "ground800")) {
                l.add(view(v));
                l.add(res(0.25F)); l.add(ext(0.055F)); l.add(pipe(0));
                l.add(mode(20, p + v + "__legacy", true));
                l.add(res(Float.NaN)); l.add(ext(0.055F / 0.085F));
                l.add(mode(20, p + v + "__s5", true));
                l.add(mode(28, p + v + "__s5_m28", false));
                l.add(pipe(1));
                l.add(mode(20, p + v + "__s5_cm", false));
                l.add(pipe(0));
            }
            return l;
        }

        /**
         * Stabilisation S7a (log E7): D2 first (its bake freezes the cameras), then S7a at the same cameras; per seed the
         * underside, low underside, edge-on foundation profile and elevated side in mode 20, the gradient normals (28) and
         * raking light (27).
         */
        static List<Act> buildS7() {
            List<Act> l = new ArrayList<>();
            for (long seed : new long[]{2609101L, 2609196L}) {
                for (Candidate c : new Candidate[]{Candidate.T18D2, Candidate.T19}) {
                    l.add(bake(c, seed, "mature", c == Candidate.T18D2, 35));
                    for (String v : List.of("lunder", "lounder", "fprofile", "eside")) {
                        l.add(view(v));
                        String base = String.format(Locale.ROOT, "s7_s%d_%s_%s", seed, c.id, v);
                        l.add(mode(20, base + "__m20", false));
                        l.add(mode(28, base + "__m28", false));
                        if (!v.equals("eside")) l.add(mode(27, base + "__m27", false));
                    }
                }
            }
            return l;
        }

        static Act pipe(int m) { return new Act("pipe", null, 0, null, false, null, m, 0, 0, null, false); }

        /** Preview extinction override for the next shots (NaN = the previewExtinction() default); re-bakes the light. */
        static Act ext(float e) { return new Act("ext", null, 0, null, false, null, 0, e, 0, null, false); }

        static List<Act> buildLF10() {
            List<Act> l = new ArrayList<>();
            Object[][] seeds = {{2609101L, "mature"}, {2609196L, "mature"}, {2609102L, "developing"}};
            for (Object[] sd : seeds) {
                l.add(bake(Candidate.T10, (Long) sd[0], (String) sd[1], true, 35));
                l.add(view("three_quarter"));
                for (int m : new int[]{20, 42, 43}) l.add(mode(m, String.format(Locale.ROOT, "s%d_t10_sun35_three_quarter_m%d", (Long) sd[0], m), true));
            }
            return l;
        }

        /** T260.18 second session: the close view again (canopy clearance around the camera), timed, every candidate. */
        static List<Act> build18close() {
            List<Act> l = new ArrayList<>();
            for (long seed : new long[]{2609101L, 2609196L}) {
                Candidate[] cs = {Candidate.T18A, Candidate.T14, Candidate.T15, Candidate.T18B, Candidate.T18C};
                for (int k = 0; k < cs.length; k++) {
                    l.add(bake(cs[k], seed, "mature", k == 0, 35));
                    l.add(view("close"));
                    l.add(mode(20, String.format(Locale.ROOT, "s%d_%s_close2_m20", seed, cs[k].id), true));
                }
            }
            return l;
        }

        static List<Act> build() {
            List<Act> l = new ArrayList<>();
            Object[][] seeds = {{2609101L, "mature"}, {2609196L, "mature"}, {2609102L, "developing"}};
            for (Object[] sd : seeds) {
                long seed = (Long) sd[0];
                String stage = (String) sd[1];
                boolean mature = stage.equals("mature");
                l.add(bake(Candidate.T15, seed, stage, true, 35));
                for (int elev : new int[]{35, 12}) {
                    if (elev != 35) l.add(sun(elev, Float.NaN));
                    for (String v : FULL) {
                        l.add(view(v));
                        String base = String.format(Locale.ROOT, "s%d_t15_sun%d_%s", seed, elev, v);
                        for (int m : CMP) l.add(mode(m, base + "_m" + m, TIMED.contains(v)));
                        if (elev == 35 && (v.equals("lunder") || v.equals("lounder"))) {
                            for (int m : FACTORS) l.add(mode(m, base + "_m" + m, false));
                            if (!D) {
                                l.add(res(1f));
                                l.add(mode(20, base + "_m20_res100", false));
                                l.add(mode(33, base + "_m33_res100", false));
                                l.add(res(Float.NaN));
                            }
                        }
                        if (elev == 35 && v.equals("eside")) { l.add(check("exact", base + "_m20_exact")); l.add(check("parity", "s" + seed + "_t15")); }
                    }
                }
                if (!mature) continue;
                // factor 4: the light lattice at 10-block spacing (default 14-16)
                if (!D) l.add(sun(35, 10));
                for (String v : D ? List.<String>of() : List.of("lunder", "lounder")) {
                    l.add(view(v));
                    String base = String.format(Locale.ROOT, "s%d_t15_sun35_%s", seed, v);
                    l.add(mode(20, base + "_m20_sp10", false));
                    l.add(mode(33, base + "_m33_sp10", false));
                }
                // geometry discrimination: T260.14 at the identical underside cameras
                l.add(bake(Candidate.T14, seed, stage, false, 35));
                for (String v : List.of("lunder", "lounder")) {
                    l.add(view(v));
                    for (int m : CMP) l.add(mode(m, String.format(Locale.ROOT, "s%d_t14_sun35_%s_m%d", seed, v, m), false));
                }
                // foundation only (diagnostic)
                l.add(bake(Candidate.T15F, seed, stage, false, 35));
                for (int elev : new int[]{35, 12}) {
                    if (elev != 35) l.add(sun(elev, Float.NaN));
                    for (String v : FOUND) {
                        l.add(view(v));
                        for (int m : CMP) l.add(mode(m, String.format(Locale.ROOT, "s%d_t15f_sun%d_%s_m%d", seed, elev, v, m), false));
                    }
                }
            }
            return l;
        }

        static void tick(Minecraft mc) {
            mc.options.pauseOnLostFocus = false;
            if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
            if (done) return;
            wait++;
            long frames = VolumetricCloudRenderHook.presentedFrames();
            if (idx >= ACTS.size()) {
                if (wait == 1) { hud = true; viewBox = null; closeTarget = null; closeDist = 0; clear(); VolumetricCloudDebugConfig.setFixedResolutionScale(Float.NaN);
                    // Restore the demo overrides before the normal-cloud shot.
                    extinctionOverride = Float.NaN;
                    VolumetricCloudDebugConfig.setColorPipeline(VolumetricCloudDebugConfig.PRODUCTION_COLOR_PIPELINE); }
                if (wait < 120) return;
                mc.gui.getChat().clearMessages(false);
                Demo.shoot(mc, "zz_cleared_normal_clouds");
                mc.player.connection.sendCommand("gamerule sendCommandFeedback true");
                mc.player.connection.sendCommand("gamerule doDaylightCycle true");
                ProjectAtmosphere.LOGGER.info("CLOUD_PREVIEW_DEMO_DONE");
                done = true;
                mc.stop();
                return;
            }
            Act a = ACTS.get(idx);
            switch (a.kind()) {
                case "bake" -> {
                    if (!started) {
                        if (wait < 40) return;
                        if (a.first()) { viewBox = null; closeTarget = null; viewSide = 0; closeDist = 0; pinXZ = null; }
                        viewBoxLifted = T18;
                        sunElevation = a.a(); lightSpacingOverride = Float.NaN;
                        anchorDir = new double[]{0, 1, anchorX0, anchorZ0};
                        anchored = true;
                        centreOnAnchor = true;
                        stageName = a.stage();
                        submit(new Request(a.cand(), a.seed(), CloudPreviewModels.stage(a.cand(), a.stage()), scale, quality));
                        started = true; wait = 0;
                        return;
                    }
                    // The quality clause makes a same-system re-bake at another quality wait for its own bake (without it
                    // the root-cause audit's first session accepted the already-shown NORMAL bake as "HIGH").
                    if (shown != null && shown.request().seed() == a.seed() && shown.request().candidate() == a.cand() && shown.request().quality() == quality && ExperimentalStormMorphologyState.enabled()) {
                        if (T18) relift(LIFT18);
                        if (a.first()) { viewBox = box(); if (T18) pinXZ = new double[]{ox, oz}; }
                        next();
                    } else if (status.startsWith("failed") || wait > 20 * 600) {
                        ProjectAtmosphere.LOGGER.warn("CLOUD_PREVIEW_DEMO system failed: {}", status);
                        // skip every action up to the next bake
                        do idx++; while (idx < ACTS.size() && !ACTS.get(idx).kind().equals("bake"));
                        started = false; wait = 0;
                    }
                }
                case "sun" -> {
                    if (!started) { gen0 = relightGeneration; relight(a.a(), a.b()); started = true; wait = 0; return; }
                    if (relightGeneration != gen0 && wait > 20) next();
                    else if (wait > 20 * 300) { ProjectAtmosphere.LOGGER.warn("CLOUD_PREVIEW_DEMO relight timed out"); next(); }
                }
                case "view" -> {
                    if (!started) {
                        hud = false; groupFrames = false;
                        if (!T18) relift(a.view().equals("lunder") || a.view().equals("lounder") ? LIFT : 0);
                        ExperimentalStormMorphologyState.lighting(20);
                        String err = teleport(a.view());
                        if (err != null) {
                            ProjectAtmosphere.LOGGER.info("CLOUD_PREVIEW_DEMO skip view {}: {}", a.view(), err);
                            do idx++; while (idx < ACTS.size() && ACTS.get(idx).kind().equals("mode"));
                            wait = 0; return;
                        }
                        started = true; settled = 0; wait = 0;
                        return;
                    }
                    boolean arrived = pendingGround == null && lastTp != null
                            && Math.abs(mc.player.getX() - lastTp[0]) < 3 && Math.abs(mc.player.getY() - lastTp[1]) < 3 && Math.abs(mc.player.getZ() - lastTp[2]) < 3;
                    if (!arrived) { settled = 0; if (wait < 20 * 120) return; }
                    if (settled == 0) mc.gui.getChat().clearMessages(false);
                    if (++settled < 160) return;
                    mc.gui.getChat().clearMessages(false);
                    next();
                }
                case "res" -> {
                    if (!started) { VolumetricCloudDebugConfig.setFixedResolutionScale(a.a()); VolumetricCloudRenderer.invalidateHistory(); started = true; frameMark = frames; return; }
                    if (frames - frameMark >= 30) next();
                }
                case "mode" -> {
                    if (!started) {
                        ExperimentalStormMorphologyState.lighting(a.mode());
                        VolumetricCloudRenderer.invalidateHistory();
                        frameMark = frames; PIPE.clear(); PASS.clear();
                        pipeSerial = ExperimentalStormMorphologyState.pipelineSerial();
                        passSerial = VolumetricCloudRenderer.lastGpuTimingSample();
                        started = true;
                        return;
                    }
                    if (frames - frameMark < 24) return;
                    if (a.timed()) {
                        long sp = ExperimentalStormMorphologyState.pipelineSerial();
                        if (sp != pipeSerial) { pipeSerial = sp; float t = ExperimentalStormMorphologyState.pipelineMs(); if (Float.isFinite(t) && t > 0) PIPE.add(t); }
                        long ps = VolumetricCloudRenderer.lastGpuTimingSample();
                        if (ps != passSerial) { passSerial = ps; float t = VolumetricCloudRenderer.currentGpuQueryMilliseconds(); if (Float.isFinite(t) && t > 0) PASS.add(t); }
                        if ((PIPE.size() < 120 || PASS.size() < 120) && frames - frameMark < 4000) return;
                        Demo.GPU.clear(); Demo.GPU.addAll(PIPE);
                        Demo.GPU_PASS.clear(); Demo.GPU_PASS.addAll(PASS);
                        Demo.logGpu16(a.name());
                    }
                    if (frames - frameMark < 30) return;
                    mc.gui.getChat().clearMessages(false);
                    Demo.shoot(mc, a.name());
                    next();
                }
                case "exact" -> {
                    if (!started) { ExperimentalStormMorphologyState.lighting(a.mode() != 0 ? a.mode() : 20); ExperimentalStormMorphologyState.intervals(false); VolumetricCloudRenderer.invalidateHistory(); frameMark = frames; started = true; return; }
                    if (frames - frameMark < 30) return;
                    mc.gui.getChat().clearMessages(false);
                    Demo.shoot(mc, a.name());
                    ExperimentalStormMorphologyState.intervals(true);
                    next();
                }
                case "ext" -> {
                    if (!started) {
                        extinctionOverride = a.a();
                        ProjectAtmosphere.LOGGER.info("CLOUD_PREVIEW_EXTINCTION {}", previewExtinction());
                        gen0 = relightGeneration; relight(sunElevation, Float.NaN); started = true; wait = 0; return;
                    }
                    if (relightGeneration != gen0 && wait > 20) next();
                    else if (wait > 20 * 300) { ProjectAtmosphere.LOGGER.warn("CLOUD_PREVIEW_EXTINCTION relight timed out"); next(); }
                }
                case "pipe" -> { // S1 colour pipeline for the next shots (march + composite)
                    if (!started) { VolumetricCloudDebugConfig.setColorPipeline(a.mode()); started = true; frameMark = frames; return; }
                    if (frames - frameMark >= 10) next();
                }
                case "parity" -> {
                    if (!started) { ExperimentalStormMorphologyState.parity(true); frameMark = frames; started = true; return; }
                    if (frames - frameMark < 6) return;
                    try { Demo.densityParity(a.name()); } catch (RuntimeException e) { ProjectAtmosphere.LOGGER.warn("CLOUD_PREVIEW_PARITY name={} failed: {}", a.name(), e.toString()); }
                    ExperimentalStormMorphologyState.parity(false);
                    next();
                }
                default -> next();
            }
        }

        static void next() { idx++; started = false; wait = 0; frameMark = 0; }
    }
}
