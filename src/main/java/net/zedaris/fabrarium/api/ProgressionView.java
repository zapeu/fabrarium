package net.zedaris.fabrarium.api;

import java.util.Set;
import java.util.UUID;

public record ProgressionView(UUID playerId, long experience, int level, Set<String> unlocks) {
    public ProgressionView {
        unlocks = Set.copyOf(unlocks);
    }
}
