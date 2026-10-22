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

    private static final SuggestionProvider<CommandSourceStack> CANDIDATES = (c, b) -> SharedSuggestionProvider.suggest(List.of("t10", "t9"), b);
    private static final SuggestionProvider<CommandSourceStack> STAGES = (c, b) -> SharedSuggestionProvider.suggest(List.of("initial", "developing", "growing", "mature", "46"), b);
    private static final SuggestionProvider<CommandSourceStack> PRESETS = (c, b) -> SharedSuggestionProvider.suggest(List.of("isolated", "multicell", "broad"), b);
    private static final SuggestionProvider<CommandSourceStack> VIEWS = (c, b) -> SharedSuggestionProvider.suggest(List.of("ground", "side", "three_quarter", "below", "above", "far"), b);
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
                                                .executes(c -> spawn(c, Candidate.parse(StringArgumentType.getString(c, "candidate")), LongArgumentType.getLong(c, "seed"),
                                                        CloudPreviewModels.stage(StringArgumentType.getString(c, "stage"))))))))
                .then(Commands.literal("preset")
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(PRESETS)
                                .executes(c -> {
                                    var p = CloudPreviewModels.preset(StringArgumentType.getString(c, "name"));
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
                .then(Commands.literal("hud").executes(c -> { hud = !hud; return say(c, "Preview HUD " + (hud ? "on" : "off")); }))
                .then(Commands.literal("info").executes(c -> say(c, info()))));
    }

    private static String help() {
        return "Experimental cloud preview (" + status + "). /cloudpreview preset <isolated|multicell|broad>, spawn <t10|t9> <seed> <stage>, view <ground|side|three_quarter|below|above|far>, "
                + "scale <f> [v], quality <fast|normal|high>, base <y|auto>, regenerate, clear, hud, info. Scale " + scaleText() + ", quality " + quality.name().toLowerCase(Locale.ROOT) + ".";
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
                NeutralLitProgram lit = new NeutralLitProgram(b.program(), b.volume(), VolumetricCloudRenderer.Tuning.CELLS.extinctionScale());
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
        double ahead = .5 * Math.max(dims.width(), dims.depth()) + Math.max(250, .5 * dims.height());
        double cx = anchorDir[2] + anchorDir[0] * ahead, cz = anchorDir[3] + anchorDir[1] * ahead;
        ox = cx - dims.centerX();
        oz = cz - dims.centerZ();
        oy = baseOverride != null ? baseOverride : mc.level.getSeaLevel() + b.baseAglMetres() * b.request().scale().vertical();
        ExperimentalStormMorphologyState.install(lit, (float) ox, (float) oy, (float) oz);
        ExperimentalStormMorphologyState.intervals(true);
        ExperimentalStormMorphologyState.legacy(false);
        ExperimentalStormMorphologyState.poison(false);
        ExperimentalStormMorphologyState.lighting(20); // T260.5 neutral volumetric (baked light)
        shown = b;
        bakeSeconds = bakeS;
        lightSeconds = lightS;
        status = "showing " + b.request().describe();
        ProjectAtmosphere.LOGGER.info("CLOUD_PREVIEW_SHOWN {} cells={} blocks W={} D={} H={} baseY={} topY={} modelKm W={} D={} H={} bakeS={} lightS={} bricks={} textureMB={} origin=({}, {}, {}) env=[{}]",
                b.request().describe(), b.cells(), fmt(dims.width()), fmt(dims.depth()), fmt(dims.height()), fmt(oy + dims.minY()), fmt(oy + dims.maxY()),
                fmt(b.model().width() / 1000), fmt(b.model().depth() / 1000), fmt(b.model().height() / 1000), fmt(bakeS), fmt(lightS), b.volume().liveBricks(),
                fmt(lit.textureWidth() * (double) lit.textureHeight() * 16 / 1048576), fmt(ox), fmt(oy), fmt(oz), b.environment());
        chat(info(), false);
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
        return String.format(Locale.ROOT, "%s, %d live cells: %.0f wide x %.0f deep x %.0f tall blocks (base y %.0f, top y %.0f); model %.1f x %.1f x %.1f km; bake %.1f s + light %.1f s, %d bricks.",
                b.request().describe(), b.cells(), d.width(), d.depth(), d.height(), oy + d.minY(), oy + d.maxY(), b.model().width() / 1000, b.model().depth() / 1000,
                b.model().height() / 1000, bakeSeconds, lightSeconds, b.volume().liveBricks());
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
        double[] b = box();
        double cx = .5 * (b[0] + b[3]), cz = .5 * (b[2] + b[5]), w = Math.max(b[3] - b[0], b[5] - b[2]), h = b[4] - b[1];
        double vf = Math.toRadians(mc.options.fov().get() * mc.player.getFieldOfViewModifier());
        double sx = -Math.sqrt(.5), sz = Math.sqrt(.5); // toward the sun (NeutralLightVolume.SUN azimuth)
        double ex, ey, ez, ty;
        switch (name) {
            case "ground" -> { double dist = Math.max(400, .6 * w + 200); ex = cx + sx * dist; ez = cz + sz * dist; ey = Double.NaN; ty = b[1] + .5 * h; }
            case "far" -> { double dist = 2.2 * Math.max(w, h) + 600; ex = cx + sx * dist; ez = cz + sz * dist; ey = Double.NaN; ty = b[1] + .5 * h; }
            case "side", "three_quarter" -> {
                double fit = .62 * Math.max(h, w) / Math.tan(.5 * vf) + .5 * w;
                double ax = name.equals("side") ? -1 : sx, az = name.equals("side") ? 0 : sz;
                ex = cx + ax * fit; ez = cz + az * fit; ey = b[1] + .5 * h; ty = ey;
            }
            case "below" -> { ex = cx + sx * .3 * w; ez = cz + sz * .3 * w; ey = Double.NaN; ty = b[1] + .6 * h; }
            case "above" -> { double dist = .9 * w + 200; ex = cx + sx * dist; ez = cz + sz * dist; ey = b[4] + .6 * w + 200; ty = b[1] + .4 * h; }
            default -> { return "Unknown view '" + name + "' (ground, side, three_quarter, below, above, far)"; }
        }
        double dx = cx - ex, dz = cz - ez, hd = Math.hypot(dx, dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        if (Double.isNaN(ey)) {
            // Stand on the terrain: go there high first, then drop once the chunk is loaded (two-step, see tick).
            pendingGround = new double[]{ex, ez, yaw, ty, hd};
            pendingTicks = 0;
            sendTp(ex, Math.max(mc.level.getSeaLevel() + 120, 200), ez, yaw, (float) -Math.toDegrees(Math.atan2(ty - 200, hd)));
            return null;
        }
        sendTp(ex, ey, ez, yaw, (float) -Math.toDegrees(Math.atan2(ty - ey, hd)));
        return null;
    }

    private static double[] lastTp;

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
                pendingGround = null;
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
            lines.add(b.request().describe());
            lines.add(String.format(Locale.ROOT, "W %.0f  D %.0f  H %.0f blocks  base y %.0f  top y %.0f", d.width(), d.depth(), d.height(), box[1], box[4]));
            lines.add(String.format(Locale.ROOT, "model %.1f x %.1f x %.1f km, %d cells", b.model().width() / 1000, b.model().depth() / 1000, b.model().height() / 1000, b.cells()));
            lines.add(String.format(Locale.ROOT, "you: %.0f blocks from centre, y %.0f", Math.hypot(mc.player.getX() - cx, mc.player.getZ() - cz), mc.player.getY()));
            lines.add(String.format(Locale.ROOT, "bake %.1f s + light %.1f s (worker thread)", bakeSeconds, lightSeconds));
        }
        if (status.startsWith("baking") || status.startsWith("failed")) lines.add("cloud preview: " + status);
        if (lines.isEmpty()) return;
        var g = event.getGuiGraphics();
        int top = 26, y = top + 2, w = 0; // below the top-centre performance line
        for (String s : lines) w = Math.max(w, mc.font.width(s));
        g.fill(2, top, 8 + w, top + 4 + lines.size() * 10, 0x90000000);
        for (String s : lines) { g.drawString(mc.font, s, 5, y, 0xFFE0F0FF, false); y += 10; }
    }

    // ================================================================ demonstration driver (PA_CLOUD_PREVIEW_DEMO=1)

    /**
     * Unattended demonstration for validation: in the first world joined, shows each preset, takes screenshots from
     * the in-game inspection points into screenshots/cloudpreview/, clears the preview (normal clouds return), takes a
     * final screenshot and stops the client. Off unless PA_CLOUD_PREVIEW_DEMO=1.
     */
    static final class Demo {
        private static final boolean ON = ENABLED && "1".equals(System.getenv("PA_CLOUD_PREVIEW_DEMO"));
        private static final List<String> VIEWS = List.of("ground", "three_quarter", "below", "far");
        private static int step = -1, wait, preset, viewIndex;
        private static long frameMark;
        private static int settled;

        static void tick(Minecraft mc) {
            if (!ON || mc.player == null || mc.level == null) return;
            mc.options.pauseOnLostFocus = false; // an unfocused window must not pause the integrated server mid-demo
            if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
            if (step == -1) {
                if (!VolumetricCloudRenderHook.isActive() || ++wait < 100) return;
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1600, 900);
                mc.player.connection.sendCommand("gamerule sendCommandFeedback false");
                mc.player.connection.sendCommand("gamemode spectator");
                mc.player.connection.sendCommand("time set 6000");
                mc.player.connection.sendCommand("weather clear");
                mc.player.connection.sendCommand("tp @s ~ 120 ~ 0 0");
                step = 0; wait = 0; preset = 0;
                return;
            }
            wait++;
            long frames = VolumetricCloudRenderHook.presentedFrames();
            switch (step) {
                case 0 -> { // spawn the next preset
                    if (wait < 40) return;
                    if (preset >= CloudPreviewModels.PRESETS.size()) { step = 5; wait = 0; clear(); return; }
                    var p = CloudPreviewModels.PRESETS.get(preset);
                    var look = new double[]{0, 1};
                    anchorDir = new double[]{look[0], look[1], mc.player.getX(), mc.player.getZ()};
                    anchored = true;
                    submit(new Request(p.candidate(), p.seed(), p.stage(), scale, quality));
                    step = 1; wait = 0;
                }
                case 1 -> { // wait for the bake
                    if (shown != null && shown.request().seed() == CloudPreviewModels.PRESETS.get(preset).seed() && ExperimentalStormMorphologyState.enabled()) { step = 2; wait = 0; viewIndex = 0; }
                    else if (status.startsWith("failed") || wait > 20 * 600) { ProjectAtmosphere.LOGGER.warn("CLOUD_PREVIEW_DEMO preset failed: {}", status); preset++; step = 0; wait = 0; }
                }
                case 2 -> { // go to the next view
                    if (viewIndex >= VIEWS.size()) { preset++; step = 0; wait = 0; return; }
                    teleport(VIEWS.get(viewIndex));
                    step = 3; wait = 0;
                }
                case 3 -> { // settle (ground views drop in two steps), then let enough frames present
                    // Server teleports lag while new terrain generates: wait for arrival, then for the terrain.
                    boolean arrived = pendingGround == null && lastTp != null
                            && Math.abs(mc.player.getX() - lastTp[0]) < 3 && Math.abs(mc.player.getY() - lastTp[1]) < 3 && Math.abs(mc.player.getZ() - lastTp[2]) < 3;
                    if (!arrived) { settled = 0; if (wait < 20 * 120) return; }
                    if (++settled < 160) return;
                    if (frameMark == 0) frameMark = frames;
                    if (frames - frameMark < 30) return;
                    frameMark = 0;
                    settled = 0;
                    mc.gui.getChat().clearMessages(false);
                    shoot(mc, CloudPreviewModels.PRESETS.get(preset).name() + "_" + VIEWS.get(viewIndex));
                    viewIndex++; step = 2; wait = 0;
                }
                case 5 -> { // preview cleared: normal clouds again, from the last viewpoint
                    if (wait < 120) return;
                    mc.gui.getChat().clearMessages(false);
                    shoot(mc, "zz_cleared_normal_clouds");
                    mc.player.connection.sendCommand("gamerule sendCommandFeedback true");
                    step = 6; wait = 0;
                }
                case 6 -> {
                    if (wait < 40) return;
                    ProjectAtmosphere.LOGGER.info("CLOUD_PREVIEW_DEMO_DONE");
                    step = 7;
                    mc.stop();
                }
                default -> { }
            }
        }

        private static void shoot(Minecraft mc, String name) {
            new java.io.File(mc.gameDirectory, "screenshots/cloudpreview").mkdirs();
            Screenshot.grab(mc.gameDirectory, "cloudpreview/" + name + ".png", mc.getMainRenderTarget(), msg -> { });
            ProjectAtmosphere.LOGGER.info("CLOUD_PREVIEW_SHOT name={} player=({}, {}, {}) yaw={} pitch={} fov={} {}", name, fmt(mc.player.getX()), fmt(mc.player.getY()),
                    fmt(mc.player.getZ()), fmt(mc.player.getYRot()), fmt(mc.player.getXRot()), mc.options.fov().get(), info());
        }
    }
}
