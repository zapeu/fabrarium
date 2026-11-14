package net.zedaris.fabrarium.api;

import java.util.List;

public record RecipeView(String id, List<String> inputs, String output, String station) {
    public RecipeView {
        inputs = List.copyOf(inputs);
    }
}
