package net.zedaris.fabrarium.bukkit;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.zedaris.fabrarium.api.TechRpgService;
import net.zedaris.fabrarium.domain.MachineCoordinator;
import net.zedaris.fabrarium.domain.MachineKey;
import net.zedaris.fabrarium.domain.MachinePhase;
import net.zedaris.fabrarium.domain.MachineState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class MachineListener implements Listener {
    private static final Component PREFIX = Component.text("[Fabrarium] ", NamedTextColor.GOLD);

    private final Plugin plugin;
    private final MachineCoordinator machines;
    private final CustomItems items;
    private final TechRpgService service;
    private final SchedulerBridge scheduler;

    public MachineListener(
            Plugin plugin,
            MachineCoordinator machines,
            CustomItems items,
            TechRpgService service,
            SchedulerBridge scheduler) {
        this.plugin = plugin;
        this.machines = machines;
        this.items = items;
        this.service = service;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!items.is(event.getItemInHand(), CustomItems.ZEDRIUM_REFINERY)) return;
        MachineKey key = key(event.getBlockPlaced());
        UUID playerId = event.getPlayer().getUniqueId();
        scheduler.region(key, location -> {
            if (!markMachine(location)) {
                scheduler.message(playerId, error("A refinaria precisa ocupar um alto-forno."));
                return;
            }
            machines.install(UUID.randomUUID(), key).whenComplete((state, failure) -> {
                if (failure != null) {
                    plugin.getLogger().warning("Falha ao persistir refinaria " + key.stableValue() + ": " + failure.getMessage());
                    scheduler.region(key, failedLocation -> {
                        if (isMachine(failedLocation)) failedLocation.getBlock().setType(Material.AIR, false);
                    });
                    scheduler.give(playerId, required(CustomItems.ZEDRIUM_REFINERY, 1));
                }
            });
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        MachineKey key = key(event.getBlock());
        if (machines.state(key).isEmpty()) return;
        event.setCancelled(true);
        UUID playerId = event.getPlayer().getUniqueId();
        scheduler.region(key, location -> {
            if (!isMachine(location)) {
                removeMissing(key);
                return;
            }
            UUID operationId = UUID.randomUUID();
            machines.remove(operationId, key).whenComplete((removed, failure) -> {
                if (failure != null) {
                    scheduler.message(playerId, error("A refinaria esta ocupada; tente novamente."));
                    return;
                }
                scheduler.region(key, current -> {
                    if (isMachine(current)) {
                        current.getBlock().setType(Material.AIR, false);
                        current.getWorld().dropItemNaturally(current.clone().add(0.5, 0.5, 0.5),
                                required(CustomItems.ZEDRIUM_REFINERY, 1));
                    }
                });
            });
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null || event.getHand() == null) return;
        MachineKey key = key(clicked);
        MachineState known = machines.state(key).orElse(null);
        if (known == null) return;
        event.setCancelled(true);

        UUID playerId = event.getPlayer().getUniqueId();
        if (known.phase() == MachinePhase.PROCESSING) {
            long remainingMillis = Math.max(0, known.readyAtEpochMilli() - System.currentTimeMillis());
            long seconds = (remainingMillis + 999) / 1000;
            scheduler.message(playerId, info("Refino em andamento: " + seconds + "s."));
            return;
        }

        if (known.phase() == MachinePhase.READY) {
            scheduler.region(key, location -> collect(location, key, playerId));
            return;
        }

        ItemStack held = held(event);
        if (!items.is(held, CustomItems.RAW_ZEDRIUM) || held.getAmount() < 2) {
            scheduler.message(playerId, info("Insira 2 Zedriuns brutos para iniciar o refino."));
            return;
        }

        consume(event, 2);
        scheduler.region(key, location -> {
            if (!isMachine(location)) {
                scheduler.give(playerId, required(CustomItems.RAW_ZEDRIUM, 2));
                removeMissing(key);
                return;
            }
            machines.submit(UUID.randomUUID(), key).whenComplete((state, failure) -> {
                if (failure != null) {
                    scheduler.give(playerId, required(CustomItems.RAW_ZEDRIUM, 2));
                    scheduler.message(playerId, error("Nao foi possivel iniciar o refino."));
                } else {
                    scheduler.message(playerId, info("Refino iniciado."));
                }
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        String id = items.id(event.getRecipe().getResult()).orElse(null);
        if (id == null) return;
        long experience = CustomItems.ZEDRIUM_CORE.equals(id) ? 8 : 2;
        UUID playerId = event.getWhoClicked().getUniqueId();
        service.grantExperience(UUID.randomUUID(), playerId, experience).whenComplete((progress, failure) -> {
            if (failure == null) {
                scheduler.message(playerId, Component.text("+" + experience + " XP de tecnologia", NamedTextColor.AQUA));
            }
        });
    }

    public void tick(MachineKey key, Location location) {
        if (!isMachine(location)) {
            removeMissing(key);
            return;
        }
        machines.tick(key).exceptionally(failure -> {
            if (!(failure.getCause() instanceof IllegalStateException)) {
                plugin.getLogger().warning("Falha ao atualizar refinaria " + key.stableValue() + ": " + failure.getMessage());
            }
            return null;
        });
    }

    private void collect(Location location, MachineKey key, UUID playerId) {
        if (!isMachine(location)) {
            removeMissing(key);
            return;
        }
        UUID operationId = UUID.randomUUID();
        machines.collect(operationId, key).whenComplete((amount, failure) -> {
            if (failure != null) {
                scheduler.message(playerId, error("A saida ainda nao esta pronta."));
                return;
            }
            scheduler.give(playerId, required(CustomItems.ZEDRIUM_DUST, amount));
            UUID xpOperation = UUID.nameUUIDFromBytes((operationId + ":xp").getBytes(StandardCharsets.UTF_8));
            service.grantExperience(xpOperation, playerId, 5).whenComplete((progress, xpFailure) -> {
                if (xpFailure == null) {
                    scheduler.message(playerId, info("Coletados " + amount + " pos de Zedrium. Nivel tecnologico: "
                            + progress.level() + "."));
                }
            });
        });
    }

    private boolean markMachine(Location location) {
        Block block = location.getBlock();
        if (block.getType() != Material.BLAST_FURNACE) return false;
        BlockState state = block.getState();
        if (!(state instanceof TileState tile)) return false;
        tile.getPersistentDataContainer().set(
                items.machineTypeKey(), PersistentDataType.STRING, CustomItems.ZEDRIUM_REFINERY);
        return tile.update(true, false);
    }

    private boolean isMachine(Location location) {
        Block block = location.getBlock();
        if (block.getType() != Material.BLAST_FURNACE) return false;
        BlockState state = block.getState();
        if (!(state instanceof TileState tile)) return false;
        return CustomItems.ZEDRIUM_REFINERY.equals(tile.getPersistentDataContainer()
                .get(items.machineTypeKey(), PersistentDataType.STRING));
    }

    private void removeMissing(MachineKey key) {
        if (machines.state(key).isEmpty()) return;
        machines.remove(UUID.randomUUID(), key).exceptionally(failure -> null);
    }

    private static MachineKey key(Block block) {
        return new MachineKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private static ItemStack held(PlayerInteractEvent event) {
        return event.getHand() == EquipmentSlot.HAND
                ? event.getPlayer().getInventory().getItemInMainHand()
                : event.getPlayer().getInventory().getItemInOffHand();
    }

    private static void consume(PlayerInteractEvent event, int amount) {
        ItemStack held = held(event);
        int remaining = held.getAmount() - amount;
        if (remaining <= 0) {
            if (event.getHand() == EquipmentSlot.HAND) {
                event.getPlayer().getInventory().setItemInMainHand(null);
            } else {
                event.getPlayer().getInventory().setItemInOffHand(null);
            }
        } else {
            held.setAmount(remaining);
        }
    }

    private ItemStack required(String id, int amount) {
        return items.create(id, amount).orElseThrow();
    }

    private static Component info(String text) {
        return PREFIX.append(Component.text(text, NamedTextColor.GRAY));
    }

    private static Component error(String text) {
        return PREFIX.append(Component.text(text, NamedTextColor.RED));
    }
}
