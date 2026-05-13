package net.Gabou.projectatmosphere.modules.tornado;

import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static net.Gabou.projectatmosphere.util.AtmosphereUtils.isGlass;

/**
 * Tracks damage done to glass blocks by tornado debris and handles auto repair.
 */
public class GlassDamageManager {

    private static final long REPAIR_DELAY_MS = 5 * 60 * 1000L;
    private static final Map<BlockPos, GlassState> GLASS_STATE = new HashMap<>();
    private static final int MAX_DAMAGE = 8;
    public static boolean DEBUG_GLASS_DAMAGE = false;
    public static boolean DEBUG_GLASS_REPAIR = false;
    public static boolean DEBUG_GLASS_DESTROY = false;
    public static boolean doDamageGlass = AtmoCommonConfig.DAMAGE_GLASS_ON_TORNADO.get();
    public static boolean doAutoRepairGlass = AtmoCommonConfig.AUTO_REPAIR_GLASS.get();
    private static it.unimi.dsi.fastutil.longs.LongArrayList toDestroy = new it.unimi.dsi.fastutil.longs.LongArrayList(2048);


    /**
     * Records damage to a glass block at the specified position.
     * If the damage exceeds the maximum threshold, the block is destroyed.
     *
     * @param level The server level where the block is located.
     * @param toProcess A list of block positions (as long values) to process for damage.
     */
    public static void damageGlass(ServerLevel level, it.unimi.dsi.fastutil.longs.LongArrayList toProcess) {
        if (!AtmoCommonConfig.ENABLE_TORNADO_DESTRUCTION.get()) return;
        if (toProcess.isEmpty()) return;
        for(int i = 0; i < toProcess.size(); i++) {
            BlockPos pos = BlockPos.of(toProcess.getLong(i));
            if (!level.isLoaded(pos)) continue;

            BlockState state = level.getBlockState(pos);
            if(!isGlass(state)) continue;

            GlassState glass = GLASS_STATE.computeIfAbsent(pos.immutable(), p -> new GlassState(state));
            glass.damage = Math.min(MAX_DAMAGE, glass.damage + 1);
            glass.lastDamageTime = System.currentTimeMillis();

            if (glass.damage >= MAX_DAMAGE && !glass.broken && AtmoCommonConfig.DAMAGE_GLASS_ON_TORNADO.get()) {
                toDestroy.add((pos.asLong()));
                glass.broken = true;
            }
        }
        if (toDestroy.isEmpty()) return;

        // destruction uniquement sur le thread serveur
        final int perTick = 256;
        _destroyCursor = 0;
        AsyncAtmosphereService.runOnMainThread(() -> processGlassDestruction(level, toDestroy, perTick));
    }

    private static int _destroyCursor = 0;
    private static void processGlassDestruction(ServerLevel level,
                                           it.unimi.dsi.fastutil.longs.LongArrayList list,
                                           int perTick) {
        if (!AtmoCommonConfig.ENABLE_TORNADO_DESTRUCTION.get() || !AtmoCommonConfig.DAMAGE_GLASS_ON_TORNADO.get()) {
            _destroyCursor = 0;
            list.clear();
            return;
        }
        if (_destroyCursor >= list.size()) { _destroyCursor = 0; return; }

        int end = Math.min(_destroyCursor + perTick, list.size());
        for (int i = _destroyCursor; i < end; i++) {
            BlockPos pos = BlockPos.of(list.getLong(i));
            if (!level.isLoaded(pos)) continue;

            BlockState state = level.getBlockState(pos);
            if(!isGlass(state)) continue;
            level.destroyBlock(pos, false);
        }

        _destroyCursor = end;
        if (_destroyCursor < list.size()) {
            level.getServer().execute(() -> processGlassDestruction(level, list, perTick));
        } else {
            _destroyCursor = 0;
        }
    }


    public static void tick(ServerLevel level) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<BlockPos, GlassState>> iterator = GLASS_STATE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, GlassState> entry = iterator.next();
            GlassState state = entry.getValue();
            if (now - state.lastDamageTime >= REPAIR_DELAY_MS) {
                if (state.broken && doAutoRepairGlass) {
                    level.setBlock(entry.getKey(), state.originalState, 3);
                }
                iterator.remove();
            }
        }
    }


    private static class GlassState {
        final BlockState originalState;
        int damage = 0;
        boolean broken = false;
        long lastDamageTime = System.currentTimeMillis();

        GlassState(BlockState originalState) {
            this.originalState = originalState;
        }
    }
}
