package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;

/**
 * Marker-gated T132 fixture filesystem support.  This deliberately operates
 * only on the two explicitly named autorun worlds and its own template.  It
 * never touches a player-created save and excludes {@code session.lock} from
 * every snapshot/restore operation.
 */
final class StormT132WorldFixture {
    static final String SOURCE_WORLD_ID = "pa-t132-autogen-source";
    static final String RESTORED_WORLD_ID = "pa-t132-autogen-restored";
    static final Path TEMPLATE_ROOT = Path.of("t132-fixtures", "pristine-current");
    private static final String LEVEL_DAT = "level.dat";
    private static final String SESSION_LOCK = "session.lock";

    private StormT132WorldFixture() {
    }

    static Path savesRoot(Minecraft minecraft) {
        return minecraft.getLevelSource().getBaseDir().toAbsolutePath().normalize();
    }

    static Path worldPath(Minecraft minecraft, String worldId) {
        if (!SOURCE_WORLD_ID.equals(worldId) && !RESTORED_WORLD_ID.equals(worldId)) {
            throw new IllegalArgumentException("not a T132 autorun world: " + worldId);
        }
        Path root = savesRoot(minecraft);
        Path result = root.resolve(worldId).normalize();
        if (!result.getParent().equals(root)) {
            throw new IllegalStateException("unsafe T132 world path: " + result);
        }
        return result;
    }

    static void clearOwnedWorlds(Minecraft minecraft) throws IOException {
        deleteTree(worldPath(minecraft, SOURCE_WORLD_ID));
        deleteTree(worldPath(minecraft, RESTORED_WORLD_ID));
        deleteTree(TEMPLATE_ROOT.toAbsolutePath().normalize());
    }

    static Validation snapshot(Path source) {
        // The source is intentionally still open at this point.  Its active
        // lock must not be copied, but level.dat is a current-build file and
        // can be independently decoded before creating the pristine template.
        return copyValidated(source, TEMPLATE_ROOT.toAbsolutePath().normalize(), "snapshot", false);
    }

    static Validation restore(Minecraft minecraft) {
        return copyValidated(TEMPLATE_ROOT.toAbsolutePath().normalize(),
                worldPath(minecraft, RESTORED_WORLD_ID), "restore", true);
    }

    static Validation validate(Path root, String operation) {
        return validate(root, operation, true);
    }

    private static Validation validate(Path root, String operation, boolean requireNoLock) {
        Path normalized = root.toAbsolutePath().normalize();
        Path levelDat = normalized.resolve(LEVEL_DAT);
        Path lock = normalized.resolve(SESSION_LOCK);
        if (!Files.isRegularFile(levelDat)) {
            return Validation.invalid(operation, "missing_level_dat", normalized);
        }
        if (requireNoLock && Files.exists(lock)) {
            return Validation.invalid(operation, "stale_session_lock", normalized);
        }
        try {
            CompoundTag rootTag = NbtIo.readCompressed(levelDat.toFile());
            CompoundTag data = rootTag.getCompound("Data");
            int dataVersion = data.getInt("DataVersion");
            if (dataVersion <= 0) {
                return Validation.invalid(operation, "invalid_data_version", normalized);
            }
            return Validation.valid(operation, normalized, dataVersion);
        } catch (IOException exception) {
            return Validation.invalid(operation, "unreadable_level_dat:" + exception.getClass().getSimpleName(), normalized);
        }
    }

    private static Validation copyValidated(Path source, Path target, String operation, boolean sourceMustBeUnlocked) {
        Validation sourceValidation = validate(source, operation + "_source", sourceMustBeUnlocked);
        if (!sourceValidation.valid()) {
            return sourceValidation;
        }
        try {
            deleteTree(target);
            Files.createDirectories(target);
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                    Files.createDirectories(target.resolve(source.relativize(directory)));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!SESSION_LOCK.equals(file.getFileName().toString())) {
                        Files.copy(file, target.resolve(source.relativize(file)));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            Validation targetValidation = validate(target, operation);
            if (targetValidation.valid()) {
                ProjectAtmosphere.LOGGER.info(
                        "T132_AUTORUN_INFRA {} valid path={} dataVersion={} sessionLockExcluded=true",
                        operation, targetValidation.path(), targetValidation.dataVersion());
            }
            return targetValidation;
        } catch (IOException exception) {
            return Validation.invalid(operation, "copy_failed:" + exception.getClass().getSimpleName(), target);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    record Validation(boolean valid, String operation, String reason, Path path, int dataVersion) {
        static Validation valid(String operation, Path path, int dataVersion) {
            return new Validation(true, operation, "ok", path, dataVersion);
        }

        static Validation invalid(String operation, String reason, Path path) {
            return new Validation(false, operation, reason, path.toAbsolutePath().normalize(), 0);
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%s valid=%s reason=%s path=%s dataVersion=%d",
                    operation, valid, reason, path, dataVersion);
        }
    }
}
