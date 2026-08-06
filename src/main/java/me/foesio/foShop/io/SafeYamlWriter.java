package me.foesio.foShop.io;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class SafeYamlWriter {

    public WriteResult write(File target, YamlConfiguration yaml) {
        Path targetPath = target.toPath();
        Path parent = targetPath.getParent();
        Path backupPath = null;
        Path tempPath = null;

        try {
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            String fileName = target.getName();
            tempPath = parent.resolve(fileName + ".tmp." + System.nanoTime());
            Files.writeString(tempPath, yaml.saveToString(), StandardCharsets.UTF_8);

            YamlConfiguration verify = YamlConfiguration.loadConfiguration(tempPath.toFile());
            verify.getKeys(true);

            if (Files.exists(targetPath)) {
                Path backupDir = parent.resolve(".backup");
                Files.createDirectories(backupDir);
                backupPath = backupDir.resolve(fileName + "." + System.currentTimeMillis() + ".bak");
                Files.move(targetPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            }

            moveReplace(tempPath, targetPath);
            return new WriteResult(true, null, backupPath == null ? null : backupPath.toFile());
        } catch (Exception exception) {
            tryRestore(targetPath, backupPath);
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {
                }
            }
            return new WriteResult(false, exception.getMessage(), backupPath == null ? null : backupPath.toFile());
        }
    }

    private void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void tryRestore(Path targetPath, Path backupPath) {
        if (backupPath == null || !Files.exists(backupPath)) {
            return;
        }

        try {
            Files.move(backupPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    public record WriteResult(boolean success, String error, File backupFile) {
    }
}
