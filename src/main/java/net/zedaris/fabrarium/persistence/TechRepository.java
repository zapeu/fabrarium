package net.zedaris.fabrarium.persistence;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import net.zedaris.fabrarium.domain.MachineKey;
import net.zedaris.fabrarium.domain.MachineState;
import net.zedaris.fabrarium.domain.Progression;

public interface TechRepository extends AutoCloseable {
    CompletionStage<Void> initialize();

    CompletionStage<Map<MachineKey, MachineState>> loadMachines();

    CompletionStage<Optional<MachineState>> findMachine(MachineKey key);

    CompletionStage<MachineState> saveMachine(UUID operationId, MachineState state);

    CompletionStage<Boolean> deleteMachine(UUID operationId, MachineKey key);

    CompletionStage<Progression> progression(UUID playerId);

    CompletionStage<Progression> addExperience(UUID operationId, UUID playerId, long amount);

    @Override
    void close();
}
