package net.zedaris.fabrarium.bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import net.zedaris.fabrarium.persistence.DatabaseSettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigLoader {
    private ConfigLoader() {}

    public static PluginSettings load(JavaPlugin plugin) {
        Path dataDirectory = plugin.getDataFolder().toPath();
        Path configPath = dataDirectory.resolve("config.yml");
        try {
            Files.createDirectories(dataDirectory);
            if (Files.notExists(configPath)) {
                try (InputStream resource = plugin.getResource("config.yml")) {
                    if (resource == null) throw new IllegalStateException("bundled config.yml is missing");
                    Files.copy(resource, configPath, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("cannot prepare Fabrarium configuration", exception);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configPath.toFile());
        String url = config.getString("database.url", "").trim();
        if (url.isBlank()) {
            url = "jdbc:sqlite:" + dataDirectory.resolve("fabrarium.db").toAbsolutePath();
        }
        DatabaseSettings database = new DatabaseSettings(
                url,
                config.getString("database.username", ""),
                config.getString("database.password", ""),
                config.getString("database.driver", ""));
        long processingSeconds = Math.max(1, config.getLong("machine.processing-seconds", 8));
        return new PluginSettings(database, Duration.ofSeconds(processingSeconds));
    }
}
