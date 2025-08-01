package net.Gabou.projectatmosphere.config;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs

public class AtmoCommonConfig {
    public static final ForgeConfigSpec.BooleanValue FORCE_SHARED_EXECUTOR;
    public static final ForgeConfigSpec.BooleanValue ENABLE_STORM_DEBRIS;
    public static final ForgeConfigSpec.IntValue MAX_STORM_DEBRIS_PER_CHUNK;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("performance");
        FORCE_SHARED_EXECUTOR = builder
                .comment("Force use of shared executor for all async tasks, regardless of CPU count")
                .define("forceSharedExecutor", false);
        builder.pop();
        builder.push("storms");
        ENABLE_STORM_DEBRIS = builder
                .comment("Enable random debris spawning during storms")
                .define("enableStormDebris", true);
        MAX_STORM_DEBRIS_PER_CHUNK = builder
                .comment("Maximum number of storm debris items allowed per chunk")
                .defineInRange("maxStormDebrisPerChunk", 30, 0, Integer.MAX_VALUE);
        builder.pop();
        COMMON_SPEC = builder.build();
    }

    public static final ForgeConfigSpec COMMON_SPEC;
}

