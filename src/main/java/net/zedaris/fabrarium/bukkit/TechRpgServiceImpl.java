package net.zedaris.fabrarium.bukkit;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import net.zedaris.fabrarium.api.ProgressionView;
import net.zedaris.fabrarium.api.RecipeView;
import net.zedaris.fabrarium.api.TechRpgService;
import net.zedaris.fabrarium.persistence.TechRepository;
import org.bukkit.inventory.ItemStack;

public final class TechRpgServiceImpl implements TechRpgService {
    private final TechRepository repository;
    private final CustomItems items;
    private final RecipeCatalog recipes;

    public TechRpgServiceImpl(TechRepository repository, CustomItems items, RecipeCatalog recipes) {
        this.repository = repository;
        this.items = items;
        this.recipes = recipes;
    }

    @Override
    public String apiVersion() {
        return API_VERSION;
    }

    @Override
    public CompletionStage<ProgressionView> progression(UUID playerId) {
        return repository.progression(playerId).thenApply(progress -> progress.view());
    }

    @Override
    public CompletionStage<ProgressionView> grantExperience(UUID operationId, UUID playerId, long amount) {
        return repository.addExperience(operationId, playerId, amount).thenApply(progress -> progress.view());
    }

    @Override
    public Optional<ItemStack> createItem(String itemId, int amount) {
        return items.create(itemId, amount).map(ItemStack::clone);
    }

    @Override
    public Set<RecipeView> recipes() {
        return recipes.views();
    }
}
