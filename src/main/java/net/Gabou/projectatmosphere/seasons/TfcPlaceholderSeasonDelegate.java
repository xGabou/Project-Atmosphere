package net.Gabou.projectatmosphere.seasons;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Placeholder delegate for a future PA-for-TFC bridge.
 * Currently returns neutral data; real logic should replace this when the bridge mod is present.
 */
public class TfcPlaceholderSeasonDelegate implements SeasonTimeDelegate {
    private static final ResourceLocation ID = new ResourceLocation("projectatmosphere", "tfc_placeholder");

    @Override
    public SeasonSnapshot snapshot(Level level) {
        return new SeasonSnapshot(ID, SeasonStage.NEUTRAL, 0.0f, 0.0f);
    }

    @Override
    public long seasonCycleTicks(Level level) {
        return 24000L * 4;
    }

    @Override
    public long seasonDuration(Level level) {
        return 24000L;
    }

    @Override
    public long dayDuration(Level level) {
        return 24000L;
    }
}
