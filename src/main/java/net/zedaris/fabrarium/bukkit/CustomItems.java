package net.zedaris.fabrarium.bukkit;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class CustomItems {
    public static final String RAW_ZEDRIUM = "raw_zedrium";
    public static final String ZEDRIUM_DUST = "zedrium_dust";
    public static final String ZEDRIUM_INGOT = "zedrium_ingot";
    public static final String ZEDRIUM_CORE = "zedrium_core";
    public static final String ZEDRIUM_REFINERY = "zedrium_refinery";

    private final NamespacedKey itemIdKey;
    private final NamespacedKey machineTypeKey;

    public CustomItems(Plugin plugin) {
        itemIdKey = new NamespacedKey(plugin, "item_id");
        machineTypeKey = new NamespacedKey(plugin, "machine_type");
    }

    public Optional<ItemStack> create(String id, int amount) {
        if (amount < 1 || amount > 99) return Optional.empty();
        ItemStack item = switch (id.toLowerCase(Locale.ROOT)) {
            case RAW_ZEDRIUM -> item(Material.RAW_IRON, id, amount, "Zedrium Bruto", NamedTextColor.DARK_AQUA,
                    "Uma liga instavel pronta para refino.");
            case ZEDRIUM_DUST -> item(Material.GLOWSTONE_DUST, id, amount, "Po de Zedrium", NamedTextColor.AQUA,
                    "Refinado pelo Fabrarium.");
            case ZEDRIUM_INGOT -> item(Material.IRON_INGOT, id, amount, "Lingote de Zedrium", NamedTextColor.BLUE,
                    "Metal tecnologico de alta condutividade.");
            case ZEDRIUM_CORE -> item(Material.HEART_OF_THE_SEA, id, amount, "Nucleo de Zedrium", NamedTextColor.LIGHT_PURPLE,
                    "Componente avancado da engenharia ZeDaris.");
            case ZEDRIUM_REFINERY -> item(Material.BLAST_FURNACE, id, amount, "Refinaria Fabrilis", NamedTextColor.GOLD,
                    "Converte 2 Zedriuns brutos em 2 pos.");
            default -> null;
        };
        return Optional.ofNullable(item);
    }

    public Optional<String> id(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return Optional.empty();
        return Optional.ofNullable(item.getItemMeta().getPersistentDataContainer()
                .get(itemIdKey, PersistentDataType.STRING));
    }

    public boolean is(ItemStack item, String id) {
        return id(item).filter(id::equals).isPresent();
    }

    public NamespacedKey machineTypeKey() {
        return machineTypeKey;
    }

    private ItemStack item(
            Material material, String id, int amount, String name, NamedTextColor color, String lore) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text(name, color));
        meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }
}
