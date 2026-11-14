package net.zedaris.fabrarium.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import net.zedaris.fabrarium.domain.MachineKey;
import net.zedaris.fabrarium.domain.MachinePhase;
import net.zedaris.fabrarium.domain.MachineState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcTechRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void repeatedOperationIdDoesNotDuplicateExperience() {
        JdbcTechRepository repository = repository("idempotency.db");
        repository.initialize().toCompletableFuture().join();
        UUID playerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        var first = repository.addExperience(operationId, playerId, 10).toCompletableFuture().join();
        var repeated = repository.addExperience(operationId, playerId, 10).toCompletableFuture().join();

        assertEquals(10, first.experience());
        assertEquals(first, repeated);
        assertEquals(10, repository.progression(playerId).toCompletableFuture().join().experience());
        repository.close();
    }

    @Test
    void reusedOperationIdWithDifferentPayloadFails() {
        JdbcTechRepository repository = repository("conflict.db");
        repository.initialize().toCompletableFuture().join();
        UUID operationId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        repository.addExperience(operationId, playerId, 2).toCompletableFuture().join();

        CompletionException failure = assertThrows(CompletionException.class,
                () -> repository.addExperience(operationId, playerId, 3).toCompletableFuture().join());

        assertInstanceOf(IdempotencyConflictException.class, failure.getCause());
        repository.close();
    }

    @Test
    void machineStateSurvivesRepositoryRestart() {
        MachineKey key = new MachineKey(UUID.randomUUID(), 10, 64, -12);
        MachineState processing = MachineState.idle(key).start(123_456L);
        JdbcTechRepository firstRepository = repository("restart.db");
        firstRepository.initialize().toCompletableFuture().join();
        firstRepository.saveMachine(UUID.randomUUID(), processing).toCompletableFuture().join();
        firstRepository.close();

        JdbcTechRepository secondRepository = repository("restart.db");
        secondRepository.initialize().toCompletableFuture().join();
        MachineState restored = secondRepository.findMachine(key).toCompletableFuture().join().orElseThrow();

        assertEquals(MachinePhase.PROCESSING, restored.phase());
        assertEquals(123_456L, restored.readyAtEpochMilli());
        assertTrue(secondRepository.loadMachines().toCompletableFuture().join().containsKey(key));
        secondRepository.close();
    }

    private JdbcTechRepository repository(String fileName) {
        return new JdbcTechRepository(new DatabaseSettings(
                "jdbc:sqlite:" + temporaryDirectory.resolve(fileName), "", "", "org.sqlite.JDBC"));
    }
}
