package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.type.CloudFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Orthographic CPU diagnostic only. No claim of native renderer acceptance or GPU cost. */
public final class StormMorphologyPreview {
    private static final String[] TYPES = {"cumulonimbus_calvus", "cumulonimbus_capillatus", "supercell"};
    private static final long[] SEEDS = {20261008L, 20268927L, 20276846L};
    private static final String[] VIEWS = {"side", "opposite_side", "front", "back", "above", "below", "low_angle", "far", "close", "updraft_oblique"};

    public static void main(String[] args) throws IOException {
        Path output = Path.of("specs/001-native-storm-rendering/validation/t255-storm-prototype/cpu-diagnostics-v" + StormMorphologyPrototype.VERSION);
        for (String type : TYPES) {
            for (long seed : SEEDS) {
                var program = StormMorphologyPrototype.compile(type,
                        CloudShapeProfile.defaultFor(type, CloudFamily.CUMULONIMBUS, null), seed);
                Path folder = output.resolve(type).resolve(Long.toString(seed));
                Files.createDirectories(folder);
                for (String view : VIEWS) {
                    ImageIO.write(render(program, view), "png", folder.resolve(view + ".png").toFile());
                }
                System.out.println("CPU diagnostic " + type + " version=" + StormMorphologyPrototype.VERSION + " seed=" + seed + " signature=" + Long.toUnsignedString(program.signature(), 16)
                        + " sectors=" + program.sectors().size() + " ribbons=" + program.filaments().size()
                        + " packedBytes=" + program.packed().length * 4);
            }
        }
        System.out.println("90 CPU diagnostic views written. NOT native/GPU acceptance.");
    }

    private static BufferedImage render(StormMorphologyPrototype.Program program, String view) {
        return render(StormGeometryProgram.legacy(program), view);
    }

    static BufferedImage render(StormGeometryProgram program, String view) {
        int width = program.growthDefined() ? 512 : 256, height = program.growthDefined() ? 320 : 160;
        double yaw = switch (view) {
            case "opposite_side" -> Math.PI;
            case "front" -> Math.PI * .5;
            case "back" -> -Math.PI * .5;
            case "low_angle", "updraft_oblique" -> Math.PI * .25;
            default -> 0;
        };
        double pitch = switch (view) {
            case "above" -> -.90;
            case "below" -> .90;
            case "low_angle" -> .24;
            case "updraft_oblique" -> -.24;
            default -> 0;
        };
        // Same scale and aim for all archetypes, even close/far arms.
        double span = view.equals("far") ? 6200 : view.equals("close") ? 3000 : 4200;
        double rx = Math.cos(yaw), rz = -Math.sin(yaw);
        double dx = Math.sin(yaw) * Math.cos(pitch), dy = Math.sin(pitch), dz = Math.cos(yaw) * Math.cos(pitch);
        double ux = -Math.sin(yaw) * Math.sin(pitch), uy = Math.cos(pitch), uz = -Math.cos(yaw) * Math.sin(pitch);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var b = program.bounds();
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                double horizontal = (px + .5 - width * .5) / width * span;
                double vertical = (height * .5 - py - .5) / width * span;
                double ox = 520 + rx * horizontal + ux * vertical - dx * 6000;
                double oy = 360 + uy * vertical - dy * 6000;
                double oz = rz * horizontal + uz * vertical - dz * 6000;
                double start = 0, end = 12000;
                double[] origin = {ox, oy, oz}, direction = {dx, dy, dz};
                float[] lo = {b.minX(), b.minY(), b.minZ()}, hi = {b.maxX(), b.maxY(), b.maxZ()};
                for (int axis = 0; axis < 3; axis++) {
                    if (Math.abs(direction[axis]) < 1e-8) {
                        if (origin[axis] < lo[axis] || origin[axis] > hi[axis]) end = -1;
                    } else {
                        double a = (lo[axis] - origin[axis]) / direction[axis], c = (hi[axis] - origin[axis]) / direction[axis];
                        start = Math.max(start, Math.min(a, c)); end = Math.min(end, Math.max(a, c));
                    }
                }
                double transmittance = 1;
                for (double t = start + 5; t < end && transmittance > .008; t += 10) {
                    float density = program.density((float) (ox + dx * t), (float) (oy + dy * t),
                            (float) (oz + dz * t), StormMorphologyPrototype.Scale.MESO);
                    transmittance *= Math.exp(-density * .07);
                }
                int r = (int) (159 * (1 - transmittance) + 18 * transmittance);
                int g = (int) (168 * (1 - transmittance) + 23 * transmittance);
                int blue = (int) (189 * (1 - transmittance) + 30 * transmittance);
                image.setRGB(px, py, (r << 16) | (g << 8) | blue);
            }
        }
        return image;
    }
}
