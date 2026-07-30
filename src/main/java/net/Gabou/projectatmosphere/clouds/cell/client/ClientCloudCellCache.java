package net.Gabou.projectatmosphere.clouds.cell.client;

import net.Gabou.projectatmosphere.clouds.cell.CloudCell;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Client cache for synced cloud cells. Presents a smoothly interpolated,
 * slightly delayed view of the server cell simulation so cells move
 * continuously between one-second sync packets instead of teleporting.
 */
public final class ClientCloudCellCache {
    private static final double PRESENTATION_DELAY_TICKS = 20.0D;
    private static final double EXTRAPOLATION_LIMIT_TICKS = 40.0D;
    private static final double FADE_TICKS = 30.0D;

    private static final ConcurrentMap<UUID, Track> TRACKS = new ConcurrentHashMap<>();
    private static volatile long lastPacketWorldTime = -1L;
    private static volatile boolean receivedAnyPacket;

    private ClientCloudCellCache() {
    }

    public static boolean hasCells() {
        return receivedAnyPacket && !TRACKS.isEmpty();
    }

    public static long lastPacketWorldTime() {
        return lastPacketWorldTime;
    }

    public static int trackedCellCount() {
        return TRACKS.size();
    }

    /** Applies one full snapshot: cells not in the collection fade out. */
    public static void applyFullSnapshot(Collection<CloudCell> cells, long worldTime) {
        receivedAnyPacket = true;
        lastPacketWorldTime = Math.max(lastPacketWorldTime, worldTime);
        Set<UUID> active = new HashSet<>();
        if (cells != null) {
            for (CloudCell cell : cells) {
                if (cell == null) {
                    continue;
                }
                active.add(cell.id());
                TRACKS.computeIfAbsent(cell.id(), id -> new Track()).push(cell);
            }
        }
        for (Track track : TRACKS.values()) {
            if (!active.contains(track.latestId())) {
                track.markMissing();
            }
        }
    }

    /** Applies a delta: updated cells replace tracks, removed ids fade out. */
    public static void applyDelta(Collection<CloudCell> updated, Collection<UUID> removed, long worldTime) {
        receivedAnyPacket = true;
        lastPacketWorldTime = Math.max(lastPacketWorldTime, worldTime);
        if (updated != null) {
            for (CloudCell cell : updated) {
                if (cell != null) {
                    TRACKS.computeIfAbsent(cell.id(), id -> new Track()).push(cell);
                }
            }
        }
        if (removed != null) {
            for (UUID id : removed) {
                Track track = TRACKS.get(id);
                if (track != null) {
                    track.markMissing();
                }
            }
        }
    }

    public static void clear() {
        TRACKS.clear();
        lastPacketWorldTime = -1L;
        receivedAnyPacket = false;
    }

    /**
     * Produces the interpolated presentation cells for this frame.
     *
     * @param dimensionId current client dimension id
     * @param worldTime current client world time
     * @param partialTick current partial tick
     * @return interpolated cells ready for rendering and density queries
     */
    public static List<CloudCell> presentCells(String dimensionId, long worldTime, float partialTick) {
        double renderTime = worldTime + (Float.isFinite(partialTick) ? partialTick : 0.0F);
        List<CloudCell> result = new ArrayList<>();
        for (UUID id : TRACKS.keySet()) {
            Track track = TRACKS.get(id);
            if (track == null) {
                continue;
            }
            if (track.shouldRemove(renderTime)) {
                TRACKS.remove(id, track);
                continue;
            }
            CloudCell presented = track.present(renderTime);
            if (presented == null) {
                continue;
            }
            if (dimensionId != null && !dimensionId.isBlank() && !dimensionId.equals(presented.dimensionId())) {
                continue;
            }
            result.add(presented);
        }
        return result;
    }

    private static final class Track {
        private CloudCell older;
        private CloudCell newer;
        private boolean presentInLatestPacket = true;
        private double firstPresentedTime = Double.NaN;
        private double missingSinceTime = Double.NaN;

        private synchronized UUID latestId() {
            return newer == null ? new UUID(0L, 0L) : newer.id();
        }

        private synchronized void push(CloudCell cell) {
            if (newer == null || cell.worldTime() >= newer.worldTime()) {
                older = newer;
                newer = cell;
            } else if (older == null || cell.worldTime() > older.worldTime()) {
                older = cell;
            }
            presentInLatestPacket = true;
            missingSinceTime = Double.NaN;
        }

        private synchronized void markMissing() {
            presentInLatestPacket = false;
        }

        private synchronized boolean shouldRemove(double renderTime) {
            return !presentInLatestPacket
                    && !Double.isNaN(missingSinceTime)
                    && renderTime - missingSinceTime > FADE_TICKS;
        }

        private synchronized CloudCell present(double renderTime) {
            if (newer == null) {
                return null;
            }
            if (Double.isNaN(firstPresentedTime)) {
                firstPresentedTime = renderTime;
            }
            if (!presentInLatestPacket && Double.isNaN(missingSinceTime)) {
                missingSinceTime = renderTime;
            }

            float fade = fade01((renderTime - firstPresentedTime) / FADE_TICKS);
            if (!presentInLatestPacket) {
                fade *= 1.0F - fade01((renderTime - missingSinceTime) / FADE_TICKS);
            }
            if (fade <= 0.002F) {
                return null;
            }

            double targetTime = renderTime - PRESENTATION_DELAY_TICKS;
            CloudCell base;
            if (older != null && newer.worldTime() > older.worldTime()
                    && targetTime >= older.worldTime() && targetTime <= newer.worldTime()) {
                base = interpolate(older, newer, targetTime);
            } else if (targetTime > newer.worldTime()) {
                base = extrapolate(newer, targetTime);
            } else {
                base = newer;
            }
            if (fade >= 0.999F) {
                return base;
            }
            return withDensity(base, base.density() * fade);
        }
    }

    private static CloudCell interpolate(CloudCell older, CloudCell newer, double targetTime) {
        double span = Math.max(1.0D, newer.worldTime() - older.worldTime());
        float t = (float) Math.max(0.0D, Math.min(1.0D, (targetTime - older.worldTime()) / span));
        float smoothT = t * t * (3.0F - 2.0F * t);

        // Hermite center interpolation using per-cell wind as tangents keeps
        // motion continuous across packets instead of piecewise-linear.
        Vec3 p0 = new Vec3(older.x(), 0.0D, older.z());
        Vec3 p1 = new Vec3(newer.x(), 0.0D, newer.z());
        Vec3 m0 = older.wind().scale(span);
        Vec3 m1 = newer.wind().scale(span);
        float t2 = smoothT * smoothT;
        float t3 = t2 * smoothT;
        double h00 = 2.0D * t3 - 3.0D * t2 + 1.0D;
        double h10 = t3 - 2.0D * t2 + smoothT;
        double h01 = -2.0D * t3 + 3.0D * t2;
        double h11 = t3 - t2;
        Vec3 center = p0.scale(h00).add(m0.scale(h10)).add(p1.scale(h01)).add(m1.scale(h11));

        return new CloudCell(
                newer.id(),
                newer.seed(),
                newer.dimensionId(),
                center.x(),
                center.z(),
                lerp(older.baseY(), newer.baseY(), smoothT),
                lerp(older.topY(), newer.topY(), smoothT),
                lerp(older.radiusMajor(), newer.radiusMajor(), smoothT),
                lerp(older.radiusMinor(), newer.radiusMinor(), smoothT),
                lerpAngle(older.orientationRadians(), newer.orientationRadians(), smoothT),
                lerp(older.density(), newer.density(), smoothT),
                lerp(older.edgeSoftness(), newer.edgeSoftness(), smoothT),
                lerp(older.energy(), newer.energy(), smoothT),
                lerp(older.rotation(), newer.rotation(), smoothT),
                lerp(older.funnelStrength(), newer.funnelStrength(), smoothT),
                lerp(older.funnelGroundY(), newer.funnelGroundY(), smoothT),
                newer.wind(),
                newer.phase(),
                newer.classification(),
                newer.ageTicks(),
                (long) Math.floor(targetTime)
        );
    }

    private static CloudCell extrapolate(CloudCell cell, double targetTime) {
        double deltaTicks = Math.max(0.0D, Math.min(EXTRAPOLATION_LIMIT_TICKS, targetTime - cell.worldTime()));
        return new CloudCell(
                cell.id(),
                cell.seed(),
                cell.dimensionId(),
                cell.x() + cell.wind().x() * deltaTicks,
                cell.z() + cell.wind().z() * deltaTicks,
                cell.baseY(),
                cell.topY(),
                cell.radiusMajor(),
                cell.radiusMinor(),
                cell.orientationRadians(),
                cell.density(),
                cell.edgeSoftness(),
                cell.energy(),
                cell.rotation(),
                cell.funnelStrength(),
                cell.funnelGroundY(),
                cell.wind(),
                cell.phase(),
                cell.classification(),
                cell.ageTicks() + Math.round(deltaTicks),
                (long) Math.floor(targetTime)
        );
    }

    private static CloudCell withDensity(CloudCell cell, float density) {
        return new CloudCell(
                cell.id(), cell.seed(), cell.dimensionId(), cell.x(), cell.z(),
                cell.baseY(), cell.topY(), cell.radiusMajor(), cell.radiusMinor(),
                cell.orientationRadians(), density, cell.edgeSoftness(), cell.energy(),
                cell.rotation(), cell.funnelStrength(), cell.funnelGroundY(), cell.wind(),
                cell.phase(), cell.classification(), cell.ageTicks(), cell.worldTime()
        );
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float lerpAngle(float a, float b, float t) {
        float delta = (float) Math.atan2(Math.sin(b - a), Math.cos(b - a));
        return a + delta * t;
    }

    private static float fade01(double t) {
        float clamped = (float) Math.max(0.0D, Math.min(1.0D, t));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }
}
