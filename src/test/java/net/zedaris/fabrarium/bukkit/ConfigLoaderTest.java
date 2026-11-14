package net.zedaris.fabrarium.bukkit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {
    @Test
    void copiesBundledConfigWithoutRequiringFilesystemAttributes(@TempDir java.nio.file.Path directory)
            throws Exception {
        byte[] content = "database:\n  url: ''\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var destination = directory.resolve("config.yml");

        ConfigLoader.copyDefaultConfig(new ByteArrayInputStream(content), destination);

        assertArrayEquals(content, Files.readAllBytes(destination));
    }
}
