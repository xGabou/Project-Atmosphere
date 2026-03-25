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

    private static float shaderTime = 0.0f;
    @OnlyIn(Dist.CLIENT)
    public static void spawnClient(UUID id, Vec3 pos, float radius, WindVector wind, float bottomY, float height) {
        if (!AtmoCommonConfig.ENABLE_TORNADOES.get()) return;
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        CLIENT_TORNADOES.removeIf(tornado -> tornado.getId().equals(id));
        CloudRegion cloud = findIntersectingCloud(level, pos, radius);
        CLIENT_TORNADOES.add(new TornadoInstance(id, new Vec3(pos.x, bottomY, pos.z), radius, wind, bottomY, height, cloud));
        if (ProjectAtmosphere.DEBUG_MODE) {
            ProjectAtmosphere.LOGGER.info(
                    "[TornadoDebug] Client spawnClient kept tornado id={} activeCount={} cloudRegionPresent={} pos={} radius={} bottomY={} height={}",
                    id,
                    CLIENT_TORNADOES.size(),
                    cloud != null,
                    pos,
                    radius,
                    bottomY,
                    height
            );
        }
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
        if (cloud instanceof ITornadoRegion tornadoRegion) {
            tornadoRegion.addTornado(createRuntimeDescriptor(id, cloud, pos, radius, wind, geometry));
        }

        Vec3 spawnPos = new Vec3(pos.x, geometry.bottomY(), pos.z);
        SERVER_TORNADOES.add(new TornadoInstance(id, spawnPos, radius, wind, geometry.bottomY(), geometry.height(), cloud));
        NetworkHandler.CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                new SpawnTornadoPacket(id, spawnPos, radius, wind, geometry.bottomY(), geometry.height())
        );
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
        }
    }

    public static void clearTornadoes() {
        for (TornadoInstance tornado : new ArrayList<>(SERVER_TORNADOES)) {
            removeAttachedDescriptor(tornado);
            broadcastRemoval(tornado.getId());
        }
        SERVER_TORNADOES.clear();
    }

    public static void removeClientTornado(UUID id) {
        CLIENT_TORNADOES.removeIf(tornado -> tornado.getId().equals(id));
        if (ProjectAtmosphere.DEBUG_MODE) {
            ProjectAtmosphere.LOGGER.info(
                    "[TornadoDebug] Client removed tornado id={} remainingClientCount={}",
                    id,
                    CLIENT_TORNADOES.size()
            );
        }
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

        boolean shouldDebugLog = ProjectAtmosphere.DEBUG_MODE && level.isClientSide && level.getGameTime() % 20L == 0L;
        List<TornadoInstance> activeTornadoes = getTornadoes(level);
        if (shouldDebugLog) {
            ProjectAtmosphere.LOGGER.info("[TornadoDebug] Client TornadoManager.tick activeCount={}", activeTornadoes.size());
        }

        Iterator<TornadoInstance> iterator = activeTornadoes.iterator();
        while (iterator.hasNext()) {
            TornadoInstance tornado = iterator.next();
            if (tornado.getLifetimeSeconds() > 600) {
                removeAttachedDescriptor(tornado);
                if (ProjectAtmosphere.DEBUG_MODE) {
                    ProjectAtmosphere.LOGGER.info(
                            "[TornadoDebug] Removing tornado id={} reason=lifetimeExceeded clientSide={}",
                            tornado.getId(),
                            level.isClientSide
                    );
                }
                iterator.remove();
                if (!level.isClientSide) {
                    broadcastRemoval(tornado.getId());
                }
                continue;
            }

            if (tornado.getCloudRegion() == null) {
                tornado.setCloudRegion(findIntersectingCloud(level, tornado.position, tornado.radius));
            }

            boolean synced = tornado.synchronizeWithDescriptor();
            if (tornado.isDescriptorMissing()) {
                if (!level.isClientSide) {
                    if (ProjectAtmosphere.DEBUG_MODE) {
                        ProjectAtmosphere.LOGGER.info(
                                "[TornadoDebug] Removing tornado id={} reason=descriptorMissing serverSide",
                                tornado.getId()
                        );
                    }
                    iterator.remove();
                    broadcastRemoval(tornado.getId());
                    continue;
                }
                if (shouldDebugLog) {
                    ProjectAtmosphere.LOGGER.info(
                            "[TornadoDebug] Keeping client tornado id={} despite missing descriptor; cloudRegionPresent={} pos={} bottomY={} height={}",
                            tornado.getId(),
                            tornado.getCloudRegion() != null,
                            tornado.position,
                            tornado.getVisualBottomY(),
                            tornado.getVisualHeight()
                    );
                }
            }
            if (!synced) {
                tornado.advanceByWind();
            }
            if (shouldDebugLog) {
                ProjectAtmosphere.LOGGER.info(
                        "[TornadoDebug] Tick tornado id={} synced={} descriptorMissing={} cloudRegionPresent={} pos={} radius={} bottomY={} height={}",
                        tornado.getId(),
                        synced,
                        tornado.isDescriptorMissing(),
                        tornado.getCloudRegion() != null,
                        tornado.position,
                        tornado.radius,
                        tornado.getVisualBottomY(),
                        tornado.getVisualHeight()
                );
            }
            tornado.tick(level);
        }
        if (level.isClientSide) {
            shaderTime += 0.05f;
        }
    }

    private static List<TornadoInstance> getTornadoes(Level level) {
        return level.isClientSide ? getClientTornadoes() : SERVER_TORNADOES;
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
        float bottomY = (float)pos.y;
        float cloudBase = CloudManager.get(level).getCloudHeight();
        float reachToCloudBase = Math.max(0.0F, cloudBase - bottomY);
        float height = Math.max(MIN_VISUAL_HEIGHT, reachToCloudBase + radius * HEIGHT_RADIUS_FACTOR + HEIGHT_CLOUD_PADDING);
        return new TornadoGeometry(bottomY, height);
    }

    private static TornadoDescriptor createRuntimeDescriptor(UUID id, CloudRegion cloud, Vec3 pos, float radius,
                                                             WindVector wind, TornadoGeometry geometry) {
        float driftSpeed = wind.baseSpeed() * 0.2f;
        float offsetX = (float)(pos.x - cloud.getWorldX());
        float offsetZ = (float)(pos.z - cloud.getWorldZ());
        float velocityX = (float)(Math.cos(wind.angleRadians()) * driftSpeed);
        float velocityZ = (float)(Math.sin(wind.angleRadians()) * driftSpeed);
        return new TornadoDescriptor(
                id,
                RUNTIME_TORNADO_CONTROLLER,
                offsetX,
                offsetZ,
                velocityX,
                velocityZ,
                radius,
                geometry.bottomY(),
                geometry.height()
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

    private record TornadoGeometry(float bottomY, float height) {
    }
}
