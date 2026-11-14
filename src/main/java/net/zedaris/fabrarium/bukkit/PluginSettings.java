package net.zedaris.fabrarium.bukkit;

import java.time.Duration;
import net.zedaris.fabrarium.persistence.DatabaseSettings;

public record PluginSettings(DatabaseSettings database, Duration processingTime) {}
