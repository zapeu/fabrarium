package net.zedaris.fabrarium.domain;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.zedaris.fabrarium.api.ProgressionView;

public record Progression(UUID playerId, long experience, int level, Set<String> unlocks, long revision) {
    private static final long[] THRESHOLDS = {0, 10, 30, 75};

    public Progression {
        if (playerId == null || experience < 0 || level < 0 || revision < 0) {
            throw new IllegalArgumentException("invalid progression");
        }
        unlocks = Set.copyOf(unlocks);
    }

    public static Progression initial(UUID playerId) {
        return new Progression(playerId, 0, 0, Set.of(), 0);
    }

    public Progression addExperience(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("experience must be positive");
        }
        long nextExperience = Math.addExact(experience, amount);
        int nextLevel = 0;
        for (int candidate = 1; candidate < THRESHOLDS.length; candidate++) {
            if (nextExperience >= THRESHOLDS[candidate]) {
                nextLevel = candidate;
            }
        }
        Set<String> nextUnlocks = new LinkedHashSet<>(unlocks);
        if (nextLevel >= 1) nextUnlocks.add("zedaris:zedrium_processing");
        if (nextLevel >= 2) nextUnlocks.add("zedaris:zedrium_engineering");
        if (nextLevel >= 3) nextUnlocks.add("zedaris:zedrium_mastery");
        return new Progression(playerId, nextExperience, nextLevel, nextUnlocks, revision + 1);
    }

    public ProgressionView view() {
        return new ProgressionView(playerId, experience, level, unlocks);
    }
}
