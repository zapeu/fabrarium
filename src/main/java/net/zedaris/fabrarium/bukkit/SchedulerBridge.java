package net.zedaris.fabrarium.bukkit;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.zedaris.fabrarium.domain.MachineKey;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;

public final class SchedulerBridge {
    private final Plugin plugin;
    private final Server server;

    public SchedulerBridge(Plugin plugin) {
        this.plugin = plugin;
        this.server = plugin.getServer();
    }

    public void region(MachineKey key, Consumer<Location> action) {
        server.getGlobalRegionScheduler().execute(plugin, () -> {
            World world = server.getWorld(key.worldId());
            if (world == null) return;
            Location location = new Location(world, key.x(), key.y(), key.z());
            server.getRegionScheduler().execute(plugin, location, () -> action.accept(location));
        });
    }

    public void player(UUID playerId, Consumer<Player> action) {
        server.getGlobalRegionScheduler().execute(plugin, () -> {
            Player player = server.getPlayer(playerId);
            if (player == null) return;
            EntityScheduler entityScheduler = player.getScheduler();
            entityScheduler.execute(plugin, () -> {
                Player ownedPlayer = server.getPlayer(playerId);
                if (ownedPlayer != null) action.accept(ownedPlayer);
            }, null, 1L);
        });
    }

    public void message(UUID playerId, Component message) {
        player(playerId, player -> player.sendMessage(message));
    }

    public void give(UUID playerId, ItemStack item) {
        player(playerId, player -> {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            for (ItemStack remaining : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), remaining);
            }
        });
    }
}
