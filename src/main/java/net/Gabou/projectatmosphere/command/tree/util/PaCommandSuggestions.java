package net.Gabou.projectatmosphere.command.tree.util;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class PaCommandSuggestions {
    private PaCommandSuggestions() {
    }

    public static final SuggestionProvider<CommandSourceStack> BIOME_SUGGESTIONS = (ctx, builder) -> {
        builder.suggest("current");
        builder.suggest("current_biome");
        builder.suggest("minecraft:plains");
        builder.suggest("minecraft:desert");
        ctx.getSource().getServer().registryAccess()
                .registryOrThrow(Registries.BIOME)
                .keySet()
                .forEach(id -> builder.suggest(id.toString()));
        return builder.buildFuture();
    };

    public static final SuggestionProvider<CommandSourceStack> CLOUD_TYPE_SUGGESTIONS = (ctx, builder) -> {
        builder.suggest("cumulus");
        builder.suggest("stratus");
        builder.suggest("nimbostratus");
        builder.suggest("cumulonimbus");
        builder.suggest("simpleclouds:cumulonimbus");
        CloudTypeRegistry.getAll().keySet().forEach(builder::suggest);
        return builder.buildFuture();
    };

    public static final SuggestionProvider<CommandSourceStack> TORNADO_MODE_SUGGESTIONS = (ctx, builder) -> {
        builder.suggest("normal");
        builder.suggest("no_cloud");
        return builder.buildFuture();
    };

    public static final SuggestionProvider<CommandSourceStack> HURRICANE_CATEGORY_SUGGESTIONS = (ctx, builder) -> {
        for (int i = 1; i <= 5; i++) {
            builder.suggest(Integer.toString(i));
        }
        return builder.buildFuture();
    };

    public static List<String> cloudTypeSuggestions() {
        List<String> suggestions = new ArrayList<>(CloudTypeRegistry.getAll().keySet());
        suggestions.add("cumulus");
        suggestions.add("stratus");
        suggestions.add("nimbostratus");
        suggestions.add("cumulonimbus");
        suggestions.add("simpleclouds:cumulonimbus");
        return suggestions;
    }
}
