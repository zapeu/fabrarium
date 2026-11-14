package net.zedaris.fabrarium.bukkit;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.zedaris.fabrarium.api.TechRpgService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FabrariumCommand implements CommandExecutor, TabCompleter {
    private final TechRpgService service;
    private final SchedulerBridge scheduler;

    public FabrariumCommand(TechRpgService service, SchedulerBridge scheduler) {
        this.service = service;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando requer um jogador.");
            return true;
        }
        String action = args.length == 0 ? "guia" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "guia" -> guide(player);
            case "progresso" -> progression(player);
            case "dar" -> give(player, args);
            default -> scheduler.message(player.getUniqueId(), Component.text(
                    "Use /" + label + " [guia|progresso|dar <item>]", NamedTextColor.YELLOW));
        }
        return true;
    }

    private void guide(Player player) {
        scheduler.message(player.getUniqueId(), Component.text(
                "Cadeia: Zedrium bruto -> Refinaria Fabrilis -> po -> fornalha -> lingote -> nucleo.",
                NamedTextColor.AQUA));
    }

    private void progression(Player player) {
        service.progression(player.getUniqueId()).whenComplete((progress, failure) -> {
            if (failure != null) {
                scheduler.message(player.getUniqueId(), Component.text("Falha ao consultar progressao.", NamedTextColor.RED));
                return;
            }
            scheduler.message(player.getUniqueId(), Component.text(
                    "Nivel " + progress.level() + " | XP " + progress.experience() + " | Desbloqueios: "
                            + (progress.unlocks().isEmpty() ? "nenhum" : String.join(", ", progress.unlocks())),
                    NamedTextColor.AQUA));
        });
    }

    private void give(Player player, String[] args) {
        if (!player.hasPermission("fabrarium.admin")) {
            scheduler.message(player.getUniqueId(), Component.text("Sem permissao.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            scheduler.message(player.getUniqueId(), Component.text("Informe o item.", NamedTextColor.YELLOW));
            return;
        }
        ItemStack item = service.createItem(args[1], 1).orElse(null);
        if (item == null) {
            scheduler.message(player.getUniqueId(), Component.text("Item desconhecido.", NamedTextColor.RED));
            return;
        }
        scheduler.give(player.getUniqueId(), item);
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) return List.of("guia", "progresso", "dar");
        if (args.length == 2 && "dar".equalsIgnoreCase(args[0])) {
            return List.of(
                    CustomItems.RAW_ZEDRIUM,
                    CustomItems.ZEDRIUM_DUST,
                    CustomItems.ZEDRIUM_INGOT,
                    CustomItems.ZEDRIUM_CORE,
                    CustomItems.ZEDRIUM_REFINERY);
        }
        return List.of();
    }
}
