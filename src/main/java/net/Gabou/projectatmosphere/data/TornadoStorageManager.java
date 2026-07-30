package net.Gabou.projectatmosphere.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class TornadoStorageManager {
    private static final String STORAGE_ID = "project_atmosphere_runtime_storms";
    private static final String COOLDOWNS_KEY = "cooldowns";
    private static final String TORNADOES_KEY = "tornadoes";
    private static final String HURRICANES_KEY = "hurricanes";
    private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();

    private TornadoStorageManager() {}

    public static void load(ServerLevel level) {
        if (!AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            return;
        }
        RuntimeStormData data = RuntimeStormData.get(level);
        COOLDOWNS.clear();
        COOLDOWNS.putAll(data.cooldowns);
        AtmosphereCloudServices.get().loadSevereWeather(level, data.tornadoes, data.hurricanes);
    }

    public static void save(ServerLevel level) {
        if (!AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            return;
        }
        RuntimeStormData data = RuntimeStormData.get(level);
        data.cooldowns.clear();
        data.cooldowns.putAll(COOLDOWNS);
        data.tornadoes.clear();
        data.hurricanes.clear();
        AtmosphereCloudServices.get().saveSevereWeather(data.tornadoes, data.hurricanes);
        data.setDirty();
    }

    public static void setCooldown(RegionInstanceKey key, long untilTick) {
        if (key == null) {
            return;
        }
        COOLDOWNS.put(key.toString(), untilTick);
    }

    public static boolean isOnCooldown(RegionInstanceKey key, long nowTick) {
        if (key == null) {
            return false;
        }
        Long until = COOLDOWNS.get(key.toString());
        return until != null && nowTick < until;
    }

    private static final class RuntimeStormData extends SavedData {
        private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
        private final List<CompoundTag> tornadoes = new ArrayList<>();
        private final List<CompoundTag> hurricanes = new ArrayList<>();

        private static RuntimeStormData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(RuntimeStormData::new, RuntimeStormData::load),
                    STORAGE_ID
            );
        }

        private static RuntimeStormData load(CompoundTag tag, HolderLookup.Provider provider) {
            RuntimeStormData data = new RuntimeStormData();
            ListTag cooldownTags = tag.getList(COOLDOWNS_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < cooldownTags.size(); i++) {
                CompoundTag cooldownTag = cooldownTags.getCompound(i);
                data.cooldowns.put(cooldownTag.getString("key"), cooldownTag.getLong("until"));
            }

            ListTag tornadoTags = tag.getList(TORNADOES_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < tornadoTags.size(); i++) {
                data.tornadoes.add(tornadoTags.getCompound(i));
            }

            ListTag hurricaneTags = tag.getList(HURRICANES_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < hurricaneTags.size(); i++) {
                data.hurricanes.add(hurricaneTags.getCompound(i));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            ListTag cooldownTags = new ListTag();
            for (Map.Entry<String, Long> entry : this.cooldowns.entrySet()) {
                CompoundTag cooldownTag = new CompoundTag();
                cooldownTag.putString("key", entry.getKey());
                cooldownTag.putLong("until", entry.getValue());
                cooldownTags.add(cooldownTag);
            }
            tag.put(COOLDOWNS_KEY, cooldownTags);

            ListTag tornadoTags = new ListTag();
            for (CompoundTag tornado : this.tornadoes) {
                tornadoTags.add(tornado.copy());
            }
            tag.put(TORNADOES_KEY, tornadoTags);

            ListTag hurricaneTags = new ListTag();
            for (CompoundTag hurricane : this.hurricanes) {
                hurricaneTags.add(hurricane.copy());
            }
            tag.put(HURRICANES_KEY, hurricaneTags);
            return tag;
        }
    }
}
