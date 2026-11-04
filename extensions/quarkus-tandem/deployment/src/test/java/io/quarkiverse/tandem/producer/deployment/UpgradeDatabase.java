package io.quarkiverse.tandem.producer.deployment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.function.Consumer;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A database migrated to an earlier Tandem schema before Quarkus starts (LLD-quarkus §13.4). Dev Services
 * starts its database with the application, too late for that, so each scenario starts its own. A
 * {@code QuarkusExtensionTest} class is initialised by two class loaders: the coordinates are kept in system
 * properties so the second initialisation reuses the database the first one prepared.
 */
record UpgradeDatabase(String url, String username, String password) {

    static UpgradeDatabase prepare(String scenario, Consumer<UpgradeDatabase> migrateToEarlierSchema) {
        String prefix = "tandem.test.upgrade." + scenario + ".";
        String url = System.getProperty(prefix + "url");
        if (url != null) {
            return new UpgradeDatabase(url, System.getProperty(prefix + "username"),
                    System.getProperty(prefix + "password"));
        }
        PostgreSQLContainer container = new PostgreSQLContainer(OutboxRows.POSTGRES_IMAGE);
        container.start(); // stopped by Testcontainers' reaper when the test JVM exits
        UpgradeDatabase database = new UpgradeDatabase(container.getJdbcUrl(), container.getUsername(),
                container.getPassword());
        migrateToEarlierSchema.accept(database);
        System.setProperty(prefix + "url", database.url());
        System.setProperty(prefix + "username", database.username());
        System.setProperty(prefix + "password", database.password());
        return database;
    }

    Connection connect() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
