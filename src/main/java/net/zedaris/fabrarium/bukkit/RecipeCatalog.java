package net.zedaris.fabrarium.bukkit;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.zedaris.fabrarium.api.RecipeView;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

public final class RecipeCatalog {
    private final Plugin plugin;
    private final CustomItems items;
    private final Set<NamespacedKey> keys = new LinkedHashSet<>();
    private final Set<RecipeView> views;

    public RecipeCatalog(Plugin plugin, CustomItems items) {
        this.plugin = plugin;
        this.items = items;
        this.views = Set.of(
                new RecipeView("zedaris:raw_zedrium", List.of("redstone", "amethyst_shard", "raw_iron"),
                        "2x raw_zedrium", "crafting_table"),
                new RecipeView("zedaris:zedrium_refinery", List.of("blast_furnace", "copper_ingot", "redstone", "amethyst_shard"),
                        "zedrium_refinery", "crafting_table"),
                new RecipeView("zedaris:refining", List.of("2x raw_zedrium"), "2x zedrium_dust", "zedrium_refinery"),
                new RecipeView("zedaris:zedrium_ingot", List.of("zedrium_dust", "fuel"), "zedrium_ingot", "furnace"),
                new RecipeView("zedaris:zedrium_core", List.of("4x zedrium_ingot", "redstone_block"), "zedrium_core", "crafting_table"));
    }

    public void register(Server server) {
        unregister(server);

        NamespacedKey rawKey = key("raw_zedrium");
        ShapelessRecipe raw = new ShapelessRecipe(rawKey, required(CustomItems.RAW_ZEDRIUM, 2));
        raw.addIngredient(Material.REDSTONE);
        raw.addIngredient(Material.AMETHYST_SHARD);
        raw.addIngredient(Material.RAW_IRON);
        server.addRecipe(raw);

        NamespacedKey refineryKey = key("zedrium_refinery");
        ShapedRecipe refinery = new ShapedRecipe(refineryKey, required(CustomItems.ZEDRIUM_REFINERY, 1));
        refinery.shape("CAC", "RBR", "CAC");
        refinery.setIngredient('C', Material.COPPER_INGOT);
        refinery.setIngredient('A', Material.AMETHYST_SHARD);
        refinery.setIngredient('R', Material.REDSTONE);
        refinery.setIngredient('B', Material.BLAST_FURNACE);
        server.addRecipe(refinery);

        NamespacedKey ingotKey = key("zedrium_ingot");
        FurnaceRecipe ingot = new FurnaceRecipe(
                ingotKey,
                required(CustomItems.ZEDRIUM_INGOT, 1),
                RecipeChoice.exactChoice(required(CustomItems.ZEDRIUM_DUST, 1)),
                0.8f,
                160);
        server.addRecipe(ingot);

        NamespacedKey coreKey = key("zedrium_core");
        ShapedRecipe core = new ShapedRecipe(coreKey, required(CustomItems.ZEDRIUM_CORE, 1));
        core.shape(" I ", "IRI", " I ");
        core.setIngredient('I', RecipeChoice.exactChoice(required(CustomItems.ZEDRIUM_INGOT, 1)));
        core.setIngredient('R', Material.REDSTONE_BLOCK);
        server.addRecipe(core);
    }

    public void unregister(Server server) {
        for (NamespacedKey key : keys) server.removeRecipe(key);
        keys.clear();
    }

    public Set<RecipeView> views() {
        return views;
    }

    private NamespacedKey key(String value) {
        NamespacedKey key = new NamespacedKey(plugin, value);
        keys.add(key);
        return key;
    }

    private ItemStack required(String id, int amount) {
        return items.create(id, amount).orElseThrow();
    }
}
