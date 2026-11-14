package net.zedaris.fabrarium.domain;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import net.zedaris.fabrarium.persistence.TechRepository;

public final class MachineCoordinator {
    private final TechRepository repository;
    private final Clock clock;
    private final Duration processingTime;
    private final Map<MachineKey, MachineState> machines = new ConcurrentHashMap<>();
    private final Set<MachineKey> pending = ConcurrentHashMap.newKeySet();

    public MachineCoordinator(TechRepository repository, Clock clock, Duration processingTime) {
        this.repository = repository;
        this.clock = clock;
        this.processingTime = processingTime;
        if (processingTime.isNegative() || processingTime.isZero()) {
            throw new IllegalArgumentException("processingTime must be positive");
        }
    }

    public CompletionStage<Integer> load() {
        return repository.loadMachines().thenApply(loaded -> {
            machines.clear();
            machines.putAll(loaded);
            return loaded.size();
        });
    }

    public Optional<MachineState> state(MachineKey key) {
        return Optional.ofNullable(machines.get(key));
    }

    public Set<MachineKey> keys() {
        return Set.copyOf(machines.keySet());
    }

    public CompletionStage<MachineState> install(UUID operationId, MachineKey key) {
        if (machines.containsKey(key)) return CompletableFuture.completedFuture(machines.get(key));
        return mutate(key, MachineState.idle(key), operationId);
    }

    public CompletionStage<MachineState> submit(UUID operationId, MachineKey key) {
        MachineState current = requiredState(key);
        return mutate(key, current.start(clock.millis() + processingTime.toMillis()), operationId);
    }

    public CompletionStage<MachineState> tick(MachineKey key) {
        MachineState current = requiredState(key);
        MachineState next = current.finish(clock.millis());
        if (next == current) return CompletableFuture.completedFuture(current);
        UUID operationId = deterministicOperation("finish", key, next.revision());
        return mutate(key, next, operationId);
    }

    public CompletionStage<Integer> collect(UUID operationId, MachineKey key) {
        MachineState current = requiredState(key);
        int amount = current.outputCount();
        return mutate(key, current.collect(), operationId).thenApply(ignored -> amount);
    }

    public CompletionStage<Boolean> remove(UUID operationId, MachineKey key) {
        if (!pending.add(key)) return CompletableFuture.failedFuture(new IllegalStateException("machine is busy"));
        return repository.deleteMachine(operationId, key).thenApply(deleted -> {
            machines.remove(key);
            return deleted;
        }).whenComplete((ignored, error) -> pending.remove(key));
    }

    private CompletionStage<MachineState> mutate(MachineKey key, MachineState next, UUID operationId) {
        if (!pending.add(key)) return CompletableFuture.failedFuture(new IllegalStateException("machine is busy"));
        return repository.saveMachine(operationId, next).thenApply(saved -> {
            machines.put(key, saved);
            return saved;
        }).whenComplete((ignored, error) -> pending.remove(key));
    }

    private MachineState requiredState(MachineKey key) {
        MachineState state = machines.get(key);
        if (state == null) throw new IllegalArgumentException("unknown machine: " + key.stableValue());
        return state;
    }

    private static UUID deterministicOperation(String action, MachineKey key, long revision) {
        String value = action + ":" + key.stableValue() + ":" + revision;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
