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

    private static final SuggestionProvider<CommandSourceStack> CANDIDATES = (c, b) -> SharedSuggestionProvider.suggest(List.of("t13a", "t13b", "t13c", "t12", "t12off", "t11", "t10", "t9"), b);
    private static final SuggestionProvider<CommandSourceStack> STAGES = (c, b) -> SharedSuggestionProvider.suggest(List.of("initial", "developing", "growing", "mature", "late"), b);
    private static final SuggestionProvider<CommandSourceStack> PRESETS = (c, b) -> SharedSuggestionProvider.suggest(List.of("isolated", "multicell", "broad"), b);
    private static final SuggestionProvider<CommandSourceStack> VIEWS = (c, b) -> SharedSuggestionProvider.suggest(List.of("ground300", "ground800", "ground1500", "ground", "side",
            "three_quarter", "below", "above", "far", "close", "cground", "cthree", "cabove"), b);
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
                .then(Commands.literal("hud").executes(c -> { hud = !hud; return say(c, "Preview HUD " + (hud ? "on" : "off")); }))
                .then(Commands.literal("groups").executes(c -> { groupFrames = !groupFrames; return say(c, "Group boundaries " + (groupFrames ? "on" : "off")); }))
                .then(Commands.literal("info").executes(c -> say(c, info()))));
    }

    private static String help() {
        return "Experimental cloud preview (" + status + "). /cloudpreview preset <isolated|multicell|broad>, spawn <t13a|t13b|t13c|t12|t12off|t11|t10|t9> <seed> <stage>, "
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
                NeutralLitProgram lit = new NeutralLitProgram(b.program(), b.volume(), VolumetricCloudRenderer.Tuning.CELLS.extinctionScale(),
                        CloudPreviewModels.lightSpacing(b.volume()));
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
        for (var gi : b.groups())
            ProjectAtmosphere.LOGGER.info("CLOUD_PREVIEW_GROUP G{} role={} cells={} localT={} W={} D={} baseY={} topY={} centre=({}, {}) cached={}", gi.index(), gi.role(), gi.cells(),
                    fmt(gi.localTime()), fmt(gi.blocks().width()), fmt(gi.blocks().depth()), fmt(oy + gi.blocks().minY()), fmt(oy + gi.blocks().maxY()),
                    fmt(ox + gi.blocks().centerX()), fmt(oz + gi.blocks().centerZ()), gi.cached());
        if (!CloudPreviewModels.lastSystemLog.isEmpty() && b.request().candidate() == Candidate.T11) ProjectAtmosphere.LOGGER.info("{}", CloudPreviewModels.lastSystemLog);
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
        return String.format(Locale.ROOT, "%s, %d live cells%s: %.0f wide x %.0f deep x %.0f tall blocks (base y %.0f, top y %.0f); model %.1f x %.1f x %.1f km; bake %.1f s + light %.1f s, %d bricks.",
                b.request().describe(), b.cells(), b.groups().isEmpty() ? "" : " in " + b.groups().size() + " groups", d.width(), d.depth(), d.height(), oy + d.minY(), oy + d.maxY(),
                b.model().width() / 1000, b.model().depth() / 1000, b.model().height() / 1000, bakeSeconds, lightSeconds, b.volume().liveBricks());
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
        if (name.equals("close")) {
            // T260.12: on the ground beside the strongest live connection, across its pair axis (sun side), looking at it.
            double[] c = closeTarget != null ? closeTarget : strongestConnection();
            if (c == null) return "No live connection to look at";
            closeTarget = c;
            double px = -c[4], pz = c[3];
            if (px * sx + pz * sz < 0) { px = -px; pz = -pz; }
            // Far enough out that no cloud hangs over the camera (the column above it is clear).
            double dist = 320;
            double[] bb = box();
            while (dist < 2000) {
                boolean clear = true;
                for (double yy = bb[1]; yy <= bb[4] && clear; yy += 16) clear = density(c[0] + px * dist, yy, c[2] + pz * dist) == 0;
                if (clear) break;
                dist += 80;
            }
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
            case "below" -> { ex = cx + sx * .3 * w; ez = cz + sz * .3 * w; ey = Double.NaN; ty = b[1] + .6 * h; }
            case "above" -> { double dist = .6 * w + 150; ex = cx + sx * dist; ez = cz + sz * dist; ey = b[4] + .45 * w + 150; ty = b[1] + .4 * h; }
            default -> { return "Unknown view '" + name + "' (ground, side, three_quarter, below, above, far)"; }
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
        private static final List<String> VIEWS = List.of("cground", "cthree", "cabove", "close");
        /** GPU timing views (the fixture pass timer, median / p95 over 120 presented frames). */
        private static final List<String> TIMED = List.of("cground", "cthree");
        /**
         * T260.12 comparison: per seed T260.12 (connections), T260.12 without connections and T260.11, at identical
         * cameras (view points from the T260.12 system box, close view on its strongest connection).
         */
        private record Shot(String name, Candidate candidate, long seed, String stage, boolean first) {}
        private static final List<Shot> SHOTS = List.of(
                new Shot("s2609196_t13a", Candidate.T13A, 2609196L, "mature", true), new Shot("s2609196_t13b", Candidate.T13B, 2609196L, "mature", false),
                new Shot("s2609196_t13c", Candidate.T13C, 2609196L, "mature", false), new Shot("s2609196_t12", Candidate.T12, 2609196L, "mature", false),
                new Shot("s2609196_t11", Candidate.T11, 2609196L, "mature", false));
        private static double anchorX0, anchorZ0;
        private static int step = -1, wait, preset, viewIndex, settled;
        private static long frameMark, timingSerial;
        private static final List<Float> GPU = new ArrayList<>();
        private static boolean diagnosticDone;

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
                anchorX0 = mc.player.getX();
                anchorZ0 = mc.player.getZ();
                step = 0; wait = 0; preset = 0;
                return;
            }
            wait++;
            long frames = VolumetricCloudRenderHook.presentedFrames();
            switch (step) {
                case 0 -> { // bake the next system
                    if (wait < 40) return;
                    if (preset >= SHOTS.size()) { step = 5; wait = 0; hud = true; viewBox = null; closeTarget = null; clear(); return; }
                    var p = SHOTS.get(preset);
                    if (p.first()) { viewBox = null; closeTarget = null; viewSide = 0; }
                    anchorDir = new double[]{0, 1, anchorX0, anchorZ0};
                    anchored = true;
                    centreOnAnchor = true;
                    stageName = p.stage();
                    submit(new Request(p.candidate(), p.seed(), CloudPreviewModels.stage(p.candidate(), p.stage()), scale, quality));
                    step = 1; wait = 0;
                }
                case 1 -> { // wait for the bake; freeze the cameras on the seed's first (T260.12) system
                    var p = SHOTS.get(preset);
                    if (shown != null && shown.request().seed() == p.seed() && shown.request().candidate() == p.candidate() && ExperimentalStormMorphologyState.enabled()) {
                        if (p.first()) viewBox = box();
                        step = 2; wait = 0; viewIndex = 0;
                    } else if (status.startsWith("failed") || wait > 20 * 600) { ProjectAtmosphere.LOGGER.warn("CLOUD_PREVIEW_DEMO system failed: {}", status); preset++; step = 0; wait = 0; }
                }
                case 2 -> { // next view (beauty: no HUD, no frames)
                    if (viewIndex >= VIEWS.size()) {
                        var p = SHOTS.get(preset);
                        if (p.candidate() == Candidate.T13A || p.candidate() == Candidate.T13B || p.candidate() == Candidate.T13C) { step = 7; wait = 0; return; }
                        preset++; step = 0; wait = 0; return;
                    }
                    hud = false; groupFrames = false;
                    String err = teleport(VIEWS.get(viewIndex));
                    if (err != null) { ProjectAtmosphere.LOGGER.info("CLOUD_PREVIEW_DEMO skip view {}: {}", VIEWS.get(viewIndex), err); viewIndex++; return; }
                    step = 3; wait = 0;
                }
                case 3 -> { // arrival, terrain, then frames; GPU timing on the timed views
                    boolean arrived = pendingGround == null && lastTp != null
                            && Math.abs(mc.player.getX() - lastTp[0]) < 3 && Math.abs(mc.player.getY() - lastTp[1]) < 3 && Math.abs(mc.player.getZ() - lastTp[2]) < 3;
                    if (!arrived) { settled = 0; if (wait < 20 * 120) return; }
                    if (settled == 0) mc.gui.getChat().clearMessages(false);
                    if (++settled < 160) return;
                    if (frameMark == 0) { frameMark = frames; GPU.clear(); timingSerial = ExperimentalStormMorphologyState.pipelineSerial(); }
                    String v = VIEWS.get(viewIndex);
                    if (TIMED.contains(v)) {
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
                    if (TIMED.contains(v)) logGpu(SHOTS.get(preset).name() + "_" + v);
                    shoot(mc, SHOTS.get(preset).name() + "_" + v);
                    viewIndex++; step = 2; wait = 0;
                }
                case 7 -> { // one diagnostic capture: HUD and outlines on
                    if (wait == 1) { hud = true; groupFrames = true; teleport("cthree"); }
                    boolean arrived = lastTp != null && Math.abs(mc.player.getX() - lastTp[0]) < 3 && Math.abs(mc.player.getZ() - lastTp[2]) < 3;
                    if (!arrived || wait < 160) return;
                    mc.gui.getChat().clearMessages(false);
                    if (wait < 200) return;
                    shoot(mc, SHOTS.get(preset).name() + "_diagnostic_cthree");
                    hud = false; groupFrames = false;
                    diagnosticDone = true;
                    preset++; step = 0; wait = 0;
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
                    step = 8;
                    mc.stop();
                }
                default -> { }
            }
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
            ProjectAtmosphere.LOGGER.info("CLOUD_PREVIEW_SHOT name={} player=({}, {}, {}) yaw={} pitch={} fov={} terrainLift={} eyeDensity={} {}", name, fmt(mc.player.getX()),
                    fmt(mc.player.getY()), fmt(mc.player.getZ()), fmt(mc.player.getYRot()), fmt(mc.player.getXRot()), mc.options.fov().get(), fmt(lastLift),
                    fmt(density(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ())), info());
        }
    }
}
