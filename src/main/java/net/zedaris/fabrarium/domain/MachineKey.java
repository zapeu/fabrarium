package net.zedaris.fabrarium.domain;

import java.util.UUID;

public record MachineKey(UUID worldId, int x, int y, int z) {
    public MachineKey {
        if (worldId == null) {
            throw new IllegalArgumentException("worldId is required");
        }
    }

    public String stableValue() {
        return worldId + ":" + x + ":" + y + ":" + z;
    }
}
