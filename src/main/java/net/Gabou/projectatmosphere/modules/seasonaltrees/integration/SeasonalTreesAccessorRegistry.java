package net.Gabou.projectatmosphere.modules.seasonaltrees.integration;

import java.lang.reflect.InvocationTargetException;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.modules.seasonaltrees.core.SeasonPhase;
import net.Gabou.projectatmosphere.modules.seasonaltrees.core.TreeKey;
import net.Gabou.projectatmosphere.modules.seasonaltrees.core.TreeRecord;
import net.Gabou.projectatmosphere.modules.seasonaltrees.core.TreeType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.ArrayList;
import java.util.List;

public class SeasonalTreesAccessorRegistry {
    private static final String DYNAMIC_TREES_ACCESSOR_CLASS =
            "net.Gabou.projectatmosphere.modules.seasonaltrees.integration.DynamicTreesAccessor";

    private final SeasonalTreesTreeAccessor dynamicAccessor = createDynamicAccessor();
    private final SeasonalTreesTreeAccessor vanillaAccessor = new VanillaTreesAccessor();

    public SeasonalTreesTreeAccessor getAccessor(TreeType type) {
        return type == TreeType.VANILLA ? vanillaAccessor : dynamicAccessor;
    }

    public List<SeasonalTreesTreeAccessor> getEnabledAccessors() {
        List<SeasonalTreesTreeAccessor> accessors = new ArrayList<>();
        if (dynamicAccessor.isEnabled()) {
            accessors.add(dynamicAccessor);
        }
        if (vanillaAccessor.isEnabled()) {
            accessors.add(vanillaAccessor);
        }
        return accessors;
    }

    private static SeasonalTreesTreeAccessor createDynamicAccessor() {
        if (!CompatHandler.isDynamicTreesLoaded()) {
            return DisabledAccessor.INSTANCE;
        }
        try {
            Class<?> clazz = Class.forName(DYNAMIC_TREES_ACCESSOR_CLASS);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (instance instanceof SeasonalTreesTreeAccessor accessor) {
                return accessor;
            }
        } catch (ClassNotFoundException | NoClassDefFoundError | InstantiationException
                 | IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Dynamic Trees detected but seasonal tree integration could not initialize. Falling back to disabled adapter.", ex);
            return DisabledAccessor.INSTANCE;
        }
        return DisabledAccessor.INSTANCE;
    }

    private enum DisabledAccessor implements SeasonalTreesTreeAccessor {
        INSTANCE;

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public boolean isTreeValid(ServerLevel level, TreeKey key) {
            return false;
        }

        @Override
        public BlockPos findRootInColumn(ServerLevel level, ChunkAccess chunk, int localX, int localZ) {
            return null;
        }

        @Override
        public TreeRecord createRecord(ServerLevel level, BlockPos rootPos) {
            return null;
        }

        @Override
        public void applyLeafState(ServerLevel level, TreeRecord record, SeasonPhase phase) {
        }

        @Override
        public boolean isMature(ServerLevel level, TreeRecord record) {
            return false;
        }

        @Override
        public ResourceLocation getSpeciesId(ServerLevel level, BlockPos rootPos) {
            return null;
        }

        @Override
        public boolean plantSeed(ServerLevel level, BlockPos pos, ResourceLocation speciesId) {
            return false;
        }
    }
}
