package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

/**
 * T197. Everything the shared storm field derives from its horizontal
 * resolution, in one place, so the atlas the renderer allocates, the node
 * count the oracle expects, the bytes the report quotes and the half-cell the
 * shader dilates by cannot disagree.
 *
 * <p>The field covers {@link CloudWeatherMapRenderer#WEATHER_EXTENT} on both
 * horizontal axes at every resolution - resolution changes the node spacing,
 * never the coverage - and {@link #Y} slices over the slab. The atlas keeps
 * its 8x4 tile layout because Y is fixed; a tile is one slice, {@code xz}
 * nodes square.
 *
 * <p>Deliberately Minecraft-free so the sandbox can check the derivation
 * headless.
 */
public final class StormFieldGeometry {
    /** T196's grid, and the value FINAL bakes. */
    public static final int DEFAULT_XZ = 256;
    /**
     * T199. The consumers production reads, as {@code PaStormFieldEnabled}
     * bits: 1 the light cone, 2 the empty-span probe, 8 the T198 exact-noise
     * tap (the cone interpolates the node's threshold and height and
     * evaluates the base noise, the detail and the erosion itself). FINAL
     * bakes this value, the monolith uploads it unless a campaign overrides
     * it, and every program specialized from FINAL inherits it. Bit 4, the
     * T196 hybrid, is dead at this value.
     */
    public static final int PRODUCTION_CONSUMER_BITS = 1 | 2 | 8;
    /** Vertical slices. T197 varies XZ only. */
    public static final int Y = 32;
    public static final int TILES_X = 8;
    public static final int TILES_Y = 4;
    /**
     * RGBA16F since T198: threshold, probe floor, height and the T196
     * density, four half floats a node (T196 and T197 measured RG16F, 4).
     */
    public static final int BYTES_PER_NODE = 8;
    public static final String FORMAT = "RGBA16F";
    /** The candidate-tile edge the node walk reads, in blocks. */
    public static final float CANDIDATE_TILE_BLOCKS =
            CloudWeatherMapRenderer.WEATHER_EXTENT / PuffLobeSpatialIndex.GRID_SIZE;

    private StormFieldGeometry() {
    }

    /** True for a resolution the atlas and the shader can carry. */
    public static boolean supported(int xz) {
        return xz >= 16 && xz <= 1024;
    }

    public static float voxelXZ(int xz) {
        return CloudWeatherMapRenderer.WEATHER_EXTENT / xz;
    }

    public static float halfCellXZ(int xz) {
        return 0.5F * voxelXZ(xz);
    }

    public static long nodes(int xz) {
        return (long) xz * xz * Y;
    }

    public static int atlasWidth(int xz) {
        return xz * TILES_X;
    }

    public static int atlasHeight(int xz) {
        return xz * TILES_Y;
    }

    public static long bytes(int xz) {
        return nodes(xz) * BYTES_PER_NODE;
    }

    /**
     * Whether every node's dual cell lies inside one 16-block candidate tile.
     * T196's soundness argument reads the tile the node sits in and needs
     * every point of the cell to read that same tile; that holds when the
     * tile edge is an integer multiple of the cell edge (256 and 512) and
     * fails at 384, whose 10.67-block cells straddle tile boundaries, where
     * the oracle - not the argument - has to decide.
     */
    public static boolean nestsInCandidateTiles(int xz) {
        double cellsPerTile = CANDIDATE_TILE_BLOCKS / voxelXZ(xz);
        return Math.abs(cellsPerTile - Math.rint(cellsPerTile)) < 1.0e-6D;
    }

    public static String describe(int xz) {
        return String.format(java.util.Locale.ROOT,
                "xz=%d y=%d nodes=%d voxelXZ=%.4f halfCellXZ=%.4f atlas=%dx%d bytes=%d"
                        + " megabytes=%.2f nestsInCandidateTiles=%s",
                xz, Y, nodes(xz), voxelXZ(xz), halfCellXZ(xz), atlasWidth(xz),
                atlasHeight(xz), bytes(xz), bytes(xz) / (1024.0D * 1024.0D),
                nestsInCandidateTiles(xz));
    }
}
