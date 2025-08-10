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

    private static final long REPAIR_DELAY_MS = 5 * 60 * 1000L;
    private static final Map<BlockPos, GlassState> GLASS_STATE = new HashMap<>();
    private static final int MAX_DAMAGE = 8;
    public static boolean DEBUG_GLASS_DAMAGE = false;
    public static boolean DEBUG_GLASS_REPAIR = false;
    public static boolean DEBUG_GLASS_DESTROY = false;
    public static boolean doDamageGlass = AtmoCommonConfig.DAMAGE_GLASS_ON_TORNADO.get();
    public static boolean doAutoRepairGlass = AtmoCommonConfig.AUTO_REPAIR_GLASS.get();



    /**
     * Records damage to a glass block at the specified position.
     * If the damage exceeds the maximum threshold, the block is destroyed.
     *
     * @param level The server level where the block is located.
     * @param pos The position of the glass block.
     * @param state The original state of the glass block.
     * @param amount The amount of damage to apply.
     */
    public static void damageGlass(ServerLevel level, BlockPos pos, BlockState state, int amount) {
        GlassState glass = GLASS_STATE.computeIfAbsent(pos.immutable(), p -> new GlassState(state));
        glass.damage = Math.min(MAX_DAMAGE, glass.damage + Math.max(1, amount));
        glass.lastDamageTime = System.currentTimeMillis();

        if (glass.damage >= MAX_DAMAGE && !glass.broken && doDamageGlass) {
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
