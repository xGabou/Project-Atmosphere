package net.Gabou.projectatmosphere.clouds.field;

import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Standalone CloudField preview. This intentionally avoids Minecraft startup
 * and renders the renderer-input contract into a static SVG/HTML artifact.
 */
public final class CloudFieldSandbox {
    private static final int WIDTH = 1100;
    private static final int HEIGHT = 720;
    private static final double SCALE = 1.15D;
    private static final double ORIGIN_X = WIDTH * 0.52D;
    private static final double ORIGIN_Y = HEIGHT * 0.52D;

    private CloudFieldSandbox() {
    }

    public static void main(String[] args) throws IOException {
        CloudFieldRendererInput input = sampleInput();
        Path output = Path.of("build", "cloud-field-sandbox", "cloud-field-debug.html");
        Files.createDirectories(output.getParent());
        Files.writeString(output, renderHtml(input), StandardCharsets.UTF_8);
        System.out.println("CloudField sandbox wrote " + output.toAbsolutePath().normalize());
        System.out.println("fields=" + input.fields().size() + " dynamicCloudlets=" + input.dynamicCloudletCount());
    }

    private static CloudFieldRendererInput sampleInput() {
        Vec3 camera = new Vec3(0.0D, 128.0D, 0.0D);
        long worldTime = 24000L;
        return new CloudFieldRendererInput(
                List.of(
                        snapshot(
                                "348c31a1-4ae7-4ab4-b30f-c7b5f1f05e01",
                                930511L,
                                new Vec3(-170.0D, 142.0D, -75.0D),
                                new Vec3(-184.0D, 142.0D, -85.0D),
                                155.0F,
                                108.0F,
                                182.0F,
                                0.78F,
                                0.66F,
                                0.92F,
                                new Vec3(0.75D, 0.0D, 0.22D),
                                CloudLodBand.DYNAMIC,
                                CloudFieldHydrationState.HYDRATED,
                                1.0F,
                                72
                        ),
                        snapshot(
                                "86f25a54-7d8a-4d57-b47d-f5470ee2b7ac",
                                441802L,
                                new Vec3(135.0D, 158.0D, 85.0D),
                                new Vec3(126.0D, 158.0D, 78.0D),
                                118.0F,
                                118.0F,
                                236.0F,
                                0.86F,
                                0.74F,
                                0.88F,
                                new Vec3(-0.35D, 0.0D, 0.55D),
                                CloudLodBand.DYNAMIC,
                                CloudFieldHydrationState.HYDRATING,
                                0.62F,
                                54
                        ),
                        snapshot(
                                "127ee2dc-b71f-4270-87d1-49346a7397e3",
                                733921L,
                                new Vec3(30.0D, 132.0D, -205.0D),
                                new Vec3(24.0D, 132.0D, -198.0D),
                                210.0F,
                                96.0F,
                                142.0F,
                                0.48F,
                                0.57F,
                                0.76F,
                                new Vec3(0.15D, 0.0D, -0.42D),
                                CloudLodBand.FAR_PROCEDURAL,
                                CloudFieldHydrationState.DEHYDRATING,
                                0.34F,
                                128
                        )
                ),
                worldTime,
                0.5F,
                camera
        );
    }

    private static CloudFieldSnapshot snapshot(
            String id,
            long seed,
            Vec3 center,
            Vec3 previousCenter,
            float radius,
            float baseY,
            float topY,
            float density,
            float coverage,
            float growth,
            Vec3 wind,
            CloudLodBand lodBand,
            CloudFieldHydrationState hydrationState,
            float hydrationProgress,
            int cloudlets
    ) {
        return new CloudFieldSnapshot(
                UUID.fromString(id),
                seed,
                "minecraft:overworld",
                center,
                previousCenter,
                radius,
                baseY,
                topY,
                density,
                coverage,
                growth,
                0.0F,
                0.7F,
                wind,
                Math.min(1.0F, (topY - baseY) / 160.0F),
                density * coverage,
                lodBand,
                CloudLodBand.TRANSITION,
                hydrationState,
                hydrationProgress,
                cloudlets,
                Math.round(cloudlets * hydrationProgress),
                1400L,
                12000L,
                24000L,
                0.5F,
                Vec3.ZERO
        );
    }

    private static String renderHtml(CloudFieldRendererInput input) {
        StringBuilder svg = new StringBuilder();
        svg.append("<svg viewBox=\"0 0 ").append(WIDTH).append(' ').append(HEIGHT).append("\" role=\"img\">");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#101419\"/>");
        svg.append("<g stroke=\"#26313c\" stroke-width=\"1\">");
        for (int x = -500; x <= 500; x += 50) {
            svg.append(line(new Vec3(x, 0.0D, -320.0D), new Vec3(x, 0.0D, 320.0D), "#26313c", 1.0D));
        }
        for (int z = -300; z <= 300; z += 50) {
            svg.append(line(new Vec3(-520.0D, 0.0D, z), new Vec3(520.0D, 0.0D, z), "#26313c", 1.0D));
        }
        svg.append("</g>");

        for (CloudFieldSnapshot snapshot : input.fields()) {
            renderField(svg, snapshot);
        }

        svg.append("</svg>");
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>Project Atmosphere CloudField Sandbox</title>
                  <style>
                    body { margin: 0; background: #101419; color: #d9e2ea; font: 14px/1.45 system-ui, sans-serif; }
                    main { display: grid; grid-template-columns: minmax(0, 1fr) 320px; min-height: 100vh; }
                    svg { width: 100%; height: 100vh; display: block; }
                    aside { padding: 18px; border-left: 1px solid #26313c; background: #151b22; }
                    h1 { margin: 0 0 12px; font-size: 18px; }
                    code { color: #95d5ff; }
                    .metric { margin: 10px 0; color: #9fb0bf; }
                  </style>
                </head>
                <body>
                <main>
                """ + svg + """
                <aside>
                  <h1>CloudField Sandbox</h1>
                  <div class="metric">Standalone renderer-contract preview. No Minecraft client is launched.</div>
                  <div class="metric">Fields: <code>""" + input.fields().size() + """
                  </code></div>
                  <div class="metric">Dynamic cloudlets: <code>""" + input.dynamicCloudletCount() + """
                  </code></div>
                  <div class="metric">Rings = field radius, dots = deterministic cloudlets, line = wind/current motion cue.</div>
                </aside>
                </main>
                </body>
                </html>
                """;
    }

    private static void renderField(StringBuilder svg, CloudFieldSnapshot snapshot) {
        String color = color(snapshot);
        double cx = sx(snapshot.center().x());
        double cy = sy(snapshot.center().z());
        double r = snapshot.radius() * SCALE;
        svg.append("<circle cx=\"").append(format(cx)).append("\" cy=\"").append(format(cy))
                .append("\" r=\"").append(format(r)).append("\" fill=\"").append(color)
                .append("\" fill-opacity=\"0.10\" stroke=\"").append(color)
                .append("\" stroke-width=\"2\"/>");
        svg.append(line(snapshot.previousCenter(), snapshot.center(), "#ffcc66", 2.0D));
        Vec3 windEnd = snapshot.center().add(snapshot.windVector().normalize().scale(Math.min(64.0D, snapshot.radius() * 0.35D)));
        svg.append(line(snapshot.center(), windEnd, "#f3f6f8", 2.0D));
        svg.append("<circle cx=\"").append(format(cx)).append("\" cy=\"").append(format(cy))
                .append("\" r=\"5\" fill=\"#f3f6f8\"/>");

        int count = Math.min(snapshot.dynamicCloudletCount(), 96);
        for (int i = 0; i < count; i++) {
            CloudletLayout.Cloudlet cloudlet = CloudletLayout.generate(snapshot, CloudletId.of(i));
            Vec3 center = cloudlet.worldCenter(snapshot);
            double size = Math.max(2.0D, Math.min(12.0D, cloudlet.horizontalRadius() * 0.08D * SCALE));
            svg.append("<circle cx=\"").append(format(sx(center.x()))).append("\" cy=\"").append(format(sy(center.z())))
                    .append("\" r=\"").append(format(size)).append("\" fill=\"").append(color)
                    .append("\" fill-opacity=\"").append(format(0.28D + snapshot.hydrationProgress() * 0.46D)).append("\"/>");
        }
    }

    private static String line(Vec3 start, Vec3 end, String color, double width) {
        return "<line x1=\"" + format(sx(start.x())) + "\" y1=\"" + format(sy(start.z()))
                + "\" x2=\"" + format(sx(end.x())) + "\" y2=\"" + format(sy(end.z()))
                + "\" stroke=\"" + color + "\" stroke-width=\"" + format(width) + "\" stroke-linecap=\"round\"/>";
    }

    private static String color(CloudFieldSnapshot snapshot) {
        return switch (snapshot.lodBand()) {
            case DYNAMIC -> "#5eead4";
            case TRANSITION -> "#93c5fd";
            case FAR_PROCEDURAL -> "#fbbf24";
            case HAZE -> "#f87171";
        };
    }

    private static double sx(double x) {
        return ORIGIN_X + x * SCALE;
    }

    private static double sy(double z) {
        return ORIGIN_Y + z * SCALE;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
