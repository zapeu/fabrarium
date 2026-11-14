package net.zedaris.fabrarium.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import net.zedaris.fabrarium.persistence.DatabaseSettings;
import net.zedaris.fabrarium.persistence.JdbcTechRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MachineCoordinatorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void completesProductionAfterElapsedWallClockAcrossReload() {
        Path database = temporaryDirectory.resolve("machines.db");
        MachineKey key = new MachineKey(UUID.randomUUID(), 2, 70, 3);
        MutableClock clock = new MutableClock(1_000L);

        JdbcTechRepository firstRepository = repository(database);
        firstRepository.initialize().toCompletableFuture().join();
        MachineCoordinator first = new MachineCoordinator(firstRepository, clock, Duration.ofSeconds(8));
        first.load().toCompletableFuture().join();
        first.install(UUID.randomUUID(), key).toCompletableFuture().join();
        first.submit(UUID.randomUUID(), key).toCompletableFuture().join();
        firstRepository.close();

        clock.advance(Duration.ofSeconds(12));
        JdbcTechRepository secondRepository = repository(database);
        secondRepository.initialize().toCompletableFuture().join();
        MachineCoordinator restored = new MachineCoordinator(secondRepository, clock, Duration.ofSeconds(8));
        restored.load().toCompletableFuture().join();
        restored.tick(key).toCompletableFuture().join();

        assertEquals(MachinePhase.READY, restored.state(key).orElseThrow().phase());
        assertEquals(2, restored.collect(UUID.randomUUID(), key).toCompletableFuture().join());
        assertEquals(MachinePhase.IDLE, restored.state(key).orElseThrow().phase());
        secondRepository.close();
    }

    private static JdbcTechRepository repository(Path path) {
        return new JdbcTechRepository(new DatabaseSettings("jdbc:sqlite:" + path, "", "", "org.sqlite.JDBC"));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(long epochMilli) {
            this.instant = Instant.ofEpochMilli(epochMilli);
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
