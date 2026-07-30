package net.Gabou.projectatmosphere.modules.weathercell;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class WeatherCellSavedData extends SavedData {
    private static final String DATA_NAME = "project_atmosphere_weather_cells";
    private static final String TAG_VERSION = "Version";
    private static final String TAG_CELLS = "Cells";
    private static final int VERSION = 1;

    private final Map<UUID, WeatherCellState> cells = new LinkedHashMap<>();

    public WeatherCellSavedData() {
    }

    public WeatherCellSavedData(@NotNull CompoundTag tag) {
        load(tag);
    }

    public static @NotNull WeatherCellSavedData get(@NotNull ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        WeatherCellSavedData::new,
                        (tag, provider) -> new WeatherCellSavedData(tag)
                ),
                DATA_NAME
        );
    }

    public @NotNull Collection<WeatherCellState> getCells() {
        return cells.values();
    }

    public void add(WeatherCellState cell) {
        if (cell == null) {
            return;
        }
        cells.put(cell.getId(), cell);
        setDirty();
    }

    public void remove(UUID id) {
        if (id != null && cells.remove(id) != null) {
            setDirty();
        }
    }

    public void markChanged() {
        setDirty();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt(TAG_VERSION, VERSION);
        ListTag list = new ListTag();
        for (WeatherCellState cell : cells.values()) {
            if (cell != null) {
                list.add(cell.save());
            }
        }
        tag.put(TAG_CELLS, list);
        return tag;
    }

    private void load(CompoundTag tag) {
        cells.clear();
        if (tag == null || !tag.contains(TAG_CELLS, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = tag.getList(TAG_CELLS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            WeatherCellState cell = WeatherCellState.load(list.getCompound(i));
            cells.put(cell.getId(), cell);
        }
    }
}
