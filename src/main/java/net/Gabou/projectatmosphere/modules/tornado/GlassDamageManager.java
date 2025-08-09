package net.Gabou.projectatmosphere.modules.tornado;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Tracks damage done to glass blocks by tornado debris and handles auto repair.
 */
public class GlassDamageManager {

    private static final Map<BlockPos, GlassState> GLASS_STATE = new HashMap<>();
    private static final int MAX_DAMAGE = 3;
    public static void damageGlass(ServerLevel level, BlockPos pos, BlockState state) {
        GlassState glass = GLASS_STATE.computeIfAbsent(pos.immutable(), p -> new GlassState(state));
        glass.damage++;
        glass.lastDamageTime = System.currentTimeMillis();
        if (glass.damage >= MAX_DAMAGE) {
            level.destroyBlock(pos, false);
            glass.broken = true;
        }
    }

    public static void tick(ServerLevel level) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<BlockPos, GlassState>> iterator = GLASS_STATE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, GlassState> entry = iterator.next();
            GlassState state = entry.getValue();
            if (now - state.lastDamageTime >= REPAIR_DELAY_MS) {
                if (state.broken && AtmoCommonConfig.AUTO_REPAIR_GLASS.get()) {
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
