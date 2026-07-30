package net.Gabou.projectatmosphere.client.screen;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Gère la lecture et l'écriture du shader live des nuages.
 * Cette classe ne dessine rien et sert seulement de passerelle disque.
 */
public final class CloudShaderSourceManager {

    private static final String RESOURCE_PATH = "assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh";
    private static final String SOURCE_RELATIVE_PATH = "src/main/resources/" + RESOURCE_PATH;
    private static final String BUILD_RELATIVE_PATH = "build/resources/main/" + RESOURCE_PATH;

    private CloudShaderSourceManager() {

    }

    /**
     * Lit le shader depuis la meilleure source disponible.
     *
     * @return contenu texte du shader
     * @throws IOException si la lecture échoue
     */
    public static String readShaderSource() throws IOException {
        for (Path path : resolveEditableTargets()) {
            if (Files.isRegularFile(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        }

        URL resource = CloudShaderSourceManager.class.getClassLoader().getResource(RESOURCE_PATH);
        if (resource != null) {
            try (InputStream inputStream = resource.openStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        throw new IOException("Impossible de lire le shader cloud_atmosphere_volume.fsh");
    }

    /**
     * Écrit le shader dans toutes les cibles éditables détectées.
     *
     * @param source contenu texte du shader
     * @return résultat d'écriture
     * @throws IOException si aucune cible n'est disponible ou si tout échoue
     */
    public static SaveResult saveShaderSource(String source) throws IOException {
        List<Path> targets = resolveEditableTargets();
        if (targets.isEmpty()) {
            throw new IOException("Aucune cible d'écriture disponible pour le shader.");
        }

        List<Path> writtenTargets = new ArrayList<>();
        IOException lastError = null;

        for (Path target : targets) {
            try {
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(target, source, StandardCharsets.UTF_8);
                writtenTargets.add(target);
            } catch (IOException exception) {
                lastError = exception;
            }
        }

        if (writtenTargets.isEmpty()) {
            if (lastError != null) {
                throw lastError;
            }
            throw new IOException("Écriture du shader impossible.");
        }

        return new SaveResult(writtenTargets, targets);
    }

    /**
     * Décrit la meilleure cible éditable disponible.
     *
     * @return description lisible de la cible
     */
    public static String describePrimaryTarget() {
        List<Path> targets = resolveEditableTargets();
        if (targets.isEmpty()) {
            return "lecture seule";
        }
        return toDisplayPath(targets.get(0));
    }

    /**
     * Indique si au moins une cible éditable existe.
     *
     * @return true si l'écriture disque est possible
     */
    public static boolean hasEditableTargets() {
        return !resolveEditableTargets().isEmpty();
    }

    /**
     * Recharge les ressources client après une sauvegarde du shader.
     *
     * @return future de rechargement des ressources client
     */
    public static CompletableFuture<Void> reloadClientResources() {
        Minecraft minecraft = Minecraft.getInstance();
        ProjectAtmosphere.LOGGER.info("[CloudState] resourceReload.request source={}", RESOURCE_PATH);
        return minecraft.reloadResourcePacks().whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                ProjectAtmosphere.LOGGER.warn("[CloudState] resourceReload.failed source={}", RESOURCE_PATH, throwable);
            } else {
                ProjectAtmosphere.LOGGER.info("[CloudState] resourceReload.complete source={}", RESOURCE_PATH);
            }
        });
    }

    private static List<Path> resolveEditableTargets() {
        Set<Path> targets = new LinkedHashSet<>();

        Path runtimeResource = resolveRuntimeResourcePath();
        if (runtimeResource != null) {
            targets.add(runtimeResource);
        }

        Path projectRoot = locateProjectRoot();
        if (projectRoot != null) {
            targets.add(projectRoot.resolve(SOURCE_RELATIVE_PATH));
            targets.add(projectRoot.resolve(BUILD_RELATIVE_PATH));
        }

        return List.copyOf(targets);
    }

    private static Path resolveRuntimeResourcePath() {
        URL resource = CloudShaderSourceManager.class.getClassLoader().getResource(RESOURCE_PATH);
        if (resource == null || !"file".equalsIgnoreCase(resource.getProtocol())) {
            return null;
        }

        try {
            return Paths.get(resource.toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException exception) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Impossible de résoudre le chemin du shader actif.", exception);
            return null;
        }
    }

    private static Path locateProjectRoot() {
        Path current = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("build.gradle"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private static String toDisplayPath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        try {
            return cwd.relativize(normalized).toString().replace('\\', '/');
        } catch (IllegalArgumentException exception) {
            return normalized.toString().replace('\\', '/');
        }
    }

    /**
     * Résultat d'écriture du shader.
     *
     * @param writtenPaths cibles réellement écrites
     * @param allTargets toutes les cibles tentées
     */
    public record SaveResult(List<Path> writtenPaths, List<Path> allTargets) {
    }
}
