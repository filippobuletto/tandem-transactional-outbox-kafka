package io.quarkiverse.tandem.it.minimal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.testcontainers.postgresql.PostgreSQLContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * A PostgreSQL whose schema is applied outside Quarkus, with plain JDBC, from the SQL files the
 * {@code tandem-jdbc} jar ships: the application has no migration extension, so no schema-ready item exists
 * (HLD-quarkus §5.2).
 */
public class TandemSchemaDatabase implements QuarkusTestResourceLifecycleManager {

    static final String POSTGRES_IMAGE = "postgres:18";
    static final String URL = "tandem.it.minimal.url";
    static final String USERNAME = "tandem.it.minimal.username";
    static final String PASSWORD = "tandem.it.minimal.password";

    private static final String CHANGELOG = "tandem/schema/postgres/changelog/";
    private static final List<String> SCHEMA_FILES = List.of("v1-baseline.sql", "v2-replays.sql",
            "v3-managed-seq.sql", "v4-optional-seq.sql", "v5-claim-under-backlog.sql");

    private PostgreSQLContainer container;

    @Override
    public Map<String, String> start() {
        container = new PostgreSQLContainer(POSTGRES_IMAGE);
        container.start();
        System.setProperty(URL, container.getJdbcUrl());
        System.setProperty(USERNAME, container.getUsername());
        System.setProperty(PASSWORD, container.getPassword());
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            for (String file : SCHEMA_FILES) {
                statement.execute(read(CHANGELOG + file));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return Map.of("quarkus.datasource.jdbc.url", container.getJdbcUrl(),
                "quarkus.datasource.username", container.getUsername(),
                "quarkus.datasource.password", container.getPassword());
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }

    static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getProperty(URL), System.getProperty(USERNAME),
                System.getProperty(PASSWORD));
    }

    private static String read(String resource) {
        try (InputStream in = TandemSchemaDatabase.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Schema file not on the classpath resource:" + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
