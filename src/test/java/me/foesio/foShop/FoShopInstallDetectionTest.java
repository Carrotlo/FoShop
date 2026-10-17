package me.foesio.foShop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoShopInstallDetectionTest {

    @Test
    void precreatedEmptyFolderIsFreshButAnyExistingOwnerFileIsNot(@TempDir Path root) throws Exception {
        Path data = root.resolve("FoShop");
        assertTrue(FoShop.isFreshInstall(data.toFile()));
        Files.createDirectory(data);
        assertTrue(FoShop.isFreshInstall(data.toFile()));
        Files.writeString(data.resolve("messages.yml"), "# Owner file\n");
        assertFalse(FoShop.isFreshInstall(data.toFile()));
    }
}
