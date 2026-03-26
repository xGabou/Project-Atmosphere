package net.Gabou.projectatmosphere.modules.tornado;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.SpawnRegion;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.api.common.cloud.region.ITornadoRegion;
import net.Gabou.projectatmosphere.api.common.cloud.region.TornadoDescriptor;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.Gabou.projectatmosphere.network.RemoveTornadoPacket;
import net.Gabou.projectatmosphere.network.SpawnTornadoPacket;
import net.Gabou.projectatmosphere.network.SyncTornadoesPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class TornadoManager {
    private static final List<TornadoInstance> SERVER_TORNADOES = new ArrayList<>();
    private static final List<TornadoInstance> CLIENT_TORNADOES = new ArrayList<>();
    private static final ResourceLocation RUNTIME_TORNADO_CONTROLLER =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "runtime_spawn");
    private static final float MIN_VISUAL_HEIGHT = 96.0F;
    private static final float HEIGHT_RADIUS_FACTOR = 6.0F;
    private static final float HEIGHT_CLOUD_PADDING = 40.0F;
    private static final int SYNC_INTERVAL_TICKS = 5;

    private static float shaderTime = 0.0F;

    @OnlyIn(Dist.CLIENT)
    public static void spawnClient(UUID id, Vec3 pos, float radius, WindVector wind, float bottomY, float height) {
        applyClientSnapshot(new TornadoSnapshot(
                id,
                new Vec3(pos.x, bottomY, pos.z),
                radius,
                bottomY,
                height,
                wind.baseSpeed(),
                wind.angleRadians(),
                wind.gustSpeed(),
                Mth.clamp((radius - 5.0F) / 20.0F, 0.25F, 1.0F),
                net.Gabou.projectatmosphere.modules.weather.StormLifecyclePhase.FORMING
        ));
    }

    public static boolean spawnServer(ServerLevel level, Vec3 pos, float radius, WindVector wind) {
        if (!AtmoCommonConfig.ENABLE_TORNADOES.get()) {
            return false;
        }

        CloudRegion cloud = findIntersectingCloud(level, pos, radius);
        if (cloud == null) {
            return false;
        }

        UUID id = UUID.randomUUID();
        TornadoGeometry geometry = computeGeometry(level, pos, radius);
        Vec3 spawnPos = new Vec3(pos.x, geometry.bottomY(), pos.z);
        TornadoInstance tornado = new TornadoInstance(id, spawnPos, radius, wind, geometry.bottomY(), geometry.height(), cloud);
        attachDescriptor(cloud, tornado);
        SERVER_TORNADOES.add(tornado);

        NetworkHandler.CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                new SpawnTornadoPacket(id, spawnPos, radius, wind, geometry.bottomY(), geometry.height())
        );
        broadcastSnapshots();
        return true;
    }

    public static List<TornadoInstance> getActiveTornadoes() {
        return SERVER_TORNADOES;
    }

    public static List<TornadoInstance> getClientTornadoes() {
        return CLIENT_TORNADOES;
    }

    public static void removeTornado(TornadoInstance tornado) {
        if (SERVER_TORNADOES.remove(tornado)) {
            removeAttachedDescriptor(tornado);
            broadcastRemoval(tornado.getId());
            broadcastSnapshots();
        }
    }

    public static void clearTornadoes() {
        for (TornadoInstance tornado : new ArrayList<>(SERVER_TORNADOES)) {
            removeAttachedDescriptor(tornado);
            broadcastRemoval(tornado.getId());
        }
        SERVER_TORNADOES.clear();
        broadcastSnapshots();
    }

    public static void removeClientTornado(UUID id) {
        CLIENT_TORNADOES.removeIf(tornado -> tornado.getId().equals(id));
    }

    public static void clearClientTornadoes() {
        CLIENT_TORNADOES.clear();
    }

    @OnlyIn(Dist.CLIENT)
    public static float getShaderTime() {
        return shaderTime;
    }

    public static void tick(Level level) {
        if (level == null) {
            return;
        }

        if (level.isClientSide) {
            shaderTime += 0.05F;
            for (TornadoInstance tornado : CLIENT_TORNADOES) {
                tornado.tickClient();
            }
            return;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        long gameTime = serverLevel.getGameTime();
        Iterator<TornadoInstance> iterator = SERVER_TORNADOES.iterator();
        while (iterator.hasNext()) {
            TornadoInstance tornado = iterator.next();
            CloudRegion currentRegion = findIntersectingCloud(serverLevel, tornado.position, tornado.radius);
            if (currentRegion != tornado.getCloudRegion()) {
                removeAttachedDescriptor(tornado);
                tornado.setCloudRegion(currentRegion);
                if (currentRegion != null) {
                    attachDescriptor(currentRegion, tornado);
                }
            } else if (currentRegion != null) {
                ensureDescriptor(currentRegion, tornado);
            }

            if (tornado.getCloudRegion() == null && tornado.getLifetimeSeconds() > 5.0F) {
                tornado.markDissipating();
            }

            tornado.tickServer(serverLevel, gameTime);
            if (tornado.isDead()) {
                removeAttachedDescriptor(tornado);
                iterator.remove();
                broadcastRemoval(tornado.getId());
            }
        }

        if (gameTime % SYNC_INTERVAL_TICKS == 0L) {
            broadcastSnapshots();
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void applyClientSnapshots(List<TornadoSnapshot> snapshots) {
        List<TornadoInstance> next = new ArrayList<>(snapshots.size());
        for (TornadoSnapshot snapshot : snapshots) {
            TornadoInstance existing = findClient(snapshot.id());
            CloudRegion cloud = findClientCloud(snapshot.position(), snapshot.radius());
            if (existing == null) {
                existing = new TornadoInstance(
                        snapshot.id(),
                        snapshot.position(),
                        snapshot.radius(),
                        new WindVector(snapshot.windSpeed(), snapshot.windAngle(), snapshot.windGust()),
                        snapshot.visualBottomY(),
                        snapshot.visualHeight(),
                        cloud
                );
            }
            existing.applySnapshot(snapshot, cloud);
            next.add(existing);
        }
        CLIENT_TORNADOES.clear();
        CLIENT_TORNADOES.addAll(next);
    }

    @OnlyIn(Dist.CLIENT)
    private static void applyClientSnapshot(TornadoSnapshot snapshot) {
        TornadoInstance existing = findClient(snapshot.id());
        CloudRegion cloud = findClientCloud(snapshot.position(), snapshot.radius());
        if (existing == null) {
            existing = new TornadoInstance(
                    snapshot.id(),
                    snapshot.position(),
                    snapshot.radius(),
                    new WindVector(snapshot.windSpeed(), snapshot.windAngle(), snapshot.windGust()),
                    snapshot.visualBottomY(),
                    snapshot.visualHeight(),
                    cloud
            );
            CLIENT_TORNADOES.add(existing);
        }
        existing.applySnapshot(snapshot, cloud);
    }

    @OnlyIn(Dist.CLIENT)
    private static TornadoInstance findClient(UUID id) {
        for (TornadoInstance tornado : CLIENT_TORNADOES) {
            if (tornado.getId().equals(id)) {
                return tornado;
            }
        }
        return null;
    }

    @OnlyIn(Dist.CLIENT)
    @Nullable
    private static CloudRegion findClientCloud(Vec3 pos, float radius) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        return findIntersectingCloud(level, pos, radius);
    }

    @Nullable
    private static CloudRegion findIntersectingCloud(Level level, Vec3 pos, float radius) {
        SpawnRegion temporaryRegion = new SpawnRegion(Mth.floor(pos.x), Mth.floor(pos.z), Mth.ceil(radius));
        for (CloudRegion cloud : CloudManager.get(level).getClouds()) {
            if (cloud.intersects(temporaryRegion)) {
                return cloud;
            }
        }
        return null;
    }

    private static TornadoGeometry computeGeometry(Level level, Vec3 pos, float radius) {
        float bottomY = (float) pos.y;
        float cloudBase = CloudManager.get(level).getCloudHeight();
        float reachToCloudBase = Math.max(0.0F, cloudBase - bottomY);
        float height = Math.max(MIN_VISUAL_HEIGHT, reachToCloudBase + radius * HEIGHT_RADIUS_FACTOR + HEIGHT_CLOUD_PADDING);
        return new TornadoGeometry(bottomY, height);
    }

    private static void attachDescriptor(CloudRegion cloud, TornadoInstance tornado) {
        if (cloud instanceof ITornadoRegion tornadoRegion) {
            tornadoRegion.replaceTornado(createRuntimeDescriptor(tornado, cloud));
        }
    }

    private static void ensureDescriptor(CloudRegion cloud, TornadoInstance tornado) {
        if (cloud instanceof ITornadoRegion tornadoRegion && tornadoRegion.findTornado(tornado.getId()) == null) {
            tornadoRegion.addTornado(createRuntimeDescriptor(tornado, cloud));
        }
    }

    private static TornadoDescriptor createRuntimeDescriptor(TornadoInstance tornado, CloudRegion cloud) {
        float offsetX = (float) (tornado.position.x - cloud.getWorldX());
        float offsetZ = (float) (tornado.position.z - cloud.getWorldZ());
        return new TornadoDescriptor(
                tornado.getId(),
                RUNTIME_TORNADO_CONTROLLER,
                offsetX,
                offsetZ,
                tornado.wind.baseSpeed() * (float) Math.cos(tornado.wind.angleRadians()) * 0.05F,
                tornado.wind.baseSpeed() * (float) Math.sin(tornado.wind.angleRadians()) * 0.05F,
                tornado.radius,
                tornado.getVisualBottomY(),
                tornado.getVisualHeight()
        );
    }

    private static void removeAttachedDescriptor(TornadoInstance tornado) {
        if (tornado.getCloudRegion() instanceof ITornadoRegion tornadoRegion) {
            tornadoRegion.removeTornado(tornado.getId());
        }
    }

    private static void broadcastRemoval(UUID id) {
        NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), new RemoveTornadoPacket(id));
    }

    private static void broadcastSnapshots() {
        List<TornadoSnapshot> snapshots = new ArrayList<>(SERVER_TORNADOES.size());
        for (TornadoInstance tornado : SERVER_TORNADOES) {
            snapshots.add(tornado.snapshot());
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), new SyncTornadoesPacket(snapshots));
    }

    private record TornadoGeometry(float bottomY, float height) {
    }
}
