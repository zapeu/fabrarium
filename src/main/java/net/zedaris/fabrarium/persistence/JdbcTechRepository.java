package net.zedaris.fabrarium.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.zedaris.fabrarium.domain.MachineKey;
import net.zedaris.fabrarium.domain.MachinePhase;
import net.zedaris.fabrarium.domain.MachineState;
import net.zedaris.fabrarium.domain.Progression;

public final class JdbcTechRepository implements TechRepository {
    private static final String CREATE_MACHINES = """
            CREATE TABLE IF NOT EXISTS fabrarium_machines (
              world_id TEXT NOT NULL,
              x INTEGER NOT NULL,
              y INTEGER NOT NULL,
              z INTEGER NOT NULL,
              phase TEXT NOT NULL,
              input_count INTEGER NOT NULL,
              output_count INTEGER NOT NULL,
              ready_at BIGINT NOT NULL,
              revision BIGINT NOT NULL,
              PRIMARY KEY (world_id, x, y, z)
            )
            """;
    private static final String CREATE_PROGRESS = """
            CREATE TABLE IF NOT EXISTS fabrarium_progress (
              player_id TEXT PRIMARY KEY,
              experience BIGINT NOT NULL,
              level INTEGER NOT NULL,
              unlocks TEXT NOT NULL,
              revision BIGINT NOT NULL
            )
            """;
    private static final String CREATE_OPERATIONS = """
            CREATE TABLE IF NOT EXISTS fabrarium_operations (
              operation_id TEXT PRIMARY KEY,
              operation_type TEXT NOT NULL,
              payload_hash TEXT NOT NULL,
              result_value TEXT NOT NULL,
              created_at BIGINT NOT NULL
            )
            """;

    private final DatabaseSettings settings;
    private final ExecutorService executor;

    public JdbcTechRepository(DatabaseSettings settings) {
        this.settings = settings;
        this.executor = Executors.newSingleThreadExecutor(Thread.ofPlatform()
                .name("fabrarium-database")
                .daemon(true)
                .factory());
    }

    @Override
    public CompletionStage<Void> initialize() {
        return run(() -> {
            loadDriver();
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.execute(CREATE_MACHINES);
                statement.execute(CREATE_PROGRESS);
                statement.execute(CREATE_OPERATIONS);
            }
            return null;
        });
    }

    @Override
    public CompletionStage<Map<MachineKey, MachineState>> loadMachines() {
        return run(() -> {
            Map<MachineKey, MachineState> machines = new LinkedHashMap<>();
            try (Connection connection = connection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT world_id,x,y,z,phase,input_count,output_count,ready_at,revision FROM fabrarium_machines");
                    ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    MachineState state = readMachine(results);
                    machines.put(state.key(), state);
                }
            }
            return Map.copyOf(machines);
        });
    }

    @Override
    public CompletionStage<Optional<MachineState>> findMachine(MachineKey key) {
        return run(() -> {
            try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                    "SELECT world_id,x,y,z,phase,input_count,output_count,ready_at,revision "
                            + "FROM fabrarium_machines WHERE world_id=? AND x=? AND y=? AND z=?")) {
                bindKey(statement, key, 1);
                try (ResultSet results = statement.executeQuery()) {
                    return results.next() ? Optional.of(readMachine(results)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public CompletionStage<MachineState> saveMachine(UUID operationId, MachineState state) {
        String payload = serialize(state);
        return runInTransaction(connection -> {
            Optional<String> prior = priorResult(connection, operationId, "save_machine", hash(payload));
            if (prior.isPresent()) {
                return deserializeMachine(prior.orElseThrow());
            }
            int updated;
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE fabrarium_machines SET phase=?,input_count=?,output_count=?,ready_at=?,revision=? "
                            + "WHERE world_id=? AND x=? AND y=? AND z=?")) {
                statement.setString(1, state.phase().name());
                statement.setInt(2, state.inputCount());
                statement.setInt(3, state.outputCount());
                statement.setLong(4, state.readyAtEpochMilli());
                statement.setLong(5, state.revision());
                bindKey(statement, state.key(), 6);
                updated = statement.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO fabrarium_machines "
                                + "(world_id,x,y,z,phase,input_count,output_count,ready_at,revision) VALUES (?,?,?,?,?,?,?,?,?)")) {
                    statement.setString(1, state.key().worldId().toString());
                    statement.setInt(2, state.key().x());
                    statement.setInt(3, state.key().y());
                    statement.setInt(4, state.key().z());
                    statement.setString(5, state.phase().name());
                    statement.setInt(6, state.inputCount());
                    statement.setInt(7, state.outputCount());
                    statement.setLong(8, state.readyAtEpochMilli());
                    statement.setLong(9, state.revision());
                    statement.executeUpdate();
                }
            }
            recordOperation(connection, operationId, "save_machine", hash(payload), payload);
            return state;
        });
    }

    @Override
    public CompletionStage<Boolean> deleteMachine(UUID operationId, MachineKey key) {
        String payload = key.stableValue();
        return runInTransaction(connection -> {
            Optional<String> prior = priorResult(connection, operationId, "delete_machine", hash(payload));
            if (prior.isPresent()) {
                return Boolean.parseBoolean(prior.orElseThrow());
            }
            int changed;
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM fabrarium_machines WHERE world_id=? AND x=? AND y=? AND z=?")) {
                bindKey(statement, key, 1);
                changed = statement.executeUpdate();
            }
            String result = Boolean.toString(changed > 0);
            recordOperation(connection, operationId, "delete_machine", hash(payload), result);
            return changed > 0;
        });
    }

    @Override
    public CompletionStage<Progression> progression(UUID playerId) {
        return run(() -> {
            try (Connection connection = connection()) {
                return readProgression(connection, playerId).orElseGet(() -> Progression.initial(playerId));
            }
        });
    }

    @Override
    public CompletionStage<Progression> addExperience(UUID operationId, UUID playerId, long amount) {
        if (amount <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("experience must be positive"));
        }
        String payload = playerId + ":" + amount;
        return runInTransaction(connection -> {
            Optional<String> prior = priorResult(connection, operationId, "add_experience", hash(payload));
            if (prior.isPresent()) {
                return deserializeProgression(prior.orElseThrow());
            }
            Progression next = readProgression(connection, playerId)
                    .orElseGet(() -> Progression.initial(playerId))
                    .addExperience(amount);
            int updated;
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE fabrarium_progress SET experience=?,level=?,unlocks=?,revision=? WHERE player_id=?")) {
                statement.setLong(1, next.experience());
                statement.setInt(2, next.level());
                statement.setString(3, String.join(",", next.unlocks()));
                statement.setLong(4, next.revision());
                statement.setString(5, playerId.toString());
                updated = statement.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO fabrarium_progress (player_id,experience,level,unlocks,revision) VALUES (?,?,?,?,?)")) {
                    statement.setString(1, playerId.toString());
                    statement.setLong(2, next.experience());
                    statement.setInt(3, next.level());
                    statement.setString(4, String.join(",", next.unlocks()));
                    statement.setLong(5, next.revision());
                    statement.executeUpdate();
                }
            }
            String result = serialize(next);
            recordOperation(connection, operationId, "add_experience", hash(payload), result);
            return next;
        });
    }

    private void loadDriver() throws ClassNotFoundException {
        String driver = settings.driverClass();
        if (driver.isBlank()) {
            driver = settings.url().startsWith("jdbc:postgresql:") ? "org.postgresql.Driver" : "org.sqlite.JDBC";
        }
        Class.forName(driver);
    }

    private Connection connection() throws SQLException {
        Properties properties = new Properties();
        if (!settings.username().isBlank()) properties.setProperty("user", settings.username());
        if (!settings.password().isBlank()) properties.setProperty("password", settings.password());
        return DriverManager.getConnection(settings.url(), properties);
    }

    private Optional<Progression> readProgression(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT experience,level,unlocks,revision FROM fabrarium_progress WHERE player_id=?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) return Optional.empty();
                return Optional.of(new Progression(
                        playerId,
                        results.getLong("experience"),
                        results.getInt("level"),
                        parseUnlocks(results.getString("unlocks")),
                        results.getLong("revision")));
            }
        }
    }

    private Optional<String> priorResult(Connection connection, UUID operationId, String type, String payloadHash)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT operation_type,payload_hash,result_value FROM fabrarium_operations WHERE operation_id=?")) {
            statement.setString(1, operationId.toString());
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) return Optional.empty();
                if (!type.equals(results.getString("operation_type"))
                        || !payloadHash.equals(results.getString("payload_hash"))) {
                    throw new IdempotencyConflictException("operationId reused with a different payload: " + operationId);
                }
                return Optional.of(results.getString("result_value"));
            }
        }
    }

    private void recordOperation(
            Connection connection, UUID operationId, String type, String payloadHash, String result) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO fabrarium_operations "
                        + "(operation_id,operation_type,payload_hash,result_value,created_at) VALUES (?,?,?,?,?)")) {
            statement.setString(1, operationId.toString());
            statement.setString(2, type);
            statement.setString(3, payloadHash);
            statement.setString(4, result);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private static MachineState readMachine(ResultSet results) throws SQLException {
        MachineKey key = new MachineKey(
                UUID.fromString(results.getString("world_id")),
                results.getInt("x"),
                results.getInt("y"),
                results.getInt("z"));
        return new MachineState(
                key,
                MachinePhase.valueOf(results.getString("phase")),
                results.getInt("input_count"),
                results.getInt("output_count"),
                results.getLong("ready_at"),
                results.getLong("revision"));
    }

    private static void bindKey(PreparedStatement statement, MachineKey key, int offset) throws SQLException {
        statement.setString(offset, key.worldId().toString());
        statement.setInt(offset + 1, key.x());
        statement.setInt(offset + 2, key.y());
        statement.setInt(offset + 3, key.z());
    }

    private static String serialize(MachineState state) {
        return String.join("|",
                state.key().worldId().toString(),
                Integer.toString(state.key().x()),
                Integer.toString(state.key().y()),
                Integer.toString(state.key().z()),
                state.phase().name(),
                Integer.toString(state.inputCount()),
                Integer.toString(state.outputCount()),
                Long.toString(state.readyAtEpochMilli()),
                Long.toString(state.revision()));
    }

    private static MachineState deserializeMachine(String value) {
        String[] fields = value.split("\\|", -1);
        if (fields.length != 9) throw new IllegalStateException("corrupt machine operation result");
        MachineKey key = new MachineKey(
                UUID.fromString(fields[0]),
                Integer.parseInt(fields[1]),
                Integer.parseInt(fields[2]),
                Integer.parseInt(fields[3]));
        return new MachineState(
                key,
                MachinePhase.valueOf(fields[4]),
                Integer.parseInt(fields[5]),
                Integer.parseInt(fields[6]),
                Long.parseLong(fields[7]),
                Long.parseLong(fields[8]));
    }

    private static String serialize(Progression progression) {
        return String.join("|",
                progression.playerId().toString(),
                Long.toString(progression.experience()),
                Integer.toString(progression.level()),
                String.join(",", progression.unlocks()),
                Long.toString(progression.revision()));
    }

    private static Progression deserializeProgression(String value) {
        String[] fields = value.split("\\|", -1);
        if (fields.length != 5) throw new IllegalStateException("corrupt progression operation result");
        return new Progression(
                UUID.fromString(fields[0]),
                Long.parseLong(fields[1]),
                Integer.parseInt(fields[2]),
                parseUnlocks(fields[3]),
                Long.parseLong(fields[4]));
    }

    private static Set<String> parseUnlocks(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return new LinkedHashSet<>(Arrays.asList(value.split(",")));
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private <T> CompletionStage<T> run(SqlSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("database operation failed", exception);
            }
        }, executor);
    }

    private <T> CompletionStage<T> runInTransaction(TransactionWork<T> work) {
        return run(() -> {
            try (Connection connection = connection()) {
                connection.setAutoCommit(false);
                try {
                    T result = work.execute(connection);
                    connection.commit();
                    return result;
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                }
            }
        });
    }

    @Override
    public void close() {
        executor.shutdown();
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    private interface TransactionWork<T> {
        T execute(Connection connection) throws Exception;
    }
}
