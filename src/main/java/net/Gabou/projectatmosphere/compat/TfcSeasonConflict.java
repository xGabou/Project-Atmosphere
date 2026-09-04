package net.Gabou.projectatmosphere.compat;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Detects season providers that cannot share a TFC installation with Project
 * Atmosphere. This class intentionally has no client imports so the rule stays
 * identical on both physical sides; only the client presents the warning UI.
 */
public final class TfcSeasonConflict {
    private static final String TFC_MOD_ID = "tfc";
    private static final Set<String> KNOWN_SEASON_MOD_IDS = Set.of(
            "sereneseasons",
            "sereneseasonsplus",
            "eclipticseasons",
            "betterdays"
    );

    private TfcSeasonConflict() {
    }

    public static Optional<Conflict> detectLoadedConflict() {
        ModList modList = ModList.get();
        if (!modList.isLoaded(TFC_MOD_ID)) {
            return Optional.empty();
        }

        List<SeasonMod> seasonMods = new ArrayList<>();
        for (IModInfo modInfo : modList.getMods()) {
            String modId = modInfo.getModId();
            if (isSeasonMod(modId)) {
                seasonMods.add(new SeasonMod(modId, modInfo.getDisplayName()));
            }
        }

        if (seasonMods.isEmpty()) {
            return Optional.empty();
        }

        seasonMods.sort(Comparator.comparing(SeasonMod::modId));
        return Optional.of(new Conflict(List.copyOf(seasonMods)));
    }

    private static boolean isSeasonMod(String modId) {
        if (modId == null || modId.isBlank()) {
            return false;
        }

        String normalizedId = modId.toLowerCase(Locale.ROOT);
        return KNOWN_SEASON_MOD_IDS.contains(normalizedId) || normalizedId.contains("season");
    }

    public record Conflict(List<SeasonMod> seasonMods) {
        public String displayNames() {
            return seasonMods.stream()
                    .map(SeasonMod::displayName)
                    .collect(java.util.stream.Collectors.joining(", "));
        }

        public boolean hasSereneSeasons() {
            return seasonMods.stream().anyMatch(mod -> mod.modId().equals("sereneseasons"));
        }
    }

    public record SeasonMod(String modId, String displayName) {
        public SeasonMod {
            displayName = displayName == null || displayName.isBlank() ? modId : displayName;
        }
    }
}
