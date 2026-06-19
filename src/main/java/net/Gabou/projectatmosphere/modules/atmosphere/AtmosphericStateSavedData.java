package net.Gabou.projectatmosphere.modules.atmosphere;

import net.Gabou.projectatmosphere.modules.ocean.OceanBasinManager;
import net.Gabou.projectatmosphere.modules.wind.WindEngine;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Persists the mutable weather layer only.
 * Forecast baselines and native cloud regions are saved by their own systems.
 */
public final class AtmosphericStateSavedData extends SavedData {
    private static final String DATA_NAME = "project_atmosphere_live_atmosphere";
    private static final int VERSION = 1;

    private CompoundTag payload = new CompoundTag();

    public AtmosphericStateSavedData() {
    }

    public AtmosphericStateSavedData(@NotNull CompoundTag tag) {
        payload = tag.contains("LiveAtmosphere", Tag.TAG_COMPOUND)
                ? tag.getCompound("LiveAtmosphere")
                : new CompoundTag();
    }

    public static void restore(ServerLevel level) {
        if (level == null) {
            return;
        }
        AtmosphericStateSavedData data = get(level);
        data.apply(level);
    }

    public static void snapshot(ServerLevel level) {
        if (level == null) {
            return;
        }
        AtmosphericStateSavedData data = get(level);
        data.capture(level);
        data.setDirty();
    }

    private static AtmosphericStateSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                AtmosphericStateSavedData::new,
                AtmosphericStateSavedData::new,
                DATA_NAME
        );
    }

    private void capture(ServerLevel level) {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", VERSION);
        root.putLong("SavedGameTime", level.getGameTime());
        root.putLong("SavedDayTime", level.getDayTime());

        ListTag states = new ListTag();
        for (RegionAtmosphereState state : AtmosphericStateRegistry.snapshot()) {
            if (state == null || state.getRegionId() == null) {
                continue;
            }
            CompoundTag stateTag = new CompoundTag();
            stateTag.put("Region", saveRegionKey(state.getRegionId()));
            stateTag.put("State", state.saveMutableState());
            states.add(stateTag);
        }
        root.put("States", states);

        ListTag activeRegions = new ListTag();
        for (RegionInstanceKey key : AtmosphericStateRegistry.getActiveStates()) {
            if (key != null) {
                activeRegions.add(saveRegionKey(key));
            }
        }
        root.put("ActiveRegions", activeRegions);

        root.put("Scheduler", AtmosphericUpdateScheduler.savePersistentState());
        root.put("WeakLows", WeakLowManager.savePersistentState());
        root.put("Cyclones", CycloneManager.savePersistentState());
        root.put("OceanBasins", OceanBasinManager.savePersistentState());
        root.put("WindEngine", WindEngine.savePersistentState());
        root.put("SeasonalDrift", SeasonalAtmosphericDrift.savePersistentState());
        payload = root;
    }

    private void apply(ServerLevel level) {
        if (payload == null || payload.isEmpty()) {
            return;
        }

        ListTag states = payload.getList("States", Tag.TAG_COMPOUND);
        for (int i = 0; i < states.size(); i++) {
            CompoundTag stateTag = states.getCompound(i);
            RegionInstanceKey key = loadRegionKey(stateTag.getCompound("Region"));
            if (key == null) {
                continue;
            }
            RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
            if (state == null) {
                continue;
            }
            state.applyMutableState(stateTag.getCompound("State"));
        }

        Set<RegionInstanceKey> activeRegions = new HashSet<>();
        ListTag activeTags = payload.getList("ActiveRegions", Tag.TAG_COMPOUND);
        for (int i = 0; i < activeTags.size(); i++) {
            RegionInstanceKey key = loadRegionKey(activeTags.getCompound(i));
            if (key != null && AtmosphericStateRegistry.getState(key) != null) {
                activeRegions.add(key);
            }
        }
        AtmosphericStateRegistry.replaceActiveStates(activeRegions);

        AtmosphericUpdateScheduler.loadPersistentState(payload.getCompound("Scheduler"));
        WeakLowManager.loadPersistentState(payload.getCompound("WeakLows"));
        CycloneManager.loadPersistentState(level, payload.getCompound("Cyclones"));
        OceanBasinManager.loadPersistentState(payload.getCompound("OceanBasins"));
        WindEngine.loadPersistentState(payload.getCompound("WindEngine"));
        SeasonalAtmosphericDrift.loadPersistentState(payload.getCompound("SeasonalDrift"));
        AtmosphericStateRegistry.rebuildNeighbors();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        tag.put("LiveAtmosphere", payload == null ? new CompoundTag() : payload.copy());
        return tag;
    }

    private static CompoundTag saveRegionKey(RegionInstanceKey key) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("RegionX", key.regionX());
        tag.putInt("RegionZ", key.regionZ());
        tag.putInt("RegionSize", key.regionSize());
        return tag;
    }

    private static RegionInstanceKey loadRegionKey(CompoundTag tag) {
        if (tag == null || !tag.contains("RegionX", Tag.TAG_INT) || !tag.contains("RegionZ", Tag.TAG_INT)) {
            return null;
        }
        int size = tag.contains("RegionSize", Tag.TAG_INT) ? tag.getInt("RegionSize") : RegionInstanceKey.DEFAULT_REGION_SIZE;
        return new RegionInstanceKey(tag.getInt("RegionX"), tag.getInt("RegionZ"), size);
    }
}
