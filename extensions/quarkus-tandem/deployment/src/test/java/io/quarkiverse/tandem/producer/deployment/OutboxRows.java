package io.quarkiverse.tandem.producer.deployment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads the database the tests write to with plain JDBC, outside any transaction. The schema's own names
 * appear here and nowhere else in the tests (LLD-quarkus §13.3).
 */
final class OutboxRows {

    static final String POSTGRES_IMAGE = "postgres:18";
    static final String DEV_SERVICES_IMAGE_KEY = "quarkus.datasource.devservices.image-name";

    /** The floor's last schema version: its file, and the index it creates. */
    static final String FLOOR_LAST_SCHEMA_FILE = "v5-claim-under-backlog.sql";
    static final String FLOOR_LAST_SCHEMA_INDEX = "idx_tandem_outbox_pending_id";

    /** One outbox row, as the relay would read it. */
    record Row(long id, String aggregateType, String aggregateId, long seq, String payload, String headers) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private OutboxRows() {
    }

    static Map<String, String> headersOf(Row row) {
        if (row.headers() == null) {
            return Map.of();
        }
        try {
            return JSON.readValue(row.headers(), new TypeReference<Map<String, String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static List<Row> all(DataSource dataSource) {
        List<Row> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT id, aggregate_type, aggregate_id, seq,"
                        + " payload::text, headers::text FROM tandem_outbox ORDER BY id")) {
            while (result.next()) {
                rows.add(new Row(result.getLong(1), result.getString(2), result.getString(3), result.getLong(4),
                        result.getString(5), result.getString(6)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return rows;
    }

    static String recordedBucketCount(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection
                        .prepareStatement("SELECT value FROM tandem_meta WHERE key = 'bucket_count'");
                ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getString(1) : null;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean hasOutboxTable(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT to_regclass('tandem_outbox') IS NOT NULL")) {
            result.next();
            return result.getBoolean(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean hasIndex(DataSource dataSource, String index) {
        return queryBoolean(dataSource, "SELECT to_regclass('" + index + "') IS NOT NULL");
    }

    /** The Liquibase changeset files in execution order, one entry per changeset. */
    static List<String> liquibaseHistory(DataSource dataSource) {
        return queryStrings(dataSource, "SELECT filename || '::' || id FROM databasechangelog ORDER BY orderexecuted");
    }

    /** The Flyway script file names in installation order (Quarkus records a script with its location). */
    static List<String> flywayHistory(DataSource dataSource) {
        return queryStrings(dataSource, "SELECT script FROM flyway_schema_history WHERE success ORDER BY installed_rank")
                .stream().map(script -> script.substring(script.lastIndexOf('/') + 1)).toList();
    }

    private static boolean queryBoolean(DataSource dataSource, String sql) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getBoolean(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> queryStrings(DataSource dataSource, String sql) {
        List<String> values = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                values.add(result.getString(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return values;
    }

    static void clear(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE tandem_outbox");
            statement.execute("TRUNCATE orders");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
