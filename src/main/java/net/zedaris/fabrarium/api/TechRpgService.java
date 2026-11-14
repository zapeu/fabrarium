package net.zedaris.fabrarium.api;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.bukkit.inventory.ItemStack;

/** Public, runtime-discovered API for the Tech/RPG domain. */
public interface TechRpgService {
    String API_VERSION = "1.0";

    String apiVersion();

    CompletionStage<ProgressionView> progression(UUID playerId);

    CompletionStage<ProgressionView> grantExperience(UUID operationId, UUID playerId, long amount);

    Optional<ItemStack> createItem(String itemId, int amount);

    Set<RecipeView> recipes();
}
