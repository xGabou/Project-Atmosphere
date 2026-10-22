package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.type.CloudFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;

/** T258 scratch probe: parity-sample occupancy as the native parity arm samples it. */
public final class StormAnatomyCProbe {
    public static void main(String[] args) {
        var c = StormAnatomyCandidateC.compile("supercell", CloudShapeProfile.defaultFor("supercell", CloudFamily.CUMULONIMBUS, null), 20261008L);
        var b = c.bounds();
        int W = 1200, H = 675, occ = 0, n = 0, gridOcc = 0;
        for (int iy = 0; iy < H; iy += 7) for (int ix = 0; ix < W; ix += 11) {
            float x = b.minX() + (b.maxX() - b.minX()) * ((ix + .5f) / W);
            float y = b.minY() + (b.maxY() - b.minY()) * (((iy % 64) + .5f) / 64f);
            float z = b.minZ() + (b.maxZ() - b.minZ()) * (((iy / 64) + .5f) / (float) Math.ceil(H / 64.0));
            n++;
            if (c.density(x, y, z, StormMorphologyPrototype.Scale.MESO) > .01f) occ++;
            if (c.gridOccupied(x, y, z)) gridOcc++;
        }
        System.out.println("samples=" + n + " cpuOccupied=" + occ + " gridOccupied=" + gridOcc + " tex=" + c.textureWidth() + "x" + c.textureHeight());
    }
}
